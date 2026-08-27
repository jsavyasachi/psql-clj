(ns psql.catalog
  "Schema and catalog introspection over JDBC `DatabaseMetaData`, returning
  normalized Clojure data. Each function takes a db spec or datasource (anything
  `next.jdbc/get-connection` accepts) and, optionally, a table name.

  Table and schema names are accepted as strings or keywords. Results use
  keyword names so they compose with the rest of psql-clj."
  (:require [next.jdbc :as jdbc])
  (:import [java.sql Connection DatabaseMetaData ResultSet]))

(defn- ->name [x] (when x (name x)))

(defn- rows
  "Realize a metadata ResultSet into a vector of maps built by ROW-FN."
  [^ResultSet rs row-fn]
  (loop [acc (transient [])]
    (if (.next rs)
      (recur (conj! acc (row-fn rs)))
      (persistent! acc))))

(defn- metadata ^DatabaseMetaData [^Connection conn]
  (.getMetaData conn))

(defn schemas
  "Return the set of schema names (as keywords) in the database."
  [db]
  (with-open [^Connection conn (jdbc/get-connection db)]
    (let [rs (.getSchemas (metadata conn))]
      (into #{} (map keyword) (rows rs #(.getString ^ResultSet % "TABLE_SCHEM"))))))

(defn views
  "Return the set of view names (as keywords) visible in the database."
  [db]
  (with-open [^Connection conn (jdbc/get-connection db)]
    (let [rs (.getTables (metadata conn) nil nil nil (into-array String ["VIEW"]))]
      (into #{} (map keyword) (rows rs #(.getString ^ResultSet % "TABLE_NAME"))))))

(defn columns
  "Return the columns of TABLE as a vector of maps ordered by position:
  `:name`, `:type` (database type name), `:sql-type` (JDBC int), `:size`,
  `:nullable?`, `:default`, `:position`."
  [db table]
  (with-open [^Connection conn (jdbc/get-connection db)]
    (let [rs (.getColumns (metadata conn) nil nil (->name table) nil)]
      (rows rs (fn [^ResultSet r]
                 {:name (keyword (.getString r "COLUMN_NAME"))
                  :type (.getString r "TYPE_NAME")
                  :sql-type (.getInt r "DATA_TYPE")
                  :size (.getInt r "COLUMN_SIZE")
                  :nullable? (= DatabaseMetaData/columnNullable (.getInt r "NULLABLE"))
                  :default (.getString r "COLUMN_DEF")
                  :position (.getInt r "ORDINAL_POSITION")})))))

(defn primary-keys
  "Return the primary-key column names of TABLE (as keywords) in key order."
  [db table]
  (with-open [^Connection conn (jdbc/get-connection db)]
    (let [rs (.getPrimaryKeys (metadata conn) nil nil (->name table))]
      (->> (rows rs (fn [^ResultSet r]
                      {:column (keyword (.getString r "COLUMN_NAME"))
                       :seq (.getShort r "KEY_SEQ")}))
           (sort-by :seq)
           (mapv :column)))))

(defn foreign-keys
  "Return the foreign keys declared on TABLE as a vector of maps:
  `:name` (constraint name), `:column`, and `:references` `{:table … :column …}`."
  [db table]
  (with-open [^Connection conn (jdbc/get-connection db)]
    (let [rs (.getImportedKeys (metadata conn) nil nil (->name table))]
      (rows rs (fn [^ResultSet r]
                 {:name (keyword (.getString r "FK_NAME"))
                  :column (keyword (.getString r "FKCOLUMN_NAME"))
                  :references {:table (keyword (.getString r "PKTABLE_NAME"))
                               :column (keyword (.getString r "PKCOLUMN_NAME"))}})))))

(defn indexes
  "Return the indexes on TABLE as a vector of maps, one per index:
  `:name`, `:unique?`, and `:columns` (a vector of column-name keywords in
  index order). The implicit table-statistics row is skipped."
  [db table]
  (with-open [^Connection conn (jdbc/get-connection db)]
    (let [rs (.getIndexInfo (metadata conn) nil nil (->name table) false false)
          raw (rows rs (fn [^ResultSet r]
                         {:name (.getString r "INDEX_NAME")
                          :unique? (not (.getBoolean r "NON_UNIQUE"))
                          :column (.getString r "COLUMN_NAME")
                          :position (.getShort r "ORDINAL_POSITION")
                          :type (.getShort r "TYPE")}))]
      (->> raw
           (remove #(or (nil? (:name %))
                        (= DatabaseMetaData/tableIndexStatistic (:type %))))
           (group-by :name)
           (mapv (fn [[index-name entries]]
                   {:name (keyword index-name)
                    :unique? (:unique? (first entries))
                    :columns (mapv (comp keyword :column)
                                   (sort-by :position entries))}))))))
