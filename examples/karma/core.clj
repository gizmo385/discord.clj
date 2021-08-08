(ns karma.core
  (:require
    [clojure.string :as s]
    [discord.interactions.core :as i]
    [discord.interactions.slash :as slash]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining the slash command interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(slash/register-globally-on-startup!
  (slash/command
    :karma "Manage a simple karma system in your server!"
    (slash/sub-command
      :get "Get a user's karma"
      (slash/user-option :user "A user to retrieve Karma for."))
    (slash/sub-command
      :add "Give a user karma"
      (slash/user-option :user "The user to give karma to" :required? true)
      (slash/integer-option :amount "The amount of karma to give" :required? true))
    (slash/sub-command :top "See the top-10 karma users")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining the karma system
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def karma-store (atom {})) ; Guild -> User -> Karma
(def max-karma 5)
(def nice-try-gif-url "https://media.giphy.com/media/3o7aTpVyQCkQKfekVy/giphy.gif")

(defmethod slash/handle-slash-command-interaction [:karma :get]
  [{:keys [interaction arguments]} auth _]
  (let [guild-id (:guild-id interaction)
        user-id (or (:user arguments) (i/interaction->user-id interaction))]
    (if-let [karma (get-in @karma-store [guild-id user-id])]
      (i/channel-message-response interaction auth (format "<@%s> has `%s` karma!" user-id karma))
      (i/channel-message-response interaction auth (format "<@%s> has no karma!" user-id)))))

(defmethod slash/handle-slash-command-interaction [:karma :add]
  [{:keys [interaction arguments]} auth _]
  (let [guild-id (:guild-id interaction)
        amount (:amount arguments)
        recipient-user-id (:user arguments)
        sender-user-id (i/interaction->user-id interaction)
        current-karma (get-in @karma-store [guild-id recipient-user-id] 0)]
    (cond
      (> amount max-karma)
      (i/channel-message-response
        interaction auth (format "You can only give someone `%s` karma at once!" max-karma))

      (not (pos? amount))
      (i/channel-message-response interaction auth "You can't give someone negative karma!")

      (= recipient-user-id sender-user-id)
      (i/channel-message-response interaction auth nice-try-gif-url)

      :default
      (do
        (swap! karma-store assoc-in [guild-id recipient-user-id] (+ current-karma amount))
        (i/channel-message-response
          interaction
          auth
          (format "<@%s> gave %s karma to <@%s>!" sender-user-id amount recipient-user-id))))))

(defmethod slash/handle-slash-command-interaction [:karma :top]
  [{:keys [interaction]} auth _]
  (let [guild-id (:guild-id interaction) ]
    (->> guild-id
         (get @karma-store)
         (sort-by (fn [[u k]] [k u]))
         (reverse)
         (take 10)
         (map (fn [[u k]] (format "<@%s> - %s" u k)))
         (s/join \newline)
         (i/channel-message-response interaction auth))))
