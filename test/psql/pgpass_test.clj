(ns psql.pgpass-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.pgpass :as pgpass]))

(deftest parse-pgpass-line
  (is (= {:pg-hostname "localhost" :pg-port "5432" :pg-database "mydb"
          :pg-username "me" :pg-password "secret"}
         (pgpass/parse-pgpass-line "localhost:5432:mydb:me:secret"))))

(deftest pgpass-matches?
  (testing "all-wildcard line matches and yields the password"
    (is (= "pw" (pgpass/pgpass-matches?
                 {:host "h" :port "5432" :dbname "d" :user "u"}
                 {:pg-hostname "*" :pg-port "*" :pg-database "*"
                  :pg-username "*" :pg-password "pw"}))))
  (testing "localhost line matches a nil host"
    (is (= "pw" (pgpass/pgpass-matches?
                 {:host nil :port nil :dbname "d" :user "u"}
                 {:pg-hostname "localhost" :pg-port "5432" :pg-database "*"
                  :pg-username "*" :pg-password "pw"}))))
  (testing "username mismatch does not match"
    (is (not (pgpass/pgpass-matches?
              {:host "h" :port "5432" :dbname "d" :user "u"}
              {:pg-hostname "*" :pg-port "*" :pg-database "*"
               :pg-username "someone-else" :pg-password "pw"})))))
