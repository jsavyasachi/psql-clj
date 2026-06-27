(ns psql.gis-integration-test
  "PostGIS round-trips against a live PostgreSQL with the postgis extension.

   Tagged :integration; run with `lein test :integration` against a PG*-described
   database. Requiring psql.gis.types activates the geometry/geography coercion."
  (:require [clojure.test :refer [deftest is testing]]
            [psql.core :as pg]
            [psql.spatial :as st]
            [psql.gis.types]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- db [] (pg/spec))

(deftest ^:integration geometry-roundtrip
  (jdbc/with-transaction [tx (db) {:rollback-only true}]
    (jdbc/execute! tx ["CREATE EXTENSION IF NOT EXISTS postgis"])
    (jdbc/execute! tx ["CREATE TEMP TABLE g (geom geometry)"])
    (testing "a geometry object inserts and reads back as GeoJSON"
      (jdbc/execute! tx ["INSERT INTO g (geom) VALUES (?)" (st/point [1 2])])
      (is (= {:type :Point :coordinates [1.0 2.0]}
             (:geom (jdbc/execute-one! tx ["SELECT geom FROM g"] opts)))))
    (testing "a GeoJSON map binds to a geometry column"
      (jdbc/execute! tx ["DELETE FROM g"])
      (jdbc/execute! tx ["INSERT INTO g (geom) VALUES (?::geometry)"
                         {:type :Point :coordinates [3 4]}])
      (is (= {:type :Point :coordinates [3.0 4.0]}
             (:geom (jdbc/execute-one! tx ["SELECT geom FROM g"] opts)))))))

(deftest ^:integration geography-roundtrip
  (jdbc/with-transaction [tx (db) {:rollback-only true}]
    (jdbc/execute! tx ["CREATE EXTENSION IF NOT EXISTS postgis"])
    (jdbc/execute! tx ["CREATE TEMP TABLE gg (geog geography(Point,4326))"])
    (testing "a WGS84-tagged geometry stores in a geography column"
      (jdbc/execute! tx ["INSERT INTO gg (geog) VALUES (?)"
                         (st/geography (st/point [13.4 52.5]))])
      (is (= {:type :Point :coordinates [13.4 52.5]}
             (:geog (jdbc/execute-one! tx ["SELECT geog FROM gg"] opts)))))))
