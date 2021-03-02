(ns discord.extensions.alias
  (:require [clojure.core.async :refer [go >!] :as async]
            [clojure.string :refer [starts-with?] :as s]
            [taoensso.timbre :as timbre]
            [discord.bot :as bot]
            [discord.embeds :as embeds]
            [discord.permissions :as perms]
            [discord.config :as config]
            [discord.constants :as const]))

;;; Create the configuraton file if necessary
(defonce alias-settings "data/alias/aliases.json")
(config/file-io alias-settings :check)

;;; We'll define an atom to maintain the map of aliases that are defined within the system.
(defonce alias->command (atom {}))

;;; Some helper functions for interacting with the alias definitions
(defn- add-alias! [new-alias command]
  (swap! alias->command assoc new-alias command)
  (config/file-io alias-settings :save :data @alias->command))

(defn- remove-aliases! [aliases-to-remove]
    (swap! alias->command #(apply dissoc % aliases-to-remove))
    (config/file-io alias-settings :save :data @alias->command))

(defn- clear-aliases! []
  (reset! alias->command {})
  (config/file-io alias-settings :save :data @alias->command))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Command definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn list-aliases
  [client message]
  (if-let [aliases (not-empty @alias->command)]
    (->> aliases
         (reduce
           (fn [embed [defined-alias command]]
             (embeds/+field embed (name defined-alias) command))
           (embeds/create-embed :title "Available aliases:"))
         (bot/reply-in-channel client message))
    (bot/reply-in-channel client message "No aliases defined.")))

(defn add-alias
  [client message new-alias & command]
  (if (perms/has-permission? client message perms/ADMINISTRATOR)
    (if (not-empty command)
      (do
        (add-alias! new-alias (s/join " " command))
        (bot/reply-in-channel client message (format "Added new alias '%s'." new-alias)))
      (bot/reply-in-channel client message "Error: Please supply a valid alias definition."))
    (bot/reply-in-channel "You don't have permission to add new aliases.")))

(defn remove-aliases
  [client message & aliases-to-remove]
  (if (perms/has-permission? client message perms/ADMINISTRATOR)
    (do (remove-aliases! aliases-to-remove)
        (bot/reply-in-channel client message "Removed aliases."))
    (bot/reply-in-channel client message "You don't have permission to remove aliases.")))

(defn clear-all-aliases
  [client message]
  (if (perms/has-permission? client message perms/ADMINISTRATOR)
    (do (clear-aliases!)
        (bot/reply-in-channel client message "Removed all aliases."))
    (bot/reply-in-channel client message "You don't have permission to remove aliases.")))

(bot/install-modules!
  (bot/with-module :alias
    {:add add-alias
     :list list-aliases
     :remove remove-aliases
     :clear clear-all-aliases}))

(defn alias-message-handler
  [prefix client message]
  (let [receive-chan    (:receive-channel client)
        message-content (:content message)]
    (doseq [[defined-alias command] @alias->command]
      (timbre/infof "Checking message '%s' against '%s'" message-content defined-alias)
      (when (= message-content (name defined-alias))
        (timbre/infof "Alias matches: %s" defined-alias)
        (go (>! receive-chan (assoc message :content command)))))))

(bot/add-handler! alias-message-handler)
