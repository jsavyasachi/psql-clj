(ns psql.largeobject-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.largeobject :as lo]
            [psql.core :as pg]
            [next.jdbc :as jdbc])
  (:import [java.nio.charset StandardCharsets]))

(deftest ^:integration large-object-store-and-fetch
  (jdbc/with-transaction [tx (pg/spec)]
    (let [data (.getBytes "hello large object" StandardCharsets/UTF_8)
          oid (lo/store! tx data)]
      (is (pos? oid))
      (is (= "hello large object" (String. (lo/fetch tx oid) StandardCharsets/UTF_8)))
      (lo/unlink! tx oid))))

(deftest ^:integration large-object-seek-read-truncate
  (jdbc/with-transaction [tx (pg/spec)]
    (let [oid (lo/store! tx (.getBytes "0123456789" StandardCharsets/UTF_8))
          obj (lo/open tx oid :read-write)]
      (try
        (testing "size and seek/tell"
          (is (= 10 (lo/lo-size obj)))
          (lo/seek! obj 4)
          (is (= 4 (lo/tell obj)))
          (is (= "456" (String. (lo/read-bytes obj 3) StandardCharsets/UTF_8))))
        (testing "seek from end"
          (lo/seek! obj -2 :end)
          (is (= "89" (String. (lo/read-bytes obj 2) StandardCharsets/UTF_8))))
        (testing "write at position"
          (lo/seek! obj 0)
          (lo/write-bytes! obj (.getBytes "AB" StandardCharsets/UTF_8))
          (is (= "AB23456789" (String. (lo/fetch tx oid) StandardCharsets/UTF_8))))
        (testing "truncate"
          (lo/truncate! obj 3)
          (is (= 3 (lo/lo-size obj))))
        (finally (lo/close! obj) (lo/unlink! tx oid))))))

(deftest ^:integration open-rejects-unknown-mode
  (jdbc/with-transaction [tx (pg/spec)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown large-object mode"
                          (lo/open tx 1 :bogus)))))
