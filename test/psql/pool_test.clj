(ns psql.pool-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.pool :as pool]))

(deftest db-spec->pool-config
  (testing "host and port build a jdbc url"
    (is (= {:jdbc-url "jdbc:postgresql://localhost:5432/mydb" :username "me"}
           (pool/db-spec->pool-config
            {:dbtype "postgresql" :host "localhost" :port 5432
             :dbname "mydb" :user "me"}))))
  (testing "host without port"
    (is (= "jdbc:postgresql://h/d"
           (:jdbc-url (pool/db-spec->pool-config
                       {:dbtype "postgresql" :host "h" :dbname "d" :user "u"})))))
  (testing "password and extra hikari options are merged in"
    (let [c (pool/db-spec->pool-config
             {:dbtype "postgresql" :host "h" :dbname "d" :user "u"
              :password "pw" :hikari {:maximum-pool-size 3}})]
      (is (= "pw" (:password c)))
      (is (= 3 (:maximum-pool-size c)))))
  (testing "pgjdbc connection properties are appended to the jdbc url"
    (let [config (pool/db-spec->pool-config
                  {:dbtype "postgresql" :host "h" :dbname "d" :user "u"
                   :sslmode "verify-full"
                   :ApplicationName "psql-clj"
                   :connectTimeout 10
                   :socketTimeout 30
                   :tcpKeepAlive true
                   :options "-c search_path=app,public"
                   :sslcert "/certs/client cert.pem"
                   :sslkey "/certs/client.key"
                   :sslrootcert "/certs/root.crt"
                   :channelBinding "require"
                   :targetServerType "primary"
                   :loadBalanceHosts true
                   :currentSchema "app,public"
                   :prepareThreshold 2
                   :defaultRowFetchSize 100
                   :reWriteBatchedInserts true
                   :futureProperty "kept"})
          url (:jdbc-url config)]
      (doseq [parameter ["sslmode=verify-full"
                         "ApplicationName=psql-clj"
                         "connectTimeout=10"
                         "socketTimeout=30"
                         "tcpKeepAlive=true"
                         "options=-c%20search_path%3Dapp%2Cpublic"
                         "sslcert=%2Fcerts%2Fclient%20cert.pem"
                         "sslkey=%2Fcerts%2Fclient.key"
                         "sslrootcert=%2Fcerts%2Froot.crt"
                         "channelBinding=require"
                         "targetServerType=primary"
                         "loadBalanceHosts=true"
                         "currentSchema=app%2Cpublic"
                         "prepareThreshold=2"
                         "defaultRowFetchSize=100"
                         "reWriteBatchedInserts=true"
                         "futureProperty=kept"]]
        (is (re-find (re-pattern (java.util.regex.Pattern/quote parameter)) url)
            parameter))))
  (testing "an IAM-shaped spec keeps its mandatory TLS mode"
    (is (re-find #"[?&]sslmode=require(?:&|$)"
                 (:jdbc-url
                  (pool/db-spec->pool-config
                   {:dbtype "postgresql" :host "rds.example.com" :port 5432
                    :dbname "app" :user "iam-user" :password "token"
                    :sslmode "require"})))))
  (testing "structured hosts build a pgjdbc failover url"
    (is (= "jdbc:postgresql://primary.example:5432,standby.example:5433/app?loadBalanceHosts=true&targetServerType=primary"
           (:jdbc-url
            (pool/db-spec->pool-config
             {:dbtype "postgresql"
              :hosts [{:host "primary.example" :port 5432}
                      {:host "standby.example" :port 5433}]
              :dbname "app" :user "u"
              :targetServerType "primary" :loadBalanceHosts true})))))
  (testing "a comma-separated host list applies the shared port to each host"
    (is (= "jdbc:postgresql://a.example:5432,b.example:5432/app"
           (:jdbc-url
            (pool/db-spec->pool-config
             {:dbtype "postgresql" :host "a.example,b.example" :port 5432
              :dbname "app" :user "u"})))))
  (testing "prebuilt jdbc urls and service urls are first-class"
    (doseq [spec [{:jdbc-url "jdbc:postgresql://one/app"}
                  {:jdbcUrl "jdbc:postgresql://two/app"}
                  {:service "jdbc:postgresql://three/app"}]]
      (is (= (or (:jdbc-url spec) (:jdbcUrl spec) (:service spec))
             (:jdbc-url (pool/db-spec->pool-config (assoc spec :user "u")))))))
  (testing "prebuilt urls retain existing query parameters and gain properties"
    (is (= "jdbc:postgresql://one/app?currentSchema=base&sslmode=require"
           (:jdbc-url
            (pool/db-spec->pool-config
             {:jdbc-url "jdbc:postgresql://one/app?currentSchema=base"
              :user "u" :sslmode "require"})))))
  (testing "database, host, and property values are safely encoded"
    (is (= "jdbc:postgresql://db%20host:5432/my%2Fdb?options=-c%20statement_timeout%3D5%20000"
           (:jdbc-url
            (pool/db-spec->pool-config
             {:dbtype "postgresql" :host "db host" :port 5432
              :dbname "my/db" :user "u"
              :options "-c statement_timeout=5 000"}))))))
