(ns psql.types
  "Extend next.jdbc's SettableParameter and ReadableColumn protocols so that
   PostGIS geometry, PGobject (json/jsonb), SQL arrays and inet values move
   between Clojure data and PostgreSQL without manual wrapping."
  (:require [psql.coerce :as coerce]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [cheshire.core :as json])
  (:import [org.postgresql.util PGobject]
           [net.postgis.jdbc.geometry Geometry]
           [net.postgis.jdbc PGgeometryLW]
           [java.sql PreparedStatement]
           [java.net InetAddress]))

;;
;; Metadata helpers (handy when debugging which SQL type a column/parameter is)
;;

(defn pmd
  "Convert one column of ParameterMetaData to a map."
  [^java.sql.ParameterMetaData md i]
  {:parameter-class (.getParameterClassName md i)
   :parameter-mode (.getParameterMode md i)
   :parameter-type (.getParameterType md i)
   :parameter-type-name (.getParameterTypeName md i)
   :precision (.getPrecision md i)
   :scale (.getScale md i)
   :nullable? (.isNullable md i)
   :signed? (.isSigned md i)})

(defn rsmd
  "Convert one column of ResultSetMetaData to a map."
  [^java.sql.ResultSetMetaData md i]
  {:catalog-name (.getCatalogName md i)
   :column-class-name (.getColumnClassName md i)
   :column-display-size (.getColumnDisplaySize md i)
   :column-label (.getColumnLabel md i)
   :column-type (.getColumnType md i)
   :column-type-name (.getColumnTypeName md i)
   :precision (.getPrecision md i)
   :scale (.getScale md i)
   :schema-name (.getSchemaName md i)
   :table-name (.getTableName md i)
   :auto-increment? (.isAutoIncrement md i)
   :case-sensitive? (.isCaseSensitive md i)
   :currency? (.isCurrency md i)
   :definitely-writable? (.isDefinitelyWritable md i)
   :nullable? (.isNullable md i)
   :read-only? (.isReadOnly md i)
   :searchable? (.isSearchable md i)
   :signed? (.isSigned md i)
   :writable? (.isWritable md i)})

(defn- param-type-name
  "PostgreSQL SQL type name of parameter I (1-based) on prepared statement PS."
  [^PreparedStatement ps ^long i]
  (.getParameterTypeName (.getParameterMetaData ps) i))

;;;;
;; Write side: convert Clojure values into SQL parameters.
;;;;

;; multimethod selector keyed on the target SQL type name
(defn parameter-dispatch-fn
  [_ type-name]
  (keyword type-name))

(defn- to-pg-json
  [data json-type]
  (doto (PGobject.)
    (.setType (name json-type))
    (.setValue (json/generate-string data))))

;; Clojure maps -> SQL value, by target column type
(defmulti map->parameter parameter-dispatch-fn)

(defmethod map->parameter :geometry
  [m _]
  (PGgeometryLW. ^Geometry (coerce/geojson->postgis m)))

(defmethod map->parameter :json
  [m _]
  (to-pg-json m :json))

(defmethod map->parameter :jsonb
  [m _]
  (to-pg-json m :jsonb))

(defmethod map->parameter :default
  [m _]
  m)

;; Clojure vectors -> SQL value, by target column type
(defmulti vec->parameter parameter-dispatch-fn)

(defmethod vec->parameter :json
  [v _]
  (to-pg-json v :json))

(defmethod vec->parameter :jsonb
  [v _]
  (to-pg-json v :jsonb))

(defmethod vec->parameter :inet
  [v _]
  (if (= (count v) 4)
    (doto (PGobject.) (.setType "inet") (.setValue (str/join "." v)))
    v))

(defmethod vec->parameter :default
  [v _]
  v)

;; Numbers -> SQL value, by target column type
(defmulti num->parameter parameter-dispatch-fn)

(defmethod num->parameter :timestamptz
  [v _]
  (java.sql.Timestamp. (long v)))

(defmethod num->parameter :timestamp
  [v _]
  (java.sql.Timestamp. (long v)))

(defmethod num->parameter :default
  [v _]
  v)

(extend-protocol prepare/SettableParameter
  ;; Maps become json/jsonb/geometry depending on the column type.
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement ps ^long i]
    (.setObject ps i (map->parameter m (keyword (param-type-name ps i)))))

  ;; Vectors become SQL arrays, or json/jsonb/inet for those column types.
  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement ps ^long i]
    (let [type-name (param-type-name ps i)]
      (if-let [elem-type (when type-name (second (re-find #"^_(.*)" type-name)))]
        (.setObject ps i (.createArrayOf (.getConnection ps) elem-type (to-array v)))
        (.setObject ps i (vec->parameter v type-name)))))

  ;; Any other seqable (lists, lazy seqs) is handled like a vector.
  clojure.lang.Seqable
  (set-parameter [seqable ^PreparedStatement ps ^long i]
    (prepare/set-parameter (vec (seq seqable)) ps i))

  ;; Numbers may need coercion for e.g. timestamp columns.
  java.lang.Number
  (set-parameter [n ^PreparedStatement ps ^long i]
    (.setObject ps i (num->parameter n (param-type-name ps i))))

  ;; Inet addresses map onto PostgreSQL inet.
  InetAddress
  (set-parameter [^InetAddress inet-addr ^PreparedStatement ps ^long i]
    (.setObject ps i (doto (PGobject.)
                       (.setType "inet")
                       (.setValue (.getHostAddress inet-addr)))))

  ;; PostGIS geometry objects wrap into the lightweight PGgeometry.
  Geometry
  (set-parameter [^Geometry g ^PreparedStatement ps ^long i]
    (.setObject ps i (PGgeometryLW. ^Geometry g))))

;;;;
;; Read side: convert SQL result values into Clojure data.
;;;;

(defn read-pg-vector
  "oidvector, int2vector, etc. are space separated lists."
  [s]
  (when (seq s) (str/split s #"\s+")))

(defn read-pg-array
  "Arrays are of the form {1,2,3}."
  [s]
  (when (seq s)
    (when-let [[_ content] (re-matches #"^\{(.+)\}$" s)]
      (if-not (empty? content) (str/split content #"\s*,\s*") []))))

(defmulti read-pgobject
  "Convert a returned PGobject to a Clojure value."
  (fn [^PGobject x] (keyword (when x (.getType x)))))

(defmethod read-pgobject :oidvector
  [^PGobject x]
  (when-let [v (.getValue x)] (mapv read-string (read-pg-vector v))))

(defmethod read-pgobject :int2vector
  [^PGobject x]
  (when-let [v (.getValue x)] (mapv read-string (read-pg-vector v))))

(defmethod read-pgobject :anyarray
  [^PGobject x]
  (when-let [v (.getValue x)] (vec (read-pg-array v))))

(defmethod read-pgobject :json
  [^PGobject x]
  (when-let [v (.getValue x)] (json/parse-string v)))

(defmethod read-pgobject :jsonb
  [^PGobject x]
  (when-let [v (.getValue x)] (json/parse-string v)))

(defmethod read-pgobject :default
  [^PGobject x]
  (.getValue x))

(extend-protocol rs/ReadableColumn
  ;; Return the PostGIS geometry (as GeoJSON) instead of the PGgeometry wrapper.
  net.postgis.jdbc.PGgeometry
  (read-column-by-label [^net.postgis.jdbc.PGgeometry v _]
    (coerce/postgis->geojson (.getGeometry v)))
  (read-column-by-index [^net.postgis.jdbc.PGgeometry v _ _]
    (coerce/postgis->geojson (.getGeometry v)))

  ;; Parse SQLXML into a Clojure map representing the XML content.
  java.sql.SQLXML
  (read-column-by-label [^java.sql.SQLXML v _]
    (xml/parse (.getBinaryStream v)))
  (read-column-by-index [^java.sql.SQLXML v _ _]
    (xml/parse (.getBinaryStream v)))

  ;; Convert java.sql.Array to a Clojure vector.
  java.sql.Array
  (read-column-by-label [^java.sql.Array v _]
    (vec (.getArray v)))
  (read-column-by-index [^java.sql.Array v _ _]
    (vec (.getArray v)))

  ;; PGobjects route through the read-pgobject multimethod.
  PGobject
  (read-column-by-label [^PGobject v _]
    (read-pgobject v))
  (read-column-by-index [^PGobject v _ _]
    (read-pgobject v)))
