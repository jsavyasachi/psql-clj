(ns psql.core
  "Use PostgreSQL from Clojure with connection parameter defaults from PGDATABASE, PGHOST, PGPORT, and PGUSER.
  Read the password from ~/.pgpass when it is available."
  (:require [clojure.string :as str]
            [psql.types]
            [psql.pool :refer [pooled-db] :as pool]
            [psql.pgpass :as pgpass]
            [psql.service :as service]
            [next.jdbc :as jdbc])
  (:import org.postgresql.util.PGobject
           org.postgresql.util.PGmoney
           org.postgresql.util.PGInterval
           org.postgresql.geometric.PGbox
           org.postgresql.geometric.PGcircle
           org.postgresql.geometric.PGline
           org.postgresql.geometric.PGlseg
           org.postgresql.geometric.PGpath
           org.postgresql.geometric.PGpoint
           org.postgresql.geometric.PGpolygon))

(defn getenv->map
  "Keywordize an environment-style map. With no argument, read (System/getenv).
  Accept any map-like input. Callers can pass a plain map in tests."
  ([env]
   {:post [(map? %)]}
   (persistent!
    (reduce-kv (fn [acc k v] (assoc! acc (keyword k) v))
               (transient {})
               (into {} env))))
  ([]
   (getenv->map (System/getenv))))

(defn default-spec
  "Set defaults like the psql command line tool.
  Use the username for the user and dbname. Do not set a host."
  []
  (let [username (java.lang.System/getProperty "user.name")]
    {:dbtype "postgresql"
     :user username
     :dbname username}))

(defn- guc-option
  "Render a libpq server-setting env var as a `-c NAME=value` startup option,
  backslash-escaping spaces in the value the way libpq's options string requires."
  [setting value]
  (str "-c " setting "=" (str/replace value " " "\\ ")))

(defn- env-options
  "Merge PGOPTIONS with the PGTZ and PGDATESTYLE server settings into a single
  pgjdbc `options` string, or nil when none of the three are present."
  [{:keys [PGOPTIONS PGTZ PGDATESTYLE]}]
  (let [parts (cond-> []
                PGOPTIONS (conj PGOPTIONS)
                PGTZ (conj (guc-option "TimeZone" PGTZ))
                PGDATESTYLE (conj (guc-option "DateStyle" PGDATESTYLE)))]
    (when (seq parts) (str/join " " parts))))

(defn env-spec
  "Get a db spec from libpq PG* environment variables, using pgjdbc property
  names where they differ. `PGTZ` and `PGDATESTYLE` are folded into the pgjdbc
  `options` string (merged after any `PGOPTIONS`).

  Not supported, because pgjdbc has no equivalent: `PGHOSTADDR` (no DNS-bypass
  address; use `PGHOST`), `PGCLIENTENCODING` (pgjdbc always speaks UTF-8),
  `PGREQUIREPEER`, `PGSSLSNI`, `PGSSLCRL`, the SSL min/max protocol vars, and
  Unix-domain sockets (pgjdbc needs a custom socket factory)."
  [{:keys [PGDATABASE PGHOST PGPORT PGUSER PGTARGETSESSIONATTRS] :as env}]
  {:pre [(map? env)]
   :post [(map? %)]}
  (let [env-properties {:PGSSLMODE :sslmode
                        :PGSSLCERT :sslcert
                        :PGSSLKEY :sslkey
                        :PGSSLROOTCERT :sslrootcert
                        :PGSSLPASSWORD :sslpassword
                        :PGAPPNAME :ApplicationName
                        :PGCONNECT_TIMEOUT :connectTimeout
                        :PGCHANNELBINDING :channelBinding
                        :PGGSSENCMODE :gssEncMode
                        :PGGSSLIB :gsslib
                        :PGKRBSRVNAME :kerberosServerName
                        :PGREQUIREAUTH :requireAuth
                        :PGSSLNEGOTIATION :sslNegotiation
                        :PGLOADBALANCEHOSTS :loadBalanceHosts}
        target-server-types {"any" "any"
                             "read-write" "primary"
                             "read-only" "secondary"
                             "primary" "primary"
                             "standby" "secondary"
                             "prefer-standby" "preferSecondary"}]
    (cond-> (reduce-kv (fn [spec env-key property-key]
                         (if-let [value (get env env-key)]
                           (assoc spec property-key value)
                           spec))
                       {}
                       env-properties)
      PGDATABASE (assoc :dbname PGDATABASE)
      PGHOST (assoc :host PGHOST)
      PGPORT (assoc :port PGPORT)
      PGUSER (assoc :user PGUSER)
      (env-options env) (assoc :options (env-options env))
      PGTARGETSESSIONATTRS
      (assoc :targetServerType
             (get target-server-types PGTARGETSESSIONATTRS PGTARGETSESSIONATTRS)))))

(defn spec
  "Create a PostgreSQL database spec with libpq-style layers. Explicit options override a :service/PGSERVICE definition.
  The service definition overrides ordinary PG* environment values. Environment values override built-in defaults. Accept overrides:
  (spec :dbname ... :host ... :port ... :user ... :password ...)

  Password precedence uses the same layers, then uses ~/.pgpass.
  Pass :env with a plain environment map for repeatable tests."
  [& {:keys [password service env] :as opts}]
  {:post [(contains? % :dbname)
          (contains? % :user)]}
  (let [env (getenv->map (or env (System/getenv)))
        service-spec (service/resolve-service (or service (:PGSERVICE env)) env)
        explicit-opts (dissoc opts :password :service :env)
        db-spec (merge (default-spec)
                       (env-spec env)
                       service-spec
                       explicit-opts)
        password (or password
                     (:password service-spec)
                     (:PGPASSWORD env)
                     (pgpass/pgpass-lookup db-spec))]
    (cond-> (dissoc db-spec :password)
      password (assoc :password password))))

(defn pool
  [& rest]
  (let [m (apply spec rest)]
    (pooled-db m {})))

(defn close!
  "Close db-spec if possible. Return true if the datasource can close and is closed."
  [{:keys [datasource]}]
  (when (instance? java.io.Closeable datasource)
    (.close ^java.io.Closeable datasource)
    true))

(defn tables
  "Return the set of table names (as keywords) visible in the database DB."
  [db]
  (with-open [conn (jdbc/get-connection db)]
    (let [md (.getMetaData ^java.sql.Connection conn)
          rs (.getTables md nil nil nil (into-array String ["TABLE"]))]
      (loop [acc (transient #{})]
        (if (.next rs)
          (recur (conj! acc (keyword (.getString rs "TABLE_NAME"))))
          (persistent! acc))))))

;;
;; Types
;;

(defn object
  "Create a custom PGobject. Example: (pg/object \"json\" \"{}\")"
  [type value]
  (doto (PGobject.)
    (.setType (name type))
    (.setValue (str value))))

(defn interval
  "Create a PGinterval. Example: (pg/interval :hours 2)"
  [& {:keys [years months days hours minutes seconds]
      :or {years 0 months 0 days 0 hours 0 minutes 0 seconds 0.0}}]
  (PGInterval. years months days hours minutes ^double seconds))

(defn money
  "Create a PGmoney object."
  [amount]
  (PGmoney. ^double amount))

(defn xml
  "Create a PostgreSQL XML object."
  [s]
  (object :xml (str s)))

(defn tsvector
  "Create a PostgreSQL `tsvector` (full-text document) value."
  [s]
  (object :tsvector (str s)))

(defn tsquery
  "Create a PostgreSQL `tsquery` (full-text query) value."
  [s]
  (object :tsquery (str s)))

(defn jsonpath
  "Create a PostgreSQL `jsonpath` value."
  [s]
  (object :jsonpath (str s)))

(defn macaddr
  "Create a PostgreSQL `macaddr` (6-byte MAC address) value."
  [s]
  (object :macaddr (str s)))

(defn macaddr8
  "Create a PostgreSQL `macaddr8` (8-byte EUI-64 MAC address) value."
  [s]
  (object :macaddr8 (str s)))

(defn pg-lsn
  "Create a PostgreSQL `pg_lsn` (log sequence number) value."
  [s]
  (object :pg_lsn (str s)))

;;
;; Constructors for geometric types
;;

(defn point
  "Create a PGpoint object."
  ([x y]
   (PGpoint. x y))
  ([obj]
   (cond
     (instance? PGpoint obj) obj
     (coll? obj) (point (first obj) (second obj))
     :else (PGpoint. (str obj)))))

(defn box
  "Create a PGbox object."
  ([p1 p2]
   (PGbox. (point p1) (point p2)))
  ([x1 y1 x2 y2]
   (PGbox. x1 y1 x2 y2))
  ([obj]
   (if (instance? PGbox obj)
     obj
     (PGbox. (str obj)))))

(defn circle
  "Create a PGcircle object."
  ([x y r]
   (PGcircle. x y r))
  ([center-point r]
   (PGcircle. (point center-point) r))
  ([obj]
   (if (instance? PGcircle obj)
     obj
     (PGcircle. (str obj)))))

(defn line
  "Create a PGline object."
  ([x1 y1 x2 y2]
   (PGline. x1 y1 x2 y2))
  ([p1 p2]
   (PGline. (point p1) (point p2)))
  ([obj]
   (if (instance? PGline obj)
     obj
     (PGline. (str obj)))))

(defn lseg
  "Create a PGlseg object."
  ([x1 y1 x2 y2]
   (PGlseg. x1 y1 x2 y2))
  ([p1 p2]
   (PGlseg. (point p1) (point p2)))
  ([obj]
   (if (instance? PGlseg obj)
     obj
     (PGlseg. (str obj)))))

(defn path
  "Create a PGpath object."
  ([points open?]
   (PGpath. (into-array PGpoint (map point points)) open?))
  ([obj]
   (if (instance? PGpath obj)
     obj
     (PGpath. (str obj)))))

(defn polygon
  "Create a PGpolygon object."
  [points-or-str]
  (if (coll? points-or-str)
    (PGpolygon. ^"[Lorg.postgresql.geometric.PGpoint;" (into-array PGpoint (map point points-or-str)))
    (PGpolygon. ^String (str points-or-str))))
