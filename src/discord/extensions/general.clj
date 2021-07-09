(ns discord.extensions.general
  (:require
    [clojure.string :as s]
    [discord.bot :as bot]
    [discord.components :as c]
    [discord.interactions :as i]
    [discord.embeds :as e]
    [discord.http :as http]
    [discord.utils :as utils]))

(defn rock-paper-scissors
  [user-choice]
  (if (some #{user-choice} [:rock :paper :scissors])
    (condp = [user-choice (rand-nth [:rock :paper :scissors])]
      ;; Tie choices
      [:rock :rock]           ":moyai: We're even! :moyai:"
      [:paper :paper]         ":newspaper: We're even! :newspaper:"
      [:scissors :scissors]   ":scissors: We're even! :scissors:"

      [:rock :paper]          ":newspaper: I win! :newspaper:"
      [:rock :scissors]       ":scissors: You win! :scissors:"

      [:paper :rock]          ":moyai: You win! :moyai:"
      [:paper :scissors]      ":scissors: I win! :scissors:"

      [:scissors :rock]       ":moyai: I win! :moyai:"
      [:scissors :paper]      ":newspaper: You win! :newspaper:")
    "Please choose either rock, paper, or scissors."))

(bot/install-modules!
  (bot/command
    :say [client message & _]
    (let [space-index (s/index-of (:content message) " ")]
      (if (some? space-index)
        (bot/reply-in-channel client message (subs (:content message) space-index))
        (bot/reply-in-channel
          client message "https://media2.giphy.com/media/L1W47cPwMyrUs7zEjP/giphy.gif"))))

  (bot/command
    :botsay [client message & _]
    (let [space-index (s/index-of (:content message) " ")]
      (bot/delete-original-message client message)
      (when (some? space-index)
        (bot/reply-in-channel client message (subs (:content message) space-index)))))

  (bot/command
    :working [client message]
    (bot/reply-in-channel client message "https://giphy.com/gifs/9K2nFglCAQClO"))

  (bot/command
    :roll
    ([client message]
     (bot/reply-in-channel client message (format ":game_die: %d :game_die:" (inc (rand-int 100)))))
    ([client message limit]
     (try (let [limit-num (Integer/parseInt limit)]
            (bot/reply-in-channel client message (format ":game_die: %d :game_die:"
                                                         (inc (rand-int limit-num))))) 
          (catch Exception e
            (bot/reply-in-channel client message "Please supply a number :)")))))

  (bot/command
    :choose [client message & choices]
    (if (not-empty choices)
      (bot/reply-in-channel client message (rand-nth choices))
      (bot/reply-in-channel client message "Error: Nothing to choose from")))

  (bot/command
    :servers [client message]
    (let [bot-servers (http/get-servers client)]
      (->> bot-servers
           (map :name)
           (s/join \newline)
           (format "Servers I'm a part of: \n%s")
           (bot/reply-in-channel client message))))

  (bot/command
    :flip [client message]
    (let [res (rand-nth ["Heads" "Tails"])]
      (bot/reply-in-channel client message (format "Flipping a coin....%s" res))))

  (bot/command
    :rps [client message user-choice]
    (bot/reply-in-channel client message (rock-paper-scissors (keyword user-choice))))

  (bot/command
    :count [client message number]
    (try
      (let [count-limit (Integer/parseInt number)]
        (doseq [n (range count-limit)]
          (bot/reply-in-channel client message (format "Number: %d" n))))
      (catch NumberFormatException _
        (bot/reply-in-channel client message "Error: Please supply a number!"))))
  (bot/command
    :components [client message]
    (let [components [(c/action-row
                        (c/primary-button :primary :label "Primary")
                        (c/secondary-button :secondary :label "Secondary")
                        (c/success-button :success :label "Success")
                        (c/danger-button :danger :label "Danger")
                        (c/link-button "http://google.com" :label "Google it"))
                      (c/action-row
                        (c/select-menu
                          :cool-menu
                          [(c/menu-option "Option 1" :option1 :description "Testing")
                           (c/menu-option "Option 2" :option2 :description "Testing")]
                          :placeholder-text "Select a cool option"))]]
      (bot/reply-in-channel client message "Hi" components))))

(defmethod c/handle-button-press :default
  [original-message gateway custom-id]
  (let [response (format "You interacted with %s" custom-id)]
    (i/defer-channel-message-response original-message gateway)
    (Thread/sleep 5000)
    (i/channel-message-response original-message gateway response nil nil)))

(defmethod c/handle-menu-selection :default
  [original-message gateway custom-id selected-values]
  (let [response (format "You selected [%s] from %s" selected-values custom-id)]
    (i/defer-update-message-response original-message gateway)
    (Thread/sleep 5000)
    (i/update-message original-message gateway response nil nil)))
