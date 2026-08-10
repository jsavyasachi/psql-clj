# psql-clj

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/psql-clj.svg)](https://clojars.org/net.clojars.savya/psql-clj)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/psql-clj)](https://cljdoc.org/d/net.clojars.savya/psql-clj)
[![test](https://github.com/jsavyasachi/psql-clj/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/psql-clj/actions/workflows/test.yml)

PostgreSQL helpers for Clojure: connection specs that use `PG*`, `PGSERVICE`, and `.pgpass`; HikariCP pools that keep pgjdbc properties; and [next.jdbc](https://github.com/seancorfield/next-jdbc) type coercion. It supports JSON/JSONB, ranges, arrays, `inet`/`cidr`, geometry, interval, money, and enums. It also provides `COPY` and `LISTEN`/`NOTIFY` helpers. The `psql-clj-gis` companion provides PostGIS geometry.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://clojure.org/guides/deps_and_cli"><img src="https://img.shields.io/badge/deps.edn-5881D8?style=flat&logo=clojure&logoColor=fff" alt="deps.edn" /></a>
<a href="https://clojure.github.io/tools.build/"><img src="https://img.shields.io/badge/tools.build-5881D8?style=flat&logo=clojure&logoColor=fff" alt="tools.build" /></a>
<a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white" alt="PostgreSQL" /></a>
<a href="https://postgis.net"><img src="https://img.shields.io/badge/PostGIS-559343?style=flat&logo=postgresql&logoColor=white" alt="PostGIS" /></a>
<a href="https://github.com/seancorfield/next-jdbc"><img src="https://img.shields.io/badge/next.jdbc-5881D8?style=flat&logo=clojure&logoColor=white" alt="next.jdbc" /></a>
<a href="https://github.com/tomekw/hikari-cp"><img src="https://img.shields.io/badge/HikariCP-0A7E07?style=flat&logoColor=white" alt="HikariCP" /></a>

## Installation

The library has three artifacts. Core has no PostGIS or AWS dependencies.

| Artifact | For |
|---|---|
| `net.clojars.savya/psql-clj` | connection specs (`PG*`/`PGSERVICE`/`.pgpass`), pooling, json/jsonb, ranges, arrays, inet/cidr, geometry, interval, money, enums, COPY, LISTEN/NOTIFY |
| `net.clojars.savya/psql-clj-gis` | PostGIS geometry + geography (pulls `postgis-jdbc`) |
| `net.clojars.savya/psql-clj-aws` | RDS/Aurora IAM authentication (pulls the AWS SDK) |

deps.edn:

```clj
net.clojars.savya/psql-clj     {:mvn/version "2.1.1"}
net.clojars.savya/psql-clj-gis {:mvn/version "2.0.2"}  ;; optional, for PostGIS
net.clojars.savya/psql-clj-aws {:mvn/version "2.0.2"}  ;; optional, for RDS IAM auth
```

Leiningen:

```clj
[net.clojars.savya/psql-clj "2.1.1"]
[net.clojars.savya/psql-clj-gis "2.0.2"]   ;; optional, for PostGIS
[net.clojars.savya/psql-clj-aws "2.0.2"]   ;; optional, for RDS IAM auth
```

## Documentation

- [Connecting](doc/connecting.md)
- [Type conversion](doc/type-conversion.md)
- [PostGIS (psql-clj-gis)](doc/postgis.md)
- [RDS IAM authentication (psql-clj-aws)](doc/rds-iam.md)

The `psql.copy` and `psql.notify` namespace docstrings describe `COPY` streaming and `LISTEN`/`NOTIFY` on [cljdoc](https://cljdoc.org/d/net.clojars.savya/psql-clj).

## Development

Core is at the repository root. The companions are in `modules/gis` and
`modules/aws`. They depend on core, so install core locally first.

```bash
clojure -M:test && clojure -T:build jar               # core (root)
(cd modules/gis && clojure -M:test && clojure -T:build jar)   # PostGIS companion
(cd modules/aws && clojure -M:test && clojure -T:build jar)   # AWS companion
clojure -M:1.10:test                                  # repeat with :1.11 and :1.12
clojure -T:build deploy                               # publish core to Clojars
(cd modules/gis && clojure -T:build deploy)           # publish gis companion
(cd modules/aws && clojure -T:build deploy)           # publish aws companion
```

The core and GIS `:integration` suites read the standard `PG*` variables. Start a local PostGIS instance:

```bash
docker run -d -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=psql_clj_test \
  -p 5432:5432 postgis/postgis:16-3.4
export PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres PGDATABASE=psql_clj_test
clojure -M:test --focus-meta :integration       # core
(cd modules/gis && clojure -M:test --focus-meta :integration)  # gis
```

## License

Copyright © 2014, Remod Oy. All rights reserved.

Maintenance fork from 2026 by Savyasachi. Original project:
<https://github.com/remodoy/clj-postgresql>.

Distributed under the BSD 2-Clause License. See [LICENSE](LICENSE).
