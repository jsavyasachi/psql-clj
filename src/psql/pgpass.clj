(ns psql.pgpass
  "Logic for matching passwords ~/.pgpass passwords to db specs."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn parse-pgpass-line
  "The .pgpass file has lines of format: hostname:port:database:username:password.
  Return a map of fields {:pg-hostname \"*\" ...}, or nil for an ignored line."
  [s]
  (when-not (or (str/blank? s) (re-find #"^\\s*#" s))
    (let [fields (loop [remaining (seq s)
                        field (StringBuilder.)
                        result []]
                   (if-let [c (first remaining)]
                     (let [next-c (second remaining)]
                       (cond
                         (and (= c \\) (#{\: \\} next-c))
                         (recur (nnext remaining) (.append field next-c) result)

                         (= c \:)
                         (recur (next remaining) (StringBuilder.)
                                (conj result (str field)))

                         :else
                         (recur (next remaining) (.append field c) result)))
                     (conj result (str field))))]
      (when (= 5 (count fields))
        (zipmap
         [:pg-hostname :pg-port :pg-database :pg-username :pg-password]
         fields)))))

(defn read-pgpass
  "Find ~/.pgpass, read it and parse lines into maps"
  []
  (let [homedir (io/file (System/getProperty "user.home"))
        passfile (io/file homedir ".pgpass")]
    (when (.isFile passfile)
      (with-open [r (io/reader passfile)]
        (->> r
             line-seq
             (map parse-pgpass-line)
             doall)))))

(defn pgpass-matches?
  "(filter (partial pgpass-matches? spec) pgpass-lines)"
  [{:keys [host port dbname user]} {:keys [pg-hostname pg-port pg-database pg-username pg-password]}]
  (let [local-host? (or (nil? host)
                        (= "" host)
                        (and (string? host) (.isAbsolute (io/file host))))
        port (str (or port 5432))]
    (when
     (and
      (or (= pg-hostname "*")
          (= pg-hostname host)
          (and (= pg-hostname "localhost") local-host?))
      (or (= pg-port "*") (= (str pg-port) port))
      (or (= pg-database "*") (= pg-database dbname))
      (or (= pg-username "*") (= pg-username user)))
      pg-password)))

(defn pgpass-lookup
  "Look up password from ~/.pgpass based on db spec {:host ... :port ... :dbname ... :user ...}"
  [spec]
  (when-let [match (first (filter (partial pgpass-matches? spec) (read-pgpass)))]
    (:pg-password match)))
