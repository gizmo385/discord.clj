(ns discord.interactions.slash
  (:require
    [clojure.walk :as w]
    [discord.config :as config]
    [discord.api.interactions :as interactions-api]
    [discord.interactions.core :as i]
    [discord.utils :as utils]
    [taoensso.timbre :as timbre]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants defined in the developer documentation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

(defn command-option*
  "A helper function for defining command options."
  [option-type option-name description & {:keys [required? choices]
                                          :or {required? false}}]
  (cond-> {:name option-name :description description :type option-type :required required?}
    (some? choices) (assoc :choices choices)))

(def string-option      (partial command-option* string-option-type))
(def integer-option     (partial command-option* integer-option-type))
(def boolean-option     (partial command-option* boolean-option-type))
(def user-option        (partial command-option* user-option-type))
(def channel-option     (partial command-option* channel-option-type))
(def role-option        (partial command-option* role-option-type))
(def mentionable-option (partial command-option* mentionable-option-type))
(def number-option      (partial command-option* number-option-type))

(defn command
  "Helper for defining a slash command."
  [command-name description & options]
  {:name command-name
   :description description
   :options options})

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
(defrecord SlashCommandInvocation
  [command-path arguments interaction])

(defn interaction->command-list
  "Given a slash command interaction, returns a vector representing the executed command.

   For example, if `/groups edit add` were executed as a command, the result of this function would
   be [:groups :edit :add]."
  [interaction]
  (letfn [(nested-command?  [c]
            (or (nil? (:type c))
                (contains? #{sub-command-option-type sub-command-group-option-type}
                           (:type c))))]
    (->> interaction
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
  (let [arguments (flatten (w/postwalk (fn [s] (or (:options s) s)) interaction))]
    (zipmap (map (comp keyword :name) arguments) (map :value arguments))))

(defn interaction->slash-command-invocation
  "Parses the command and arguments from a slash command interaction and returns a
   SlashCommandInvocation record instance."
  [interaction]
  (->SlashCommandInvocation
    (interaction->command-list (:data interaction))
    (interaction->argument-map (:data interaction))
    interaction))

(defmulti handle-slash-command-interaction
  "Multi-method for providing the implementation of slash commands. This method dispatches based on
   the full command path. So, for example, if you are implementing a slash command such as
   `/clubs manage add <club-name>`, then the implementation of that slash command would be handled
   by a defmethod whose dispatch value is `[:clubs :manage :add]`"
  (fn [invocation auth gateway] (:command-path invocation)))

(defmethod handle-slash-command-interaction :default
  [invocation auth gateway]
  (timbre/errorf "Unhandled slash command: %s" (:command-path invocation)))

(defmethod i/handle-interaction i/slash-command-interaction
  [command-interaction auth metadata]
  (let [invocation (interaction->slash-command-invocation command-interaction)]
    (handle-slash-command-interaction invocation auth metadata)))
