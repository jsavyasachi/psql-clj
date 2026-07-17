# Changelog

## [2.1.1] - 2026-07-16
### Fixed
- `.pgpass` comment lines are now skipped by the intended leading-`#` rule rather
  than only by an incidental field-count check, so a commented-out entry with five
  colon fields can never be parsed into a record (`psql.pgpass`).

## [2.1.0] - 2026-07-16

Parity pass over pgjdbc 42.7.13 + libpq 16/17 semantics + next.jdbc. All additions are backward
compatible.

### Added
- **Type read/write symmetry** (`psql.types`): built-in geometric types
  (point/box/circle/line/lseg/path/polygon), `interval`, and `money` now read back into Clojure data
  instead of raw strings, symmetric with the existing write constructors.
- **Range and multirange coercion**: `int4range`/`int8range`/`numrange`/`tsrange`/`tstzrange`/`daterange`
  and their multirange forms round-trip as maps, modeling inclusive/exclusive and unbounded bounds and
  the empty range.
- **`inet`/`cidr`** now read back (IPv6 included) with address family and prefix preserved.
- **Recursive multi-dimensional / nested SQL arrays** round-trip on both read and write.
- **JSON/JSONB scalar writes** via `psql.types/json` / `jsonb` (strings, numbers, booleans, JSON null),
  complementing the existing map/vector coercion.
- **`PGSERVICE` / `pg_service.conf`** (`psql.service`): connection service files resolve through pgjdbc's
  own parser, honoring `PGSERVICEFILE`/`PGSYSCONFDIR`, with libpq precedence (explicit options > service >
  `PG*` env > defaults).
- **libpq environment variables**: `spec` now maps `PGSSLMODE`, `PGSSLCERT`/`PGSSLKEY`/`PGSSLROOTCERT`,
  `PGAPPNAME`, `PGCONNECT_TIMEOUT`, `PGOPTIONS`, `PGTARGETSESSIONATTRS`, `PGCHANNELBINDING`, `PGGSSENCMODE`
  and related vars to their pgjdbc properties.
- **`COPY` streaming** (`psql.copy`): thin `copy-in` / `copy-out` over pgjdbc's `CopyManager`.
- **`LISTEN`/`NOTIFY`** (`psql.notify`): `listen!` / `unlisten!` / `notify!` with injection-safe channel
  validation, plus `get-notifications` polling.

### Fixed
- **Pooled connections no longer drop pgjdbc properties.** `db-spec->pool-config` previously kept only
  host/port/dbname/user/password and silently discarded everything else, so a pooled spec lost `sslmode`,
  `ApplicationName`, timeouts, and more - including the `sslmode=require` that `psql-clj-aws`' IAM specs
  depend on. All non-structural spec keys now flow through to the connection, with multi-host/failover and
  prebuilt/`service` URLs supported and URL components safely encoded.
- **`.pgpass` now follows libpq rules** (`psql.pgpass`): honors `PGPASSFILE`, splits only on unescaped
  colons and unescapes `\:`/`\\`, skips comment/blank/malformed lines, normalizes port matching, and
  ignores a group/world-readable password file (a credential-exposure fix) on POSIX filesystems.

## [2.0.2] - 2026-07-12
### Changed
- Migrate the build to deps.edn and tools.build, with Leiningen supported via lein-tools-deps.
- Reorganized the README into a cljdoc article tree under `doc/` (Connecting, Type conversion, PostGIS, RDS IAM). Documentation content is unchanged; ships with the next release.

All notable changes to this project are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project adheres to [Semantic Versioning](https://semver.org/).

## [2.0.1] - 2026-06-27

### Changed
- Dependency currency (via antq): `postgis-jdbc` 2024.1.0 → 2025.1.1,
  `postgresql` 42.7.7 → 42.7.11, AWS SDK `rds` 2.46.7 → 2.46.17. Validated
  against live PostGIS; the postgis-jdbc year bump did not change packages.

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

[2.0.1]: https://github.com/jsavyasachi/psql-clj/releases/tag/v2.0.1
[2.0.0]: https://github.com/jsavyasachi/psql-clj/releases/tag/v2.0.0
[1.0.0]: https://github.com/jsavyasachi/psql-clj/releases/tag/v1.0.0
