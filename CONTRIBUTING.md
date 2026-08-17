# Contributing to psql-clj

Send bug reports, fixes, and focused feature contributions for `psql-clj`.

## Before you start

- For work beyond a trivial fix, **open an issue first**. We can agree on the
  approach before you spend time.
- Check existing issues and pull requests to avoid duplicate work.

## Project layout

`psql-clj` is a `deps.edn` monorepo with three artifacts:

| Path | Artifact |
|---|---|
| `.` (root) | `net.clojars.savya/psql-clj` - core |
| `modules/gis` | `net.clojars.savya/psql-clj-gis` - PostGIS |
| `modules/aws` | `net.clojars.savya/psql-clj-aws` - RDS IAM auth |

The companions depend on core. Install core locally before you build them:

```bash
clojure -M:test && clojure -T:build install     # core (root)
(cd modules/gis && clojure -M:test)              # PostGIS companion
(cd modules/aws && clojure -M:test)              # AWS companion
```

The core and GIS `:integration` suites need a live PostgreSQL instance with PostGIS. They read the standard `PG*` variables:

```bash
docker run -d -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=psql_clj_test \
  -p 5432:5432 postgis/postgis:16-3.4
export PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres PGDATABASE=psql_clj_test
clojure -M:test --focus-meta :integration
(cd modules/gis && clojure -M:test --focus-meta :integration)
```

Requirements for a mergeable change:

- **Tests first.** Add or update tests for the behavior you change; for a bug
  fix, include a regression test that fails before your fix and passes after.
- **Green build.** The test suite passes and the build reports **zero**
  reflection warnings in every affected module.
- **One scope.** Keep each pull request to one logical change.

## Commits and pull requests

- Follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` …).
- Keep the subject in the imperative mood and under ~72 characters.
- Update `CHANGELOG.md` when your change is user-visible.
- Rebase on the latest `main` before you open the pull request.

## License

By contributing, you agree that your contributions will be licensed under the
BSD 2-Clause License, the same license as this project (see `LICENSE`).
