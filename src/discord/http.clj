(ns discord.http
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [swiss.arrows :refer [-<> -<>>]]
            [discord.types :refer [Authenticated] :as types]
            [discord.utils :as utils]))

;;; Global constants for interacting with the API
(defonce user-agent "DiscordBot (https://github.com/gizmo385/discord.clj)")
(defonce discord-url "https://discordapp.com/api/v6")

;;; Defining types relevant to the Discord APIs
(defrecord Server [id name permissions owner? icon region])

(defn build-server [server-map]
  (map->Server
    {:id          (:id server-map)
     :name        (:name server-map)
     :owner?      (:owner server-map)
     :icon        (:icon server-map)
     :permissions (:permissions server-map)
     :region      (get types/server-region (:region server-map))}))

(defrecord User [id username roles deaf mute avatar joined discriminator])

(defn build-user [user-map]
  (map->User
    {:deaf          (:deaf user-map)
     :mute          (:mute user-map)
     :roles         (:roles user-map)
     :joined        (:joined_at user-map)
     :username      (-> user-map :user :username)
     :id            (-> user-map :user :id)
     :avatar        (-> user-map :user :avatar)
     :discriminator (-> user-map :user :discriminator)}))

(defrecord Channel [id guild-id name type position topic])

(defonce channel-type-map
  {0      :text
   :text  :text
   2      :voice
   :voice :voice})

(defn build-channel [channel-map]
  (map->Channel
    {:guild-id  (:guild_id channel-map)
     :name      (:name channel-map)
     :topic     (:topic channel-map)
     :position  (:position channel-map)
     :id        (:id channel-map)
     :type      (get channel-type-map (:type channel-map))}))



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
   :get-member          (Route. "/guilds/%s/members/%s" :get)
   :edit-member         (Route. "/guilds/%s/members/%s" :patch)

   ;; Current user management
   :get-current-user    (Route. "/users/@me" :get)
   :edit-profile        (Route. "/users/@me" :patch)
   :update-nickname     (Route. "/guilds/%s/members/@me/nick" :patch)

   ;; Server/Guild Management
   :get-servers         (Route. "/users/@me/guilds" :get)
   :leave-server        (Route. "/users/@me/guilds/%s" :delete)
   :delete-server       (Route. "/guilds/%s" :delete)
   :create-server       (Route. "/guilds" :post)
   :edit-server         (Route. "/guilds/%s" :patch)
   :list-members        (Route. "/guilds/%s/members" :get)
   :prune-members       (Route. "/guilds/%s/prune" :post)
   :prunable-members    (Route. "/guilds/%s/prune" :get)

   ;; Channel management
   :get-channel         (Route. "/channels/%s" :get)
   :get-guild-channels  (Route. "/guilds/%s/channels" :get)
   :create-channel      (Route. "/guilds/%s/channels" :post)
   :edit-channel        (Route. "/channels/%s" :patch)
   :delete-channel      (Route. "/channels/%s" :delete)

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
   :get-gateway         (Route. "/gateway" :get)
   :get-bot-gateway     (Route. "/gateway/bot" :get)
   :application-info    (Route. "/oauth2/applications/@me" :get)})

(defn get-endpoint [endpoint-key & args]
  (if-let [{:keys [endpoint method]} (get endpoint-mapping endpoint-key)]
    {:endpoint (apply format endpoint (map utils/get-id args)) :method method}
    (throw (ex-info "Invalid endpoint supplied" {:endpoint endpoint-key}))))

;;; Making requests to the Discord API
(defn build-auth-string [auth]
  (format "%s %s" (types/token-type auth) (types/token auth)))

(defn- build-request [endpoint method auth json params]
  (let  [url      (str discord-url endpoint)
         headers  {:User-Agent    user-agent
                   :Authorization (build-auth-string auth)
                   :Accept        "application/json"}
         request  {:headers headers
                   :url     url
                   :method  method}]
    (condp = method
      :get      (assoc request :params params)
      :post     (assoc request :body (json/write-str json) :content-type :json)
      :put      (assoc request :body (json/write-str json) :content-type :json)
      :patch    (assoc request :body (json/write-str json) :content-type :json)
      :delete   (assoc request :body (json/write-str json) :content-type :json)
      (throw (ex-info "Unknown request method" {:endpoint endpoint :method method})))))

;;; General request wrapper
(defn discord-request
  "General wrapper function for handling requests to the discord APIs.

   Arguments:
   endpoint-key: A keyword that maps to a defined endpoint in endpoint-mapping
   auth: Something that implements the Authenticated protocol to auth with the Discord APIs

   Options are passed :key val ... Supported options:

   :json map - An optional JSON body to pass along with post/put/patch/delete request
   :params map - Optional query parameters to pass along with a get request
   :args list - In order (format) arguments to correctly format the endpoint from endpoint-mapping
   :constructor f - A function which is mapped over API responses to create appropriate Records."
  [endpoint-key auth & {:keys [json params args constructor] :or {constructor identity}}]
  (let [{:keys [endpoint method]} (apply get-endpoint endpoint-key args)
        request                   (build-request endpoint method auth json params)]
    (try
      (let [result (client/request request)]
        (condp = (:status result)
          200 (-<> result
                   (:body <>)
                   (json/read-str <> :key-fn keyword)
                   (map constructor <>))
          204 true
          result))
      (catch Exception e (log/error e)))))

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

;;; Iteracting with guilds/servers
(defn get-servers [auth]
  (discord-request :get-servers auth :constructor build-server))

(defn find-server [auth server-name]
  (if-let [servers (get-servers auth)]
    (filter #(= server-name (:name %1)) servers)))

(defn create-server [auth server-name icon region]
  (discord-request :create-server auth :json {:name server-name :icon icon :region region}
                   :constructor build-server))

(defn edit-server [auth guild-id & {:keys [name region] :as params}]
  (discord-request :edit-server auth :args [guild-id] :json params))

(defn delete-server [auth guild-id]
  (discord-request :delete-server auth :args [guild-id]))

;;; Server member management
(defn list-members [auth guild-id & {:keys [limit after] :as params}]
  (discord-request :list-members auth :args [guild-id] :params params
                   :constructor build-user))

(defn find-member [auth guild-id member-name]
  (let [members (list-members auth guild-id)]
    (filter (fn [member] (= member-name (-> member :user :username))) members)))

(defn get-member [auth guild-id user-id]
  (discord-request :get-member auth :args [guild-id user-id] :constructor build-user))

(defn edit-member [auth guild-id user-id & {:keys [nick roles mute deaf channel_id] :as params}]
  (discord-request :edit-member auth :args [guild-id user-id] :json params))

(defn kick [auth guild-id user-id]
  (discord-request :kick auth :args [guild-id user-id]))

(defn ban [auth guild-id user-id]
  (discord-request :ban auth :args [guild-id user-id]))

(defn unban [auth guild-id user-id]
  (discord-request :unban auth :args [guild-id user-id]))

(defn update-nickname [auth guild-id nickname]
  (discord-request :update-nickname auth :args [guild-id] :json {:nick nickname}))

(defn prune-members [auth guild-id days]
  (discord-request :prune-members auth :args [guild-id] :json {:days days}))

(defn estimate-prune-members [auth guild-id days]
  (discord-request :prunable-members auth :args [guild-id] :params {:days days}))

;;; Current user management
(defn get-current-user [auth]
  ;; The response from /users/@me is a bit strange, so special parsing is needed
  (build-user (into {} (discord-request :get-current-user auth))))

(defn edit-profile [auth & {:keys [username avatar] :as params}]
  (discord-request :edit-profile auth :json params))


;;; Interacting with channels
(defn get-channel [auth channel-id]
  (build-channel (into {} (discord-request :get-channel auth :args [channel-id]))))

(defn get-guild-channels [auth guild-id]
  (discord-request :get-guild-channels auth :args [guild-id] :constructor build-channel))

(defn get-voice-channels [auth guild-id]
  (let [channels (get-guild-channels auth guild-id)]
    (filter (fn [channel] (= 2 (:type channel))) channels)))

(defn get-text-channels [auth guild-id]
  (let [channels (get-guild-channels auth guild-id)]
    (filter (fn [channel] (= 0 (:type channel))) channels)))

(defn find-channel [auth guild-id channel-name]
  (let [channels (get-guild-channels auth guild-id)]
    (filter (fn [channel] (= channel-name (:name channel))) channels)))

(defn create-channel [auth guild-id channel-name & {:keys [type bitrate] :as params}]
  (discord-request :create-channel auth :args [guild-id] :json (assoc params :name channel-name)))

(defn edit-channel [auth channel-id & {:keys [name topic bitrate position] :as params}]
  (discord-request :edit-channel auth :args [channel-id] :json params))

(defn move-channel [auth channel-id new-position]
  (edit-channel auth channel-id :position new-position))

(defn delete-channel [auth channel-id]
  (discord-request :delete-channel auth :args [channel-id]))



;;; Miscellaneous
(defn send-typing [auth channel-id]
  (discord-request :send-typing auth :args [channel-id]))

(defn get-gateway [auth]
  ;; We don't use the constructor here due to the strange response from get-gateway
  (discord-request :get-gateway auth))

(defn get-bot-gateway [auth]
  (discord-request :get-bot-gateway auth))
