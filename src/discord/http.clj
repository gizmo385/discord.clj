(ns discord.http
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]

            [discord.config :as config]))

;;; Global constants for interacting with the API
(defonce user-agent "DiscordBot (https://github.com/gizmo385/discord.clj)")
(defonce discord-url "https://discordapp.com/api/v6")

;;; Defining an authentication protocol for interaction with the API
(defprotocol Authenticated
  (token [this])
  (token-type [this]))

;;; Stub the authentication protocol with a local token for testing
(defrecord BotStub []
  Authenticated
  (token [_] (config/get-token))
  (token-type [_] "Bot"))

;;; General discord request wrapper
(defn discord-request
  ([auth method route]
   (discord-request auth method route nil))

  ([auth method route data]
   (let [auth-token (format "%s %s" (token-type auth) (token auth))
         headers    {:User-Agent    user-agent
                     :Authorization auth-token}
         url        (str discord-url route)
         request (assoc data :headers headers :url url :method method)]
     (client/request request))))


;;; Sending messages and updates to channels
(defn send-typing [auth channel-id]
  (discord-request auth :post (format "/channels/%s/typing" channel-id)))

(defn send-message [auth channel-id content & {:keys [tts embed]}]
  (let [payload {:content content :tts (boolean tts) :embed embed}]
    (json/read-str
      (:body
        (discord-request
          auth :post (format "/channels/%s/messages" channel-id)
          {:body (json/write-str payload)
           :content-type :json})))))

(defn edit-message [auth message-id channel-id content & {:keys [embed]}]
  (let [payload {:content content :embed embed}]
    (discord-request
      auth :patch (format "/channels/%s/messages/%s" channel-id message-id)
      {:body (json/write-str payload)
       :content-type :json})))

;;; Iteracting with guilds
(defn get-guilds [auth]
  (discord-request auth :get "/users/@me/guilds"))

(defn get-channels [auth guild-id]
  (json/read-str
    (:body
      (discord-request auth :get (format "/guilds/%s/channels" guild-id)))))
