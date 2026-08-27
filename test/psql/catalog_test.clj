(ns psql.catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [psql.catalog :as cat]
            [psql.core :as pg]
            [next.jdbc :as jdbc]))

(defn- fixture-db []
  (let [db (pg/spec)]
    (jdbc/execute! db ["DROP TABLE IF EXISTS cat_child, cat_parent CASCADE"])
    (jdbc/execute! db ["DROP VIEW IF EXISTS cat_view CASCADE"])
    (jdbc/execute! db ["CREATE TABLE cat_parent (id int PRIMARY KEY, name text NOT NULL)"])
    (jdbc/execute! db ["CREATE TABLE cat_child (id int PRIMARY KEY,
                        parent_id int REFERENCES cat_parent(id), tag text)"])
    (jdbc/execute! db ["CREATE INDEX cat_child_tag_idx ON cat_child (tag)"])
    (jdbc/execute! db ["CREATE VIEW cat_view AS SELECT id FROM cat_parent"])
    db))

(deftest ^:integration schemas-includes-public
  (is (contains? (cat/schemas (pg/spec)) :public)))

(deftest ^:integration views-lists-created-view
  (fixture-db)
  (is (contains? (cat/views (pg/spec)) :cat_view)))

(deftest ^:integration columns-normalized
  (let [db (fixture-db)
        cols (cat/columns db "cat_parent")
        by-name (into {} (map (juxt :name identity)) cols)]
    (is (= [:id :name] (mapv :name (sort-by :position cols))))
    (is (false? (:nullable? (by-name :id))))
    (is (false? (:nullable? (by-name :name))))
    (is (= "int4" (:type (by-name :id))))))

(deftest ^:integration primary-keys-in-order
  (let [db (fixture-db)]
    (is (= [:id] (cat/primary-keys db "cat_parent")))))

(deftest ^:integration foreign-keys-resolved
  (let [db (fixture-db)
        fks (cat/foreign-keys db "cat_child")]
    (is (= 1 (count fks)))
    (is (= :parent_id (:column (first fks))))
    (is (= {:table :cat_parent :column :id} (:references (first fks))))))

(deftest ^:integration indexes-grouped-by-name
  (let [db (fixture-db)
        idx (cat/indexes db "cat_child")
        by-name (into {} (map (juxt :name identity)) idx)]
    (testing "the explicit non-unique index is reported with its column"
      (is (= {:name :cat_child_tag_idx :unique? false :columns [:tag]}
             (by-name :cat_child_tag_idx))))
    (testing "the primary-key index is unique"
      (is (some :unique? idx)))))
