(ns psql.pgpass-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.pgpass :as pgpass]))

(deftest parse-pgpass-line
  (is (= {:pg-hostname "localhost" :pg-port "5432" :pg-database "mydb"
          :pg-username "me" :pg-password "secret"}
         (pgpass/parse-pgpass-line "localhost:5432:mydb:me:secret")))
  (testing "escaped colons and backslashes survive parsing"
    (is (= {:pg-hostname "local:host" :pg-port "5432" :pg-database "mydb"
            :pg-username "me" :pg-password "colon:and\\slash"}
           (pgpass/parse-pgpass-line
            "local\\:host:5432:mydb:me:colon\\:and\\\\slash"))))
  (testing "malformed records are ignored"
    (is (nil? (pgpass/parse-pgpass-line "localhost:5432:mydb:me")))
    (is (nil? (pgpass/parse-pgpass-line "localhost:5432:mydb:me:pw:extra"))))
  (testing "blank and comment lines are ignored"
    (is (nil? (pgpass/parse-pgpass-line "")))
    (is (nil? (pgpass/parse-pgpass-line "   ")))
    (is (nil? (pgpass/parse-pgpass-line "  # comment")))))

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
  (testing "ports are normalized"
    (is (= "pw" (pgpass/pgpass-matches?
                 {:host "h" :port 5432 :dbname "d" :user "u"}
                 {:pg-hostname "h" :pg-port "5432" :pg-database "d"
                  :pg-username "u" :pg-password "pw"}))))
  (testing "localhost matches empty and absolute socket hosts"
    (doseq [host ["" "/var/run/postgresql"]]
      (is (= "pw" (pgpass/pgpass-matches?
                   {:host host :port "5432" :dbname "d" :user "u"}
                   {:pg-hostname "localhost" :pg-port "5432" :pg-database "d"
                    :pg-username "u" :pg-password "pw"})))))
  (testing "username mismatch does not match"
    (is (not (pgpass/pgpass-matches?
              {:host "h" :port "5432" :dbname "d" :user "u"}
              {:pg-hostname "*" :pg-port "*" :pg-database "*"
               :pg-username "someone-else" :pg-password "pw"})))))
