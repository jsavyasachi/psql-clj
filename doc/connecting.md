# Connecting

`spec` and `pool` read **PGHOST**, **PGPORT**, **PGUSER**, **PGDATABASE**, and **PGPASSWORD** from the environment. They use `~/.pgpass` if no password is set. Function arguments override environment values.

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

Use `delay` to prevent connection parameter resolution and pool creation at load time:

```clj
(def db (delay (pg/pool)))
(jdbc/execute! @db ["SELECT 1"])
```

`spec` resolves its map in this order:

1. `:dbtype` defaults to `"postgresql"`. The current OS username sets `:user` and `:dbname`, as `psql` does.
2. `PGHOST` / `PGPORT` / `PGUSER` / `PGDATABASE` override `:host` / `:port` / `:user` / `:dbname`.
3. Explicit `spec` arguments override everything above.
4. The password comes from an explicit `:password`, then `PGPASSWORD`, then a `~/.pgpass` match.
