(ns discord.extensions.builtin.debug
  (:require
    [discord.api.guilds :as guilds-api]
    [discord.interactions.core :as i]
    [discord.interactions.slash :as slash]
    [discord.extensions.core :as ext]
    [discord.extensions.utils :as ext-utils]
    [discord.types.permissions :as perms]
    [discord.types.role :as roles]
    [discord.types.snowflake :as sf]
    [discord.embeds :as e]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Slash command definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(slash/register-globally-on-startup!
  (slash/command
    :debug "Debugging information"
    (slash/sub-command
      :get-permissions "Retrieve permissions for yourself or the mentioned user."
      (slash/user-option :user "The user whose permissions you want to check."))
    (slash/sub-command
      :account-creation-date "Determine the account creation date for yourself or the mentioend user."
      (slash/user-option :user "The user whose account creation date you want to check."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Debugging user permissions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn determine-user-permissions
  "Given a list of roles that a user has and a list of roles available in the guild, determines
   which permissions a user has been granted based on their roles."
  [user-roles guild-roles]
  (->> (for [role user-roles]
         (map (fn [permission] [permission (roles/role-has-permission? role permission)])
              (keys perms/permission-values)))
       (map (partial into {}))
       (apply merge-with #(or %1 %2))))

(defn permission-map->embed
  "Given a map of permissions to a boolean indicating whether or not a user has that permission,
   generates an embed that displays that information."
  [permission-map]
  (reduce (fn [embed [permission possessed?]]
            (e/+field embed (name permission) (str possessed?) :inline true))
          (e/create-embed)
          permission-map))

(defn build-user-permissions-embed
  "Given the IDs of the roles that they have and the roles available in the guild generates an embed
   showing the permissions that user does and does not have."
  [user-role-ids guild-roles]
  (let [user-roles (filter (fn [role] (some #{(:id role)} user-role-ids)) guild-roles)
        user-permissions (determine-user-permissions user-roles guild-roles)]
    (permission-map->embed user-permissions)))

(defmethod slash/handle-slash-command-interaction [:debug :get-permissions]
  [{:keys [interaction arguments]} auth metadata]
  (if-let [guild-id (:guild-id interaction)]
    (let [user-id (or (:user arguments)
                      (i/interaction->user-id interaction))
          guild-member (if (:user arguments)
                         (guilds-api/get-guild-member auth guild-id user-id)
                         (:member interaction))
          user-role-ids (:role-ids guild-member)
          guild-roles (guilds-api/get-guild-roles auth (:guild-id interaction))
          permissions-embed (build-user-permissions-embed user-role-ids guild-roles)
          message (format "Permissions for <@%s>:" user-id)]
      (i/channel-message-response interaction auth message nil [permissions-embed]))
    (i/channel-message-response interaction auth "Please run this command within a guild!" nil nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Determining when a user's account was created
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod slash/handle-slash-command-interaction [:debug :account-creation-date]
  [{:keys [interaction arguments]} auth _]
  (let [user-id (or (:user arguments)
                    (i/interaction->user-id interaction))
        creation-timestamp (quot (sf/snowflake->timestamp user-id) 1000)
        message (format "Account for <@%s> created at <t:%s:F>" user-id creation-timestamp)]
    (i/channel-message-response interaction auth message nil nil)))
