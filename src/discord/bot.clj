(ns discord.bot
  (:require [clojure.string :refer [starts-with? ends-with?] :as s]
            [clojure.java.io :as io]
            [clojure.core.async :refer [go >!] :as async]
            [clojure.tools.logging :as log]
            [discord.client :as client]
            [discord.config :as config]
            [discord.http :as http]
            [discord.types :as types]
            [discord.utils :as utils])
  (:import [discord.types ConfigurationAuth]))

;;; Defining what an Extension and a Bot is
(defrecord Extension [command handler options])
(defrecord DiscordBot [bot-name extensions prefix client]
  java.io.Closeable
  (close [this]
    (.close (:client this))))

;;; Helper functions
(defn- trim-message-command
  "Trims the prefix and command from the helper function."
  [message command]
  (assoc message :content (-> message :content (s/replace-first command "") s/triml)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Patch functions exposed to bot developers:
;;;
;;; Patch functions that get exposed dynamically to clients within bot handler functions. These
;;; functions are nil unless invoked within the context of a function called from within a
;;; build-handler-fn.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ^:dynamic say [message])
(defn ^:dynamic delete [message])

;;; These functions locally patch the above functions based on context supplied by the handler
(defn- say*
  [send-channel channel content]
  (go (>! send-channel {:channel channel :content content :options {}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; General bot definition and cog/extension dispatch building
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- send-general-help-message
  [client extensions])

(defn- dispatch-to-handlers
  "Dispatches to relevant function handlers when the the messages starts with <prefix><command>."
  [client message prefix extensions]
  (doseq [{:keys [command handler] :as ext} extensions]
    (let [command-string (str prefix command)]
      (if (starts-with? (:content message) command-string)
        (handler client (trim-message-command message command-string))))))

(defn- build-handler-fn
  "Builds a handler around a set of extensions and rebinds 'say' to send to the message source"
  [prefix extensions]
  ;; Builds a handler function for a bot that will dispatch messages matching the supplied prefix
  ;; to the handlers of any extensions whose "command" is present immediately after the prefix
  (fn [client message]
    ;; First we'll overwrite all of the dynamic functions
    (binding [say     (partial say* (:send-channel client) (:channel message))
              delete  (partial http/delete-message client (:channel message))]
      (if (-> message :content (starts-with? prefix))
        (dispatch-to-handlers client message prefix extensions)))))


;;; General bot creation
(defn create-bot
  "Creates a bot that will dynamically dispatch to different extensions based on <prefix><command>
   style messages."
  ([bot-name extensions]
   (create-bot bot-name extensions (config/get-prefix) (ConfigurationAuth.)))

  ([bot-name extensions prefix]
   (create-bot bot-name extensions prefix (ConfigurationAuth.)))

  ([bot-name extensions prefix auth]
   (let [handler          (build-handler-fn prefix extensions)
         discord-client   (client/create-discord-client auth handler)]
     (log/info (format "Creating bot with prefix: %s" prefix))
     (DiscordBot. bot-name extensions prefix discord-client))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining a bot and its cogs inline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn build-extensions-syntax
  ([key-func-pairs]
   (into [] (map (partial apply build-extensions-syntax)
                 (partition 2 key-func-pairs))))
  ([fn-key fn-body]
   `(map->Extension {:command ~fn-key
                     :handler ~fn-body}))
  ([fn-key fn-body command-options]
   `(map->Extension {:command ~fn-key
                     :handler ~fn-body
                     :options ~command-options})))

(defmacro open-with-cogs
  "Given a name, prefix and series of :keyword-function pairs, builds a new bot inside of a
   with-open block that will sleep the while background threads manage the bot."
  [bot-name prefix & key-func-pairs]
  `(with-open [bot# (create-bot ~bot-name ~(build-extensions-syntax key-func-pairs) ~prefix)]
     (while true (Thread/sleep 3000))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining global cogs and commands that get loaded dynamically on bot startup
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce global-cog-registry (atom (list)))

(defn register-cog!
  ;; Cog without options
  ([cog-name cog-function]
   (let [cog-map {:command  cog-name
                  :handler  cog-function
                  :options  nil}]
     (swap! global-cog-registry conj cog-map)))
  ;; Cog with options
  ([cog-name cog-function cog-options]
   (let [cog-map {:command  cog-name
                  :handler  cog-function
                  :options  cog-options}]
     (swap! global-cog-registry conj cog-map))))

(defmacro defcog
  "Defines a multi-method with the supplied name with a 2-arity dispatch function which dispatches
   based upon the first word in the message. It also defines a :default which responds back with an
   error.

   Example:
   (defcog test-cog [client message]
    \"Optional global cog documentation\"
    (:say
      \"Optional command documentation\"
      (say message))

    (:greet
      (say \"Hello Everyone!\"))

    (:kick
      (doseq [user (:user-mentions message)]
        (let [guild-id (get-in message [:channel :guild-id] message)]
          (http/kick client guild-id user)))))

   Arguments:
    cog-name    :: String -- The name of the cog, and subsequent multi-method being defined.
    arg-vector  :: Vector -- A 2-element vector defining the argument vector for the cog. The first
                              argument is the client being passed to the message
    docstring?  :: String -- Optional documentation that can be supplied for this cog.
    impls       :: Forms  -- A sequence of lists, each representing a command implementation. The
                              first argument to each implementation is a keyword representing the
                              command being implemented. Optionally, the first argument in the
                              implementation can be documentation for this particular command."
  {:arglists '([cog-name [client-param message-param] docstring? & impls])}
  [cog-name [client-param message-param :as arg-vector] & impls]
  ;; Verify that the argument vector supplied to defcog is a list of 2
  {:pre [(or (= 2 (count arg-vector))
             (throw (ex-info "Cog definition argument vector requires 2 items (client & message)."
                             {:len (count arg-vector) :curr arg-vector})))]}
  (let [docstring?  (first impls)
        m           (if (string? docstring?)
                      {:doc docstring?}
                      {})
        impls       (if (string? docstring?)
                      (rest impls)
                      impls)
        options     (if (map? (first impls))
                      (first impls)
                      {})
        impls       (if (map? (first impls))
                      (rest impls)
                      impls)]
    `(do
       ;; Define the multimethod
       (defmulti ~(with-meta cog-name m)
         (fn [client# message#]
           (-> message# :content utils/words first keyword)))

       ;; Register the cog with the global cog hierarchy
       (register-cog! ~(keyword cog-name) ~cog-name ~options)

       ;; Supply a "default" error message responding back with an unknown command message
       (defmethod ~cog-name :default [_# message#]
         (say (format "Unrecognized command for %s: %s"
                      ~(name cog-name)
                      (-> message# :content utils/words first))))

       ;; Add the docstring and the arglist to this command
       (alter-meta! (var ~cog-name) assoc
                    :doc      (str ~docstring? "\n\nAvailable Subcommands:\n")
                    :arglists (quote ([~client-param ~message-param])))

       ;; Build the method implementations
       ~@(for [[dispatch-val & body] impls]
           ; Alter the documentation meta to include this subcommand
           `(let [command-doc#  (if ~(string? (first body))
                                  ~(format "\t%s: %s\n" (name dispatch-val) (first body))
                                  ~(format "\t%s\n" (name dispatch-val)))]
              ; Add documentation for this command to the multimethod documentation
              (alter-meta!
                (var ~cog-name)
                (fn [current-val#]
                  (let [current-doc# (:doc current-val#)]
                    (assoc current-val# :doc (str current-doc# command-doc#)))))

              ; Define the method for this particular dispatch value
              (defmethod ~cog-name ~dispatch-val [~client-param ~message-param]
                ; If docstring is provided to the command, we need to skip the first argument in
                ; the implementation
                (if (string? (first ~body))
                  (do ~@(rest body))
                  (do ~@body))))))))

(defmacro defcommand
  "Defines a one-off command and adds that to the global cog registry. This is a single function
   that will respond to commands and is not a part of a larger command infrastructure.

   Example:
   (defcommand botsay [client message]
   \t(say (:content message))
   \t(delete message))

   The above code expands into the following:

   (do
   \t(defn botsay [client message]
   \t\t(say (:content message))
   \t\t(delete message))

   \t(register-cog! :botsay botsay))
   "
  {:arglists '([command [client-param message-param] docstring? options? & command-body])}
  [command [client-param message-param :as arg-vector] & command-body]
  ;; Try and parse out some of the options that can be supplied to the command
  (let [m             (if (string? (first command-body))
                        {:doc (first command-body)}
                        {})
        command-body  (if (string? (first command-body))
                        (rest command-body)
                        command-body)
        options       (if (map? (first command-body))
                        (first command-body)
                        {})
        command-body  (if (map? (first command-body))
                        (rest command-body)
                        command-body)]
    `(do
       (defn ~(with-meta command m) [~client-param ~message-param] ~@command-body)
       (register-cog! ~(keyword command) ~command ~options))))

;;; Loading cogs from other files
(defn load-clojure-files-in-folder!
  "Given a directory, loads all of the .clj files in that directory tree. This can be used to
   dynamically load cogs defined with defcog or manually calling register-cog! out of a folder."
  [folder]
  (let [clojure-files (->> folder
                           (io/file)
                           (file-seq)
                           (filter (fn [f] (ends-with? f ".clj"))))]
    (doseq [file clojure-files]
      (let [filename (.getAbsolutePath file)]
        (log/info (format "Loading cogs from: %s" filename))
        (load-file filename)))))


(defn create-extension
  [command handler]
  (map->Extension {:command (name command)
                   :handler handler}))

(defn build-extensions [key-func-pairs]
  (into [] (map (partial apply create-extension) key-func-pairs)))

(defn get-registry-extensions
  "Returns the current global-cog-registry as a list of Extensions."
  []
  (map map->Extension @global-cog-registry))

(defmacro with-file-cogs
  "Creates a bot where the cogs are those present in all Clojure files present in the directories
   supplied. This allows you to dynamically add files to a cogs/ directory and have them get
   automatically loaded by the bot when it starts up."
  [bot-name prefix folders]
  `(do
     ;; Loads all the clojure files in the folders supplied
     (doseq [folder# ~folders]
       (load-clojure-files-in-folder! folder#))

     ;; Opens a bot with those extensions
     (let [cogs# (get-registry-extensions)]
       (log/info (format "Loaded %d cogs: %s."
                         (count cogs#)
                         (s/join ", " (map :command cogs#))))
       (with-open [discord-bot# (create-bot ~bot-name cogs# ~prefix)]
         (while true (Thread/sleep 3000))))))
