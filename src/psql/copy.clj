(ns psql.copy
  "Streaming PostgreSQL COPY helpers backed directly by pgjdbc's CopyManager.

  COPY runs on a real java.sql.Connection. The caller owns that connection and
  must keep it open for the complete COPY operation."
  (:require [clojure.string :as str])
  (:import [java.io InputStream OutputStream Reader Writer]
           [java.sql Connection]
           [org.postgresql PGConnection]
           [org.postgresql.copy CopyManager]))

(defn copy-manager
  "Return pgjdbc's CopyManager for an open PostgreSQL connection.

  The caller owns the connection lifecycle; this function never closes it."
  ^CopyManager [^Connection conn]
  (let [pg (.unwrap conn PGConnection)]
    (.getCopyAPI ^PGConnection pg)))

(defn- valid-copy-sql! [sql]
  (when-not (and (string? sql) (not (str/blank? sql)))
    (throw (IllegalArgumentException. "COPY SQL must be a non-empty string")))
  sql)

(defn copy-in
  "Run COPY SQL FROM STDIN using a Reader or InputStream and return the row count.

  The stream may contain text, CSV, or PostgreSQL binary COPY data. The caller
  owns both the stream and the open connection lifecycle."
  [^Connection conn sql source]
  (valid-copy-sql! sql)
  (when-not (or (instance? Reader source) (instance? InputStream source))
    (throw (IllegalArgumentException.
            "COPY input must be a java.io.Reader or java.io.InputStream")))
  (let [^CopyManager manager (copy-manager conn)
        ^String sql sql]
    (if (instance? Reader source)
      (.copyIn manager sql ^Reader source)
      (.copyIn manager sql ^InputStream source))))

(defn copy-out
  "Run COPY SQL TO STDOUT into a Writer or OutputStream and return the row count.

  Use an OutputStream for PostgreSQL binary COPY data. The caller owns both the
  stream and the open connection lifecycle."
  [^Connection conn sql destination]
  (valid-copy-sql! sql)
  (when-not (or (instance? Writer destination) (instance? OutputStream destination))
    (throw (IllegalArgumentException.
            "COPY output must be a java.io.Writer or java.io.OutputStream")))
  (let [^CopyManager manager (copy-manager conn)
        ^String sql sql]
    (if (instance? Writer destination)
      (.copyOut manager sql ^Writer destination)
      (.copyOut manager sql ^OutputStream destination))))
