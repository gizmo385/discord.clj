# discord.clj

discord.clj is a [Clojure](https://clojure.org/) wrapper around the [Discord
APIs](https://discordapp.com/developers/docs/intro). This library was written to ease in the
creation of Discord bots in Clojure.

This library is currently a work in progress and not production-ready in any sense of the term.

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
(ns bot.core
  (:require [clojure.tools.logging :as log]
            [discord.config :as config]
            [discord.types :as types]
            [discord.bot :as bot]
            [discord.gateway :as gw])
  (:import [discord.types ConfigurationAuth])
  (:gen-class))


(defmulti message-handler
  (fn [bot message] message))

(defmethod message-handler :default
  [bot message]
  (log/info message))

(defn -main
  "Spins up a new bot and reads messages from it"
  [& args]
  (with-open [discord-bot (bot/create-discord-bot message-handler)]
    (while true (Thread/sleep 3000))))

```

## License

Copyright © 2017 Christopher Chapline

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
