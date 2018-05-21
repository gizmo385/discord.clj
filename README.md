# discord.clj

[![Clojars Project](https://img.shields.io/clojars/v/discord.clj.svg)](https://clojars.org/discord.clj)

discord.clj is a [Clojure](https://clojure.org/) wrapper around the [Discord
APIs](https://discordapp.com/developers/docs/intro). This library was written to ease in the
creation of Discord bots in Clojure.

This library is currently a work in progress and not production-ready in any sense of the term. The goal is to implement a [fully-compliant](https://gist.github.com/meew0/bbbbd5348967dee5f7e84c0cd58983fd) Discord API wrapper. This library is heavily influenced by [discord.py](https://github.com/Rapptz/discord.py).

## Installation

Currently, there is no central installation location for discord.clj. I will deploy it to
[Clojars](https://clojars.org/) once it is more adaquately packaged.

## Usage

You can create a standalone JAR file for this by running the following:

```Shell
$ lein uberjar
$ java -jar ./target/uberjar/discord.clj-0.1.0-SNAPSHOT-standalone.jar
```

To run the core namespace which contains a basic bot framework, you can run the following:

```Shell
$ lein run
```

## Examples

Here is a dead-simple bot you can create using discord.clj:

```Clojure
(ns example.bot
  (:require [discord.bot :refer [say delete] :as bot]
            [discord.http :as http])
  (:gen-class))

;;; Example extension implementation for admin commands
(bot/defextension admin-extension [client message]
  ;; Kicks all users mentioned in the messagj
  (:kick
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id] message)]
        (http/kick client guild-id user-id))))

  ;; Sends a message to all servers the bot is a part of
  (:broadcast
    (let [bcast-message (->> message :content utils/words rest (s/join " "))
          servers (http/get-servers client)]
      (doseq [server servers]
        (http/send-message client server bcast-message)))))


(defn -main
  "Creates a new discord bot and supplies a series of extensions to it."
  [& args]
  (bot/open-with-extensions
    "TestDiscordBot" "^"
    :say    (fn [client message]
              (say (:content message)))
    :botsay (fn [client message]
              (say (:content message))
              (delete message))
    :greet  (fn [_ _]
              (say "HELLO EVERYONE"))
    :admin  admin-extension))
```

For more examples, check out the /docs folder for walkthroughs on how to create a bot.

## License

Copyright © 2017 Christopher Chapline

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
