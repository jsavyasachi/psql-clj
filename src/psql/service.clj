(ns psql.service
  "Resolve pgjdbc service definitions into psql-clj database specs."
  (:require [clojure.java.io :as io])
  (:import [org.postgresql.jdbcurlresolver PgServiceConfParser]))

(def ^:private service-file-property "org.postgresql.pgservicefile")
(def ^:private service-parser-lock (Object.))

(defn- service-file-path
  [{:keys [PGSERVICEFILE PGSYSCONFDIR]}]
  (or PGSERVICEFILE
      (when PGSYSCONFDIR
        (str (io/file PGSYSCONFDIR "pg_service.conf")))))

(defn- with-service-file
  [env f]
  (if-let [path (service-file-path env)]
    (locking service-parser-lock
      (let [previous (System/getProperty service-file-property)]
        (try
          (System/setProperty service-file-property (str path))
          (f)
          (finally
            (if previous
              (System/setProperty service-file-property previous)
              (System/clearProperty service-file-property))))))
    (f)))

(defn- properties->spec
  [properties]
  (let [connection-keys {"PGHOST" :host
                         "PGPORT" :port
                         "PGDBNAME" :dbname
                         "user" :user
                         "password" :password}]
    (reduce-kv (fn [spec property value]
                 (assoc spec
                        (get connection-keys property (keyword property))
                        value))
               {}
               (into {} properties))))

(defn resolve-service
  "Resolve SERVICE-NAME using pgjdbc's pg_service.conf parser. ENV may supply
  PGSERVICEFILE or PGSYSCONFDIR; otherwise pgjdbc uses its normal search path."
  [service-name env]
  {:pre [(map? env)]
   :post [(map? %)]}
  (if service-name
    (or (with-service-file
          env
          #(some-> (PgServiceConfParser/getServiceProperties (str service-name))
                   properties->spec))
        {})
    {}))
