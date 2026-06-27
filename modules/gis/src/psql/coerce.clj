(ns psql.coerce
  (:require [psql.spatial :as st]))

(defmulti geojson->postgis :type)

(defmethod geojson->postgis :Point
  [m]
  (apply st/point (:coordinates m)))

(defmethod geojson->postgis :MultiPoint
  [m]
  (st/multi-point (:coordinates m)))

(defmethod geojson->postgis :LineString
  [m]
  (st/line-string (:coordinates m)))

(defmethod geojson->postgis :MultiLineString
  [m]
  (st/multi-line-string (:coordinates m)))

(defmethod geojson->postgis :Polygon
  [m]
  (st/polygon (:coordinates m)))

(defmethod geojson->postgis :MultiPolygon
  [m]
  (st/multi-polygon (:coordinates m)))

(defprotocol PostgisToCoords
  (postgis->coords [o]))

(extend-protocol PostgisToCoords
  net.postgis.jdbc.geometry.Point
  (postgis->coords [o]
    (if (= (.dimension o) 3)
      [(.x o) (.y o) (.z o)]
      [(.x o) (.y o)]))
  net.postgis.jdbc.geometry.MultiPoint
  (postgis->coords [o]
    (mapv postgis->coords (.getPoints o)))
  net.postgis.jdbc.geometry.LineString
  (postgis->coords [o]
    (mapv postgis->coords (.getPoints o)))
  net.postgis.jdbc.geometry.MultiLineString
  (postgis->coords [o]
    (mapv postgis->coords (.getLines o)))
  net.postgis.jdbc.geometry.LinearRing
  (postgis->coords [o]
    (mapv postgis->coords (.getPoints o)))
  net.postgis.jdbc.geometry.Polygon
  (postgis->coords [o]
    (mapv postgis->coords (for [i (range (.numRings o))] (.getRing o i))))
  net.postgis.jdbc.geometry.MultiPolygon
  (postgis->coords [o]
    (mapv postgis->coords (.getPolygons o))))

(defprotocol PostgisToGeoJSON
  (postgis->geojson [o]))

(extend-protocol PostgisToGeoJSON
  net.postgis.jdbc.geometry.Point
  (postgis->geojson [o]
    {:type :Point
     :coordinates (postgis->coords o)})
  net.postgis.jdbc.geometry.MultiPoint
  (postgis->geojson [o]
    {:type :MultiPoint
     :coordinates (postgis->coords o)})
  net.postgis.jdbc.geometry.LineString
  (postgis->geojson [o]
    {:type :LineString
     :coordinates (postgis->coords o)})
  net.postgis.jdbc.geometry.MultiLineString
  (postgis->geojson [o]
    {:type :MultiLineString
     :coordinates (postgis->coords o)})
  net.postgis.jdbc.geometry.Polygon
  (postgis->geojson [o]
    {:type :Polygon
     :coordinates (postgis->coords o)})
  net.postgis.jdbc.geometry.MultiPolygon
  (postgis->geojson [o]
    {:type :MultiPolygon
     :coordinates (postgis->coords o)}))
