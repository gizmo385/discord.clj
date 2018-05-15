(ns discord.cogs.no-swearing
  (:require [clojure.core.async :refer [go >!] :as async]
            [clojure.string :refer [starts-with?] :as s]
            [discord.bot :as bot]
            [discord.config :as config]
            [discord.constants :as const]
            [discord.http :as http]
            [discord.utils :as utils]))

;;; Define the list of blocked words
(defonce BLOCKED-WORDS
  ["fuck", "shit", "bitch"])

(defn- check-message [message-text]
  (let [message (s/lower-case message-text)]
    (some true?
          (for [blocked-word BLOCKED-WORDS]
            (s/includes? message blocked-word)))))

(bot/defhandler alias-message-handler [prefix client message]
  (let [message-content (:content message)
        message-channel (:channel message)
        send-channel (:send-channel client)
        needs-deletion? (check-message message-content)]
    (if needs-deletion?
      (do
        (http/delete-message client message-channel message)
        (go (>! send-channel {:channel message-channel
                              :content "Naughty, naughty! No swearing allowed! :see_no_evil:"
                              :options {}}))))))
