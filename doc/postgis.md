# PostGIS geometry & geography (psql-clj-gis)

Add `net.clojars.savya/psql-clj-gis` and require `psql.gis.types` to activate
the coercions. `psql.spatial` builds `net.postgis.jdbc.geometry.*` objects; they
can be used directly as query parameters and are read back as GeoJSON maps.

```clj
(require '[psql.spatial :as st]
         '[psql.gis.types])   ;; registers geometry/geography next.jdbc coercion

(st/point 1 2)                           ;=> POINT(1 2)
(st/point [1 2])                         ;=> POINT(1 2)
(st/multi-point [[1 2] [3 4]])           ;=> MULTIPOINT(1 2,3 4)
(st/line-string [[1 2] [3 4]])           ;=> LINESTRING(1 2,3 4)
(st/polygon [[[1 2] [3 4] [5 6] [1 2]]]) ;=> POLYGON((1 2,3 4,5 6,1 2))

(jdbc/execute! db ["INSERT INTO shapes (geom) VALUES (?)" (st/point [1 2])])
(jdbc/execute-one! db ["SELECT geom FROM shapes LIMIT 1"])
;; => {:shapes/geom {:type :Point :coordinates [1.0 2.0]}}
```

For `geography` columns (WGS84), tag the geometry with SRID 4326 via
`st/geography`:

```clj
(jdbc/execute! db ["INSERT INTO places (geog) VALUES (?)"
                   (st/geography (st/point [13.4 52.5]))])
```

