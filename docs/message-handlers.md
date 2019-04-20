# Introduction

There are a number of circumstances in which you might want to define custom behavior for your bot that doesn't fit into the confines of the extension framework. For example, you may want your bot to detect foul content or give your users the ability to add and use aliases in the bot. In either of these cases, you need your bot to examine every message coming to the channel and check if it matches certain characteristics. These are the kind of use cases that custom message handlers are designed to assist with.

# A Simple Message Handler Example

Let's breakdown a simplified version of the
[karma extension supplied with the bot](/src/discord/extensions/karma.clj):

```Clojure
(ns discord.extensions.karma
  (:require [clojure.string :as s]
            [discord.bot :as bot]))

;;; This is where we'll maintain the user's karma
(defonce user-karma
  (atom {}))

;;; This is the pattern that messages much match to alter the karma in the channel
(defonce karma-message-pattern
  (re-pattern "^<@(?<user>\\d+)>\\s*[+-](?<deltas>[+-]+)\\s*"))

(bot/defextension karma [client message]
  (:get
    (let [users  (map :id (:user-mentions message))
          karmas (for [user users] (format "<@%s>: %s" user (get @user-karma user 0)))]
      (bot/say (format "Karma: \n%s" (s/join \newline karmas)))))

  (:clear
    "Clear the karma of all users in the channel."
    (reset! user-karma {})))

(bot/defhandler karma-message-handler [prefix client message]
  (if-let [[match user-id deltas]  (re-find karma-message-pattern (:content message))]
    (let [user-karma-delta  (apply + (for [delta deltas] (case (str delta) "+" 1 "-" -1)))
          current-karma     (get @user-karma user-id 0)
          new-user-karma    (+ current-karma user-karma-delta)]
      (swap! user-karma assoc user-id new-user-karma)
      (bot/say (format "Updating <@%s>'s karma to %s" user-id new-user-karma)))))
```

The `defhandler` macro in the `bot` namespace expects a vector which will be used for the 3 arguments passed to the handler. Those arguments will be:

* The bot's prefix (ex: `!`).
* The DiscordClient that is connected to the Discord gateway.
* The Message that was received.

These 3 arguments give us all the flexibility that we need, giving us all the same power that we've had in our conventional expressions, but with the added benefit of being able to scan every message received by the bot instead of just those that have been to match some predefined command. In the case of the karma extension, we have defined a pretty simple message handler that only performs a few operations:

1. It scans each message to determine if it matches a regular expression. This regular expression is basically looking for messages that look something like this: `@user#1234+++`
2. It updates global user karma table based on the number of `+` or `-` characters in the message.

For some more examples of message handlers, take a look at the
[alias extension](/src/discord/extensions/alias.clj) or the
[block extension](/src/discord/extensions/block.clj).
