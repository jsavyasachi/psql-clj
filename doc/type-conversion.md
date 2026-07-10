# Automatic type conversion

Native Clojure maps, vectors and sequences are accepted as parameters; the
target SQL type reported by PostgreSQL decides the conversion.

```clj
(jdbc/execute! db ["SELECT ?::int[]  AS arr" [1 2 3 4]])
;; => [{:arr [1 2 3 4]}]
(jdbc/execute! db ["SELECT ?::json   AS obj" {"foo" "bar"}])
;; => [{:obj {"foo" "bar"}}]
(jdbc/execute! db ["SELECT ?::timestamptz AS epoch" 1])
;; => [{:epoch #inst "1970-01-01T00:00:00.001-00:00"}]
```

- **Maps** — `json`/`jsonb` columns accept any map; `geometry` columns accept GeoJSON-like maps. Extend with `(defmethod psql.types/map->parameter :mytype [m _] ...)`.
- **Vectors** — array columns (`int[]`, `text[]`, ...) accept vectors; `inet` accepts `[192 168 1 11]`. Extend with `(defmethod psql.types/vec->parameter :mytype [v _] ...)`.
- **Other seqables** (lists, lazy seqs) are treated like vectors.
- **Numbers** bound to `timestamp`/`timestamptz` become `java.sql.Timestamp`. Extend with `(defmethod psql.types/num->parameter :mytype [n _] ...)`.
- **Keywords** bind to `enum` columns by name: `:happy` goes into a `mood` enum as `'happy'` (and `?::mood` casts work). Enum values read back as plain strings.

On the way out, `json`/`jsonb` parse to Clojure data and arrays become vectors.

## PostgreSQL geometric types

`psql.core` constructs the built-in `org.postgresql.geometric.*` types:

```clj
(pg/point 1 2)              ;=> (1.0,2.0)
(pg/box [1 2] [3 4])        ;=> (1.0,2.0),(3.0,4.0)
(pg/circle [25 30] 5)       ;=> <(25.0,30.0),5.0>
(pg/line (pg/point 1 2) (pg/point 3 4))
(pg/lseg [1 2] [10 20])
(pg/path [[1 2] [10 20] [50 100]] true)
(pg/polygon [[1 2] [3 4] [5 6]])
```

