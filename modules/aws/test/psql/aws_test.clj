(ns psql.aws-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.aws :as aws])
  (:import [software.amazon.awssdk.auth.credentials
            AwsBasicCredentials StaticCredentialsProvider]))

;; Static, fake credentials. generateAuthenticationToken signs locally, so this
;; produces a real SigV4-signed token offline without ever calling AWS.
(def ^:private creds
  (StaticCredentialsProvider/create
   (AwsBasicCredentials/create "AKIAIOSFODNN7EXAMPLE"
                               "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")))

(deftest rds-auth-token-is-signed-locally
  (let [token (aws/rds-auth-token {:host "mydb.abc123.us-east-1.rds.amazonaws.com"
                                   :port 5432
                                   :user "appuser"
                                   :region "us-east-1"
                                   :credentials-provider creds})]
    (testing "token embeds the endpoint and a SigV4 signature"
      (is (string? token))
      (is (re-find #"^mydb\.abc123\.us-east-1\.rds\.amazonaws\.com:5432" token))
      (is (re-find #"X-Amz-Signature=" token))
      (is (re-find #"Action=connect" token))
      (is (re-find #"DBUser=appuser" token)))))

(deftest port-accepts-string-or-int
  (testing "a string port (as PG* env vars supply) is coerced"
    (let [token (aws/rds-auth-token {:host "h.rds.amazonaws.com" :port "5432"
                                     :user "u" :region "eu-central-1"
                                     :credentials-provider creds})]
      (is (re-find #"^h\.rds\.amazonaws\.com:5432" token)))))
