(ns psql.aws
  "AWS RDS/Aurora IAM authentication for psql-clj.

   Instead of a static password, RDS can authenticate with a short-lived token
   signed from your AWS credentials. `iam-spec` returns a psql.core/spec whose
   :password is a freshly generated token (valid ~15 minutes), so wrap it in a
   `delay`/refresh or build a new pool before the token expires."
  (:require [psql.core :as pg])
  (:import [software.amazon.awssdk.services.rds RdsUtilities]
           [software.amazon.awssdk.services.rds.model GenerateAuthenticationTokenRequest]
           [software.amazon.awssdk.regions Region]
           [software.amazon.awssdk.auth.credentials
            AwsCredentialsProvider DefaultCredentialsProvider]))

(defn- ->port ^long [port]
  (long (cond (nil? port) 5432
              (string? port) (Integer/parseInt port)
              :else port)))

(defn rds-auth-token
  "Generate a short-lived RDS IAM authentication token. This signs locally from
  your AWS credentials; it does not call AWS.

  Options:
    :host                  RDS endpoint hostname (required)
    :user                  database user enabled for IAM auth (required)
    :region                AWS region string, e.g. \"us-east-1\" (required)
    :port                  defaults to 5432
    :credentials-provider  an AwsCredentialsProvider; defaults to the default chain"
  ^String [{:keys [host port user region credentials-provider]}]
  (let [^AwsCredentialsProvider creds (or credentials-provider (DefaultCredentialsProvider/create))
        utils (-> (RdsUtilities/builder)
                  (.region (Region/of region))
                  (.credentialsProvider creds)
                  (.build))
        req (-> (GenerateAuthenticationTokenRequest/builder)
                (.hostname host)
                (.port (->port port))
                (.username user)
                (.build))]
    (.generateAuthenticationToken utils ^GenerateAuthenticationTokenRequest req)))

(defn iam-spec
  "Build a psql.core/spec whose :password is a fresh RDS IAM auth token.

  Resolves :host/:port/:user/:dbname through psql.core/spec (so PG* env vars and
  ~/.pgpass still apply), then overrides :password with a generated token. RDS
  IAM authentication requires TLS, so :sslmode defaults to \"require\".

  Requires :region (and a :host reachable as the RDS endpoint). Pass
  :credentials-provider to override the default AWS credentials chain."
  [& {:keys [region sslmode credentials-provider] :or {sslmode "require"} :as opts}]
  (let [spec-opts (apply concat (dissoc opts :region :sslmode :credentials-provider))
        base (apply pg/spec spec-opts)
        token (rds-auth-token {:host (:host base)
                               :port (:port base)
                               :user (:user base)
                               :region region
                               :credentials-provider credentials-provider})]
    (assoc base :password token :sslmode sslmode)))
