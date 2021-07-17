(ns discord.extensions.builtin.admin
  (:require
    [clojure.string :as s]
    [discord.permissions :as perm]
    [discord.extensions.core :as ext]
    [discord.extensions.utils :as ext-utils]
    [discord.http :as http]
    [discord.types :as types]
    [taoensso.timbre :as timbre]))

(defn doto-mentioned-users
  [gateway message action required-permission]
  (if (perm/has-permission? gateway message required-permission)
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id])]
        (action gateway guild-id user-id)))
    (ext-utils/reply-in-channel "You don't have the permission to execute that command.")))

(ext/install-prefix-commands!
  (ext/prefix-command-tree :admin
    (ext/prefix-command-tree :user
      (ext/prefix-command :kick [gateway message & _] (timbre/warn "TODO: Kick"))
      (ext/prefix-command :ban [gateway message & _] (timbre/warn "TODO: Ban"))
      (ext/prefix-command :unban [gateway message & _] (timbre/warn "TODO: Unban")))

    (ext/prefix-command-tree :region
      (ext/prefix-command
        :get [gateway message]
        (let [guild-id      (get-in message [:channel :guild-id])
              guild         (http/get-guild gateway guild-id)
              voice-region  (-> guild :region name)]
          (ext-utils/reply-in-channel
            gateway message (format "The guild's voice region is currently \"%s\"" voice-region))))
      (ext/prefix-command
        :list [gateway message]
        (let [reply (format "Supported voice regions: %s" (s/join ", " (keys types/server-region)))]
          (ext-utils/reply-in-channel gateway message reply)))
      (ext/prefix-command
        :move [gateway message new-region]
        (if (perm/has-permission? gateway message perm/MANAGE-GUILD)
          (let [guild-id (get-in message [:channel :guild-id])]
            (if-let [region-keyword (get types/server-region new-region)]
              (do
                (ext-utils/reply-in-channel
                  gateway message (format "Moving voice server to \"%s\"" new-region))
                (http/modify-server gateway guild-id :region new-region))
              (ext-utils/reply-in-channel gateway message (format "The region \"%s\" does not exist."))))
          (ext-utils/reply-in-channel
            gateway message "You don't have permission to move the voice region!"))))

    (ext/prefix-command
      :shutdown [gateway message]
      (if true #_(perm/has-permission? gateway message perm/ADMINISTRATOR)
        (System/exit 0)
        (ext-utils/reply-in-channel gateway message "https://media.giphy.com/media/RX3vhj311HKLe/giphy.gif")))))
