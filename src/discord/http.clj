(ns discord.http
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.string :as s]

            [discord.config :as config]))

;;; Global constants for interacting with the API
(defonce user-agent "DiscordBot (https://github.com/gizmo385/discord.clj)")
(defonce discord-url "https://discordapp.com/api/v6")

;;; Defining an authentication protocol for interaction with the API
(defprotocol Authenticated
  (token [this])
  (token-type [this]))

(defn build-auth-string [auth]
  (format "%s %s" (token-type auth)  (token auth)))

;;; Stub the authentication protocol with a local token for testing
(defrecord BotStub []
  Authenticated
  (token [_] (config/get-token))
  (token-type [_] "Bot"))

;;; Defining all of the Discord API endpoints (there's a lot of em)
(defrecord Route [endpoint method])

(defonce endpoint-mapping
  {;; Message operations
   :get-message         (Route. "/channels/%s/messages/%s" :get)
   :send-message        (Route. "/channels/%s/messages" :post)
   :edit-message        (Route. "/channels/%s/messages/%s" :patch)
   :delete-message      (Route. "/channels/%s/messages/%s" :delete)
   :delete-messages     (Route. "/channels/%s/messages/bulk-delete" :post)
   :pin-message         (Route. "/channels/%s/pins/%s" :put)
   :unpin-message       (Route. "/channels/%s/pins/%s" :delete)
   :pins-from           (Route. "/channels/%s/pins/%s" :get)
   :logs-from           (Route. "/channels/%s/messages" :get)

   ;; Reactions
   :add-reaction        (Route. "/channels/%s/messages/%s/reactions/%s/@me" :put)
   :remove-reaction     (Route. "/channels/%s/messages/%s/reactions/%s/%s" :delete)
   :get-reaction-users  (Route. "/channels/%s/messages/%s/reactions/%s" :get)
   :clear-reactions     (Route. "/channels/%s/messages/%s/reactions" :delete)

   ;; Server member management
   :kick                (Route. "/guilds/%s/members/%s" :delete)
   :ban                 (Route. "/guilds/%s/bans/%s" :put)
   :unban               (Route. "/guilds/%s/bans/%s" :delete)
   :server-voice-state  (Route. "/guilds/%s/members/%s" :patch)
   :edit-profile        (Route. "/users/@me" :patch)
   :update-nickname     (Route. "/guilds/%s/members/@me/nick" :patch)
   :edit-member         (Route. "/guilds/%s/members/%s" :patch)

   ;; Channel management
   :create-channel      (Route. "/guilds/%s/channels" :post)
   :edit-channel        (Route. "/channels/channel-id" :patch)
   :delete-channel      (Route. "/channels/channel-id" :patch)
   :move-channels       (Route. "/guilds/%s/channels" :patch)

   ;; Server/Guild Management
   :leave-server        (Route. "/users/@me/guilds/%s" :delete)
   :delete-server       (Route. "/guilds/%s" :delete)
   :create-server       (Route. "/guilds" :post)
   :edit-server         (Route. "/guilds/%s" :patch)
   :prune-members       (Route. "/guilds/%s/prune" :post)
   :prunable-members    (Route. "/guilds/%s/prune" :get)

   ;; Emojis
   :create-emoji        (Route. "/guilds/%s/emojis" :post)
   :delete-emoji        (Route. "/guilds/%s/emojis/%s" :delete)
   :edit-emoji          (Route. "/guilds/%s/emojis/%s" :patch)

   ;; Invites
   :create-invite       (Route. "/channels/%s/invites" :post)
   :get-invite          (Route. "/invite/%s" :get)
   :guild-invites       (Route. "/guild/%s/invites" :get)
   :channel-invites     (Route. "/channel/%s/invites" :get)
   :accept-invite       (Route. "/invite/%s" :post)
   :delete-invite       (Route. "/invite/%s" :delete)

   ;; Roles
   :edit-role           (Route. "/guilds/%s/roles/%s" :patch)
   :delete-role         (Route. "/guilds/%s/roles/%s" :delete)
   :create-role         (Route. "/guilds/%s/roles" :post)
   :add-user-role       (Route. "/guilds/%s/members/%s/roles/%s" :put)
   :remove-user-role    (Route. "/guilds/%s/members/%s/roles/%s" :delete)
   :edit-permissions    (Route. "/channels/%s/permissions/%s" :put)
   :delete-permissions  (Route. "/channels/%s/permissions/%s" :delete)

   ;; Miscellaneous
   :send-typing         (Route. "/channels/%s/typing" :post)
   :application-info    (Route. "/oauth2/applications/@me" :get)})

(defn get-endpoint [endpoint-key & args]
  (if-let [{:keys [endpoint method]} (get endpoint-mapping endpoint-key)]
    {:endpoint (apply format endpoint args)
     :method method}
    (ex-info "Invalid endpoint supplied" {:endpoint endpoint-key})))

;;; Making requests to the Discord API
(defn- build-request [endpoint method auth json params]
  (let  [url      (str discord-url endpoint)
         headers  {:User-Agent user-agent
                   :Authorization (build-auth-string auth)}
         request  {:headers headers
                   :url url
                   :method method}]
    (condp = method
      :get      (assoc request :params params)
      :post     (assoc request :body (json/write-str json) :content-type :json)
      :put      (assoc request :body (json/write-str json) :content-type :json)
      :patch    (assoc request :body (json/write-str json) :content-type :json)
      :delete   (assoc request :body (json/write-str json) :content-type :json)
      :default  (throw (ex-info (format "Unknown request method: %s for %s" method endpoint) {})))))

(defn discord-request-helper [endpoint method auth & {:keys [json params]}]
  (let [request     (build-request endpoint method auth json params)
        result      (client/request request)]
    (condp = (:status result)
      200 (json/read-str (:body result) :key-fn keyword)
      204 true
      :default result)))

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

;;; Managing messages
(defn send-message [auth channel-id content & {:keys [tts embed]}]
  (let [{:keys [endpoint method]}   (get-endpoint :send-message channel-id)
        payload                     {:content content :tts (boolean tts) :embed embed}]
    (discord-request-helper endpoint method auth :json payload)))

(defn delete-message [auth channel-id message-id content]
  (let [{:keys [endpoint method]} (get-endpoint :delete-message channel-id message-id)]
    (discord-request-helper endpoint method auth)))

(defn edit-message [auth channel-id message-id content & {:keys [embed]}]
  (let [{:keys [endpoint method]}   (get-endpoint :edit-message channel-id message-id)
        payload                     {:content content :embed embed}]
    (discord-request-helper endpoint method auth :json payload)))

;;; Miscellaneous
(defn send-typing [auth channel-id]
  (let [{:keys [endpoint method]}   (get-endpoint :send-typing channel-id)]
    (discord-request-helper endpoint method auth)))

(defn edit-message [auth channel-id message-id content & {:keys [embed]}]
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

(comment
  (require '[clojure.pprint :refer [pprint]])
  (pprint endpoint-mapping)

  (let [bot (BotStub.)
        room 328324837963464705
        message (send-message bot room "V2 Test Message")]
    (edit-message bot room (:id message) "Edited Test Message"))

  (pprint (send-message (BotStub.) 328324837963464705 "V2 Test Message"))
  (edit-message (BotStub.) 328324837963464705 3287445868 "Edited Test Message")
  (send-typing (BotStub.) 328324837963464705)
  )
