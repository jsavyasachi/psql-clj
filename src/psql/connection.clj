(ns psql.connection
  "Operational controls exposed by pgjdbc's `PGConnection` and the JDBC
  connection: the backend process id, query cancellation, server parameter
  statuses, the default fetch size, the transaction autosave mode, and a helper
  to set the per-session statement timeout.

  Each function accepts a `java.sql.Connection` (the value `next.jdbc` hands you
  inside `with-open`/`on-connection`) and unwraps the pgjdbc `PGConnection` as
  needed."
  (:import [java.sql Connection Statement]
           [org.postgresql PGConnection]
           [org.postgresql.jdbc AutoSave]))

(defn- pg-connection
  ^PGConnection [^Connection conn]
  (.unwrap conn PGConnection))

(defn backend-pid
  "Return the server-side backend process id for CONN. Useful for correlating
  with `pg_stat_activity` and for out-of-band cancellation."
  [^Connection conn]
  (.getBackendPID (pg-connection conn)))

(defn cancel-query!
  "Cancel the statement currently executing on CONN, if any. Safe to call from
  another thread than the one running the query."
  [^Connection conn]
  (.cancelQuery (pg-connection conn))
  nil)

(defn parameter-status
  "Return the server's reported value for parameter NAME (for example
  \"server_version\" or \"TimeZone\"), or nil if it is unknown."
  [^Connection conn ^String name]
  (.getParameterStatus (pg-connection conn) name))

(defn parameter-statuses
  "Return all server parameter statuses reported for CONN as a Clojure map of
  string name to string value."
  [^Connection conn]
  (into {} (.getParameterStatuses (pg-connection conn))))

(defn default-fetch-size
  "Return the default fetch size applied to statements created from CONN."
  [^Connection conn]
  (.getDefaultFetchSize (pg-connection conn)))

(defn set-default-fetch-size!
  "Set the default fetch size for statements created from CONN. A positive value
  makes result sets stream in batches instead of materializing fully."
  [^Connection conn n]
  (.setDefaultFetchSize (pg-connection conn) (int n))
  nil)

(def ^:private autosave->kw
  {AutoSave/NEVER :never
   AutoSave/ALWAYS :always
   AutoSave/CONSERVATIVE :conservative})

(def ^:private kw->autosave
  {:never AutoSave/NEVER
   :always AutoSave/ALWAYS
   :conservative AutoSave/CONSERVATIVE})

(defn autosave
  "Return the transaction autosave mode of CONN as a keyword:
  `:never`, `:always`, or `:conservative`."
  [^Connection conn]
  (get autosave->kw (.getAutosave (pg-connection conn))))

(defn set-autosave!
  "Set the transaction autosave mode of CONN. MODE is `:never`, `:always`, or
  `:conservative` (pgjdbc places a savepoint before each statement so a failed
  statement need not poison the whole transaction)."
  [^Connection conn mode]
  (if-let [value (get kw->autosave mode)]
    (.setAutosave (pg-connection conn) value)
    (throw (ex-info (str "Unknown autosave mode " mode)
                    {:psql/error :invalid-autosave :mode mode})))
  nil)

(defn escape-identifier
  "Quote S as a PostgreSQL identifier using the server's own rules, for safe
  interpolation of dynamic table/column names."
  ^String [^Connection conn ^String s]
  (.escapeIdentifier (pg-connection conn) s))

(defn statement-timeout!
  "Set `statement_timeout` for the current session on CONN to MS milliseconds
  (0 disables it). Later statements that run longer are cancelled by the server."
  [^Connection conn ms]
  (with-open [^Statement st (.createStatement conn)]
    (.execute st (str "SET statement_timeout = " (long ms))))
  nil)
