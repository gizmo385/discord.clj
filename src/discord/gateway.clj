(ns discord.gateway
  "This implements the Discord Gateway protocol"
  (:require [clojure.core.async :refer [>! put! close! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.set :refer [map-invert]]
            [clojure.pprint :refer [pprint]]
            [gniazdo.core :as ws]
            [swiss.arrows :refer [-<> -<>>]]
            [discord.http :as http]
            [discord.types :refer [Authenticated] :as types]
            [discord.config :as config]))

;;; Representing a message from the API
(defrecord Message [content attachments embeds sent-time channel author user-mentions role-mentions
                    pinned? everyone-mentioned? id])

(defn- convert-long [s]
  (try
    (Long/parseLong s)
    (catch NumberFormatException nfe s)))

(defn build-message
  "Builds a Message record based on the incoming Message from the Discord Gateway. The Gateway
   record that received the message is passed as the second argument to this function."
  [message-map gateway]
  (log/info (format "Message: %s" (with-out-str (pprint message-map))))
  (let [user-wrap (fn [user-map] {:user user-map})
        author    (http/build-user (user-wrap (get-in message-map [:d :author])))
        channel   (http/get-channel gateway (get-in message-map [:d :channel_id]))
        users     (map (comp http/build-user user-wrap) (get-in message-map [:d :mentions]))
        roles     (map (comp http/build-user user-wrap) (get-in message-map [:d :role_mentions]))]
    (map->Message
      {:author                author
       :user-mentions         users
       :role-mentions         roles
       :channel               channel
       :everyone-mentioned?   (get-in message-map [:d :mention_everyone])
       :content               (get-in message-map [:d :content])
       :embeds                (get-in message-map [:d :embeds])
       :attachments           (get-in message-map [:d :attachments])
       :pinned?               (get-in message-map [:d :pinned])
       :id                    (convert-long (get-in message-map [:d :id]))})))

;;; Implementing Discord Gateway behaviour
(defprotocol Gateway
  (send-message [this message]))

(defrecord DiscordGateway [url shards websocket auth]
  java.io.Closeable
  (close [this]
    (if (:websocket this)
      (ws/close (:websocket this))))

  Authenticated
  (token [this]
    (types/token (:auth this)))
  (token-type [this]
     (types/token-type (:auth this)))

  Gateway
  (send-message [this message]
    (ws/send-msg (:websocket this) (json/write-str message))))

(defn build-gateway [gateway-response]
  (let [gateway-map (into {} gateway-response)
        url (format "%s?v=%s&encoding=%s" (:url gateway-map) types/api-version "json")]
    (map->DiscordGateway (assoc gateway-map :url url))))


;;; The different kinds of messages that we can receive from Discord
(defonce message-name->code
  {:dispatch            0
   :heartbeat           1
   :identify            2
   :presence            3
   :voice-state         4
   :voice-ping          5
   :resume              6
   :reconnect           7
   :request-members     8
   :invalidate-session  9
   :hello               10
   :heartbeat-ack       11
   :guild-sync          12})

(defonce message-code->name
  (map-invert message-name->code))

;;; Handle server message events
(defmulti handle-server-event
  (fn [discord-event gateway receive-chan heartbeat-interval]
    (message-code->name (:op discord-event))))

(defmethod handle-server-event :hello
  [discord-event gateway receive-chan heartbeat-interval]
  ;;; Handle the initial "HELLO" message, which sets the heartbeat-interval
  (let  [new-heartbeat (get-in discord-event [:d :heartbeat_interval])]
    (log/info (format "Setting heartbeat interval to %d milliseconds" new-heartbeat))
    (reset! heartbeat-interval new-heartbeat)))

;;; Since there is nothing to do regarding a heartback ACK message, we'll just ignore it.
(defmethod handle-server-event :heartbeat-ack [& _])

(defmethod handle-server-event :default
  [discord-event gateway receive-chan heartbeat-interval]
  (log/info (format "Event of Type: %s" (message-code->name (:op discord-event)))))

;;; Handle messages from the server
(defmulti handle-server-message
  "Handle messages coming from Discord across the websocket"
  (fn [discord-message gateway receive-chan heartbeat-interval]
    (keyword (:t discord-message))))

(defmethod handle-server-message :READY [& _])
(defmethod handle-server-message :PRESENCE_UPDATE [& _])
(defmethod handle-server-message :GUILD_CREATE [& _])
(defmethod handle-server-message :TYPING_START [& _])
(defmethod handle-server-message :MESSAGE_DELETE [& _])

(defmethod handle-server-message :HELLO
  [discord-message gateway receive-chan _]
  (log/info "RECEIVED HELLO MESSAGE"))

;;; If it's a user message, put it on the receive channel for parsing by the client
(defmethod handle-server-message :MESSAGE_CREATE
  [discord-message gateway receive-chan _]
  (let [message (build-message discord-message gateway)]
    (go (>! receive-chan message))))

(defmethod handle-server-message nil
  [discord-message gateway receive-chan heartbeat-interval]
  ;; A message type of "nil" the message is an event that is handle differently
  (handle-server-event discord-message gateway receive-chan heartbeat-interval))

(defmethod handle-server-message :default
  [discord-message gateway receive-chan _]
  (log/info (format "Unknown message of type %s received: " (keyword (:t discord-message)))))


;;; Sending some of the standard messages to the Discord Gateway
(defn format-gateway-message
  "Builds the correct map structure with the correct op-codes. If the op-code supplied is not found,
   an (ex-info) Exception will be raised"
  [op data]
  (if-let [op-code (message-name->code op)]
    {:op op-code :d data}
    (throw (ex-info "Unknown op-code" {:op-code op}))))

(defn send-identify
  "Sends an identification message to the supplied Gateway. This tells the Discord gateway
   information about ourselves."
  [gateway]
  (let [identify (format-gateway-message
                   :identify
                   {:token (types/token gateway)
                    :properties {"$os"                "linux"
                                 "$browser"           "discord.clj"
                                 "$device"            "discord.clj"
                                 "$referrer"          ""
                                 "$referring_domain"  ""}
                    :compress false
                    :large_threshold 250
                    :shard [0 (:shards gateway)]})]
    (send-message gateway identify)))

(defn send-heartbeat [gateway seq-num]
  (let [heartbeat (format-gateway-message :heartbeat @seq-num)]
    (send-message gateway heartbeat)))


;;; Establishing a connection to the Discord gateway
(defn- handle-message
  "Parses a message coming from the server.

   1: Parses the message body
   2: Attempts to update the seq-num atom with the highest available message.
   3: Passes the message received onto the handle-server-message handler. That handler will either
      directly handle the message, pass it into to a more appropriate handler, or publish it to the
      receive-channel attached to the Gateway."
  [raw-message gateway receive-channel seq-num heartbeat-interval]
  (let [message               (json/read-str raw-message :key-fn keyword)
        next-sequence-number  (:s message)]
      ;; Update the sequence number (if present)
      (if next-sequence-number
        (swap! seq-num max next-sequence-number))

      ;; Pass the message on to the handler
      (handle-server-message message gateway receive-channel heartbeat-interval)))

(defn- create-websocket
  "Creates websocket and connects to the Discord gateway."
  [gateway receive-channel seq-num heartbeat-interval]
  (ws/connect
    (:url gateway)
    :on-receive (fn [message]
                  (handle-message message gateway receive-channel seq-num heartbeat-interval))
    :on-connect (fn [message] (log/info "Connected to Discord Gateway"))
    :on-error   (fn [message] (log/error (format "Error: %s" message)))
    :on-close   (fn [status reason] (log/info (format "Closed: %s (%d)" reason status)))))

(defn connect-to-gateway
  "Attempts to connect to the discord Gateway using some supplied authentication source

   Arguments:
   auth : Authenticated -- An implementation of the Authenticated protcol to authenticate with the
      Discord APIs
   receive-channel : Channel -- An asynchronous channel (core.async) that messages from the server
      will be pushed onto
   seq-num : (Atom Integer?) -- An atomic integer representing the most recent sequence number
      the gateway has seen from the server.
   heartbeat-interval : (Atom Integer?) -- An atomic integer representing the current heartbeat
      interval dictated by the Discord Gateway upon connection."
  [auth receive-channel seq-num heartbeat-interval]
  (let [gateway (build-gateway (http/get-bot-gateway auth))
        gateway (assoc gateway :auth auth)
        socket  (create-websocket gateway receive-channel seq-num heartbeat-interval)
        gateway (assoc gateway :websocket socket)]

    ;; Asynchronously send a heartbeat to the gateway
    (go-loop []
      (send-heartbeat gateway seq-num)
      (Thread/sleep (or @heartbeat-interval 1000))
      (recur))

    ;; Return the gateway that we created
    gateway))
