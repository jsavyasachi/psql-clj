(ns psql.service-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [psql.service :as service]))

(deftest resolve-service-test
  (let [service-file (java.io.File/createTempFile "psql-clj-service-" ".conf")]
    (try
      (spit service-file
            (str "[analytics]\n"
                 "host=service-host\n"
                 "port=6543\n"
                 "dbname=service-db\n"
                 "user=service-user\n"
                 "password=service-password\n"
                 "sslmode=verify-full\n"
                 "ApplicationName=service-app\n"))
      (is (= {:host "service-host"
              :port "6543"
              :dbname "service-db"
              :user "service-user"
              :password "service-password"
              :sslmode "verify-full"
              :ApplicationName "service-app"}
             (service/resolve-service
              "analytics"
              {:PGSERVICEFILE (.getAbsolutePath service-file)})))
      (testing "an unknown service resolves to an empty spec"
        (is (= {}
               (service/resolve-service
                "missing"
                {:PGSERVICEFILE (.getAbsolutePath service-file)}))))
      (finally
        (.delete service-file)))))

(deftest resolve-service-from-system-config-directory-test
  (let [directory (doto (java.io.File/createTempFile "psql-clj-service-dir-" "")
                    (.delete)
                    (.mkdir))
        service-file (io/file directory "pg_service.conf")]
    (try
      (spit service-file "[system]\nhost=system-host\ndbname=system-db\nuser=system-user\n")
      (is (= {:host "system-host" :dbname "system-db" :user "system-user"}
             (service/resolve-service
              "system"
              {:PGSYSCONFDIR (.getAbsolutePath directory)})))
      (finally
        (.delete service-file)
        (.delete directory)))))

(deftest resolve-service-without-explicit-file-serializes-parser
  (let [lock (var-get #'service/service-parser-lock)
        lock-held (promise)
        release (promise)
        parser-called (promise)
        holder (future
                 (locking lock
                   (deliver lock-held true)
                   @release))
        parser (future
                 ((var-get #'service/with-service-file)
                  {}
                  #(deliver parser-called true)))]
    (try
      @lock-held
      (is (= ::blocked (deref parser-called 100 ::blocked)))
      (deliver release true)
      (is (= true (deref parser-called 1000 ::timeout)))
      (finally
        (deliver release true)
        @holder
        @parser))))
