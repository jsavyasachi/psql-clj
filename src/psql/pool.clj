(ns psql.pool
  "Hikari based connection pool"
  (:require [clojure.string :as str]
            [hikari-cp.core :as hikari])
  (:import (java.net URLEncoder)
           (java.util.concurrent TimeUnit)))

(def ^:private structural-keys
  #{:dbtype :host :hosts :port :dbname :user :password :hikari
    :jdbc-url :jdbcUrl :service})

(defn- url-encode
  [value]
  (-> (URLEncoder/encode (String/valueOf value) "UTF-8")
      (str/replace "+" "%20")
      (str/replace "*" "%2A")
      (str/replace "%7E" "~")))

(defn- property-name
  [property]
  (if (or (keyword? property) (symbol? property))
    (name property)
    (str property)))

(defn- host-with-port
  [host port]
  (let [host (String/valueOf host)]
    (cond
      (re-matches #"\[.*\](?::\d+)?" host)
      (str host (when (and port (not (re-find #"\]:\d+$" host)))
                  (str ":" port)))

      (re-matches #"[^:]+:\d+" host)
      (let [[hostname endpoint-port] (str/split host #":" 2)]
        (str (url-encode hostname) ":" endpoint-port))

      (str/includes? host ":")
      (str "[" host "]" (when port (str ":" port)))

      :else
      (str (url-encode host) (when port (str ":" port))))))

(defn- host-part
  [{:keys [host hosts port]}]
  (if (seq hosts)
    (str/join ","
              (map (fn [endpoint]
                     (if (map? endpoint)
                       (host-with-port (:host endpoint) (or (:port endpoint) port))
                       (host-with-port endpoint port)))
                   hosts))
    (when host
      (str/join "," (map #(host-with-port (str/trim %) port)
                         (str/split (String/valueOf host) #","))))))

(defn- connection-properties
  [spec]
  (->> (apply dissoc spec structural-keys)
       (remove (comp nil? val))
       (sort-by (comp property-name key))))

(defn- append-properties
  [jdbc-url properties]
  (if (seq properties)
    (let [separator (cond
                      (or (str/ends-with? jdbc-url "?")
                          (str/ends-with? jdbc-url "&")) ""
                      (str/includes? jdbc-url "?") "&"
                      :else "?")]
      (str jdbc-url separator
           (str/join "&" (map (fn [[property value]]
                                (str (url-encode (property-name property))
                                     "=" (url-encode value)))
                              properties))))
    jdbc-url))

(defn db-spec->pool-config
  "Converts a db-spec to Hikari pool config. Connection properties are
  appended to the JDBC URL. Hikari options can be passed with `hikari`. See
  https://github.com/tomekw/hikari-cp#configuration-options for that
  list."
  [{:keys [dbtype dbname user password hikari jdbc-url jdbcUrl service]
    :as spec}]
  (let [generated-url (format "jdbc:%s://%s/%s"
                              dbtype (or (host-part spec) "")
                              (url-encode dbname))
        jdbc-url (or (:jdbc-url hikari) jdbc-url jdbcUrl service generated-url)
        config (cond-> {:jdbc-url (append-properties jdbc-url
                                                     (connection-properties spec))
             :username user}
                 password (assoc :password password))]
    (merge config (dissoc hikari :jdbc-url))))

(defn pooled-db
  [spec opts]
  (let [config (merge (db-spec->pool-config spec) opts)]
    {:datasource (hikari/make-datasource config)}))

(defn close-pooled-db!
  [{:keys [datasource]}]
  (hikari/close-datasource datasource))
