(ns psql.notify
  "PostgreSQL LISTEN/NOTIFY helpers backed directly by pgjdbc.

  Use a dedicated open connection for listening. pgjdbc surfaces notifications
  only when a query is issued or get-notifications is called. A blocking poll
  prevents other statements from using that connection until the poll returns."
  (:import [java.sql Connection Statement]
           [org.postgresql PGConnection PGNotification]))

(def ^:private channel-pattern #"[A-Za-z_][A-Za-z0-9_]*")

(defn quote-channel
  "Validate and quote a channel name as a PostgreSQL identifier.

  Channel names are restricted to portable ASCII identifier characters to make
  SQL interpolation secure by default."
  ^String [channel]
  (when-not (and (string? channel)
                 (<= (count channel) 63)
                 (re-matches channel-pattern channel))
    (throw (IllegalArgumentException.
            "channel must match [A-Za-z_][A-Za-z0-9_]* and be at most 63 characters")))
  (str "\"" channel "\""))

(defn- pg-connection
  ^PGConnection [^Connection conn]
  (.unwrap conn PGConnection))

(defn- execute-command!
  [^Connection conn ^String sql]
  (with-open [^Statement statement (.createStatement conn)]
    (.executeUpdate statement sql)))

(defn listen!
  "LISTEN on channel using a dedicated open connection.

  The caller owns the connection lifecycle."
  [^Connection conn channel]
  (execute-command! conn (str "LISTEN " (quote-channel channel))))

(defn unlisten!
  "UNLISTEN from channel on an open connection.

  The caller owns the connection lifecycle."
  [^Connection conn channel]
  (execute-command! conn (str "UNLISTEN " (quote-channel channel))))

(defn notify!
  "Send payload on channel using an open connection.

  The channel is validated as an identifier and the payload is escaped by
  pgjdbc. Payloads are never logged."
  [^Connection conn channel payload]
  (let [quoted-channel (quote-channel channel)]
    (when-not (string? payload)
      (throw (IllegalArgumentException. "NOTIFY payload must be a string")))
    (let [^PGConnection pg (pg-connection conn)
          escaped-payload (.escapeLiteral pg ^String payload)]
      (execute-command! conn
                        (str "NOTIFY " quoted-channel ", '" escaped-payload "'")))))

(defn notification->map
  "Convert a pgjdbc notification to a Clojure map."
  [^PGNotification notification]
  {:name (.getName notification)
   :parameter (.getParameter notification)
   :pid (.getPID notification)})

(defn get-notifications
  "Poll an open PostgreSQL connection for notifications.

  Returns a vector of {:name :parameter :pid} maps, or an empty vector. A
  positive timeout may block all other statements on this connection, so use a
  dedicated connection. A zero timeout performs a non-blocking poll."
  ([^Connection conn]
   (let [^PGConnection pg (pg-connection conn)]
     (mapv notification->map (or (.getNotifications pg) []))))
  ([^Connection conn timeout-millis]
   (when-not (and (integer? timeout-millis)
                  (<= 0 timeout-millis Integer/MAX_VALUE))
     (throw (IllegalArgumentException.
             "notification timeout must be a non-negative integer")))
   (let [^PGConnection pg (pg-connection conn)]
     (mapv notification->map
           (or (.getNotifications pg (int timeout-millis)) [])))))
