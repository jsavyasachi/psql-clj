(ns psql.types
  "Extend next.jdbc's SettableParameter and ReadableColumn protocols so that
   PGobject (json/jsonb/enum), SQL arrays and inet values move between Clojure
   data and PostgreSQL without manual wrapping.

   The map->parameter / vec->parameter / num->parameter multimethods are the
   extension seam: companion artifacts (e.g. psql-clj-gis) add methods for their
   own SQL types without this namespace depending on them."
  (:refer-clojure :exclude [range])
  (:require [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [cheshire.core :as json-codec])
  (:import [org.postgresql.geometric PGbox PGcircle PGline PGlseg PGpath
            PGpoint PGpolygon]
           [org.postgresql.util PGInterval PGmoney PGobject]
           [java.sql PreparedStatement]
           [java.net InetAddress]
           [java.math BigDecimal]))

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
    (.setValue (json-codec/generate-string data))))

(defn json
  "Tag any Clojure value, including scalars and nil, as PostgreSQL json."
  [value]
  (to-pg-json value :json))

(defn jsonb
  "Tag any Clojure value, including scalars and nil, as PostgreSQL jsonb."
  [value]
  (to-pg-json value :jsonb))

(defn range
  "Create a lossless Clojure representation of a PostgreSQL range. Bounds are
  strings or nil for unbounded; options describe inclusivity and emptiness."
  [lower upper & {:keys [lower-inclusive? upper-inclusive? empty?]
                  :or {lower-inclusive? true
                       upper-inclusive? false
                       empty? false}}]
  {:lower (when-not empty? lower)
   :upper (when-not empty? upper)
   :lower-inclusive? (if empty? false lower-inclusive?)
   :upper-inclusive? (if empty? false upper-inclusive?)
   :empty? empty?})

(defn- quote-range-bound
  [bound]
  (when (some? bound)
    (let [s (str bound)]
      (if (or (empty? s) (re-find #"[\s,\[\]\(\)\"\\]" s))
        (str "\"" (-> s
                       (str/replace "\\" "\\\\")
                       (str/replace "\"" "\\\"")) "\"")
        s))))

(defn- range->string
  [{:keys [lower upper lower-inclusive? upper-inclusive? empty?]}]
  (if empty?
    "empty"
    (str (if lower-inclusive? "[" "(")
         (quote-range-bound lower) "," (quote-range-bound upper)
         (if upper-inclusive? "]" ")"))))

(defn- to-pg-range
  [value type-name]
  (doto (PGobject.)
    (.setType type-name)
    (.setValue (range->string value))))

(defn- to-pg-multirange
  [values type-name]
  (doto (PGobject.)
    (.setType type-name)
    (.setValue (str "{" (str/join "," (map range->string values)) "}"))))

(defn- clj->java-array
  [value]
  (object-array
   (map #(if (sequential? %) (clj->java-array %) %) value)))

(defn- java-array->clj
  [value]
  (if (and value (.isArray ^Class (class value)))
    (mapv #(java-array->clj (java.lang.reflect.Array/get value %))
          (clojure.core/range (java.lang.reflect.Array/getLength value)))
    value))

(defn- inet-value
  [{:keys [address prefix]}]
  (let [host (cond
               (instance? InetAddress address) (.getHostAddress ^InetAddress address)
               (sequential? address) (str/join "." address)
               :else (str address))]
    (str host (when (some? prefix) (str "/" prefix)))))

(defn- to-pg-inet
  [value type-name]
  (doto (PGobject.)
    (.setType type-name)
    (.setValue (inet-value value))))

;; Clojure maps -> SQL value, by target column type
(defmulti map->parameter parameter-dispatch-fn)

(defmethod map->parameter :json
  [m _]
  (to-pg-json m :json))

(defmethod map->parameter :jsonb
  [m _]
  (to-pg-json m :jsonb))

(doseq [type-name ["int4range" "int8range" "numrange" "tsrange"
                   "tstzrange" "daterange"]]
  (defmethod map->parameter (keyword type-name)
    [m _]
    (to-pg-range m type-name)))

(doseq [type-name ["inet" "cidr"]]
  (defmethod map->parameter (keyword type-name)
    [m _]
    (to-pg-inet m type-name)))

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

(defmethod vec->parameter :cidr
  [v _]
  (if (= (count v) 4)
    (to-pg-inet {:address v} "cidr")
    v))

(doseq [type-name ["int4multirange" "int8multirange" "nummultirange"
                   "tsmultirange" "tstzmultirange" "datemultirange"]]
  (defmethod vec->parameter (keyword type-name)
    [v _]
    (to-pg-multirange v type-name)))

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
        (.setObject ps i (.createArrayOf (.getConnection ps) elem-type
                                         (clj->java-array v)))
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

  ;; Keywords bind to enum (or other named string) columns by name: the column's
  ;; own SQL type is used, so :happy goes into a `mood` enum as 'happy'. (Enum
  ;; values read back as plain strings - the driver does not carry the enum type.)
  clojure.lang.Keyword
  (set-parameter [kw ^PreparedStatement ps ^long i]
    (.setObject ps i (doto (PGobject.)
                       (.setType (param-type-name ps i))
                       (.setValue (name kw))))))

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

(defn- parse-range-fields
  [s]
  (loop [chars (seq s)
         fields []
         field (StringBuilder.)
         quoted? false
         field-quoted? false
         escaped? false]
    (if-let [ch (first chars)]
      (cond
        escaped?
        (recur (next chars) fields (doto field (.append ^char ch))
               quoted? field-quoted? false)

        (= ch \\)
        (recur (next chars) fields field quoted? field-quoted? true)

        (= ch \")
        (recur (next chars) fields field (not quoted?) true false)

        (and (= ch \,) (not quoted?))
        (recur (next chars)
               (conj fields {:value (str field) :quoted? field-quoted?})
               (StringBuilder.) false false false)

        :else
        (recur (next chars) fields (doto field (.append ^char ch))
               quoted? field-quoted? false))
      (conj fields {:value (str field) :quoted? field-quoted?}))))

(defn- read-range
  [s]
  (if (= s "empty")
    (range nil nil :empty? true)
    (let [lower-inclusive? (= \[ (first s))
          upper-inclusive? (= \] (last s))
          [lower upper] (parse-range-fields (subs s 1 (dec (count s))))
          bound (fn [{:keys [value quoted?]}]
                  (if (and (empty? value) (not quoted?)) nil value))]
      (range (bound lower) (bound upper)
             :lower-inclusive? lower-inclusive?
             :upper-inclusive? upper-inclusive?))))

(defn- split-multirange
  [s]
  (let [content (subs s 1 (dec (count s)))]
    (if (empty? content)
      []
      (loop [chars (seq content)
             ranges []
             item (StringBuilder.)
             depth 0
             quoted? false
             escaped? false]
        (if-let [ch (first chars)]
          (let [next-depth (cond
                             quoted? depth
                             (or (= ch \[) (= ch \()) (inc depth)
                             (or (= ch \]) (= ch \))) (dec depth)
                             :else depth)]
            (if (and (= ch \,) (zero? depth) (not quoted?))
              (recur (next chars) (conj ranges (str item))
                     (StringBuilder.) depth quoted? false)
              (recur (next chars) ranges (doto item (.append ^char ch))
                     (long next-depth)
                     (if (and (= ch \") (not escaped?)) (not quoted?) quoted?)
                     (and (= ch \\) (not escaped?)))))
          (conj ranges (str item)))))))

(defn- read-multirange
  [s]
  (mapv read-range (split-multirange s)))

(defn- read-inet
  [s]
  (let [[address prefix] (str/split s #"/" 2)]
    {:address address
     :prefix (when prefix (Integer/parseInt prefix))}))

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
  (when-let [v (.getValue x)] (json-codec/parse-string v)))

(defmethod read-pgobject :jsonb
  [^PGobject x]
  (when-let [v (.getValue x)] (json-codec/parse-string v)))

(doseq [type-name ["int4range" "int8range" "numrange" "tsrange"
                   "tstzrange" "daterange"]]
  (defmethod read-pgobject (keyword type-name)
    [^PGobject x]
    (when-let [v (.getValue x)] (read-range v))))

(doseq [type-name ["int4multirange" "int8multirange" "nummultirange"
                   "tsmultirange" "tstzmultirange" "datemultirange"]]
  (defmethod read-pgobject (keyword type-name)
    [^PGobject x]
    (when-let [v (.getValue x)] (read-multirange v))))

(defmethod read-pgobject :inet
  [^PGobject x]
  (when-let [v (.getValue x)] (read-inet v)))

(defmethod read-pgobject :cidr
  [^PGobject x]
  (when-let [v (.getValue x)] (read-inet v)))

(defmethod read-pgobject :default
  [^PGobject x]
  (.getValue x))

(defn- read-interval
  [^PGInterval v]
  {:years (.getYears v)
   :months (.getMonths v)
   :days (.getDays v)
   :hours (.getHours v)
   :minutes (.getMinutes v)
   :seconds (.getSeconds v)})

(defn- read-money
  [^PGmoney v]
  (when-let [value (.getValue v)]
    (BigDecimal. ^String (str/replace value "$" ""))))

(defn- read-point
  [^PGpoint v]
  [(.x v) (.y v)])

(defn- read-points
  [points]
  (mapv read-point points))

(extend-protocol rs/ReadableColumn
  ;; Parse SQLXML into a Clojure map representing the XML content.
  java.sql.SQLXML
  (read-column-by-label [^java.sql.SQLXML v _]
    (xml/parse (.getBinaryStream v)))
  (read-column-by-index [^java.sql.SQLXML v _ _]
    (xml/parse (.getBinaryStream v)))

  PGInterval
  (read-column-by-label [^PGInterval v _] (read-interval v))
  (read-column-by-index [^PGInterval v _ _] (read-interval v))

  PGmoney
  (read-column-by-label [^PGmoney v _] (read-money v))
  (read-column-by-index [^PGmoney v _ _] (read-money v))

  PGpoint
  (read-column-by-label [^PGpoint v _] (read-point v))
  (read-column-by-index [^PGpoint v _ _] (read-point v))

  PGbox
  (read-column-by-label [^PGbox v _] (read-points (.point v)))
  (read-column-by-index [^PGbox v _ _] (read-points (.point v)))

  PGcircle
  (read-column-by-label [^PGcircle v _]
    {:center (read-point (.center v)) :radius (.radius v)})
  (read-column-by-index [^PGcircle v _ _]
    {:center (read-point (.center v)) :radius (.radius v)})

  PGline
  (read-column-by-label [^PGline v _] {:a (.a v) :b (.b v) :c (.c v)})
  (read-column-by-index [^PGline v _ _] {:a (.a v) :b (.b v) :c (.c v)})

  PGlseg
  (read-column-by-label [^PGlseg v _] (read-points (.point v)))
  (read-column-by-index [^PGlseg v _ _] (read-points (.point v)))

  PGpath
  (read-column-by-label [^PGpath v _]
    {:points (read-points (.points v)) :open? (.isOpen v)})
  (read-column-by-index [^PGpath v _ _]
    {:points (read-points (.points v)) :open? (.isOpen v)})

  PGpolygon
  (read-column-by-label [^PGpolygon v _] (read-points (.points v)))
  (read-column-by-index [^PGpolygon v _ _] (read-points (.points v)))

  ;; Convert java.sql.Array to a Clojure vector.
  java.sql.Array
  (read-column-by-label [^java.sql.Array v _]
    (java-array->clj (.getArray v)))
  (read-column-by-index [^java.sql.Array v _ _]
    (java-array->clj (.getArray v)))

  ;; PGobjects route through the read-pgobject multimethod.
  PGobject
  (read-column-by-label [^PGobject v _]
    (read-pgobject v))
  (read-column-by-index [^PGobject v _ _]
    (read-pgobject v)))
