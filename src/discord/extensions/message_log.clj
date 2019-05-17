(ns discord.extensions.message-log
  (:require [discord.bot :as bot]
            [discord.permissions :as perm]
            [discord.utils :as utils]
            [discord.config :as config]
            [discord.constants :as const]
            [taoensso.timbre :as timbre]
            [clj-time.coerce :as c]
            [clj-time.format :as f]))

(defonce settings-file "data/message_log/settings.json")
(config/file-io settings-file :check)

(defonce message-log-settings (atom {}))
(defonce last-loaded-settings (atom nil))

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
    @message-log-settings
    (let [loaded-settings (config/file-io settings-file :load :default {})]
      (reset! message-log-settings loaded-settings)
      (reset! last-loaded-settings (System/currentTimeMillis))
      loaded-settings)))


(defn logging-channel? [channel]
  (let [destination-file (get-in @message-log-settings [channel :file])
        logging-enabled (get-in @message-log-settings [channel :enabled])]
    (and destination-file logging-enabled)))

(bot/defextension messagelog [client message]
  "Manage the settings for the message logger"
  (:stop
    "Stops the message logger for this channel."
    {:requires [perm/MANAGE-CHANNELS]}
    (let [message-channel (:channel message)
          new-settings (assoc-in (get-settings) [message-channel :enabled] false)]
      (reset! message-log-settings new-settings)
      (config/file-io settings-file :save :data new-settings)
      (bot/say ":x: Disabling message logging :x:")))
  (:start
    "Starts the message logger for this channel."
    {:requires [perm/MANAGE-CHANNELS]}
    (let [message-channel (:channel message)
          new-settings (assoc-in (get-settings) [message-channel :enabled] true)]
      (reset! message-log-settings new-settings)
      (config/file-io settings-file :save :data new-settings)
      (bot/say ":white_check_mark: Enabling message logging :white_check_mark:")))
  (:status
    "Checks the status of logging the current channel."
    (if (logging-channel? (:channel message))
      (bot/say "Status: :eyes: Logging messages :eyes:")
      (bot/say "Status: :x: Not logging messages :x:")))
  (:file
    "Sets the name of the file to which messages are logged."
    {:requires [perm/MANAGE-CHANNELS]}
    (let [message-channel (:channel message)
          filename (-> message :content utils/words second)
          new-settings (assoc-in (get-settings) [message-channel :file] filename)]
      (reset! message-log-settings new-settings)
      (config/file-io settings-file :save :data new-settings)
      (bot/say (format "Logging messages from this channel to: %s" filename)))))

(bot/defhandler message-logger [_ client message]
  (let [message-channel (:channel message)
        destination-file (get-in @message-log-settings [message-channel :file])
        logging-enabled (get-in @message-log-settings [message-channel :enabled])
        iso-formatter (f/formatters :date-hour-minute-second-ms)
        send-time (->> (:send-time message)
                       (c/from-long)
                       (f/unparse iso-formatter))
        log-message (format "%s/%s @ %s: %s\n"
                            (-> message :channel :name)
                            (-> message :author :username)
                            send-time
                            (:content message))]
    (if (and destination-file logging-enabled)
      (spit destination-file log-message :append true))))
