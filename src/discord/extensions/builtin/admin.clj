(ns discord.extensions.builtin.admin
  (:require
    [clojure.string :as s]
    [discord.api.guilds :as guilds-api]
    [discord.interactions.core :as i]
    [discord.interactions.slash :as slash]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Command tree definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def voice-regions
  "A list of available voice regions within Discord."
  ["us-west" "us-east" "us-south" "us-central" "eu-west" "eu-central" "singapore" "london" "sydney"
   "amsterdam" "frankfurt" "brazil"])

(slash/register-globally-on-startup!
  (slash/command
    :admin "Commands to aid with administration."
    (slash/sub-command-group
      :voice-region "Manage the guild's voice region."
      (slash/sub-command :get "Retrieve the current voice region for the guild.")
      (slash/sub-command :list "List available voice regions.")
      (slash/sub-command
        :move "Change the guild's voice region."
        (slash/string-option
          :region "The new voice region to set for the guild."
          :choices (map (fn [r] (slash/option-choice r r)) voice-regions)
          :required? true)))
    (slash/sub-command-group
      :user "User adminstrative actions."
      (slash/sub-command
        :ban "Permanently ban a user from the guild."
        (slash/user-option :user "The user to ban." :required? true)
        (slash/integer-option
          :delete-message-days "The number of days to delete their messages (0-7).")
        (slash/string-option :reason "The reason for the ban (for the audit log)."))
      (slash/sub-command
        :kick "Kick a user from the guild."
        (slash/user-option :user "The user to kick from the server." :required? true)
        (slash/string-option :reason "The reason the user was kicked (for the audit log).")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Admin Voice Region Commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod slash/handle-slash-command-interaction [:admin :voice-region :get]
  [{:keys [interaction]} auth _]
  (if-let [guild-id (:guild-id interaction)]
    (let [voice-region (->> guild-id (guilds-api/get-guild auth) :region)
          message (format "Current voice region: `%s`" voice-region)]
      (i/channel-message-response interaction auth message nil nil))
    (i/channel-message-response interaction auth "Please run this command in a guild!" nil nil)))

(defmethod slash/handle-slash-command-interaction [:admin :voice-region :list]
  [{:keys [interaction]} auth _]
  (let [message (->> voice-regions
                     (map (fn [r] (format "`%s`" r)))
                     (s/join ", ")
                     (format "Voice regions: %s"))]
    (i/channel-message-response interaction auth message nil nil)))

(defmethod slash/handle-slash-command-interaction [:admin :voice-region :move]
  [{:keys [interaction arguments]} auth _]
  (let [guild-id (:guild-id interaction)
        new-region (:region arguments)
        message (format "Moved the server's voice region to `%s`" new-region)]
    (cond
      ;; Verify the command was run in a guild
      (nil? guild-id)
      (i/channel-message-response interaction auth "Please run this command in a guild!" nil nil)

      ;; TODO: Check user permissions

      :default
      (do (guilds-api/change-voice-region auth guild-id new-region)
          (i/channel-message-response interaction auth message nil nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Admin User Commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod slash/handle-slash-command-interaction [:admin :user :kick]
  [{:keys [interaction arguments]} auth _]
  (i/channel-message-response interaction auth "TODO" nil nil))

(defmethod slash/handle-slash-command-interaction [:admin :user :ban]
  [{:keys [interaction arguments]} auth _]
  (i/channel-message-response interaction auth "TODO" nil nil))
