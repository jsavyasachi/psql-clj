(ns psql.coerce-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.coerce :as coerce]
            [psql.spatial :as st])
  (:import [net.postgis.jdbc.geometry Point Polygon]))

(deftest postgis->geojson-roundtrip
  (testing "point"
    (is (= {:type :Point :coordinates [1.0 2.0]}
           (coerce/postgis->geojson (st/point [1 2])))))
  (testing "multipoint"
    (is (= {:type :MultiPoint :coordinates [[1.0 2.0] [3.0 4.0]]}
           (coerce/postgis->geojson (st/multi-point [[1 2] [3 4]])))))
  (testing "linestring"
    (is (= {:type :LineString :coordinates [[0.0 0.0] [1.0 1.0]]}
           (coerce/postgis->geojson (st/line-string [[0 0] [1 1]])))))
  (testing "polygon"
    (is (= {:type :Polygon :coordinates [[[0.0 0.0] [0.0 1.0] [1.0 1.0] [0.0 0.0]]]}
           (coerce/postgis->geojson (st/polygon [[[0 0] [0 1] [1 1] [0 0]]]))))))

(deftest geojson->postgis-dispatch
  (is (instance? Point
                 (coerce/geojson->postgis {:type :Point :coordinates [1 2]})))
  (is (instance? Polygon
                 (coerce/geojson->postgis {:type :Polygon
                                           :coordinates [[[0 0] [0 1] [1 1] [0 0]]]}))))

(deftest geojson->postgis->geojson
  (let [gj {:type :Point :coordinates [1.0 2.0]}]
    (is (= gj (coerce/postgis->geojson (coerce/geojson->postgis gj))))))
