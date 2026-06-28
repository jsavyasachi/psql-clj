(defproject net.clojars.savya/psql-clj "2.0.1"
  :description "PostgreSQL helpers for Clojure: environment- and .pgpass-aware connection specs, HikariCP pooling, and next.jdbc type coercion for JSON/JSONB, arrays, inet and enums. PostGIS support lives in the psql-clj-gis companion."
  :url "https://github.com/jsavyasachi/psql-clj"
  :license {:name "BSD-2-Clause"
            :url "https://opensource.org/license/bsd-2-clause"}
  :scm {:name "git" :url "https://github.com/jsavyasachi/psql-clj"}
  :dependencies [[com.github.seancorfield/next.jdbc "1.3.1118"]
                 [org.postgresql/postgresql "42.7.11"]
                 [hikari-cp "4.1.0"]
                 [cheshire "6.2.0"]]
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
