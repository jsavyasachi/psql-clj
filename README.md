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

The library is split into three artifacts so you only pull what you use. Core
has no PostGIS or AWS dependencies.

| Artifact | For |
|---|---|
| `net.clojars.savya/psql-clj` | connection specs, pooling, json/jsonb, arrays, inet, enums |
| `net.clojars.savya/psql-clj-gis` | PostGIS geometry + geography (pulls `postgis-jdbc`) |
| `net.clojars.savya/psql-clj-aws` | RDS/Aurora IAM authentication (pulls the AWS SDK) |

Leiningen:

```clj
[net.clojars.savya/psql-clj "2.0.0"]
[net.clojars.savya/psql-clj-gis "2.0.0"]   ;; optional, for PostGIS
[net.clojars.savya/psql-clj-aws "2.0.0"]   ;; optional, for RDS IAM auth
```

deps.edn:

```clj
net.clojars.savya/psql-clj     {:mvn/version "2.0.0"}
net.clojars.savya/psql-clj-gis {:mvn/version "2.0.0"}  ;; optional, for PostGIS
net.clojars.savya/psql-clj-aws {:mvn/version "2.0.0"}  ;; optional, for RDS IAM auth
```

## Documentation

- [Connecting](doc/connecting.md)
- [Type conversion](doc/type-conversion.md)
- [PostGIS (psql-clj-gis)](doc/postgis.md)
- [RDS IAM authentication (psql-clj-aws)](doc/rds-iam.md)

## Development

Core is at the repo root; the companions live under `modules/gis` and
`modules/aws` and depend on core, so install core locally first.

```bash
lein check && lein test && lein install        # core (root)
cd modules/gis && lein check && lein test       # PostGIS companion
cd modules/aws && lein check && lein test       # AWS companion
lein all test                                   # across Clojure 1.10 / 1.11 / 1.12
```

The `:integration` suites (core and gis) read the standard `PG*` variables. A
quick local PostGIS:

```bash
docker run -d -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=psql_clj_test \
  -p 5432:5432 postgis/postgis:16-3.4
export PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres PGDATABASE=psql_clj_test
lein test :integration                          # core
(cd modules/gis && lein test :integration)      # gis
```

## License

Copyright © 2014, Remod Oy. All rights reserved.

Maintenance fork (2026) by Savyasachi. Original project:
<https://github.com/remodoy/clj-postgresql>.

Distributed under the BSD 2-Clause License. See [LICENSE](LICENSE).
