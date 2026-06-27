(ns psql.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.core :as pg]
            [psql.pgpass :as pgpass])
  (:import [org.postgresql.geometric PGpoint PGbox PGcircle PGline PGlseg
            PGpath PGpolygon]
           [org.postgresql.util PGInterval PGmoney PGobject]))

(deftest getenv->map-test
  (testing "keywordizes string keys of any map"
    (is (= {:PGHOST "h" :PGUSER "u"}
           (pg/getenv->map {"PGHOST" "h" "PGUSER" "u"}))))
  (testing "reads the real environment without throwing"
    (is (map? (pg/getenv->map)))))

(deftest env-spec-test
  (is (= {:dbname "d" :host "h" :port "5432" :user "u"}
         (pg/env-spec {:PGDATABASE "d" :PGHOST "h" :PGPORT "5432" :PGUSER "u"})))
  (testing "only sets keys that are present"
    (is (= {:dbname "d"} (pg/env-spec {:PGDATABASE "d"})))
    (is (= {} (pg/env-spec {})))))

(deftest spec-password-precedence
  (testing "explicit :password beats PGPASSWORD and ~/.pgpass"
    (with-redefs [pg/getenv->map (fn [& _] {:PGDATABASE "d" :PGUSER "u" :PGPASSWORD "envpw"})
                  pgpass/pgpass-lookup (fn [_] "filepw")]
      (is (= "explicit" (:password (pg/spec :password "explicit"))))))
  (testing "PGPASSWORD beats ~/.pgpass"
    (with-redefs [pg/getenv->map (fn [& _] {:PGDATABASE "d" :PGUSER "u" :PGPASSWORD "envpw"})
                  pgpass/pgpass-lookup (fn [_] "filepw")]
      (is (= "envpw" (:password (pg/spec))))))
  (testing "falls back to ~/.pgpass when no explicit/PGPASSWORD"
    (with-redefs [pg/getenv->map (fn [& _] {:PGDATABASE "d" :PGUSER "u"})
                  pgpass/pgpass-lookup (fn [_] "filepw")]
      (is (= "filepw" (:password (pg/spec)))))))

(deftest default-spec-test
  (let [s (pg/default-spec)]
    (is (= "postgresql" (:dbtype s)))
    (is (contains? s :user))
    (is (contains? s :dbname))))

(deftest type-constructors
  (is (instance? PGobject (pg/object "json" "{}")))
  (is (= "xml" (.getType ^PGobject (pg/object :xml "<a/>"))))
  (is (instance? PGInterval (pg/interval :hours 2)))
  (is (instance? PGmoney (pg/money 1.5))))

(deftest geometric-constructors
  (is (instance? PGpoint (pg/point 1 2)))
  (is (instance? PGpoint (pg/point [1 2])))
  (is (instance? PGbox (pg/box [0 0] [1 1])))
  (is (instance? PGcircle (pg/circle 0 0 5)))
  (is (instance? PGline (pg/line 0 0 1 1)))
  (is (instance? PGlseg (pg/lseg 0 0 1 1)))
  (is (instance? PGpath (pg/path [[0 0] [1 1]] true)))
  (is (instance? PGpolygon (pg/polygon [[0 0] [1 0] [1 1]]))))
