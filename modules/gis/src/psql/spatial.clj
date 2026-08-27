(ns psql.spatial
  "Constructors for PostGIS geometry objects.

   (require '[psql.spatial :as st])"
  (:import [net.postgis.jdbc.geometry Geometry GeometryCollection LineString
            LinearRing MultiLineString MultiPoint MultiPolygon Point Polygon]
           [net.postgis.jdbc PGgeometryLW]))

(defn srid
  "Return the SRID of a geometry object."
  [^Geometry geometry]
  (.getSrid geometry))

(defn with-srid!
  "Set the SRID on a geometry object. Mutate the object."
  [^Geometry geometry srid]
  (doto geometry
    (.setSrid (int srid))))

(defn geography
  "Tag a geometry with SRID 4326 (WGS84) so it can be stored in a PostGIS
  `geography` column, which requires a known SRID."
  [^Geometry geometry]
  (with-srid! geometry 4326))

(defn point
  "Create a 2D or 3D Point."
  ([x y]
   (Point. x y))
  ([x y z]
   (Point. x y z))
  ([coll-or-str]
   (cond (instance? Point coll-or-str) coll-or-str
         (coll? coll-or-str) (let [x (first coll-or-str)
                                   y (second coll-or-str)]
                               (if-let [z (nth coll-or-str 2 nil)]
                                 (Point. x y z)
                                 (Point. x y)))
         :else (Point. (str coll-or-str)))))

(defn multi-point
  "Create a MultiPoint from a collection of Points."
  [points]
  (cond (instance? MultiPoint points) points
        (coll? points) (MultiPoint. ^"[Lnet.postgis.jdbc.geometry.Point;" (into-array Point (map point points)))
        :else (MultiPoint. (str points))))

(defn line-string
  "Create a LineString from a collection of points."
  [points]
  (cond (instance? LineString points) points
        (coll? points) (LineString. ^"[Lnet.postgis.jdbc.geometry.Point;" (into-array Point (map point points)))
        :else (LineString. (str points))))

(defn multi-line-string
  "Create a MultiLineString from a collection of LineStrings."
  [line-strings]
  (cond (instance? MultiLineString line-strings) line-strings
        (coll? line-strings) (MultiLineString. ^"[Lnet.postgis.jdbc.geometry.LineString;" (into-array LineString (map line-string line-strings)))
        :else (MultiLineString. (str line-strings))))

(defn linear-ring
  "Use to construct Polygons from Points."
  [points]
  (cond (instance? LinearRing points) points
        (coll? points) (LinearRing. ^"[Lnet.postgis.jdbc.geometry.Point;" (into-array Point (map point points)))
        :else (LinearRing. (str points))))

(defn polygon
  "Create a Polygon from a collection of LinearRings."
  [linear-rings]
  (cond (instance? Polygon linear-rings) linear-rings
        (coll? linear-rings) (Polygon. ^"[Lnet.postgis.jdbc.geometry.LinearRing;" (into-array LinearRing (map linear-ring linear-rings)))
        :else (Polygon. (str linear-rings))))

(defn multi-polygon
  "Create a MultiPolygon from a collection of Polygons."
  [polygons]
  (cond (instance? MultiPolygon polygons) polygons
        (coll? polygons) (MultiPolygon. ^"[Lnet.postgis.jdbc.geometry.Polygon;" (into-array Polygon (map polygon polygons)))
        :else (MultiPolygon. (str polygons))))

(defn geometry-collection
  "Create a GeometryCollection from a collection of geometry objects."
  [geometries]
  (cond (instance? GeometryCollection geometries) geometries
        (coll? geometries) (GeometryCollection.
                            ^"[Lnet.postgis.jdbc.geometry.Geometry;"
                            (into-array Geometry geometries))
        :else (GeometryCollection. (str geometries))))

(defn pg-geom
  "Wrap a geometry in the lightweight PGgeometry envelope for JDBC."
  [^Geometry geometry]
  (PGgeometryLW. geometry))
