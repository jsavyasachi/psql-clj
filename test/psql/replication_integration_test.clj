(ns psql.replication-integration-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [psql.replication :as repl]
            [psql.core :as pg]
            [next.jdbc :as jdbc])
  (:import [java.sql Connection]
           [java.nio.charset StandardCharsets]))

;; ---- unit (no database) ---------------------------------------------------

(deftest lsn-codec-roundtrip
  (is (= "16/B374D848" (repl/lsn->string (repl/lsn "16/B374D848"))))
  (is (= 0 (repl/lsn->long (repl/lsn "0/0"))))
  (is (= "0/0" (repl/lsn->string (repl/lsn 0))))
  (is (thrown? clojure.lang.ExceptionInfo (repl/lsn {:not "an lsn"}))))

;; ---- integration (live database) ------------------------------------------

(defn- with-repl-conn [f]
  (with-open [^Connection c (jdbc/get-connection (repl/replication-spec (pg/spec)))]
    (f c)))

(deftest ^:integration logical-slot-lifecycle
  (with-repl-conn
    (fn [c]
      (let [info (repl/create-logical-slot! c "psql_clj_it_slot" "test_decoding")]
        (try
          (is (= "psql_clj_it_slot" (:slot-name info)))
          (is (= :logical (:replication-type info)))
          (is (= "test_decoding" (:output-plugin info)))
          (is (re-matches #"[0-9A-F]+/[0-9A-F]+" (:consistent-point info)))
          (testing "the slot is visible in pg_replication_slots"
            (is (= 1 (:c (jdbc/execute-one!
                          (pg/spec)
                          ["SELECT count(*) c FROM pg_replication_slots WHERE slot_name=?"
                           "psql_clj_it_slot"])))))
          (finally (repl/drop-slot! c "psql_clj_it_slot")))))))

(deftest ^:integration physical-slot-lifecycle
  (with-repl-conn
    (fn [c]
      (let [info (repl/create-physical-slot! c "psql_clj_it_phys")]
        (try
          (is (= "psql_clj_it_phys" (:slot-name info)))
          (is (= :physical (:replication-type info)))
          (finally (repl/drop-slot! c "psql_clj_it_phys")))))))

(deftest ^:integration logical-decoding-stream-sees-changes
  ;; Create a slot, make a change on a normal connection, then stream the
  ;; decoded change back through test_decoding.
  (jdbc/execute! (pg/spec) ["DROP TABLE IF EXISTS repl_t"])
  (jdbc/execute! (pg/spec) ["CREATE TABLE repl_t (id int primary key, v text)"])
  (with-repl-conn
    (fn [c]
      (repl/create-logical-slot! c "psql_clj_it_stream" "test_decoding")
      (try
        (jdbc/execute! (pg/spec) ["INSERT INTO repl_t VALUES (1, 'hello')"])
        (let [stream (repl/start-logical-replication
                      c {:slot-name "psql_clj_it_stream"
                         :slot-options {"include-xids" "false"}})
              decoded (loop [acc [] tries 0]
                        (if (or (>= (count acc) 3) (> tries 200))
                          acc
                          (if-let [buf (repl/read-pending stream)]
                            (let [bytes (byte-array (.remaining buf))]
                              (.get buf bytes)
                              (recur (conj acc (String. bytes StandardCharsets/UTF_8)) tries))
                            (do (Thread/sleep 10) (recur acc (inc tries))))))]
          (repl/close-stream! stream)
          (let [text (str/join "\n" decoded)]
            (is (re-find #"BEGIN" text))
            (is (re-find #"table public.repl_t: INSERT" text))
            (is (re-find #"hello" text))))
        (finally (repl/drop-slot! c "psql_clj_it_stream"))))))
