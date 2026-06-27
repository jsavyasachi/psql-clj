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
      (is (= 3 (:maximum-pool-size c))))))
