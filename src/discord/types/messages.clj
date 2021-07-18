(ns discord.types.messages
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.types.user :as user]
    [discord.types.guild :as guild]))

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
  (-> m
      (rename-keys {:channel_id :channel-id
                    :guild_id :guild-id
                    :edited_timestamp :edited-timestamp
                    :mentions_everyone :mentions-everyone?
                    :pinned :pinned?})
      (update :author user/build-user)
      (update :type message-types)
      (map->Message)))
