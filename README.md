# psql-clj

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/psql-clj.svg)](https://clojars.org/net.clojars.savya/psql-clj)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/psql-clj)](https://cljdoc.org/d/net.clojars.savya/psql-clj)
[![test](https://github.com/jsavyasachi/psql-clj/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/psql-clj/actions/workflows/test.yml)

PostgreSQL helpers for Clojure: environment- and `.pgpass`-aware connection specs, HikariCP pooling, and [next.jdbc](https://github.com/seancorfield/next-jdbc) type coercion for JSON/JSONB, arrays, `inet`, and PostGIS geometry.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=white" alt="Clojure" /></a>
<a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white" alt="PostgreSQL" /></a>
<a href="https://postgis.net"><img src="https://img.shields.io/badge/PostGIS-559343?style=flat&logo=postgresql&logoColor=white" alt="PostGIS" /></a>
<a href="https://github.com/seancorfield/next-jdbc"><img src="https://img.shields.io/badge/next.jdbc-5881D8?style=flat&logo=clojure&logoColor=white" alt="next.jdbc" /></a>
<a href="https://github.com/tomekw/hikari-cp"><img src="https://img.shields.io/badge/HikariCP-0A7E07?style=flat&logoColor=white" alt="HikariCP" /></a>
<a href="https://leiningen.org"><img src="https://img.shields.io/badge/Leiningen-5881D8?style=flat&logo=clojure&logoColor=white" alt="Leiningen" /></a>

## Installation

Leiningen / Boot:

```clj
[net.clojars.savya/psql-clj "1.0.0"]
```

deps.edn:

```clj
net.clojars.savya/psql-clj {:mvn/version "1.0.0"}
```

## Connecting

`spec` and `pool` read **PGHOST**, **PGPORT**, **PGUSER**, **PGDATABASE** and **PGPASSWORD** from the environment and fall back to `~/.pgpass` for the password. Function arguments override anything from the environment.

```clj
(require '[psql.core :as pg]
         '[next.jdbc :as jdbc])

;; A plain db-spec, suitable for any next.jdbc call.
(def db (pg/spec))
(jdbc/execute! db ["SELECT 1 AS one"])

;; A HikariCP-pooled datasource.
(def pooled (pg/pool :host "db1.example.com"
                     :user "myaccount"
                     :dbname "anotherdb"
                     :password "foobar"
                     :hikari {:read-only true}))
(jdbc/execute! pooled ["SELECT 'hello from db'"])
(pg/close! pooled)
```

Delay creation so connection parameters are not resolved (and the pool is not
opened) at load time:

```clj
(def db (delay (pg/pool)))
(jdbc/execute! @db ["SELECT 1"])
```

`spec` resolves its map as follows:

1. `:dbtype` defaults to `"postgresql"`; the current OS username seeds `:user` and `:dbname` (as `psql` does).
2. `PGHOST` / `PGPORT` / `PGUSER` / `PGDATABASE` override `:host` / `:port` / `:user` / `:dbname`.
3. Explicit `spec` arguments override everything above.
4. The password is taken from an explicit `:password`, then `PGPASSWORD`, then a `~/.pgpass` match.

## Automatic type conversion

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

On the way out, `json`/`jsonb` parse to Clojure data, arrays become vectors, and PostGIS geometry is returned as GeoJSON.

## PostGIS geometry

`psql.spatial` builds `net.postgis.jdbc.geometry.*` objects. They can be used
directly as query parameters and are read back as GeoJSON maps.

```clj
(require '[psql.spatial :as st])

(st/point 1 2)                           ;=> POINT(1 2)
(st/point [1 2])                         ;=> POINT(1 2)
(st/multi-point [[1 2] [3 4]])           ;=> MULTIPOINT(1 2,3 4)
(st/line-string [[1 2] [3 4]])           ;=> LINESTRING(1 2,3 4)
(st/polygon [[[1 2] [3 4] [5 6] [1 2]]]) ;=> POLYGON((1 2,3 4,5 6,1 2))

(jdbc/execute! db ["INSERT INTO shapes (geom) VALUES (?)" (st/point [1 2])])
(jdbc/execute-one! db ["SELECT geom FROM shapes LIMIT 1"])
;; => {:shapes/geom {:type :Point :coordinates [1.0 2.0]}}
```

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

## Development

```bash
lein check          # compile every namespace (0 reflection warnings)
lein test           # unit tests (no database needed)
lein test :integration   # round-trips against a live PostgreSQL+PostGIS
lein all test       # unit tests across Clojure 1.10 / 1.11 / 1.12
```

The integration suite reads the standard `PG*` variables. A quick local
database:

```bash
docker run -d -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=psql_clj_test \
  -p 5432:5432 postgis/postgis:16-3.4
PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres PGDATABASE=psql_clj_test \
  lein test :integration
```

## License

Copyright © 2014, Remod Oy. All rights reserved.

Maintenance fork (2026) by Savyasachi. Original project:
<https://github.com/remodoy/clj-postgresql>.

Distributed under the BSD 2-Clause License. See [LICENSE](LICENSE).
