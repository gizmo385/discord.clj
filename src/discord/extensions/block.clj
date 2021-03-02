(ns discord.extensions.block
  (:require [clojure.core.async :refer [go >!] :as async]
            [clojure.string :as s]
            [discord.bot :as bot]
            [discord.config :as config]
            [discord.constants :as const]
            [discord.http :as http]
            [discord.permissions :as perm]
            [discord.utils :as utils]))

;;; Create the configuraton file if necessary
(defonce settings-file "data/block/settings.json")
(config/file-io settings-file :check)

;;; We're going to need some kind of global so that we don't have to keep hitting the file system
;;; when attempting to parse a message
(defonce blocked-word-settings (atom {}))
(defonce last-loaded-settings (atom nil))

;;; Some helper functions for interacting with the settings
(defn settings-fresh?
  "This function determines whether or not the settings cache is 'fresh'. For the purposes of this
   application, we define a 'fresh' cache as a cache that has been updated in the last 5 minutes."
  []
  (let [last-loaded @last-loaded-settings
        now (System/currentTimeMillis)]
    (and (some? last-loaded)
         (< (- now last-loaded) (* 5 const/MILLISECONDS-IN-MINUTE)))))

(defn get-settings []
  (if (settings-fresh?)
    @blocked-word-settings
    (let [loaded-settings (config/file-io settings-file :load :default {})]
      (reset! blocked-word-settings loaded-settings)
      (reset! last-loaded-settings (System/currentTimeMillis))
      loaded-settings)))

;;; Helper functions to block and unblock certain words at various levels
(defn block-words-helper
  "Helper function to block a word for a particular channel. After adding the word to list, it will
   save the settings and update the last-loaded timestamp"
  [words-to-block guild channel]
  (let [old-settings  (get-settings)
        guild         (keyword guild)
        channel       (keyword channel)
        new-settings  (update-in old-settings [guild channel] concat words-to-block)]
    (config/file-io settings-file :save :data new-settings)
    (reset! blocked-word-settings new-settings)
    (reset! last-loaded-settings (System/currentTimeMillis))))

(defn block-words
  "Easy interface to block a series of words. The number of arguments determines to extent of the block.
   Simply providing the word will constitute a global block. Providing a guild in addition to a
   word will block the word only in that guild. Lastly, providing a guild and channel will only
   block the word in that channel of that guild."
  ([words-to-block]
   (block-words-helper words-to-block :global :all))
  ([words-to-block guild]
   (block-words-helper words-to-block guild :all))
  ([words-to-block guild channel]
   (block-words-helper words-to-block guild channel)))

(defn unblock-words-helper
  "Helper function to unblock a word for a particular channel. After adding the word to list, it will
   save the settings and update the last-loaded timestamp"
  [words-to-block guild channel]
  (let [old-settings  (get-settings)
        guild         (keyword guild)
        channel       (keyword channel)
        old-list      (get-in old-settings [guild channel])
        new-list      (remove (set words-to-block) old-list)
        new-settings  (assoc-in old-settings [guild channel] new-list)]
    (config/file-io settings-file :save :data new-settings)
    (reset! blocked-word-settings new-settings)
    (reset! last-loaded-settings (System/currentTimeMillis))))

(defn unblock-words
  "Easy interface for unblocking a series of words at any blocking level."
  ([words-to-unblock]
   (unblock-words-helper words-to-unblock :global :all))
  ([words-to-unblock guild]
   (unblock-words-helper words-to-unblock guild :all))
  ([words-to-unblock guild channel]
   (unblock-words-helper words-to-unblock guild channel)))

(defn get-blocked-words
  "Returns the blocked words for a particular guild and channel combination."
  [guild-id channel-id]
  (let [guild-id    (keyword guild-id)
        channel-id  (keyword channel-id)
        settings    (get-settings)]
    (concat
      (get-in settings [:global :all] [])
      (get-in settings [guild-id :all] [])
      (get-in settings [guild-id channel-id] []))))

;;; Create a extension that will be used to add and remove blocked words for servers
(bot/install-modules!
  (bot/with-module :filter
    (bot/command
      :list [client message]
      (let [channel       (get-in message [:channel :id])
          guild         (get-in message [:channel :guild-id])
          blocked-words (get-blocked-words guild channel)]
        (->> blocked-words
             (s/join ", ")
             (format "The following words are blocked in that channel: %s")
             (bot/reply-in-dm client message))))

    ;; Blocking and unblocking on a bot-global basis
    (bot/with-module :global
      (bot/command
        :block [client message & words-to-block]
        (if (perm/has-permission? client message perm/ADMINISTRATOR)
          (do (block-words words-to-block)
              (bot/reply-in-channel client message "Those words are now globally blocked."))
          (bot/reply-in-channel "You don't have permission to block words.")))
      (bot/command
        :unblock [client message & words-to-unblock]
        (when (perm/has-permission? client message perm/ADMINISTRATOR)
          (unblock-words words-to-unblock))))

    ;; Blocking and unblocking on a per-guild basis
    (bot/with-module :guild
      (bot/command
        :block [client message & words-to-block]
        (if (perm/has-permission? client message perm/MANAGE-GUILD)
          (let [guild-id (get-in message [:channel :guild-id])]
            (block-words words-to-block guild-id)
            (bot/reply-in-channel client message "Those words are now blocked in this guild."))
          (bot/reply-in-channel client message "You don't have permission to block words.")))
      (bot/command
        :unblock [client message & words-to-unblock]
        (if (perm/has-permission? client message perm/MANAGE-GUILD)
          (let [guild-id (get-in message [:channel :guild-id])]
            (unblock-words words-to-unblock guild-id)
            (bot/reply-in-channel client message "Those words are now unblocked in this guild."))
          (bot/reply-in-channel client message "You don't have permission to unblock words."))))

    ;; Blocking and unblocking on a per-channel basis
    (bot/with-module :channel
      (bot/command
        :block [client message & words-to-block]
        (if (perm/has-permission? client message perm/MANAGE-CHANNELS)
          (let [channel-id (get-in message [:channel :id])
                guild-id (get-in message [:channel :guild-id])]
            (block-words words-to-block guild-id channel-id)
            (bot/reply-in-channel
              client message "Those words are now blocked in this guild channel"))
          (bot/reply-in-channel client message "You don't have permission to block words.")))
      (bot/command
        :unblock [client message & words-to-unblock]
        (if (perm/has-permission? client message perm/MANAGE-CHANNELS)
          (let [channel-id (get-in message [:channel :id])
                guild-id (get-in message [:channel :guild-id])]
            (unblock-words words-to-unblock guild-id channel-id)
            (bot/reply-in-channel
              client message "Those words are now unblocked in this guild channel"))
          (bot/reply-in-channel client message "You don't have permission to unblock words!"))))))

;;; Build a handler that will delete the blocked words when they are used and warn the user who
;;; submitted the naughty word :)
(defn- message-needs-deletion? [message]
  (let [message-text (s/lower-case (:content message))
        channel (get-in message [:channel :id])
        guild   (get-in message [:channel :guild-id])
        blocked (get-blocked-words guild channel)]
    (some true?
          (for [blocked-word blocked]
            (s/includes? message-text (name blocked-word))))))

(defn block-message-handler [prefix client message]
  (when (message-needs-deletion? message)
    (bot/delete-original-message client message)
    (bot/reply-in-channel client message "Naughty, naughty! No swearing allowed! :see_no_evil:")))

(bot/add-handler! block-message-handler)
