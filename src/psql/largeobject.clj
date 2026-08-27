(ns psql.largeobject
  "PostgreSQL large-object (`lo`) helpers over pgjdbc's `LargeObjectManager`.

  Large objects are stored in `pg_largeobject` and addressed by an OID. Every
  operation must run inside a transaction (set autocommit off, e.g. with
  `next.jdbc/with-transaction`); pgjdbc raises otherwise. Each function accepts
  the `java.sql.Connection` for manager operations, or an open `LargeObject` for
  read/write/seek. The caller owns the connection and transaction lifecycle."
  (:import [java.sql Connection]
           [org.postgresql PGConnection]
           [org.postgresql.largeobject LargeObject LargeObjectManager]))

(def ^:private modes
  {:read LargeObjectManager/READ
   :write LargeObjectManager/WRITE
   :read-write LargeObjectManager/READWRITE})

(def ^:private whences
  {:set LargeObject/SEEK_SET
   :cur LargeObject/SEEK_CUR
   :end LargeObject/SEEK_END})

(defn manager
  "Return pgjdbc's `LargeObjectManager` for an open connection."
  ^LargeObjectManager [^Connection conn]
  (.getLargeObjectAPI ^PGConnection (.unwrap conn PGConnection)))

(defn create!
  "Create a new, empty large object and return its OID (a long)."
  [^Connection conn]
  (.createLO (manager conn)))

(defn unlink!
  "Delete the large object with OID."
  [^Connection conn oid]
  (.unlink (manager conn) (long oid))
  nil)

(defn open
  "Open the large object OID and return a `LargeObject`. MODE is `:read`,
  `:write`, or `:read-write` (default `:read-write`). Close it with `close!`."
  (^LargeObject [^Connection conn oid] (open conn oid :read-write))
  (^LargeObject [^Connection conn oid mode]
   (if-let [m (get modes mode)]
     (.open (manager conn) (long oid) (int m))
     (throw (ex-info (str "Unknown large-object mode " mode)
                     {:psql/error :invalid-lo-mode :mode mode})))))

(defn read-bytes
  "Read up to N bytes from LO at the current position, as a byte array."
  ^bytes [^LargeObject lo n]
  (.read lo (int n)))

(defn write-bytes!
  "Write a byte array to LO at the current position (optionally a sub-range)."
  ([^LargeObject lo ^bytes data]
   (.write lo data)
   nil)
  ([^LargeObject lo ^bytes data offset length]
   (.write lo data (int offset) (int length))
   nil))

(defn seek!
  "Move LO's read/write position to POS bytes, relative to WHENCE (`:set`,
  `:cur`, or `:end`; default `:set`)."
  ([^LargeObject lo pos] (seek! lo pos :set))
  ([^LargeObject lo pos whence]
   (if-let [w (get whences whence)]
     (.seek64 lo (long pos) (int w))
     (throw (ex-info (str "Unknown seek whence " whence)
                     {:psql/error :invalid-seek-whence :whence whence})))
   nil))

(defn tell
  "Return LO's current byte position."
  [^LargeObject lo]
  (.tell64 lo))

(defn lo-size
  "Return the size of LO in bytes."
  [^LargeObject lo]
  (.size64 lo))

(defn truncate!
  "Truncate LO to LENGTH bytes."
  [^LargeObject lo length]
  (.truncate64 lo (long length))
  nil)

(defn close!
  "Close an open LargeObject."
  [^LargeObject lo]
  (.close lo)
  nil)

(defn input-stream
  "Return an `InputStream` reading LO from its current position."
  ^java.io.InputStream [^LargeObject lo]
  (.getInputStream lo))

(defn output-stream
  "Return an `OutputStream` writing to LO at its current position."
  ^java.io.OutputStream [^LargeObject lo]
  (.getOutputStream lo))

(defn store!
  "Create a large object, write DATA (a byte array) into it, and return its OID.
  Runs against CONN's current transaction."
  [^Connection conn ^bytes data]
  (let [oid (create! conn)
        lo (open conn oid :write)]
    (try
      (.write lo data)
      oid
      (finally (.close lo)))))

(defn fetch
  "Read the entire contents of the large object OID as a byte array."
  ^bytes [^Connection conn oid]
  (let [lo (open conn oid :read)]
    (try
      (.read lo (int (.size64 lo)))
      (finally (.close lo)))))
