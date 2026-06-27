# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.0.0] - 2026-06-27

Modular split plus new features, addressing the upstream issue/PR backlog.

### Changed
- **Breaking:** PostGIS moved out of core into a new
  `net.clojars.savya/psql-clj-gis` artifact. Core no longer pulls
  `postgis-jdbc`/`postgis-geometry` (addresses upstream #24, #12). PostGIS users
  add `psql-clj-gis` and `(require '[psql.gis.types])`.

### Added
- `net.clojars.savya/psql-clj-gis` — `psql.spatial` / `coerce` / `geojson` plus
  the geometry next.jdbc coercion, now an opt-in companion.
- PostGIS **geography** support (upstream #5): `psql.spatial/geography` (SRID
  4326) and `PGgeography` reads.
- **Enum** binding (upstream #9): a Clojure keyword binds to an `enum` column by
  name.
- `net.clojars.savya/psql-clj-aws` — RDS/Aurora **IAM authentication** (revives
  upstream PR #26 on the current AWS SDK v2): `psql.aws/iam-spec` /
  `rds-auth-token`.

## [1.0.0] - 2026-06-27

Revival and modernization of [remodoy/clj-postgresql](https://github.com/remodoy/clj-postgresql),
republished as `net.clojars.savya/psql-clj`.

### Changed
- **Breaking:** root namespaces renamed `clj-postgresql.*` → `psql.*`.
- **Breaking:** type coercion migrated from the end-of-life `clojure.java.jdbc`
  to [next.jdbc](https://github.com/seancorfield/next-jdbc) (`SettableParameter`
  / `ReadableColumn`). Consumers now drive queries with next.jdbc.
- PostGIS updated to `postgis-jdbc 2024.1.0`, which repackaged its classes to
  `net.postgis.jdbc.*`.
- Dependencies bumped: `postgresql 42.7.7`, `hikari-cp 4.1.0`, `cheshire 6.2.0`,
  `schema 1.4.1`. Tested on Clojure 1.10 / 1.11 / 1.12.

### Added
- `spec` honors the `PGPASSWORD` environment variable (libpq precedence:
  explicit `:password` → `PGPASSWORD` → `~/.pgpass`).
- `clojure.test` suite split into unit and `:integration`.
- GitHub Actions CI: a JDK × Clojure matrix plus an integration job backed by a
  postgis service container.

### Fixed
- `psql.geojson/multi-point` returned `nil` coordinates; it now emits the actual
  point positions.
- Eliminated all reflection warnings.

### Removed
- Orphaned `protocol.clj` (an abandoned wire-protocol spike with hardcoded
  credentials) and the unused `geometric/Point.clj` example.
- Unused `clj-time` and `org.clojure/java.data` dependencies.
- Travis configuration, `deps.edn` and `Makefile`.

[2.0.0]: https://github.com/jsavyasachi/psql-clj/releases/tag/v2.0.0
[1.0.0]: https://github.com/jsavyasachi/psql-clj/releases/tag/v1.0.0
