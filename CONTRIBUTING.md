# Contributing to psql-clj

Thanks for your interest in improving `psql-clj`. Bug reports, fixes, and
focused feature contributions are all welcome.

## Before you start

- For anything beyond a trivial fix, **open an issue first** so we can agree on
  the approach before you invest time.
- Check existing issues and pull requests to avoid duplicate work.

## Project layout

`psql-clj` is a Leiningen monorepo of three artifacts:

| Path | Artifact |
|---|---|
| `.` (root) | `net.clojars.savya/psql-clj` — core |
| `modules/gis` | `net.clojars.savya/psql-clj-gis` — PostGIS |
| `modules/aws` | `net.clojars.savya/psql-clj-aws` — RDS IAM auth |

The companions depend on core, so install core locally before building them:

```bash
lein check && lein test && lein install        # core (root)
cd modules/gis && lein check && lein test       # PostGIS companion
cd modules/aws && lein check && lein test       # AWS companion
```

The `:integration` suites (core and gis) need a live PostgreSQL with PostGIS and
read the standard `PG*` variables:

```bash
docker run -d -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=psql_clj_test \
  -p 5432:5432 postgis/postgis:16-3.4
export PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres PGDATABASE=psql_clj_test
lein test :integration
(cd modules/gis && lein test :integration)
```

The bar for a mergeable change:

- **Tests first.** Add or update tests for the behavior you change; for a bug
  fix, include a regression test that fails before your fix and passes after.
- **Green build.** `lein test` passes and `lein check` reports **zero**
  reflection warnings in every affected module.
- **No scope creep.** Keep each pull request to one logical change.

## Commits and pull requests

- Follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` …).
- Keep the subject in the imperative mood and under ~72 characters.
- Update `CHANGES.md` when your change is user-visible.
- Rebase on the latest `main` before opening the pull request.

## License

By contributing, you agree that your contributions will be licensed under the
BSD 2-Clause License, the same license as this project (see `LICENSE`).
