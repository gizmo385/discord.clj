(ns discord.gateway-v2
  (:require
    [clojure.core.async :refer [>! <! chan go go-loop] :as async]
    [clojure.data.json :as json]
    [discord.config :as config]
    [discord.api.misc :as misc]
    [discord.types.auth :as auth]
    [gniazdo.core :as ws]
    [integrant.core :as ig]
    [taoensso.timbre :as timbre]
    ))

(defmethod ig/init-key :discord/message-send-chan [& _] (async/chan))
(defmethod ig/init-key :discord/message-receive-chan [& _] (async/chan))
(defmethod ig/init-key :discord/sequence-number [& _] (atom 0))
(defmethod ig/init-key :discord/heartbeat-number [& _] (atom 0))

(defmethod ig/init-key :discord/message-handler-fn
  [_ {:keys [config]}]
  (fn [client message]
    (println message)))

(defmethod ig/init-key :discord/websocket
  [_ {:keys [message-receive-chan auth]}]
  (let [gateway-url (misc/get-bot-gateway auth)]
    {:url gateway-url}
    #_(ws/connect
      gateway-url
      :on-receive (fn [message]
                    (timbre/infof "Received message: %s" message))
      :on-connect (fn [message] (timbre/info "Connected to Discord Gateway"))
      :on-error   (fn [message] (timbre/errorf "Error: %s" message))
      :on-close   (fn [status reason]
                    ;; The codes above 1001 denote erroreous closure states
                    ;; https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
                    (if (> 1001 status)
                      (do
                        (timbre/warnf "Socket closed for unexpected reason (%d): %s" status reason)
                        (timbre/warnf "Attempting to reconnect to websocket...")
                        #_(reconnect-gateway gateway))
                      (do (timbre/infof "Closing Gateway websocket, not reconnecting (%d)." status)
                          (System/exit 1)))))))

(defmethod ig/init-key :discord/gateway-connection
  [_ {:keys [auth message-send-chan message-receive-chan]}])
