(ns discord.bot
  (:require [clojure.string :refer [starts-with? ends-with?] :as s]
            [clojure.java.io :as io]
            [clojure.core.async :refer [go >!] :as async]
            [taoensso.timbre :as timbre]
            [discord.client :as client]
            [discord.config :as config]
            [discord.http :as http]
            [discord.utils :as utils]
            [discord.types :as types]))

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
;;; Custom Discord message handlers
;;;
;;; This allows the creation of more advanced plugins that directly intercept messages that in a
;;; way that allows for more customized handling than what is available through extensions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce message-handlers (atom []))

(defn add-handler! [handler]
  (swap! message-handlers conj handler))

(defn get-handlers [] @message-handlers)

(defmacro defhandler
  "This is used to create custom message handlers, for which all messages received by the bot will
   be passed through. This is used f or the creation of advanced bot functionality such as automatic
   moderation, alias creation, etc."
  [handler-name [prefix-param client-param message-param] & body]
  (let [handler-fn-name (gensym (name handler-name))]
   `(do
      (timbre/infof "Register custom message handler: %s" ~(name handler-name))
      (defn ~handler-fn-name [~prefix-param ~client-param ~message-param] ~@body)
      (add-handler! ~handler-fn-name))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Patch functions exposed to bot developers:
;;;
;;; Patch functions that get exposed dynamically to clients within bot handler functions. These
;;; functions are nil unless invoked within the context of a function called from within a
;;; build-handler-fn.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ^:dynamic say [message])
(defn ^:dynamic pm [message])
(defn ^:dynamic delete [message])

;;; These functions locally patch the above functions based on context supplied by the handler
(defn- say*
  [send-channel channel content]
  (go (>! send-channel {:channel channel :content content :options {}})))

(defn- pm* [auth send-channel user content]
  ;; Open up a DM channel with the recipient and then send them the message
  (let [dm-channel (http/create-dm-channel auth user)]
    (go (>! send-channel {:channel dm-channel :content content :options {}}))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; General bot definition and extension/extension dispatch building
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- dispatch-to-handlers
  "Dispatches to the currently registered user message handlers."
  [prefix client message]
  (let [handlers (get-handlers)]
    (doseq [handler handlers]
      (handler prefix client message))))

(defn- dispatch-to-extensions
  "Dispatches to the supplied extensions."
  [client message prefix extensions]
  (doseq [{:keys [command handler] :as ext} extensions]
    (let [command-string (str prefix (name command))]
      (if (starts-with? (:content message) command-string)
        (handler client (trim-message-command message command-string))))))

(defn- build-handler-fn
  "Builds a handler around a set of extensions and rebinds 'say' to send to the message source"
  [prefix extensions]
  ;; Builds a handler function for a bot that will dispatch messages matching the supplied prefix
  ;; to the handlers of any extensions whose "command" is present immediately after the prefix
  (fn [client message]
    ;; First we'll partially apply our helper functions based on the incoming client and message.
    (binding [say     (partial say* (:send-channel client) (:channel message))
              delete  (partial http/delete-message client (:channel message))
              pm      (partial pm* client (:send-channel client) (get-in message [:author :id]))]

      ;; If the message starts with the bot prefix, we'll dispatch to any extension extensions that
      ;; have been installed
      (if (-> message :content (starts-with? prefix))
        (go
          (dispatch-to-extensions client message prefix extensions)))

      ;; For every message, we'll dispatch to the handlers. This allows for more sophisticated
      ;; handling of messages that don't necessarily match the prefix (i.e. matching & deleting
      ;; messages with swear words).
      (go
        (dispatch-to-handlers prefix client message)))))


;;; General bot creation
(defn create-bot
  "Creates a bot that will dynamically dispatch to different extensions based on <prefix><command>
   style messages."
  ([bot-name extensions]
   (create-bot bot-name extensions (config/get-prefix) (types/configuration-auth)))

  ([bot-name extensions prefix]
   (create-bot bot-name extensions prefix (types/configuration-auth)))

  ([bot-name extensions prefix auth]
   (let [handler          (build-handler-fn prefix extensions)
         discord-client   (client/create-discord-client auth handler)]
     (timbre/infof "Creating bot with prefix: %s" prefix)
     (DiscordBot. bot-name extensions prefix discord-client))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining a bot and its extensions inline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn build-inline-extensions
  ([key-func-pairs]
   (into [] (map (partial apply build-inline-extensions)
                 (partition 2 key-func-pairs))))
  ([fn-key fn-body]
   `(map->Extension {:command ~fn-key
                     :handler ~fn-body}))
  ([fn-key fn-body command-options]
   `(map->Extension {:command ~fn-key
                     :handler ~fn-body
                     :options ~command-options})))

(defmacro with-extensions
  "Given a name, prefix and series of :keyword-function pairs, builds a new bot inside of a
   with-open block that will sleep the while background threads manage the bot."
  [bot-name prefix & key-func-pairs]
  `(with-open [bot# (create-bot ~bot-name ~(build-inline-extensions key-func-pairs) ~prefix)]
     (while true (Thread/sleep 3000))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining global extensions and commands that get loaded dynamically on bot startup
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce extension-registry (atom (list)))
(defonce extension-docs (atom {}))

(defn register-extension!
  "Creates a mapping between the supplied extension name and the handler function in the global
   extension registry."
  ;; extension without options
  ([extension-name extension-function]
   (let [extension-map {:command  extension-name
                  :handler  extension-function
                  :options  nil}]
     (swap! extension-registry conj extension-map)))

  ;; extension with options
  ([extension-name extension-function extension-options]
   (let [extension-map {:command  extension-name
                  :handler  extension-function
                  :options  extension-options}]
     (swap! extension-registry conj extension-map))))

(defn register-extension-docs!
  "Add the documentation for this particular extension to the global extension documentation
   registry."
  [extension-name documentation]
  (if (seq documentation)
    (swap! extension-docs assoc extension-name documentation)))

(defn- build-default-extension-method
  "This function adds a catch-all error handling function for the extension to handle invalid
   subcommand input."
  [extension-name]
  `(defmethod ~extension-name :default [_# message#]
     (say (format "Unrecognized subcommand: %s"
                  ~(name extension-name)
                  (-> message# :content utils/words first)))))

(defn- build-subcomand
  "For each of the defined subcommands, we need to define a multimethod that handles that
   particular subcommand. We also want to add the documentation for that subcommand to the
   documentation for the overall command."
  [extension-name client-param message-param dispatch-val & body]
  `(let [command-doc#  ~(if (string? (first body))
                          `(format "\t%s: %s\n" ~(name dispatch-val) ~(first body))
                          `(format "\t%s\n" ~(name dispatch-val)))]
     ;; Add documentation for this command to the multimethod documentation
     (alter-meta!
       (var ~extension-name)
       (fn [current-val#]
         (let [current-doc# (:doc current-val#)]
           (assoc current-val# :doc (str current-doc# command-doc#)))))

     ;; Define the method for this particular dispatch value
     (defmethod ~extension-name ~dispatch-val [~client-param ~message-param]
       ;; If docstring is provided to the command, we need to skip the first argument in
       ;; the implementation
       ~(if (string? (first body))
          `(do ~@(rest body))
          `(do ~@body)))))

(defmacro defextension
  "Defines a multi-method with the supplied name with a 2-arity dispatch function which dispatches
   based upon the first word in the message. It also defines a :default which responds back with an
   error.

   Example:
   (defextension test-extension [client message]
    \"Optional global extension documentation\"
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
    extension-name :: String -- The name of the extension, and subsequent multi-method being defined.
    arg-vector :: Vector -- A 2-element vector defining the argument vector for the extension.  The
      first argument is the client being passed to the message
    docstring? :: String -- Optional documentation that can be supplied for this extension.
    impls :: Forms  -- A sequence of lists, each representing a command implementation. The
      first argument to each implementation is a keyword representing the command being implemented.
      Optionally, the first argument in the implementation can be documentation for this particular
      command."
  {:arglists '([extension-name [client-param message-param] docstring? & impls])}
  [extension-name [client-param message-param :as arg-vector] & impls]
  ;; Verify that the argument vector supplied to defextension is a list of 2
  {:pre [(or (= 2 (count arg-vector))
             (throw (ex-info "Extension definition arg vector needs 2 args (client & message)."
                             {:len (count arg-vector) :curr arg-vector})))]}
  ;; Parse out some of the possible optional arguments
  (let [docstring?  (if (string? (first impls))
                      (first impls)
                      "")
        m           (if (string? (first impls))
                      {:doc (first impls)}
                      {})
        impls       (if (string? (first impls))
                      (rest impls)
                      impls)
        options     (if (map? (first impls))
                      (first impls)
                      {})
        impls       (if (map? (first impls))
                      (rest impls)
                      impls)

        ;; The last thing that we want to do is gensym on the extension name. This will prevent the
        ;; defined extensions from overshadowing existing functions and causing problems down the
        ;; line.
        extension-fn-name (gensym extension-name)]
    `(do
       ;; Define the multimethod
       (defmulti ~(with-meta extension-fn-name m)
         (fn [client# message#]
           (-> message# :content utils/words first keyword)))

       ;; Register the extension with the global extension hierarchy
       (register-extension! ~(keyword extension-name) ~extension-fn-name ~options)

       ;; Supply a "default" error message responding back with an unknown command message
       ~(build-default-extension-method extension-fn-name)

       ;; Add the docstring and the arglist to this command
       (alter-meta! (var ~extension-fn-name) assoc
                    :doc      (str ~docstring? "\n\nAvailable Subcommands:\n")
                    :arglists (quote ([~client-param ~message-param])))

       ;; Build the method implementations
       ~@(for [[dispatch-val & body] impls]
           (apply build-subcomand extension-fn-name client-param message-param dispatch-val body))

       ;; Register the documentation for the extension
       (register-extension-docs!
         ~(keyword extension-name)
         (-> (var ~extension-fn-name) meta :doc)))))

(defmacro defcommand
  "Defines a one-off command and adds that to the global extension registry. This is a single
   function that will respond to commands and is not a part of a larger command infrastructure.

   Example:
   (defcommand botsay [client message]
   \t(say (:content message))
   \t(delete message))

   The above code expands into the following:

   (do
   \t(defn botsay [client message]
   \t\t(say (:content message))
   \t\t(delete message))

   \t(register-extension! :botsay botsay))
   "
  {:arglists '([command [client-param message-param] docstring? options? & command-body])}
  [command [client-param message-param :as arg-vector] & command-body]
  ;; Try and parse out some of the options that can be supplied to the command
  (let [m               (if (string? (first command-body))
                          {:doc (first command-body)}
                          {})
        command-body    (if (string? (first command-body))
                          (rest command-body)
                          command-body)
        options         (if (map? (first command-body))
                          (first command-body)
                          {})
        command-body    (if (map? (first command-body))
                          (rest command-body)
                          command-body)
        command-fn-name (gensym command)]
    `(do
       (defn ~(with-meta command-fn-name m) [~client-param ~message-param] ~@command-body)
       (register-extension! ~(keyword command) ~command-fn-name ~options)
       (if (:doc ~m)
         (register-extension-docs! ~(keyword command) ~(:doc m))))))

;;; Loading extensions from other files
(defn get-clojure-files
  "Given a directory, returns all '.clj' files in that folder."
  [folder]
  (->> folder
       (io/file)
       (file-seq)
       (filter (fn [f] (ends-with? f ".clj")))
       (map (fn [f] (.getAbsolutePath f)))))

(defn load-clojure-files-in-folder!
  "Given a directory, loads all of the .clj files in that directory tree. This can be used to
   dynamically load extensions defined with defextension or manually calling register-extension!
   out of a folder."
  [folder]
  (let [clojure-files (get-clojure-files folder)]
    (doseq [filename clojure-files]
      (timbre/infof "Loading extensions from: %s" filename)
      (load-file filename))))

(defn create-extension
  [command handler]
  (map->Extension {:command (name command)
                   :handler handler}))

(defn build-extensions [key-func-pairs]
  (into [] (map (partial apply create-extension) key-func-pairs)))

(defn get-registered-extensions
  "Returns the current extension-registry as a list of Extensions."
  []
  (map map->Extension @extension-registry))

(defmacro from-files
  "Creates a bot where the extensions are those present in all Clojure files present in the
   directories supplied. This allows you to dynamically add files to a extensions/ directory and
   have them get automatically loaded by the bot when it starts up."
  [bot-name prefix folders]
  `(do
     ;; Loads all the clojure files in the folders supplied
     (doseq [folder# ~folders]
       (load-clojure-files-in-folder! folder#))

     ;; Opens a bot with those extensions
     (let [extensions# (get-registered-extensions)]
       (timbre/infof "Loaded %d extensions: %s."
                     (count extensions#)
                     (s/join ", " (map :command extensions#)))
       (with-open [discord-bot# (create-bot ~bot-name extensions# ~prefix)]
         (while true (Thread/sleep 3000))))))

;;; Builtin bot commands
(defonce doc-separator (s/join (repeat 100 "-")))

(defcommand help
  [_ _]
  "Look at help information for the available extensions."
  (let [doc-separator (s/join (repeat 100 "-"))
        command-docs  (s/join \newline
                              (for [[c d] @extension-docs]
                                (format "%s: %s" (name c) d)))]
    (if (seq command-docs)
      (pm (format "Commands:\n%s\n%s" command-docs doc-separator))
      (let [commands (s/join ", " (map (comp name :command) @extension-registry))]
        (pm (format "Commands: %s" commands))))))
