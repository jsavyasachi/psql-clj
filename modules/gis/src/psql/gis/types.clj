(ns psql.gis.types
  "next.jdbc coercion for PostGIS `geometry` and `geography` columns.

   Requiring this namespace registers the extensions (mirroring how requiring
   psql.types activates the core coercions). It plugs into core's
   psql.types/map->parameter multimethod and extends next.jdbc's protocols, so
   core never has to depend on PostGIS."
  (:require [psql.types :as types]
            [psql.coerce :as coerce]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs])
  (:import [net.postgis.jdbc.geometry Geometry]
           [net.postgis.jdbc PGgeometry PGgeography PGgeometryLW]
           [java.sql PreparedStatement]))

;; GeoJSON-like maps bound to a geometry column become PGgeometry.
(defmethod types/map->parameter :geometry
  [m _]
  (PGgeometryLW. ^Geometry (coerce/geojson->postgis m)))

;; geography columns are WGS84; tag the geometry with SRID 4326 (PostGIS rejects
;; an unset SRID on geography).
(defmethod types/map->parameter :geography
  [m _]
  (PGgeometryLW. ^Geometry (doto ^Geometry (coerce/geojson->postgis m)
                             (.setSrid 4326))))

(extend-protocol prepare/SettableParameter
  ;; PostGIS geometry/geography objects wrap into the lightweight PGgeometry.
  Geometry
  (set-parameter [^Geometry g ^PreparedStatement ps ^long i]
    (.setObject ps i (PGgeometryLW. ^Geometry g))))

(extend-protocol rs/ReadableColumn
  ;; geometry columns read back as PGgeometry; return the geometry as GeoJSON.
  PGgeometry
  (read-column-by-label [^PGgeometry v _]
    (coerce/postgis->geojson (.getGeometry v)))
  (read-column-by-index [^PGgeometry v _ _]
    (coerce/postgis->geojson (.getGeometry v)))

  ;; geography columns read back as the distinct PGgeography wrapper.
  PGgeography
  (read-column-by-label [^PGgeography v _]
    (coerce/postgis->geojson (.getGeometry v)))
  (read-column-by-index [^PGgeography v _ _]
    (coerce/postgis->geojson (.getGeometry v))))
