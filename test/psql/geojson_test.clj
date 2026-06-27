(ns psql.geojson-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.geojson :as gj]))

(deftest point-construct-and-predicate
  (is (= {:type :Point :coordinates [1 2]} (gj/point 1 2)))
  (is (gj/point? {:type :Point :coordinates [1 2]}))
  (testing "predicate rejects a non-point"
    (is (not (gj/point? {:type :LineString :coordinates [[1 2] [3 4]]})))))

(deftest multi-point-is-not-a-stub
  (testing "multi-point emits real coordinates and validates its schema"
    (is (= {:type :MultiPoint :coordinates [[1 2] [3 4]]}
           (gj/multi-point [[1 2] [3 4]])))))
