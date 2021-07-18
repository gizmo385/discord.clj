(ns discord.types.role
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.types.permissions :as perms]))

(defrecord RoleTags
  [bot-id integration-id premium-subscriber-role?])

(defn build-role-tags
  [m]
  (rename-keys m {:premium_subscriber :premium-subscriber-role?
                  :integration_id :integration-id
                  :bot_id :bot-id}))

(defrecord Role
  [id name color hoist? position permissions managed? mentionable? tags])

(defn build-role
  [m]
  (-> m
      (rename-keys {:hoist :hoist?
                    :managed :managed?
                    :mentionable :mentionable?})
      (update :permissions #(some-> % Long/parseLong))
      (update :tags build-role-tags)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helper functions for determing if a role has a particular permission
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn role-has-permission?
  "Determines whether or not a role has the specified permission."
  [role permission]
  (perms/has-permission? (:permissions role) permission))

(defn role-has-all-permissions?
  "Determines whether or not a role has ALL of the specified permission."
  [role permissions]
  (perms/has-all-permissions? (:permissions role) permissions))
