(ns discord.http
  "Functions and Records used to interact directly with the Discord API. Any actions performed
   on Discord eventually boil down to some form of API request. The API endpoints are represented as
   human-readable keywords and implemented using the discord-request helper function. Exposed
   externally are functions such as get-channel, which perform the API requests and handle the
   transformation of the response into a more usable Clojure record."
  (:require [clojure.data.json :as json]
            [clojure.string :as s]
            [clj-http.client :as client]
            [overtone.at-at :as at]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :as timbre]
            [discord.embeds :as embeds]
            [discord.types :refer [Authenticated Snowflake ->snowflake] :as types]
            [discord.utils :as utils]))

;;; Global constants for interacting with the API
(defonce user-agent "discord.clj (https://github.com/gizmo385/discord.clj)")
(defonce discord-url "https://discordapp.com/api/v6")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining Records relevant to the Discord APIs, as well as more useful constructors for
;;; those Records.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Server [id name permissions owner? icon region]
  Snowflake
  (->snowflake [server] (-> server :id Long/parseLong)))

(defn build-server [server-map]
  (map->Server
    {:id          (:id server-map)
     :name        (:name server-map)
     :owner?      (:owner server-map)
     :icon        (:icon server-map)
     :permissions (:permissions server-map)
     :region      (get types/server-region (:region server-map))}))

(defrecord User [id username mention bot? mfa-enabled? verified? roles deaf mute avatar joined
                 discriminator]
  Snowflake
  (->snowflake [user] (-> user :id Long/parseLong)))

(defn build-user [user-map]
  (let [user-id (-> user-map :user :id)
        mention (str \@ user-id)]
    (map->User
    {:id            user-id
     :mention       mention
     :deaf          (:deaf user-map)
     :mute          (:mute user-map)
     :roles         (:roles user-map)
     :joined        (:joined_at user-map)
     :bot?          (-> user-map :user :bot)
     :mfa-enabled?  (-> user-map :user :mfa_enabled)
     :verified?     (-> user-map :user :verified)
     :username      (-> user-map :user :username)
     :avatar        (-> user-map :user :avatar)
     :discriminator (-> user-map :user :discriminator)})))

(defrecord Channel [id guild-id name type position topic]
  Snowflake
  (->snowflake [channel] (-> channel :id Long/parseLong)))

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining the various API endpoints exposed by the Discord APIs. Each of these is a defined as a
;;; "Route" Record, which consistents of the endpoint (formatted for use with clojure.core/format)
;;; as well as the correct HTTP method used to make the API call.
;;;
;;; Requests should be made using the discord-request function and by passing the correct key
;;; present in the endpoint-mapping.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Route [endpoint method])

(defonce endpoint-mapping
  {;; Message operations
   :get-message         (Route. "/channels/{channel}/messages/{message}" :get)
   :send-message        (Route. "/channels/{channel}/messages" :post)
   :edit-message        (Route. "/channels/{channel}/messages/{message}" :patch)
   :delete-message      (Route. "/channels/{channel}/messages/{message}" :delete)
   :delete-messages     (Route. "/channels/{channel}/messages/bulk-delete" :post)
   :pin-message         (Route. "/channels/{channel}/pins/{message}" :put)
   :unpin-message       (Route. "/channels/{channel}/pins/{message}" :delete)
   :pins-from           (Route. "/channels/{channel}/pins/{message}" :get)
   :logs-from           (Route. "/channels/{channel}/messages" :get)

   ;; Reactions
   :add-reaction        (Route. "/channels/{channel}/messages/{message}/reactions/{emoji}/@me" :put)
   :remove-reaction     (Route. "/channels/{channel}/messages/{message}/reactions/{emoji}/{user}" :delete)
   :reaction-users      (Route. "/channels/{channel}/messages/{message}/reactions/{emoji}" :get)
   :clear-reactions     (Route. "/channels/{channel}/messages/{message}/reactions" :delete)

   ;; Server member management
   :kick                (Route. "/guilds/{guild}/members/{member}" :delete)
   :ban                 (Route. "/guilds/{guild}/bans/%s" :put)
   :unban               (Route. "/guilds/{guild}/bans/%s" :delete)
   :get-member          (Route. "/guilds/{guild}/members/{member}" :get)
   :edit-member         (Route. "/guilds/{guild}/members/{member}" :patch)

   ;; Current user management
   :get-current-user    (Route. "/users/@me" :get)
   :edit-profile        (Route. "/users/@me" :patch)
   :update-nickname     (Route. "/guilds/{guild}/members/@me/nick" :patch)

   ;; Server/Guild Management
   :get-guild           (Route. "/guilds/{guild}" :get)
   :get-servers         (Route. "/users/@me/guilds" :get)
   :leave-server        (Route. "/users/@me/guilds/{guild}" :delete)
   :delete-server       (Route. "/guilds/{guild}" :delete)
   :create-server       (Route. "/guilds" :post)
   :modify-server       (Route. "/guilds/{guild}" :patch)
   :get-guild-member    (Route. "/guilds/{guild}/members/{user}" :get)
   :list-members        (Route. "/guilds/{guild}/members" :get)
   :prune-members       (Route. "/guilds/{guild}/prune" :post)
   :prunable-members    (Route. "/guilds/{guild}/prune" :get)

   ;; Channel management
   :get-channel         (Route. "/channels/{channel}" :get)
   :get-guild-channels  (Route. "/guilds/{guild}/channels" :get)
   :create-channel      (Route. "/guilds/{guild}/channels" :post)
   :create-dm-channel   (Route. "/users/@me/channels" :post)
   :edit-channel        (Route. "/channels/{channel}" :patch)
   :delete-channel      (Route. "/channels/{channel}" :delete)

   ;; Emojis
   :create-emoji        (Route. "/guilds/{guild}/emojis" :post)
   :delete-emoji        (Route. "/guilds/{guild}/emojis/{emoji}" :delete)
   :edit-emoji          (Route. "/guilds/{guild}/emojis/{emoji}" :patch)

   ;; Invites
   :create-invite       (Route. "/channels/{channel}/invites" :post)
   :get-invite          (Route. "/invite/{invite}" :get)
   :guild-invites       (Route. "/guilds/{guild}/invites" :get)
   :channel-invites     (Route. "/channels/{channel}/invites" :get)
   :accept-invite       (Route. "/invite/{invite}" :post)
   :delete-invite       (Route. "/invite/{invite}" :delete)

   ;; Roles
   :get-roles           (Route. "/guilds/{guild}/roles" :get)
   :edit-role           (Route. "/guilds/{guild}/roles/{role}" :patch)
   :delete-role         (Route. "/guilds/{guild}/roles/{role}" :delete)
   :create-role         (Route. "/guilds/{guild}/roles" :post)
   :add-user-role       (Route. "/guilds/{guild}/members/{member}/roles/{role}" :put)
   :remove-user-role    (Route. "/guilds/{guild}/members/{member}/roles/{role}" :delete)
   :edit-permissions    (Route. "/channels/{channel}/permissions/{overwrite}" :put)
   :delete-permissions  (Route. "/channels/{channel}/permissions/{overwrite}" :delete)

   ;; Miscellaneous
   :send-typing         (Route. "/channels/{channel}/typing" :post)
   :get-gateway         (Route. "/gateway" :get)
   :get-bot-gateway     (Route. "/gateway/bot" :get)
   :application-info    (Route. "/oauth2/applications/@me" :get)})

(defn get-endpoint [endpoint-key endpoint-args]
  (if-let [{:keys [endpoint method]} (get endpoint-mapping endpoint-key)]
    {:endpoint (utils/map-format endpoint endpoint-args)
     :method method}
    (throw (ex-info "Invalid endpoint supplied" {:endpoint endpoint-key}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Functions that handle direct interactions with the Discord API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce rate-limit-pool (at/mk-pool))

(defn- build-request
  "Builds the API request based on the selected endpoint on the supplied arguments."
  [endpoint method auth json params]
  (let  [url      (str discord-url endpoint)
         headers  {:User-Agent    user-agent
                   :Authorization (format "%s %s" (types/token-type auth) (types/token auth))
                   :Accept        "application/json"}
         request  {:headers headers
                   :url     url
                   :method  method}]
    ;; Based on the HTTP method of the request being performed, we'll be attaching either a JSON
    ;; body or URL query parameters to the request.
    (condp = method
      :get      (assoc request :params params)
      :post     (assoc request :body (json/write-str json) :content-type :json)
      :put      (assoc request :body (json/write-str json) :content-type :json)
      :patch    (assoc request :body (json/write-str json) :content-type :json)
      :delete   (assoc request :body (json/write-str json) :content-type :json)
      (throw (ex-info "Unknown request method" {:endpoint endpoint :method method})))))

(defn- send-api-request
  "Sends a request to the Discord API and handles the response."
  [request constructor]
  (let  [response   (client/request request)
         status     (:status response)]
    (case status
      200 (as-> response response
            (:body response)
            (json/read-str response :key-fn keyword)
            (if (seq? response)
              (map constructor response)
              (constructor response)))
      204 true

      ;; Default
      false)))

(defn discord-request
  "General wrapper function for sending a request to one of the pre-defined Discord API endpoints.
   This function calls other helper functions to handle the following:
    - Retrieving the API endpoint to call
    - Formatting the request
    - Sending the API call
    - Deferred retries of API calls in the event of a 429 Rate Limit response

   Arguments:
   endpoint-key: A keyword that maps to a defined endpoint in endpoint-mapping
   auth: Something that implements the Authenticated protocol to auth with the Discord APIs

   Options are passed :key val ... Supported options:

   :json map - An optional JSON body to pass along with post/put/patch/delete request
   :params map - Optional query parameters to pass along with a get request
   :args list - In order (format) arguments to correctly format the endpoint from endpoint-mapping
   :constructor f - A function which is mapped over API responses to create appropriate Records."
  [endpoint-key auth & {:keys [json params args constructor] :or {constructor identity} :as opts}]
  (let [{:keys [endpoint method]} (get-endpoint endpoint-key opts)
        request                   (build-request endpoint method auth json params)]
    (try+
      (send-api-request request constructor)

      ;; Handle an API rate limit (return code 429)
      (catch [:status 429] {:keys [body]}
        (let [rate-limit-info (json/read-str body)
              wait-time       (get rate-limit-info "retry_after")]
          (timbre/info (format "Rate limited by API, waiting for %d milliseconds." wait-time))
          (at/after wait-time #(send-api-request request constructor) rate-limit-pool)))

      ;; Log any other errors that we encounter
      (catch Exception e
        (timbre/error e)
        false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Wrappers for the various API endpoints defined above. These functions perform the requests and
;;; wrap the response in the appropriate Record
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; Managing messages
(defn send-message [auth channel content & {:keys [tts embed]}]
  (let [embed-map (embeds/embed->map embed)
        payload   {:content content :tts (boolean tts) :embed embed-map}
        payload   (if (empty? embed-map) payload (assoc payload :embed embed-map))]
    (discord-request :send-message auth :channel channel :json payload)))

(defn delete-message [auth channel message]
  (discord-request :delete-message auth :channel channel :message message))

(defn delete-messages [auth channel messages]
  (discord-request :delete-messages auth :channel channel :json messages))

(defn edit-message [auth channel message content & {:keys [embed]}]
  (let [payload {:content content :embed embed}]
    (discord-request :edit-message auth :channel channel :message message :json payload)))

(defn get-message [auth channel message]
  (discord-request :get-message auth :channel channel :message message))

(defn logs-from [auth channel limit & {:keys [before after around] :as params}]
  (let  [request-params (assoc params :limit limit)]
    (discord-request :logs-from auth :channel channel :params request-params)))

(defn pin-message [auth channel message]
  (discord-request :pin-message auth :channel channel :message message))

(defn unpin-message [auth channel message]
  (discord-request :unpin-message auth :channel channel :message message))

(defn pins-from [auth channel]
  (discord-request :pins-from auth :channel channel))


;;; Managing reactions
(defn add-reaction [auth channel message emoji]
  (discord-request :add-reaction auth :channel channel :message message :emoji emoji))

(defn remove-reaction [auth channel message emoji user]
  (discord-request :remove-reaction auth :channel channel :message message :emoji emoji :user user))

(defn reaction-users [auth channel message emoji & {:keys [limit after] :as params}]
  (discord-request :reaction-users :channel channel :message message :emoji emoji :params params))

(defn clear-reactions [auth channel message]
  (discord-request :clear-reactions :channel channel :message message))

;;; Iteracting with guilds/servers
(defn get-guild [auth guild]
  (discord-request :get-guild auth :guild guild :constructor build-server))

(defn get-servers [auth]
  (discord-request :get-servers auth :constructor build-server))

(defn find-server [auth server-name]
  (if-let [servers (get-servers auth)]
    (filter #(= server-name (:name %1)) servers)))

(defn create-server [auth server-name icon region]
  (discord-request :create-server auth :json {:name server-name :icon icon :region region}
                   :constructor build-server))

(defn modify-server [auth guild & {:keys [name region] :as params}]
  (discord-request :modify-server auth :guild guild :json params))

(defn delete-server [auth guild]
  (discord-request :delete-server auth :guild guild))

;;; Server member management
(defn get-guild-member [auth guild user]
  (discord-request :get-guild-member auth :guild guild :user user :constructor build-user))

(defn list-members [auth guild & {:keys [limit after] :as params}]
  (discord-request :list-members auth :guild guild :params params :constructor build-user))

(defn find-member [auth guild member-name]
  (let [members (list-members auth guild)]
    (filter (fn [member] (= member-name (-> member :user :username))) members)))

(defn get-member [auth guild user]
  (discord-request :get-member auth :guild guild :user user :constructor build-user))

(defn edit-member [auth guild user & {:keys [nick roles mute deaf channel_id] :as params}]
  (discord-request :edit-member auth :guild guild :user user :json params))

(defn kick [auth guild user]
  (discord-request :kick auth :guild guild :user user))

(defn ban [auth guild user]
  (discord-request :ban auth :guild guild :user user))

(defn unban [auth guild user]
  (discord-request :unban auth :guild guild :user user))

(defn update-nickname [auth guild nickname]
  (discord-request :update-nickname auth :guild guild :json {:nick nickname}))

(defn prune-members [auth guild days]
  (discord-request :prune-members auth :guild guild :json {:days days}))

(defn estimate-prune-members [auth guild days]
  (discord-request :prunable-members auth :guild guild :params {:days days}))

;;; Current user management
(defn get-current-user [auth]
  ;; The response from /users/@me is a bit strange, so special parsing is needed
  (build-user (into {} (discord-request :get-current-user auth))))

(defn edit-profile [auth & {:keys [username avatar] :as params}]
  (discord-request :edit-profile auth :json params))


;;; Interacting with channels
(defn get-channel [auth channel]
  (build-channel (into {} (discord-request :get-channel auth :channel channel))))

(defn get-guild-channels [auth guild]
  (discord-request :get-guild-channels auth :guild guild :constructor build-channel))

(defn get-voice-channels [auth guild]
  (let [channels (get-guild-channels auth guild)]
    (filter (fn [channel] (= 2 (:type channel))) channels)))

(defn get-text-channels [auth guild]
  (let [channels (get-guild-channels auth guild)]
    (filter (fn [channel] (= 0 (:type channel))) channels)))

(defn find-channel [auth guild channel-name]
  (let [channels (get-guild-channels auth guild)]
    (filter (fn [channel] (= channel-name (:name channel))) channels)))

(defn create-channel [auth guild channel-name & {:keys [type bitrate] :as params}]
  (let [request-params (assoc params :name channel-name)]
    (discord-request :create-channel auth :guild guild :json request-params)))

(defn create-dm-channel [auth user]
  (let [json-params {:recipient_id (->snowflake user)}]
    (build-channel (into {} (discord-request :create-dm-channel auth :json json-params)))))

(defn edit-channel [auth channel & {:keys [name topic bitrate position] :as params}]
  (discord-request :edit-channel auth :channel channel :json params))

(defn move-channel [auth channel new-position]
  (edit-channel auth channel :position new-position))

(defn delete-channel [auth channel]
  (discord-request :delete-channel auth :channel channel))

;;; Roles
(defn get-roles [auth guild]
  (discord-request :get-roles auth :guild guild))

;;; Miscellaneous
(defn send-typing [auth channel]
  (discord-request :send-typing auth :channel channel))

(defn get-gateway [auth]
  ;; We don't use the constructor here due to the strange response from get-gateway
  (discord-request :get-gateway auth))

(defn get-bot-gateway [auth]
  (discord-request :get-bot-gateway auth))
