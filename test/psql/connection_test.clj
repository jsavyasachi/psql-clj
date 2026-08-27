(ns psql.connection-test
  (:require [clojure.test :refer [deftest is]]
            [psql.connection :as conn]
            [psql.core :as pg]
            [next.jdbc :as jdbc])
  (:import [java.sql Connection]))

;; ---- unit (no database) ---------------------------------------------------

(deftest set-autosave!-rejects-unknown-mode
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown autosave mode"
                        (conn/set-autosave! nil :bogus))))

;; ---- integration (live database) ------------------------------------------

(defn- with-conn [f]
  (with-open [^Connection c (jdbc/get-connection (pg/spec))]
    (f c)))

(deftest ^:integration backend-pid-and-parameter-statuses
  (with-conn
    (fn [c]
      (is (pos-int? (conn/backend-pid c)))
      (let [statuses (conn/parameter-statuses c)]
        (is (map? statuses))
        (is (contains? statuses "server_version")))
      (is (some? (conn/parameter-status c "server_version")))
      (is (nil? (conn/parameter-status c "definitely_not_a_real_parameter"))))))

(deftest ^:integration default-fetch-size-roundtrip
  (with-conn
    (fn [c]
      (conn/set-default-fetch-size! c 250)
      (is (= 250 (conn/default-fetch-size c))))))

(deftest ^:integration autosave-roundtrip
  (with-conn
    (fn [c]
      (conn/set-autosave! c :conservative)
      (is (= :conservative (conn/autosave c)))
      (conn/set-autosave! c :never)
      (is (= :never (conn/autosave c))))))

(deftest ^:integration escape-identifier-quotes
  (with-conn
    (fn [c]
      (is (= "\"weird name\"" (conn/escape-identifier c "weird name"))))))

(deftest ^:integration cancel-query-on-idle-is-safe
  (with-conn
    (fn [c]
      (is (nil? (conn/cancel-query! c))))))

(deftest ^:integration statement-timeout-cancels-slow-queries
  (with-conn
    (fn [c]
      (conn/statement-timeout! c 100)
      (is (thrown? org.postgresql.util.PSQLException
                   (jdbc/execute! c ["SELECT pg_sleep(3)"])))
      (conn/statement-timeout! c 0))))
