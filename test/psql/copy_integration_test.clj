(ns psql.copy-integration-test
  (:require [clojure.test :refer [deftest is]]
            [psql.copy :as copy]
            [psql.core :as pg]
            [next.jdbc :as jdbc])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.sql Connection]
           [java.nio.charset StandardCharsets]))

(defn- with-conn [f]
  (with-open [^Connection c (jdbc/get-connection (pg/spec))]
    (jdbc/execute! c ["DROP TABLE IF EXISTS copy_t"])
    (jdbc/execute! c ["CREATE TABLE copy_t (id int, name text)"])
    (f c)))

(deftest ^:integration copy-in-with-chunk-size
  (with-conn
    (fn [c]
      (let [data (.getBytes "1\tada\n2\tgus\n3\tbo\n" StandardCharsets/UTF_8)
            n (copy/copy-in c "COPY copy_t FROM STDIN" (ByteArrayInputStream. data) 4)]
        (is (= 3 n))
        (is (= 3 (:c (jdbc/execute-one! c ["SELECT count(*) c FROM copy_t"]))))))))

(deftest ^:integration low-level-copy-in-lifecycle
  (with-conn
    (fn [c]
      (let [ci (copy/start-copy-in c "COPY copy_t FROM STDIN")]
        (copy/write-copy! ci (.getBytes "10\tx\n" StandardCharsets/UTF_8))
        (copy/write-copy! ci (.getBytes "11\ty\n" StandardCharsets/UTF_8))
        (copy/flush-copy! ci)
        (is (= 2 (copy/end-copy! ci)))
        (is (= 2 (:c (jdbc/execute-one! c ["SELECT count(*) c FROM copy_t"]))))))))

(deftest ^:integration low-level-copy-out-lifecycle
  (with-conn
    (fn [c]
      (jdbc/execute! c ["INSERT INTO copy_t VALUES (1,'a'),(2,'b')"])
      (let [co (copy/start-copy-out c "COPY copy_t TO STDOUT")
            out (ByteArrayOutputStream.)]
        (loop []
          (when-let [^bytes row (copy/read-copy! co)]
            (.write out row 0 (alength row))
            (recur)))
        (let [text (.toString out "UTF-8")]
          (is (re-find #"1\ta" text))
          (is (re-find #"2\tb" text)))))))

(deftest ^:integration copy-dual-is-available
  (with-conn
    (fn [c]
      ;; copyDual requires a replication/dual-capable statement; a plain COPY
      ;; is rejected, which proves the handle reaches the server correctly.
      (is (thrown? org.postgresql.util.PSQLException
                   (copy/copy-dual c "COPY copy_t TO STDOUT"))))))
