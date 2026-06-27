(ns psql.integration-test
  "End-to-end tests against a live PostgreSQL.

   Tagged :integration so they are excluded from the default `lein test` run.
   Run them with `lein test :integration` against a database described by the
   standard PG* environment variables (PGHOST, PGPORT, PGUSER, PGDATABASE,
   PGPASSWORD)."
  (:require [clojure.test :refer [deftest is testing]]
            [psql.core :as pg]
            [psql.types]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [java.net InetAddress]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- db [] (pg/spec))

(defn- q1 [sql & params]
  (jdbc/execute-one! (db) (into [sql] params) opts))

(deftest ^:integration read-data-types
  (is (= {:x true}        (q1 "SELECT true AS x")))
  (is (= {:x false}       (q1 "SELECT false AS x")))
  (is (= {:x [1 2 3]}     (q1 "SELECT '1 2 3'::oidvector AS x")))
  (is (= {:x ["a" "b"]}   (q1 "SELECT '{a,b}'::text[] AS x")))
  (is (= {:x {"foo" 1}}   (q1 "SELECT '{\"foo\":1}'::json AS x")))
  (is (= {:x {"foo" 1}}   (q1 "SELECT '{\"foo\":1}'::jsonb AS x"))))

(deftest ^:integration write-parameter-types
  (is (= {:x true}              (q1 "SELECT true AS x WHERE true = ?" true)))
  (is (= {:x {"foo" {"bar" 1}}} (q1 "SELECT ?::json AS x" {:foo {:bar 1}})))
  (is (= {:x {"foo" {"bar" 1}}} (q1 "SELECT ?::jsonb AS x" {:foo {:bar 1}})))
  (is (= {:x [1 2 7 6 5]}       (q1 "SELECT ?::int[] AS x" [1 2 7 6 5])))
  (is (= {:x ["a" "b" "c"]}     (q1 "SELECT ?::text[] AS x" '("a" "b" "c"))))
  (is (= {:x "127.0.0.1"}       (q1 "SELECT ?::inet AS x" (InetAddress/getByName "127.0.0.1"))))
  (is (= {:x "::1"}             (q1 "SELECT ?::inet AS x" (InetAddress/getByName "::1")))))

(deftest ^:integration json-jsonb-table-roundtrip
  (jdbc/with-transaction [tx (db) {:rollback-only true}]
    (jdbc/execute! tx ["CREATE TEMP TABLE t (j json, jb jsonb)"])
    (jdbc/execute! tx ["INSERT INTO t (j, jb) VALUES (?::json, ?::jsonb)"
                       {:x 42 :a [4 3 2]} {:x 42 :a [4 3 2]}])
    (is (= {:j {"x" 42 "a" [4 3 2]} :jb {"x" 42 "a" [4 3 2]}}
           (jdbc/execute-one! tx ["SELECT * FROM t"] opts)))))
