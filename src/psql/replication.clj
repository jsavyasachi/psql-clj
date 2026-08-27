(ns psql.replication
  "PostgreSQL streaming replication over pgjdbc: log sequence numbers (LSNs),
  replication-slot management, and logical decoding streams.

  A replication connection is a special connection opened with the `replication`
  property set; `replication-spec` adds the required properties to a db spec.
  The server must run with `wal_level = logical` (and enough `max_wal_senders` /
  `max_replication_slots`) for logical replication."
  (:import [java.sql Connection]
           [org.postgresql PGConnection]
           [org.postgresql.replication LogSequenceNumber PGReplicationConnection
            PGReplicationStream ReplicationSlotInfo]))

;; ---- connection -----------------------------------------------------------

(defn replication-spec
  "Return SPEC with the properties pgjdbc needs to open a replication
  connection: `:replication \"database\"`, simple query mode, and a minimum
  server version. Pass the result to `next.jdbc/get-connection`."
  [spec]
  (merge {:preferQueryMode "simple"
          :assumeMinServerVersion "9.4"}
         spec
         {:replication "database"}))

(defn replication-api
  "Return pgjdbc's `PGReplicationConnection` for a connection opened from a
  `replication-spec`."
  ^PGReplicationConnection [^Connection conn]
  (.getReplicationAPI ^PGConnection (.unwrap conn PGConnection)))

;; ---- LSN ------------------------------------------------------------------

(defn lsn
  "Coerce X to a pgjdbc `LogSequenceNumber`. X is an LSN string like
  \"16/B374D848\", a long, or an existing `LogSequenceNumber`."
  ^LogSequenceNumber [x]
  (cond
    (instance? LogSequenceNumber x) x
    (string? x) (LogSequenceNumber/valueOf ^String x)
    (integer? x) (LogSequenceNumber/valueOf (long x))
    :else (throw (ex-info (str "Cannot coerce to an LSN: " (pr-str x))
                          {:psql/error :invalid-lsn :value x}))))

(defn lsn->string
  "Return the canonical `X/Y` string form of an LSN."
  [^LogSequenceNumber l]
  (.asString l))

(defn lsn->long
  "Return the 64-bit long value of an LSN."
  [^LogSequenceNumber l]
  (.asLong l))

;; ---- replication slots ----------------------------------------------------

(defn- slot-info->map [^ReplicationSlotInfo info]
  {:slot-name (.getSlotName info)
   :replication-type (-> info .getReplicationType .name (.toLowerCase) keyword)
   :consistent-point (some-> (.getConsistentPoint info) .asString)
   :snapshot-name (.getSnapshotName info)
   :output-plugin (.getOutputPlugin info)})

(defn create-logical-slot!
  "Create a logical replication SLOT-NAME using output PLUGIN (for example
  \"pgoutput\" or \"test_decoding\"). Returns a slot-info map with
  `:slot-name`, `:replication-type`, `:consistent-point`, `:snapshot-name`, and
  `:output-plugin`."
  [^Connection conn slot-name plugin]
  (-> (replication-api conn)
      (.createReplicationSlot)
      (.logical)
      (.withSlotName (str slot-name))
      (.withOutputPlugin (str plugin))
      (.make)
      (slot-info->map)))

(defn create-physical-slot!
  "Create a physical replication SLOT-NAME. Returns a slot-info map."
  [^Connection conn slot-name]
  (-> (replication-api conn)
      (.createReplicationSlot)
      (.physical)
      (.withSlotName (str slot-name))
      (.make)
      (slot-info->map)))

(defn drop-slot!
  "Drop the replication slot SLOT-NAME."
  [^Connection conn slot-name]
  (.dropReplicationSlot (replication-api conn) (str slot-name))
  nil)

;; ---- logical decoding stream ----------------------------------------------

(defn start-logical-replication
  "Start a logical decoding stream on an existing logical slot and return a
  pgjdbc `PGReplicationStream`. OPTS keys:

  - `:slot-name`        (required) the logical slot to stream from
  - `:start-lsn`        start position (LSN string/long/LogSequenceNumber)
  - `:status-interval-ms` standby status update interval
  - `:slot-options`     a map of output-plugin option name to string value

  Consume it with `read-pending`/`read`, acknowledge progress with
  `set-flushed-lsn!`/`set-applied-lsn!`, and `close-stream!` when done."
  ^PGReplicationStream
  [^Connection conn {:keys [slot-name start-lsn status-interval-ms slot-options]}]
  (when-not slot-name
    (throw (ex-info "start-logical-replication requires :slot-name"
                    {:psql/error :missing-slot-name})))
  (let [b (-> (replication-api conn)
              (.replicationStream)
              (.logical)
              (.withSlotName (str slot-name)))
        b (cond-> b
            start-lsn (.withStartPosition (lsn start-lsn))
            status-interval-ms (.withStatusInterval (int status-interval-ms)
                                                    java.util.concurrent.TimeUnit/MILLISECONDS))
        b (reduce-kv (fn [^org.postgresql.replication.fluent.logical.ChainedLogicalStreamBuilder acc k v]
                       (.withSlotOption acc (str k) (str v)))
                     b
                     (or slot-options {}))]
    (.start ^org.postgresql.replication.fluent.logical.ChainedLogicalStreamBuilder b)))

(defn read-pending
  "Read the next available change from STREAM as a `java.nio.ByteBuffer`, or nil
  if none is pending right now (non-blocking)."
  ^java.nio.ByteBuffer [^PGReplicationStream stream]
  (.readPending stream))

(defn read-change
  "Read the next change from STREAM as a `java.nio.ByteBuffer`, blocking until
  one is available."
  ^java.nio.ByteBuffer [^PGReplicationStream stream]
  (.read stream))

(defn last-received-lsn
  "The last LSN received on STREAM."
  ^LogSequenceNumber [^PGReplicationStream stream]
  (.getLastReceiveLSN stream))

(defn set-flushed-lsn!
  "Tell the server STREAM has durably flushed up to L (an LSN)."
  [^PGReplicationStream stream l]
  (.setFlushedLSN stream (lsn l))
  nil)

(defn set-applied-lsn!
  "Tell the server STREAM has applied up to L (an LSN)."
  [^PGReplicationStream stream l]
  (.setAppliedLSN stream (lsn l))
  nil)

(defn force-status-update!
  "Send a standby status update to the server immediately."
  [^PGReplicationStream stream]
  (.forceUpdateStatus stream)
  nil)

(defn close-stream!
  "Close a replication STREAM."
  [^PGReplicationStream stream]
  (.close stream)
  nil)
