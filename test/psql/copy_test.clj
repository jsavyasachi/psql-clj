(ns psql.copy-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [psql.copy :as copy]
            [psql.core :as pg])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream
            StringReader StringWriter]))

(deftest copy-argument-validation
  (testing "COPY SQL must be a non-empty string"
    (is (thrown? IllegalArgumentException
                 (copy/copy-in nil "" (StringReader. ""))))
    (is (thrown? IllegalArgumentException
                 (copy/copy-out nil nil (StringWriter.)))))
  (testing "COPY input must be a Reader or InputStream"
    (is (thrown? IllegalArgumentException
                 (copy/copy-in nil "COPY t FROM STDIN" "not a stream"))))
  (testing "COPY output must be a Writer or OutputStream"
    (is (thrown? IllegalArgumentException
                 (copy/copy-out nil "COPY t TO STDOUT" :not-a-stream)))))

(deftest ^:integration copy-roundtrip
  (with-open [conn (jdbc/get-connection (pg/spec))]
    (jdbc/execute! conn ["CREATE TEMP TABLE psql_copy_test (id integer, value text)"])
    (is (= 2 (copy/copy-in conn
                           "COPY psql_copy_test FROM STDIN WITH (FORMAT csv)"
                           (StringReader. "1,alpha\n2,beta\n"))))
    (let [output (StringWriter.)]
      (is (= 2 (copy/copy-out conn
                              "COPY psql_copy_test TO STDOUT WITH (FORMAT csv)"
                              output)))
      (is (= "1,alpha\n2,beta\n" (str output))))
    (let [output (ByteArrayOutputStream.)]
      (is (= 2 (copy/copy-out conn
                              "COPY psql_copy_test TO STDOUT WITH (FORMAT binary)"
                              output)))
      (is (pos? (.size output))))
    (jdbc/execute! conn ["TRUNCATE psql_copy_test"])
    (is (= 2 (copy/copy-in conn
                           "COPY psql_copy_test FROM STDIN WITH (FORMAT csv)"
                           (ByteArrayInputStream. (.getBytes "3,gamma\n4,delta\n" "UTF-8")))))))
