(ns psql.types-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [next.jdbc.result-set :as rs]
            [psql.types :as t])
  (:import [org.postgresql.geometric PGbox PGcircle PGline PGlseg PGpath PGpoint
            PGpolygon]
           [org.postgresql.util PGInterval PGmoney PGobject]))

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

(deftest range-coercion
  (testing "bounded, unbounded, empty, and escaped ranges read losslessly"
    (is (= {:lower "1" :upper "10" :lower-inclusive? true
            :upper-inclusive? false :empty? false}
           (t/read-pgobject (pgobj "int4range" "[1,10)"))))
    (is (= {:lower nil :upper nil :lower-inclusive? false
            :upper-inclusive? false :empty? false}
           (t/read-pgobject (pgobj "daterange" "(,)"))))
    (is (= {:lower nil :upper nil :lower-inclusive? false
            :upper-inclusive? false :empty? true}
           (t/read-pgobject (pgobj "numrange" "empty"))))
    (is (= {:lower "a,b" :upper "a\"b\\c" :lower-inclusive? true
            :upper-inclusive? true :empty? false}
           (t/read-pgobject
            (pgobj "tsrange" "[\"a,b\",\"a\\\"b\\\\c\"]")))))
  (testing "range constructors write typed PGobjects"
    (let [value (t/map->parameter
                 (t/range "a,b" "a\"b\\c" :lower-inclusive? true
                          :upper-inclusive? true)
                 "int4range")]
      (is (= "int4range" (.getType ^PGobject value)))
      (is (= "[\"a,b\",\"a\\\"b\\\\c\"]" (.getValue ^PGobject value)))))
  (testing "all built-in range and multirange types dispatch"
    (doseq [type ["int4range" "int8range" "numrange" "tsrange" "tstzrange"
                  "daterange"]]
      (is (= type (.getType ^PGobject
                            (t/map->parameter (t/range nil nil) type)))))
    (doseq [type ["int4multirange" "int8multirange" "nummultirange"
                  "tsmultirange" "tstzmultirange" "datemultirange"]]
      (let [value (t/vec->parameter [(t/range "1" "2")
                                     (t/range nil nil :empty? true)] type)]
        (is (= type (.getType ^PGobject value)))
        (is (= "{[1,2),empty}" (.getValue ^PGobject value)))
        (is (= [(t/range "1" "2") (t/range nil nil :empty? true)]
               (t/read-pgobject value)))))))

(deftest nested-array-coercion
  (let [nested (object-array [(int-array [1 2])
                              (object-array ["a" (object-array ["b"])])])]
    (is (= [[1 2] ["a" ["b"]]] (#'t/java-array->clj nested))))
  (let [nested (#'t/clj->java-array [[1 2] [3 4]])]
    (is (= [[1 2] [3 4]] (#'t/java-array->clj nested)))
    (is (.isArray (class (aget ^objects nested 0))))))

(deftest interval-and-money-coercion
  (is (= {:years 1 :months 2 :days 3 :hours 4 :minutes 5 :seconds 6.25}
         (rs/read-column-by-label (PGInterval. 1 2 3 4 5 6.25) nil)))
  (is (= (bigdec "123456789.25")
         (rs/read-column-by-label (PGmoney. 123456789.25) nil))))

(deftest geometric-coercion
  (let [p1 (PGpoint. 1.0 2.0)
        p2 (PGpoint. 3.0 4.0)]
    (is (= [1.0 2.0] (rs/read-column-by-label p1 nil)))
    (is (= [[1.0 2.0] [3.0 4.0]]
           (rs/read-column-by-label (PGbox. p1 p2) nil)))
    (is (= {:center [1.0 2.0] :radius 3.0}
           (rs/read-column-by-label (PGcircle. p1 3.0) nil)))
    (is (= {:a 1.0 :b 2.0 :c 3.0}
           (rs/read-column-by-label (PGline. 1.0 2.0 3.0) nil)))
    (is (= [[1.0 2.0] [3.0 4.0]]
           (rs/read-column-by-label (PGlseg. p1 p2) nil)))
    (is (= {:points [[1.0 2.0] [3.0 4.0]] :open? true}
           (rs/read-column-by-label
            (PGpath. (into-array PGpoint [p1 p2]) true) nil)))
    (is (= [[1.0 2.0] [3.0 4.0]]
           (rs/read-column-by-label
            (PGpolygon. (into-array PGpoint [p1 p2])) nil)))))

(deftest inet-and-cidr-coercion
  (doseq [[type text expected-prefix]
          [["inet" "192.0.2.7/24" 24]
           ["cidr" "2001:db8::/48" 48]]]
    (let [{:keys [address prefix] :as value}
          (t/read-pgobject (pgobj type text))]
      (is (= expected-prefix prefix))
      (is (= (first (str/split text #"/")) address))
      (let [written (t/map->parameter value type)]
        (is (= type (.getType ^PGobject written)))
        (is (= value (t/read-pgobject written))))))
  (is (= "cidr" (.getType ^PGobject
                           (t/vec->parameter [192 0 2 0] "cidr")))))

(deftest tagged-json-scalars
  (doseq [[value type expected]
          [[(t/json "hello") "json" "\"hello\""]
           [(t/jsonb 42) "jsonb" "42"]
           [(t/jsonb true) "jsonb" "true"]
           [(t/json nil) "json" "null"]]]
    (is (= type (.getType ^PGobject value)))
    (is (= expected (.getValue ^PGobject value)))))
