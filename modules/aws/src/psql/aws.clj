(ns psql.aws
  "AWS RDS/Aurora IAM authentication for psql-clj.

   RDS can authenticate with a short-lived token, not a static password. The token
   is signed with your AWS credentials. `iam-spec` returns a psql.core/spec with
   a new token as :password. The token is valid for about 15 minutes. Use
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
  "Generate a short-lived RDS IAM authentication token. Sign it locally with
  your AWS credentials. Do not call AWS.

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
  "Build a psql.core/spec with a new RDS IAM auth token as :password.

  Resolve :host/:port/:user/:dbname with psql.core/spec. PG* env vars and
  ~/.pgpass still apply. Then override :password with a new token. RDS IAM
  authentication requires TLS, so :sslmode defaults to \"require\".

  Require :region and a :host reachable as the RDS endpoint. Pass
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
