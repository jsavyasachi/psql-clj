(ns psql.pgpass
  "Logic for matching passwords ~/.pgpass passwords to db specs."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io IOException]
           [java.nio.file Files LinkOption]
           [java.nio.file.attribute PosixFilePermission]))

(def ^:private insecure-posix-permissions
  #{PosixFilePermission/GROUP_READ
    PosixFilePermission/GROUP_WRITE
    PosixFilePermission/GROUP_EXECUTE
    PosixFilePermission/OTHERS_READ
    PosixFilePermission/OTHERS_WRITE
    PosixFilePermission/OTHERS_EXECUTE})

(defn parse-pgpass-line
  "The .pgpass file has lines of format: hostname:port:database:username:password.
  Return a map of fields {:pg-hostname \"*\" ...}, or nil for an ignored line."
  [s]
  (when-not (or (str/blank? s) (re-find #"^\s*#" s))
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

(defn default-pgpass-file
  "Resolve PGPASSFILE or the platform's default password file."
  ([]
   (default-pgpass-file (System/getenv)
                        (System/getProperty "os.name")
                        (System/getProperty "user.home")))
  ([env os-name user-home]
   (if-let [passfile (not-empty (get env "PGPASSFILE"))]
     (io/file passfile)
     (if (str/starts-with? (str/lower-case os-name) "windows")
       (when-let [appdata (not-empty (get env "APPDATA"))]
         (io/file appdata "postgresql" "pgpass.conf"))
       (io/file user-home ".pgpass")))))

(defn- secure-passfile?
  [^java.io.File passfile]
  (try
    (let [permissions (Files/getPosixFilePermissions
                       (.toPath passfile)
                       (make-array LinkOption 0))]
      (not-any? insecure-posix-permissions permissions))
    (catch UnsupportedOperationException _
      true)
    (catch IOException _
      false)
    (catch SecurityException _
      false)))

(defn read-pgpass
  "Read the resolved or explicit password file and parse valid records into maps."
  ([]
   (read-pgpass (default-pgpass-file)))
  ([passfile]
   (when passfile
     (let [^java.io.File passfile (io/file passfile)]
       (when (and (.isFile passfile) (secure-passfile? passfile))
         (with-open [r (io/reader passfile)]
           (->> r
                line-seq
                (keep parse-pgpass-line)
                doall)))))))

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
  "Look up a password based on db spec {:host ... :port ... :dbname ... :user ...}."
  ([spec]
   (pgpass-lookup spec (default-pgpass-file)))
  ([spec passfile]
   (when-let [match (first (filter (partial pgpass-matches? spec)
                                  (read-pgpass passfile)))]
     (:pg-password match))))
