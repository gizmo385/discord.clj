(ns discord.types.interaction
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.types.messages :as message]
    [discord.types.user :as user]
    [discord.types.guild :as guild]))

(defrecord Interaction
  [id application-id type data guild-id channel-id member user token version message])

(defn build-interaction
  [m]
  (when m
    (-> m
        (rename-keys {:application_id :application-id
                      :guild_id :guild-id
                      :channel_id :channel-id})
        (update :user #(some-> % user/build-user))
        (update :member #(some-> % guild/build-guild-member))
        (update :message #(some-> % message/build-message)))))
