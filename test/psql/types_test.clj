(ns psql.types-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.types :as t])
  (:import [org.postgresql.util PGobject]))

(defn- pgobj [type value]
  (doto (PGobject.) (.setType type) (.setValue value)))

(deftest pg-vector-parsing
  (is (= ["1" "2" "3"] (t/read-pg-vector "1 2 3")))
  (is (nil? (t/read-pg-vector ""))))

(deftest pg-array-parsing
  (is (= ["a" "b"] (t/read-pg-array "{a,b}")))
  (is (nil? (t/read-pg-array "")))
  (testing "empty braces have no inner content, so there is nothing to split"
    (is (nil? (t/read-pg-array "{}")))))

(deftest read-pgobject-dispatch
  (testing "json/jsonb parse to Clojure data"
    (is (= {"a" 1} (t/read-pgobject (pgobj "json" "{\"a\":1}"))))
    (is (= {"a" 1} (t/read-pgobject (pgobj "jsonb" "{\"a\":1}")))))
  (testing "oidvector/anyarray"
    (is (= [1 2 3] (t/read-pgobject (pgobj "oidvector" "1 2 3"))))
    (is (= ["a" "b"] (t/read-pgobject (pgobj "anyarray" "{a,b}")))))
  (testing "unknown type falls through to the raw string value"
    (is (= "raw-value" (t/read-pgobject (pgobj "citext" "raw-value"))))))
