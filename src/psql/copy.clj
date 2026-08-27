(ns psql.copy
  "PostgreSQL COPY helpers that use pgjdbc's CopyManager.

  COPY runs on a java.sql.Connection. The caller owns the connection and must keep it open for the COPY operation."
  (:require [clojure.string :as str])
  (:import [java.io InputStream OutputStream Reader Writer]
           [java.sql Connection]
           [org.postgresql PGConnection]
           [org.postgresql.copy CopyManager CopyIn CopyOut CopyDual CopyOperation]
           [org.postgresql.util ByteStreamWriter]))

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
  "Run COPY SQL FROM STDIN with a Reader or InputStream. Return the row count.

  The stream may contain text, CSV, or PostgreSQL binary COPY data. With a
  CHUNK-SIZE (bytes/chars per network buffer) the stream is sent in fixed-size
  chunks for backpressure control. The caller owns both the stream and the open
  connection lifecycle."
  ([^Connection conn sql source]
   (valid-copy-sql! sql)
   (when-not (or (instance? Reader source) (instance? InputStream source))
     (throw (IllegalArgumentException.
             "COPY input must be a java.io.Reader or java.io.InputStream")))
   (let [^CopyManager manager (copy-manager conn)
         ^String sql sql]
     (if (instance? Reader source)
       (.copyIn manager sql ^Reader source)
       (.copyIn manager sql ^InputStream source))))
  ([^Connection conn sql source chunk-size]
   (valid-copy-sql! sql)
   (when-not (or (instance? Reader source) (instance? InputStream source))
     (throw (IllegalArgumentException.
             "COPY input must be a java.io.Reader or java.io.InputStream")))
   (let [^CopyManager manager (copy-manager conn)
         ^String sql sql
         chunk (int chunk-size)]
     (if (instance? Reader source)
       (.copyIn manager sql ^Reader source chunk)
       (.copyIn manager sql ^InputStream source chunk)))))

(defn copy-out
  "Run COPY SQL TO STDOUT with a Writer or OutputStream. Return the row count.

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

;;
;; Lower-level COPY lifecycle
;;
;; The stream-based `copy-in`/`copy-out` cover the common cases. These wrap
;; pgjdbc's CopyIn/CopyOut/CopyDual for callers that need to drive the protocol
;; directly: streaming byte chunks with their own backpressure, or bidirectional
;; COPY (as used by logical replication).

(defn start-copy-in
  "Begin `COPY SQL FROM STDIN` and return a pgjdbc `CopyIn` handle. Feed it with
  `write-copy!` and finish with `end-copy!` (or abort with `cancel-copy!`)."
  ^CopyIn [^Connection conn sql]
  (valid-copy-sql! sql)
  (.copyIn (copy-manager conn) ^String sql))

(defn write-copy!
  "Write COPY data to an in-progress `CopyIn`. DATA is a byte array (optionally a
  sub-range via OFFSET and LENGTH) or a pgjdbc `ByteStreamWriter`."
  ([^CopyIn copy-in data]
   (if (instance? ByteStreamWriter data)
     (.writeToCopy copy-in ^ByteStreamWriter data)
     (let [^bytes bs data] (.writeToCopy copy-in bs 0 (alength bs))))
   nil)
  ([^CopyIn copy-in ^bytes data offset length]
   (.writeToCopy copy-in data (int offset) (int length))
   nil))

(defn flush-copy!
  "Flush buffered COPY data on an in-progress `CopyIn`."
  [^CopyIn copy-in]
  (.flushCopy copy-in)
  nil)

(defn end-copy!
  "Finish an in-progress `CopyIn` (or `CopyDual`) and return the server's row
  count."
  [^CopyIn copy-in]
  (.endCopy copy-in))

(defn start-copy-out
  "Begin `COPY SQL TO STDOUT` and return a pgjdbc `CopyOut` handle. Pull data
  with `read-copy!` until it returns nil."
  ^CopyOut [^Connection conn sql]
  (valid-copy-sql! sql)
  (.copyOut (copy-manager conn) ^String sql))

(defn read-copy!
  "Read the next COPY data row from a `CopyOut` (or `CopyDual`) as a byte array,
  or nil when the stream is exhausted."
  ^bytes [^CopyOut copy-out]
  (.readFromCopy copy-out))

(defn copy-dual
  "Begin a bidirectional `COPY` and return a pgjdbc `CopyDual` handle. Used by
  the streaming replication protocol; write with `write-copy!` and read with
  `read-copy!`."
  ^CopyDual [^Connection conn sql]
  (valid-copy-sql! sql)
  (.copyDual (copy-manager conn) ^String sql))

(defn cancel-copy!
  "Abort an in-progress COPY operation (CopyIn, CopyOut, or CopyDual)."
  [^CopyOperation copy-op]
  (.cancelCopy copy-op)
  nil)
