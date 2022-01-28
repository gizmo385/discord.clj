(ns discord.interactions.commands
  (:require
    [clojure.walk :as w]
    [clojure.set :as s]
    [clojure.string :as st]
    [discord.config :as config]
    [discord.api.interactions :as interactions-api]
    [discord.interactions.core :as i]
    [discord.utils :as utils]
    [taoensso.timbre :as timbre]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants defined in the developer documentation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def chat-input-command-type 1)
(def user-command-type 2)
(def message-command-type 3)

(def command-types #{chat-input-command-type user-command-type message-command-type})

(def sub-command-option-type 1)
(def sub-command-group-option-type 2)
(def string-option-type 3)
(def integer-option-type 4)
(def boolean-option-type 5)
(def user-option-type 6)
(def channel-option-type 7)
(def role-option-type 8)
(def mentionable-option-type 9)
(def number-option-type 10)

(def option-types
  #{sub-command-option-type sub-command-group-option-type string-option-type integer-option-type
    boolean-option-type user-option-type channel-option-type role-option-type
    mentionable-option-type number-option-type})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining slash commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn sub-command
  "Defines a sub-command within the Discord Slash command context."
  [sub-command-name description & options]
  (cond-> {:name sub-command-name :description description :type sub-command-option-type}
    (some? options) (assoc :options options)))

(defn sub-command-group
  "Defines a group of sub commands within the Discord "
  [group-name description & sub-commands]
  (cond-> {:name group-name :description description :type sub-command-group-option-type}
    (some? sub-commands) (assoc :options sub-commands)))

(defn option-choice
  "Specify one option possible in a choice argument to a Slash command."
  [option-name value]
  {:name option-name :value value})

(defn- command-option-builder
  "Helper function for building command option functions. These handle validation of the additional
   options that can be supplied for application command options (i.e. an integer option can take a
   :min-value or :max-value but a boolean option cannot)."
  ([option-type]
   (command-option-builder option-type {}))
  ([option-type valid-options]
   (let [valid-key-set (set (keys valid-options))]
     (fn built-option
       ([option-name description]
        (built-option option-name description false nil))
       ([option-name description required?]
        (built-option option-name description required? nil))
       ([option-name description required? opts]
        (if-let [invalid-keys (not-empty (s/difference (set (keys opts)) valid-key-set))]
          (throw (ex-info (format "Invalid opts supplied: %s" (st/join ", " invalid-keys))
                          {:valid-options valid-key-set
                           :supplied-options opts
                           :invalid-option-names invalid-keys}))
          (-> {:name option-name :description description :type option-type :required required?}
              (merge (s/rename-keys opts valid-options)))))))))

(def boolean-option     (command-option-builder boolean-option-type))
(def user-option        (command-option-builder user-option-type))
(def role-option        (command-option-builder role-option-type))
(def mentionable-option (command-option-builder mentionable-option-type))
(def channel-option     (command-option-builder channel-option-type {:channel-types :channel_types}))
(def string-option      (command-option-builder string-option-type {:autocompete? :autocomplete
                                                                    :choices :choices}))
(def integer-option     (command-option-builder integer-option-type {:min-value :min_value
                                                                     :max-value :max_value
                                                                     :autocomplete? :autocomplete
                                                                     :choices :choices}))
(def number-option      (command-option-builder integer-option-type {:min-value :min_value
                                                                     :max-value :max_value
                                                                     :autocomplete? :autocomplete
                                                                     :choices :choices}))

(defn slash-command
  "Helper for defining a slash command."
  [command-name description & options]
  {:name command-name
   :description description
   :options options
   :type chat-input-command-type})

(defn user-command
  "Defines a new user command, that displays when right-clicking on a user"
  [command-name]
  {:name command-name :type user-command-type})

(defn message-command
  "Definse a new message command, that displays when right-clicking on a message"
  [command-name]
  {:name command-name :type message-command-type})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Command registeration with the Discord APIs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def global-commands-to-register-on-startup
  (atom []))

(def guild-commands-to-register-on-startup
  (atom {}))

(defn register-globally-on-startup!
  "Adds a slash command definition to the list of commands that will be registered with the Discord
   API during bot startup."
  [& command-trees]
  (swap! global-commands-to-register-on-startup concat command-trees))

(defn register-global-commands!
  "Registers Discord-wide slash commands with the Discord API."
  [auth config]
  (timbre/infof
    "Registering %s slash comands with Discord API" (count @global-commands-to-register-on-startup))
  (if-let [application-id (:application-id config)]
    (interactions-api/bulk-upsert-global-slash-commands
      auth application-id @global-commands-to-register-on-startup)
    (timbre/errorf "Couldn't find application ID!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handling slash command interactions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord CommandInvocation
  [command-type command-name full-command-path arguments interaction])

(defn interaction->full-command-path
  "Given a slash command interaction, returns a vector representing the executed command.

   For example, if `/groups edit add` were executed as a command, the result of this function would
   be [:groups :edit :add]."
  [interaction]
  (letfn [(nested-command?  [c]
            (or (nil? (:type c))
                (contains? #{sub-command-option-type sub-command-group-option-type}
                           (:type c))))]
    (some->> interaction
      (tree-seq map? :options)
      (filter nested-command?)
      (map :name)
      (map keyword)
      (into []))))

(defn interaction->argument-map
  "Given an interaction, traverses the command tree and retrieves the arguments supplied to the
   command and returns those arguments as a mapping from the keywordized argument name to the
   supplied value of the argument."
  [interaction]
  (if-let [arguments (flatten (w/postwalk (fn [s] (or (:options s) s)) interaction))]
    (zipmap (map (comp keyword :name) arguments)
            (map :value arguments))))

(defn interaction->command-invocation
  "Parses the command and arguments from a slash command interaction and returns a
   CommandInvocation record instance."
  [interaction]
  (->CommandInvocation
    (get-in interaction [:data :type])
    (get-in interaction [:data :name])
    (interaction->full-command-path (:data interaction))
    (interaction->argument-map (:data interaction))
    interaction))

(defmulti handle-slash-command-interaction
  "Multi-method for providing the implementation of slash commands. This method dispatches based on
   the full command path. So, for example, if you are implementing a slash command such as
   `/clubs manage add <club-name>`, then the implementation of that slash command would be handled
   by a defmethod whose dispatch value is `[:clubs :manage :add]`"
  (fn [invocation auth gateway] (:full-command-path invocation)))

(defmethod handle-slash-command-interaction :default
  [invocation auth gateway]
  (timbre/errorf "Unhandled slash command: %s" (:full-command-path invocation)))


(defmulti handle-user-command-interaction
  "Multi-method for providing the implementation of a user command, which can occur when a user
   right clicks on a user."
  (fn [invocation auth gateway] (:command-name invocation)))

(defmethod handle-user-command-interaction :default
  [invocation auth gateway]
  (timbre/errorf "Unhandled user command: %s - %s" (:command-name invocation) {:invocation invocation}))

(defmulti handle-message-command-interaction
  "Multi-method for providing the implementation of a message command, which can occur when a user
   right clicks on a message"
  (fn [invocation auth gateway] (:command-name invocation)))

(defmethod handle-message-command-interaction :default
  [invocation auth gateway]
  (timbre/errorf "Unhandled message command: %s - %s" (:command-name invocation) {:invocation invocation}))

(defmethod i/handle-interaction i/command-interaction
  [command-interaction auth metadata]
  (let [invocation (interaction->command-invocation command-interaction)]
    (condp = (:command-type invocation)
      chat-input-command-type (handle-slash-command-interaction invocation auth metadata)
      message-command-type (handle-message-command-interaction invocation auth metadata)
      user-command-type (handle-user-command-interaction invocation auth metadata)
      (throw (ex-info "Unsupported command interaction type! This is likely a discord.clj problem"
                      {:invocation invocation})))))
