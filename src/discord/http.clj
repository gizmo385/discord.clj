(ns discord.http
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [swiss.arrows :refer [-<> -<>>]]
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
   :reaction-users      (Route. "/channels/%s/messages/%s/reactions/%s" :get)
   :clear-reactions     (Route. "/channels/%s/messages/%s/reactions" :delete)

   ;; Server member management
   :kick                (Route. "/guilds/%s/members/%s" :delete)
   :ban                 (Route. "/guilds/%s/bans/%s" :put)
   :unban               (Route. "/guilds/%s/bans/%s" :delete)
   :server-voice-state  (Route. "/guilds/%s/members/%s" :patch)
   :edit-profile        (Route. "/users/@me" :patch)
   :update-nickname     (Route. "/guilds/%s/members/@me/nick" :patch)
   :edit-member         (Route. "/guilds/%s/members/%s" :patch)

   ;; Server/Guild Management
   :get-guilds          (Route. "/users/@me/guilds" :get)
   :leave-server        (Route. "/users/@me/guilds/%s" :delete)
   :delete-server       (Route. "/guilds/%s" :delete)
   :create-server       (Route. "/guilds" :post)
   :edit-server         (Route. "/guilds/%s" :patch)
   :prune-members       (Route. "/guilds/%s/prune" :post)
   :prunable-members    (Route. "/guilds/%s/prune" :get)

   ;; Channel management
   :get-channels        (Route. "/guilds/%s/channels" :get)
   :create-channel      (Route. "/guilds/%s/channels" :post)
   :edit-channel        (Route. "/channels/%s" :patch)
   :delete-channel      (Route. "/channels/%s" :delete)
   :move-channels       (Route. "/guilds/%s/channels" :patch)

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
    {:endpoint (apply format endpoint args) :method method}
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
      (throw (ex-info "Unknown request method" {:endpoint endpoint :method method})))))

;;; General request wrappe
(defn discord-request
  "General wrapper function for handling requests to the discord APIs.

   Arguments:
   endpoint-key: A keyword that maps to a defined endpoint in endpoint-mapping
   auth: Something that implements the Authenticated protocol to auth with the Discord APIs

   Options are passed :key val ... Supported options:

   :json map - An optional JSON body to pass along with post/put/patch/delete request
   :params map - Optional query parameters to pass along with a get request
   :args list - In order (format) arguments to correctly format the endpoint from endpoint-mapping"
  [endpoint-key auth & {:keys [json params args]}]
  (let [{:keys [endpoint method]} (apply get-endpoint endpoint-key args)
        request                   (build-request endpoint method auth json params)
        result                    (client/request request)]
    (condp = (:status result)
      200 (json/read-str (:body result) :key-fn keyword)
      204 true
      result)))

;;; Managing messages
(defn send-message [auth channel-id content & {:keys [tts embed]}]
  (let [payload {:content content :tts (boolean tts) :embed embed}]
    (discord-request :send-message auth :json payload :args [channel-id])))

(defn delete-message [auth channel-id message-id]
  (discord-request :delete-message auth :args [channel-id message-id]))

(defn delete-messages [auth channel-id message-ids]
  (discord-request :delete-messages auth :args [channel-id] :json message-ids))

(defn edit-message [auth channel-id message-id content & {:keys [embed]}]
  (let [payload {:content content :embed embed}]
    (discord-request :edit-message auth :json payload :args [channel-id message-id])))

(defn get-message [auth channel-id message-id]
  (discord-request :get-message auth :args [channel-id message-id]))

(defn logs-from [auth channel-id limit & {:keys [before after around] :as params}]
  (discord-request :logs-from auth :params (assoc params :limit limit)))

(defn pin-message [auth channel-id message-id]
  (discord-request :pin-message auth :args [channel-id message-id]))

(defn unpin-message [auth channel-id message-id]
  (discord-request :unpin-message auth :args [channel-id message-id]))

(defn pins-from [auth channel-id]
  (discord-request :pins-from auth :args [channel-id]))


;;; Managing reactions
(defn add-reaction [auth channel-id message-id emoji]
  (discord-request :add-reaction auth :args [channel-id message-id emoji]))

(defn remove-reaction [auth channel-id message-id emoji member-id]
  (discord-request :remove-reaction auth :args [channel-id message-id emoji member-id]))

(defn reaction-users [auth channel-id message-id emoji & {:keys [limit after] :as params}]
  (discord-request :reaction-users :params params :arg [channel-id message-id emoji]))

(defn clear-reactions [auth channel-id message-id]
  (discord-request :clear-reactions :args [channel-id message-id]))

;;; Iteracting with guilds
(defn get-guilds [auth]
  (discord-request :get-guilds auth))


;;; Interacting with channels
(defn get-channels [auth guild-id]
  (discord-request :get-channels auth :args [guild-id]))

(defn create-channel [auth guild-id channel-name & {:keys [type bitrate] :as params}]
  (discord-request :create-channel auth :args [guild-id] :json (assoc params :name channel-name)))

(defn edit-channel [auth channel-id & {:keys [name topic bitrate position] :as params}]
  (discord-request :edit-channel auth :args [channel-id] :json params))

(defn delete-channel [auth channel-id]
  (discord-request :delete-channel auth :args [channel-id]))

(defn move-channel [auth channel-id new-position]
  (discord-request :move-channel auth :args [channel-id] :json {:position new-position}))

;;; Miscellaneous
(defn send-typing [auth channel-id]
  (let [{:keys [endpoint method]}   (get-endpoint :send-typing channel-id)]
    (discord-request endpoint method auth)))

(comment
  (require '[clojure.pprint :refer [pprint]])
  (pprint endpoint-mapping)
  )
