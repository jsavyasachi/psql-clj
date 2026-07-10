# RDS IAM authentication (psql-clj-aws)

Add `net.clojars.savya/psql-clj-aws` to authenticate to RDS/Aurora with a
short-lived IAM token instead of a static password. `iam-spec` returns a normal
spec with the token as `:password` (and `sslmode=require`):

```clj
(require '[psql.aws :as aws]
         '[psql.pool :as pool]
         '[next.jdbc :as jdbc])

(def spec (aws/iam-spec :host "mydb.abc123.us-east-1.rds.amazonaws.com"
                        :user "appuser"
                        :dbname "app"
                        :region "us-east-1"))

(jdbc/execute! spec ["SELECT 1"])
;; or pool it: (pool/pooled-db spec {})
```

The token is signed locally from the default AWS credential chain (no API call);
pass `:credentials-provider` to override it. Tokens last ~15 minutes, so refresh
the spec (and rebuild the pool) periodically.

