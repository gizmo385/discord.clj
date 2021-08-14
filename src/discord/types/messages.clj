(ns discord.types.messages
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.types.user :as user]
    [discord.types.guild :as guild]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Standard messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def message-types
  [:default :recipient-add :recipient-remove :call :channel-name-change :channel-icon-change
   :channel-pinned-message :guild-member-join :user-premium-guild-subscription
   :user-premium-guild-subscription-tier1 :user-premium-guild-subscription-tier2
   :user-premium-guild-subscription-tier3 :channel-follow-add :guild-discovery-disqualified
   :guild-discovery-requalified :guild-discovery-grace-period-initial-warning
   :guild-discovery-grace-period-final-warning :thread-created :reply :application-command
   :thread-starter-message :guild-invite-reminder])

(defrecord Message
  [id channel-id guild-id author member content timestamp edited-timestamp tts? mentions-everyone?
   mentions attachments embeds reactions nonce pinned? type])

(defn build-message
  [m]
  (when m
    (-> m
        (rename-keys {:channel_id :channel-id
                      :guild_id :guild-id
                      :edited_timestamp :edited-timestamp
                      :mentions_everyone :mentions-everyone?
                      :pinned :pinned?})
        (update :author user/build-user)
        (update :type message-types)
        (map->Message))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Reactions to messages
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Emoji [id name])
(defn build-emoji
  "Helper function for converting a potentially nil map into an Emoji record."
  [m]
  (some-> m map->Emoji))

(defrecord MessageReaction [user-id message-id channel-id emoji])
(defn build-message-reaction
  [m]
  (some-> m
          (rename-keys {:user_id :user-id
                        :message_id :message-id
                        :channel_id :channel-id})
          (update :emoji build-emoji)
          (map->MessageReaction)))
