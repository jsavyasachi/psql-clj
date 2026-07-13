(ns build
  "Build + Clojars deploy for jose-clj (tools.build + deps-deploy).

   Usage:
     clojure -T:build jar
     clojure -T:build deploy   ; needs CLOJARS_USERNAME / CLOJARS_PASSWORD"
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'net.clojars.savya/psql-clj)
(def version "2.0.2")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "pom.xml"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/jsavyasachi/psql-clj"
                      :connection "scm:git:https://github.com/jsavyasachi/psql-clj.git"
                      :developerConnection "scm:git:ssh://git@github.com/jsavyasachi/psql-clj.git"
                      :tag (str "v" version)}
                :pom-data [[:description "PostgreSQL helpers for Clojure: environment- and .pgpass-aware connection specs, HikariCP pooling, and next.jdbc type coercion for JSON/JSONB, arrays, inet and enums. PostGIS support lives in the psql-clj-gis companion."]
                           [:url "https://github.com/jsavyasachi/psql-clj"]
                           [:licenses
                            [:license
                             [:name "BSD-2-Clause"]
                             [:url "https://opensource.org/license/bsd-2-clause"]
                             [:distribution "repo"]]]]})
  (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
  (b/jar {:class-dir class-dir :jar-file jar-file})
  (println "Wrote" jar-file))

(defn install
  "Install core to the local ~/.m2 so the companion modules (which declare a
   :mvn/version dep on core) can resolve THIS version without waiting on Clojars."
  [_]
  (jar nil)
  (dd/deploy {:installer :local
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))

(defn deploy [_]
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
