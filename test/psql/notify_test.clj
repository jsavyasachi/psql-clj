(ns psql.notify-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [psql.core :as pg]
            [psql.notify :as notify])
  (:import [org.postgresql PGNotification]))

(deftest quote-channel-test
  (testing "safe PostgreSQL identifier names are quoted"
    (is (= "\"events\"" (notify/quote-channel "events")))
    (is (= "\"Events_42\"" (notify/quote-channel "Events_42"))))
  (testing "unsafe and malformed names are rejected"
    (doseq [channel [nil "" "42events" "events; DROP TABLE users"
                     "events--" "events space" "events\"other"]]
      (is (thrown? IllegalArgumentException
                   (notify/quote-channel channel))))))

(deftest notification-shaping-test
  (let [notification (reify PGNotification
                       (getName [_] "events")
                       (getParameter [_] "ready")
                       (getPID [_] 4242))]
    (is (= {:name "events" :parameter "ready" :pid 4242}
           (notify/notification->map notification)))))

(deftest notification-argument-validation
  (is (thrown? IllegalArgumentException (notify/get-notifications nil -1)))
  (is (thrown? IllegalArgumentException (notify/notify! nil "events" nil))))

(deftest ^:integration listen-notify-roundtrip
  (with-open [listener (jdbc/get-connection (pg/spec))
              sender (jdbc/get-connection (pg/spec))]
    (notify/listen! listener "psql_clj_events")
    (notify/notify! sender "psql_clj_events" "ready")
    (let [notifications (notify/get-notifications listener 2000)]
      (is (= 1 (count notifications)))
      (is (= "psql_clj_events" (:name (first notifications))))
      (is (= "ready" (:parameter (first notifications))))
      (is (pos? (:pid (first notifications)))))
    (notify/unlisten! listener "psql_clj_events")))
