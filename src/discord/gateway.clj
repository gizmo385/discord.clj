(ns discord.gateway
  (:require
    [clojure.core.async :refer [>! <! chan go go-loop] :as async]
    [clojure.data.json :as json]
    [discord.api.misc :as misc]
    [discord.api.channels :as channels-api]
    [discord.config :as config]
    [discord.types.auth :as a]
    [discord.types.messages :as messages]
    [gniazdo.core :as ws]
    [integrant.core :as ig]
    [taoensso.timbre :as timbre]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Record Defining
;;;
;;; This is ultimately what this namespace is designed to construct and manage.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord GatewayV2 [auth metadata websocket]
  a/Authenticated
  (token-type [_] (a/token-type auth))
  (token [_] (a/token auth)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers for messages that we will be sending *to* the gateway
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def gateway-intents
  "This is the list of the valid intents that can be supplied to the Discord gateway. The
   corresponding bit shift values are the indices of each intent. These intents and the information
   they correspond to within Discord are documented here:
    https://discord.com/developers/docs/topics/gateway#gateway-intents"
  [:guilds :guild-members :guild-bans :guild-emojis :guild-integrations :guild-webhooks
   :guild-invites :guild-voice-states :guild-presences :guild-messages :guild-message-reactions
   :guild-message-typing :direct-messages :direct-message-reactions :direct-message-typing])

(defn intent-names->intent-value
  "Given a list of intent names, specifically those from the gateway-intents set, calculates the
   value that should be sent to the Discord gateway as a part of the Identify payload."
  [desired-intents]
  (let [intent-values (set (map (fn [intent-name] (.indexOf gateway-intents intent-name))
                                desired-intents))]
    (if (contains? intent-values -1)
      (throw (ex-info "Invalid intent supplied." {:valid-intents gateway-intents
                                                  :supplied-intents desired-intents}))
      (->> intent-values
           (map (fn [iv] (bit-shift-left 1 iv)))
           (reduce +)))))

(def gateway-event-types
  [:dispatch :heartbeat :identify :presence :voice-state :voice-ping :resume :reconnect
   :request-members :invalidate-session :hello :heartbeat-ack :guild-sync])

(defn format-gateway-message
  [operation-name data]
  (let [op-num (.indexOf gateway-event-types operation-name)]
    (if (not (neg? op-num))
      {:op op-num :d data}
      (throw (ex-info "Invalid operation name!"
                      {:name operation-name :data data})))))

(defn send-gateway-message
  [gateway message]
  (let [formatted-message (json/write-str message)]
    (timbre/tracef "Sending message to gateway: %s" formatted-message)
    (ws/send-msg (deref (:websocket gateway)) formatted-message)))

(defn send-heartbeat [gateway]
  (let [seq-num (get-in gateway [:metadata :seq-num])
        heartbeat (format-gateway-message :heartbeat @seq-num)]
    (timbre/debugf "Sending heartbeat for seq: %s" @seq-num)
    (send-gateway-message gateway heartbeat)))

(defn send-identify
  "Sends an identification message to the supplied Gateway. This tells the Discord gateway
   information about ourselves."
  [gateway]
  (->> {:token (a/token gateway)
        :properties {"$os" (System/getProperty "os.name")
                     "$browser" "discord.clj"
                     "$device" "discord.clj"}
        :compress false
        :large_threshold 250
        :intents (get-in gateway [:config :intents])}
       (format-gateway-message :identify)
       (send-gateway-message gateway)))

(defn send-resume
  [token websocket session-id seq-num]
  (->> {:token token
        :session_id session-id
        :seq seq-num}
       (format-gateway-message :resume)
       (ws/send-msg websocket)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Gateway Control Management
;;;
;;; The Gateway Control events are primarily based on updating the state of the gateway connection
;;; session
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-gateway-control-event
  "Specifically handle events that are sent for the purpose of managing the gateway connection."
  (fn [message metadata]
    (gateway-event-types (:op message))))

(defmethod handle-gateway-control-event :hello
  [message metadata]
  (let [new-heartbeat (get-in message [:d :heartbeat_interval])]
    (timbre/infof "Setting heartbeat interval to %d milliseconds" new-heartbeat)
    (reset! (:heartbeat-interval metadata) new-heartbeat)))

;;; Since there is nothing to do regarding a heartback ACK message, we'll just ignore it.
(defmethod handle-gateway-control-event :heartbeat-ack [& _])

(defmethod handle-gateway-control-event :heartbeat
  [message metadata])

(defmethod handle-gateway-control-event :default
  [message metadata]
  (timbre/infof "Unhandled gateway control event: %s" (gateway-event-types (:op message))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Gateway Message Events
;;;
;;; The gateway message events contain basically everything else that is transmitted over the
;;; gateway, including messages and updates about the state of users/guilds/channels that the bot is
;;; in. When a gateway event lacks a message type, that is when it is a control event and will be
;;; passed to the gateway control event handler.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-gateway-event
  "Handle messages from the Discord Gateway"
  (fn handle-gateway-event-dispatch-fn [message auth metadata] (keyword (:t message))))

(defmethod handle-gateway-event nil
  [message auth metadata]
  (handle-gateway-control-event message metadata))

(defmethod handle-gateway-event :READY
  [message auth metadata]
  (reset! (:session-id metadata)
          (get-in message [:d :session-id])))

(defmethod handle-gateway-event :HELLO
  [message auth metadata]
  (timbre/debug "Received 'HELLO' Discord event."))

(defmethod handle-gateway-event :default
  [message auth metadata]
  (timbre/debugf "Unknown message of type %s received." (keyword (:t message))))

(defmethod handle-gateway-event :MESSAGE_CREATE
  [message auth metadata]
  (timbre/debugf "Received user message: %s" message)
  (go (->> message :d messages/build-message (>! (:recv-chan metadata)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Connecting to the gateway and handling basic interactions with it
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod ig/init-key :discord/gateway-metadata
  [_ {:keys [config]}]
  (let [intents (:intents config)
        intents-value (intent-names->intent-value intents)]
    (timbre/debugf "Gateway intents value: %s (%s)" intents-value intents)
    {:heartbeat-interval (atom nil)
     :intents-value intents-value
     :intent-names intents
     :seq-num (atom 0)
     :session-id (atom 0)
     :stop-heartbeat-chan (async/chan)
     :recv-chan (async/chan)
     :send-chan (async/chan)}))

(defmethod ig/init-key :discord/message-handler-fn
  [_ {:keys [auth config metadata]}]
  (fn gateway-message-handler-fn [raw-message]
    (let [message (json/read-str raw-message :key-fn keyword)
          next-seq-num (:s message)]
      ;; Update the sequence number (if present)
      (when (some? next-seq-num)
        (swap! (:seq-num metadata) max next-seq-num))

      ;; Pass the message on to the handler
      (handle-gateway-event message auth metadata))))


(declare connect-websocket)

(defn should-reconnect?
  [close-code]
  (nil? (some #{close-code} #{1000 4004 4010 4011 4012 4013 4014})))

(defn connect-websocket!
  "Create a websocket to connect to the specified gateway URL and handle messages with the supplied
   message handler function."
  [auth metadata websocket-atom gateway-url message-handler-fn]
  (reset!
    websocket-atom
    (ws/connect
      gateway-url
      :on-receive message-handler-fn
      :on-connect (fn [message] (timbre/infof "Connected to Discord Gateway (%s)" gateway-url))
      :on-error   (fn [message] (timbre/errorf "Error: %s" message))
      :on-close   (fn [status message]
                    (when (should-reconnect? status)
                      (connect-websocket!
                        auth metadata websocket-atom gateway-url message-handler-fn)
                      (send-resume (a/token auth)
                                   @websocket-atom
                                   @(:session-id metadata)
                                   @(:seq-num metadata)))))))

(defmethod ig/init-key :discord/websocket
  [_ {:keys [auth gateway-metadata message-handler-fn]}]
  (let [gateway-url (misc/get-bot-gateway-url auth)
        websocket-atom (atom nil)]
    (connect-websocket! auth gateway-metadata websocket-atom gateway-url message-handler-fn)
    websocket-atom))

(defmethod ig/halt-key! :discord/websocket
  [_ websocket]
  (timbre/infof "Closing websocket connection due to integrant halt!")
  (ws/close (deref websocket)))

(defmethod ig/init-key :discord/gateway-connection
  [_ {:keys [auth metadata websocket]}]
  (let [gateway (->GatewayV2 auth metadata websocket)]
    ;; We'll send our initial heartbeat and identification events
    (send-heartbeat gateway)
    (send-identify gateway)

    ;; Then we'll kick off a persistent loop in the background, which is sending the heartbeat.
    (go-loop []
      (when (-> metadata :heartbeat-interval deref some?)
        (send-heartbeat gateway)
        (async/alt!
          (:stop-heartbeat-chan metadata) (timbre/warn "WebSocket closed! Stopping heartbeat")
          (async/timeout @(:heartbeat-interval metadata)) (recur))))

    ;; We want to kick off a second loop in the background that is sending messages off send-chan
    (go-loop []
      (when-let [{:keys [channel-id content]} (<! (:send-chan metadata))]
        (try (channels-api/send-message-to-channel auth channel-id content)
             (catch Exception e (timbre/errorf "Error sending message: %s" e))))
      (recur))

    gateway))
