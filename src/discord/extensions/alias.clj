(ns discord.extensions.alias
  (:require [clojure.core.async :refer [go >!] :as async]
            [clojure.string :refer [starts-with?] :as s]
            [discord.bot :as bot]
            [discord.config :as config]
            [discord.constants :as const]
            [discord.utils :as utils]))

;;; Create the configuraton file if necessary
(defonce alias-settings "data/alias/aliases.json")
(config/file-io alias-settings :check)

;;; We're going to need some kind of global so that we don't have to keep hitting the file system
;;; when attempting to parse an alias
(defonce alias-cache (atom {}))
(defonce last-loaded-aliases (atom nil))

;;; Some helper functions for interacting with the alias definitions
(defn alias-cache-fresh?
  "This function determines whether or not the alias cache is 'fresh'. For the purposes of this
   application, we define a 'fresh' cache as a cache that has been updated in the last 5 minutes."
  []
  (let [last-loaded @last-loaded-aliases
        now (System/currentTimeMillis)]
    (and (some? last-loaded)
         (< (- now last-loaded) (* 5 const/MILLISECONDS-IN-MINUTE)))))

(defn- get-aliases []
  (if (alias-cache-fresh?)
    @alias-cache
    (let [aliases (config/file-io alias-settings :load)]
      (reset! alias-cache aliases)
      (reset! last-loaded-aliases (System/currentTimeMillis))
      aliases)))

(defn- add-alias [new-alias command]
  (let [old-aliases (get-aliases)
        new-aliases (assoc old-aliases new-alias command)]
    (config/file-io alias-settings :save :data new-aliases)
    (reset! alias-cache new-aliases)
    (reset! last-loaded-aliases (System/currentTimeMillis))))

(defn- remove-aliases [aliases-to-remove]
  (let [old-aliases (get-aliases)
        new-aliases (apply dissoc old-aliases aliases-to-remove)]
    (config/file-io alias-settings :save :data new-aliases)
    (reset! alias-cache new-aliases)
    (reset! last-loaded-aliases (System/currentTimeMillis))))

(defn- clear-aliases []
  (let [new-aliases {}]
    (config/file-io alias-settings :save :data new-aliases)
    (reset! alias-cache new-aliases)
    (reset! last-loaded-aliases (System/currentTimeMillis))))

;;; The 'alias' extension will handle the addition and removal of aliases into the system.
(bot/defextension alias [client message]
  "Adding aliases for different bot commands."
  (:list
    "Lists the currently available aliases."
    (if-let [aliases (not-empty (get-aliases))]
      (bot/say (s/join
        (for [[defined-alias command] aliases]
          (format "%s: %s\n" defined-alias command))))
      (bot/say "Could not find any defined aliases. Try adding one with 'alias add'!")))

  (:add
    "Adds a new alias to the system."
    (if-let [[commmand new-alias & command] (utils/words (:content message))]
      (do
        (add-alias new-alias (s/join " " command))
        (bot/say "Sucessfully added new alias!"))
      (bot/say "Error! Please supply a valid alias definition: alias add <alias> <command words>")))

  (:remove
    "Removes the specified aliases"
    (let [aliases-to-remove (utils/words (:content message))]
      (remove-aliases aliases-to-remove)
      (bot/say (format "Sucessfully removed: %s" (s/join ", " aliases-to-remove)))))

  (:clear
    "Removes all installed aliases."
    (clear-aliases)
    (bot/say "I've removed all installed aliases.")))

;;; Now we need to build a handler to intercept messages received and look for aliases and send the
;;; mapped command back to the client's receive channel.
(bot/defhandler alias-message-handler [prefix client message]
  (let [aliases         (get-aliases)
        receive-chan    (:receive-channel client)
        message-content (:content message)]
    (doseq [[defined-alias command] aliases]
      (if (= message-content (str prefix (name defined-alias)))
        (go (>! receive-chan (assoc message :content command)))))))
