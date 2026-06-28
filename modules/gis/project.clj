(defproject net.clojars.savya/psql-clj-gis "2.0.0"
  :description "PostGIS geometry and geography support for psql-clj: spatial constructors, GeoJSON coercion, and next.jdbc column/parameter extensions."
  :url "https://github.com/jsavyasachi/psql-clj"
  :license {:name "BSD-2-Clause"
            :url "https://opensource.org/license/bsd-2-clause"}
  :scm {:name "git" :url "https://github.com/jsavyasachi/psql-clj"}
  :dependencies [[net.clojars.savya/psql-clj "2.0.0"]
                 [net.postgis/postgis-jdbc "2025.1.1" :exclusions [postgresql org.postgresql/postgresql]]
                 [prismatic/schema "1.4.1"]]
  :global-vars {*warn-on-reflection* true}
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :all (constantly true)}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.12.0"]]}
             :dev {:dependencies [[org.clojure/clojure "1.12.0"]]}
             :clojure-1-10 {:dependencies [[org.clojure/clojure "1.10.3"]]}
             :clojure-1-11 {:dependencies [[org.clojure/clojure "1.11.4"]]}
             :clojure-1-12 {:dependencies [[org.clojure/clojure "1.12.0"]]}}
  :aliases {"all" ["with-profile" "+clojure-1-10:+clojure-1-11:+clojure-1-12"]}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]])
