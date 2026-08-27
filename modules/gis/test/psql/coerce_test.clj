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

(deftest geometry-collection-roundtrip
  (let [gj {:type :GeometryCollection
            :geometries [{:type :Point :coordinates [1.0 2.0]}
                         {:type :LineString :coordinates [[0.0 0.0] [1.0 1.0]]}]}]
    (testing "geojson->postgis builds a GeometryCollection"
      (is (instance? net.postgis.jdbc.geometry.GeometryCollection
                     (coerce/geojson->postgis gj))))
    (testing "round-trips back to the same GeoJSON"
      (is (= gj (coerce/postgis->geojson (coerce/geojson->postgis gj)))))))

(deftest feature-and-feature-collection-conversion
  (testing "a Feature converts to its underlying geometry"
    (is (instance? net.postgis.jdbc.geometry.Point
                   (coerce/geojson->postgis
                    {:type :Feature
                     :geometry {:type :Point :coordinates [1 2]}
                     :properties {:name "x"}}))))
  (testing "a FeatureCollection converts to a GeometryCollection of its geometries"
    (let [gc (coerce/geojson->postgis
              {:type :FeatureCollection
               :features [{:type :Feature :geometry {:type :Point :coordinates [1 2]} :properties {}}
                          {:type :Feature :geometry {:type :Point :coordinates [3 4]} :properties {}}]})]
      (is (instance? net.postgis.jdbc.geometry.GeometryCollection gc))
      (is (= {:type :GeometryCollection
              :geometries [{:type :Point :coordinates [1.0 2.0]}
                           {:type :Point :coordinates [3.0 4.0]}]}
             (coerce/postgis->geojson gc))))))
