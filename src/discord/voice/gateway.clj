(ns discord.voice.gateway
  (:require
    [clojure.core.async :refer [>! <! go go-loop] :as async]
    [taoensso.timbre :as timbre]
    [discord.http :as http]
    [discord.gateway :as gateway]
    [discord.voice.player :as vp]
    [discord.types :as types]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Protocol/Record definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord VoiceGateway [standard-gateway websocket udp-socket])

(defrecord VoiceConnectionState [voice-state server-information])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Voice connections and voice gateways
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce ^:dynamic current-voice-connection-state (atom nil))
(defonce latest-voice-state (atom nil))
(defonce latest-voice-server-information (atom nil))
(defonce create-voice-connection-lock nil)

(defmethod gateway/handle-gateway-message :VOICE_SERVER_UPDATE
  [discord-message gateway receive-chan]
  ;; This message is going to contain information about the current voice server, such
  ;; as the token and voice endpoint
  ;; TODO: Deconstruct the VOICE_SERVER_UPDATEJ
  (let [new-server-info (:d discord-message)]
    (swap! current-voice-connection-state
           update
           [:server-information]
           new-server-info)))


(defmethod gateway/handle-gateway-control-event :VOICE-STATE
  [discord-event gateway receive-chan]
  ;; This event is going to contain information about who is joining/leaving the voice
  ;; server. When we are attempting to connect to the voice gateway, it will also
  ;; include a session ID
  (let [new-server-info (:d discord-message)]
    (swap! current-voice-connection-state
           update
           [:server-information]
           new-server-info)))

;;; TODO: Do we need to monitor the voice connection state and lock on it? Should this
;;; be done with an add-watch
