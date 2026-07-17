(ns psql.core
  "Allow using PostgreSQL from Clojure as effortlessly as possible by reading connection parameter defaults from
  PostgreSQL environment variables PGDATABASE, PGHOST, PGPORT, PGUSER and by reading password from ~/.pgpass if available."
  (:require [psql.types]
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
  "Keywordize an environment-style map. With no argument, reads (System/getenv).
  Accepts any map-like input so callers can pass a plain map in tests."
  ([env]
   {:post [(map? %)]}
   (persistent!
    (reduce-kv (fn [acc k v] (assoc! acc (keyword k) v))
               (transient {})
               (into {} env))))
  ([]
   (getenv->map (System/getenv))))

(defn default-spec
  "Reasonable defaults as with the psql command line tool.
  Use username for user and db. Don't use host."
  []
  (let [username (java.lang.System/getProperty "user.name")]
    {:dbtype "postgresql"
     :user username
     :dbname username}))

(defn env-spec
  "Get a db spec from libpq PG* environment variables, translated to pgjdbc
  property names where needed. PGHOSTADDR, PGCLIENTENCODING, PGREQUIREPEER,
  PGSSLCRL/SNI/min-max-proto, and the Unix-socket default host are unsupported."
  [{:keys [PGDATABASE PGHOST PGPORT PGUSER PGTARGETSESSIONATTRS] :as env}]
  {:pre [(map? env)]
   :post [(map? %)]}
  (let [env-properties {:PGSSLMODE :sslmode
                        :PGSSLCERT :sslcert
                        :PGSSLKEY :sslkey
                        :PGSSLROOTCERT :sslrootcert
                        :PGAPPNAME :ApplicationName
                        :PGCONNECT_TIMEOUT :connectTimeout
                        :PGOPTIONS :options
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
      PGTARGETSESSIONATTRS
      (assoc :targetServerType
             (get target-server-types PGTARGETSESSIONATTRS PGTARGETSESSIONATTRS)))))

(defn spec
  "Create a PostgreSQL database spec with libpq-style layering: explicit
  options override a :service/PGSERVICE definition, which overrides ordinary
  PG* environment values, which override built-in defaults. Accepts overrides:
  (spec :dbname ... :host ... :port ... :user ... :password ...)

  Password precedence follows the same layers, then falls back to ~/.pgpass.
  Pass :env with a plain environment map for deterministic use in tests."
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
  "Close db-spec if possible. Return true if the datasource was closeable and closed."
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
  "Make a custom PGobject, e.g. (pg/object \"json\" \"{}\")"
  [type value]
  (doto (PGobject.)
    (.setType (name type))
    (.setValue (str value))))

(defn interval
  "Create a PGinterval. (pg/interval :hours 2)"
  [& {:keys [years months days hours minutes seconds]
      :or {years 0 months 0 days 0 hours 0 minutes 0 seconds 0.0}}]
  (PGInterval. years months days hours minutes ^double seconds))

(defn money
  "Create PGmoney object"
  [amount]
  (PGmoney. ^double amount))

(defn xml
  "Make PostgreSQL XML object"
  [s]
  (object :xml (str s)))

;;
;; Constructors for geometric Types
;;

(defn point
  "Create a PGpoint object"
  ([x y]
   (PGpoint. x y))
  ([obj]
   (cond
     (instance? PGpoint obj) obj
     (coll? obj) (point (first obj) (second obj))
     :else (PGpoint. (str obj)))))

(defn box
  "Create a PGbox object"
  ([p1 p2]
   (PGbox. (point p1) (point p2)))
  ([x1 y1 x2 y2]
   (PGbox. x1 y1 x2 y2))
  ([obj]
   (if (instance? PGbox obj)
     obj
     (PGbox. (str obj)))))

(defn circle
  "Create a PGcircle object"
  ([x y r]
   (PGcircle. x y r))
  ([center-point r]
   (PGcircle. (point center-point) r))
  ([obj]
   (if (instance? PGcircle obj)
     obj
     (PGcircle. (str obj)))))

(defn line
  "Create a PGline object"
  ([x1 y1 x2 y2]
   (PGline. x1 y1 x2 y2))
  ([p1 p2]
   (PGline. (point p1) (point p2)))
  ([obj]
   (if (instance? PGline obj)
     obj
     (PGline. (str obj)))))

(defn lseg
  "Create a PGlseg object"
  ([x1 y1 x2 y2]
   (PGlseg. x1 y1 x2 y2))
  ([p1 p2]
   (PGlseg. (point p1) (point p2)))
  ([obj]
   (if (instance? PGlseg obj)
     obj
     (PGlseg. (str obj)))))

(defn path
  "Create a PGpath object"
  ([points open?]
   (PGpath. (into-array PGpoint (map point points)) open?))
  ([obj]
   (if (instance? PGpath obj)
     obj
     (PGpath. (str obj)))))

(defn polygon
  "Create a PGpolygon object"
  [points-or-str]
  (if (coll? points-or-str)
    (PGpolygon. ^"[Lorg.postgresql.geometric.PGpoint;" (into-array PGpoint (map point points-or-str)))
    (PGpolygon. ^String (str points-or-str))))
