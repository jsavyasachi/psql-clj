(ns psql.pgpass-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [psql.pgpass :as pgpass])
  (:import [java.nio.file Files]
           [java.nio.file.attribute PosixFilePermission]))

(def ^:private owner-only-permissions
  #{PosixFilePermission/OWNER_READ PosixFilePermission/OWNER_WRITE})

(defn- temp-pgpass
  [contents permissions]
  (let [passfile (java.io.File/createTempFile "pgpass-" ".conf")]
    (.deleteOnExit passfile)
    (spit passfile contents)
    (Files/setPosixFilePermissions (.toPath passfile) permissions)
    passfile))

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
    (is (nil? (pgpass/parse-pgpass-line "  # comment")))
    ;; a commented-out entry has 5 colon fields; it must still be skipped as a
    ;; comment, not parsed into a record.
    (is (nil? (pgpass/parse-pgpass-line "#localhost:5432:mydb:me:secret")))
    (is (nil? (pgpass/parse-pgpass-line "  # localhost:5432:mydb:me:secret")))))

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

(deftest default-pgpass-file
  (testing "PGPASSFILE takes precedence"
    (is (= (io/file "/custom/passfile")
           (pgpass/default-pgpass-file {"PGPASSFILE" "/custom/passfile"}
                                       "Linux" "/home/me"))))
  (testing "POSIX default"
    (is (= (io/file "/home/me" ".pgpass")
           (pgpass/default-pgpass-file {} "Linux" "/home/me"))))
  (testing "Windows default"
    (is (= (io/file "C:\\Users\\me\\AppData\\Roaming" "postgresql" "pgpass.conf")
           (pgpass/default-pgpass-file
            {"APPDATA" "C:\\Users\\me\\AppData\\Roaming"}
            "Windows 11" "C:\\Users\\me")))))

(deftest read-and-lookup-pgpass
  (let [passfile (temp-pgpass
                  (str "\n  # ignored\nmalformed\n"
                       "localhost:5432:mydb:me:colon\\:and\\\\slash\n")
                  owner-only-permissions)]
    (testing "explicit passfile lookup skips ignored records and preserves escapes"
      (is (= "colon:and\\slash"
             (pgpass/pgpass-lookup
              {:host "localhost" :port 5432 :dbname "mydb" :user "me"}
              passfile))))
    (testing "read-pgpass never emits partial candidates"
      (is (= 1 (count (pgpass/read-pgpass passfile)))))))

(deftest insecure-pgpass-is-ignored
  (doseq [permission [PosixFilePermission/GROUP_READ
                      PosixFilePermission/GROUP_WRITE
                      PosixFilePermission/OTHERS_READ
                      PosixFilePermission/OTHERS_WRITE]]
    (let [passfile (temp-pgpass
                    "localhost:5432:mydb:me:exposed\n"
                    (conj owner-only-permissions permission))]
      (is (nil? (pgpass/pgpass-lookup
                 {:host "localhost" :port 5432 :dbname "mydb" :user "me"}
                 passfile))))))
