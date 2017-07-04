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
(ns example.bot
  (:require [discord.bot :refer [say delete] :as bot]
            [discord.http :as http])
  (:gen-class))


(defn -main
  "Spins up a new client and reads messages from it"
  [& args]
  (bot/open-with-cogs
    "ExampleBot" "^"
    :say    (fn [client message]
              (say (:content message)))
    :botsay (fn [client message]
              (say (:content message))
              (delete message))
    :greet  (fn [_ _]
              (say "HELLO EVERYONE"))
    :admin  admin-cog))
```

## License

Copyright © 2017 Christopher Chapline

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
