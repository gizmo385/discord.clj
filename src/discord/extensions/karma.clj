(ns discord.extensions.karma
  (:require [clojure.string :as s]
            [taoensso.timbre :as timbre]
            [discord.bot :as bot]
            [discord.config :as config]
            [discord.embeds :as embed]
            [discord.permissions :as perm]))

;;; Create the configuraton file if necessary
(defonce karma-file "data/karma/karma.json")
(config/file-io karma-file :check)

;;; This is where we'll maintain the user's karma
(defonce user-karma
  (atom (config/file-io karma-file :load :default {})))

;;; This is the pattern that messages much match to alter the karma in the channel
(defonce karma-message-pattern
  (re-pattern "^<@(?<user>\\d+)>\\s*[+-](?<deltas>[+-]+)\\s*"))

(bot/defextension karma [client message]
  (:get
    "Retrieve the karma for mentioned users."
    (let [users  (map :id (:user-mentions message))
          karmas (for [user users] (format "<@%s>: %s" user (get @user-karma user 0)))]
      (bot/say (format "Karma: \n%s" (s/join \newline karmas)))))

  (:top
    "Retrieves the karma of the top 10 users."
    (let [users         (take 10 (sort-by val > @user-karma))
          message-body  (for [[user karma] users] (format "<@%s>: %s" (name user) karma))]
      (bot/say (format "Highest karma users:\n%s" (s/join \newline message-body)))))

  (:bottom
    "Retrieves the karma of the bottom 10 users."
    (let [users         (take 10 (sort-by val < @user-karma))
          message-body  (for [[user karma] users] (format "<@%s>: %s" (name user) karma))]
      (bot/say (format "Highest karma users:\n%s" (s/join \newline message-body)))))

  (:clear
    "Clear the karma of all users in the channel."
    {:requires [perm/ADMINISTRATOR]}
    (reset! user-karma {})))

(defn- update-karma! [user karma-delta]
  (let [current-karma (get @user-karma user 0)
        updated-karma (+ current-karma karma-delta)]
    (swap! user-karma assoc user updated-karma)
    (config/file-io karma-file :save :data @user-karma)
    updated-karma))

(defn- calculate-karma-delta
  "Given a string of '+' and '-' characters, determines the overall karma impact."
  [delta-string]
  (apply + (for [delta delta-string] (case (str delta) "+" 1 "-" -1))))

(bot/defhandler karma-message-handler [prefix client message]
  (if-let [[match user-id deltas] (re-find karma-message-pattern (:content message))]
    (let [user-karma-delta  (calculate-karma-delta deltas)
          new-user-karma    (update-karma! user-id user-karma-delta)]
      (bot/say (format "Updating <@%s>'s karma to %s" user-id new-user-karma)))))
