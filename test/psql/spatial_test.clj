(ns psql.spatial-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.spatial :as st])
  (:import [net.postgis.jdbc.geometry Point MultiPoint LineString
            MultiLineString LinearRing Polygon MultiPolygon]))

(deftest constructors
  (is (instance? Point (st/point [1 2])))
  (is (instance? Point (st/point 1 2)))
  (is (instance? Point (st/point 1 2 3)))
  (is (instance? MultiPoint (st/multi-point [[1 2] [3 4]])))
  (is (instance? LineString (st/line-string [[0 0] [1 1]])))
  (is (instance? MultiLineString (st/multi-line-string [[[0 0] [1 1]] [[2 2] [3 3]]])))
  (is (instance? LinearRing (st/linear-ring [[0 0] [0 1] [1 1] [0 0]])))
  (is (instance? Polygon (st/polygon [[[0 0] [0 1] [1 1] [0 0]]])))
  (is (instance? MultiPolygon (st/multi-polygon [[[[0 0] [0 1] [1 1] [0 0]]]]))))

(deftest idempotent-on-existing-geometry
  (let [p (st/point [1 2])]
    (is (identical? p (st/point p)))))

(deftest srid-roundtrip
  (let [p (st/with-srid! (st/point [1 2]) 4326)]
    (is (= 4326 (st/srid p)))))
