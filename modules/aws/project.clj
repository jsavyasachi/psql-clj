(defproject net.clojars.savya/psql-clj-aws "2.0.0"
  :description "AWS RDS/Aurora IAM authentication for psql-clj: build a db-spec whose password is a short-lived RDS IAM auth token."
  :url "https://github.com/jsavyasachi/psql-clj"
  :license {:name "BSD-2-Clause"
            :url "https://opensource.org/license/bsd-2-clause"}
  :scm {:name "git" :url "https://github.com/jsavyasachi/psql-clj"}
  :dependencies [[net.clojars.savya/psql-clj "2.0.0"]
                 [software.amazon.awssdk/rds "2.46.17"]]
  :global-vars {*warn-on-reflection* true}
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
