# discord.clj

[![Clojars Project](https://img.shields.io/clojars/v/discord.clj.svg)](https://clojars.org/discord.clj)

discord.clj is a [Clojure](https://clojure.org/) wrapper around the [Discord
APIs](https://discordapp.com/developers/docs/intro). The overall goal of this library to painlessly
facilitate the creation of bots and custom bot extensions within Clojure.

## Library Philosophy

This library seeks to abstract most of the complexity out of the Discord API and gateway protocols.
While many of the underlying implementation protocols are available for extension should the need
arise, it is the goal of this library that most things should Just Work:tm: out of the box.

## Installation

The library is available on Clojars [here](https://clojars.org/discord.clj). You can install it using any of the following methods:

**Leiningen/Boot:**

```[discord.clj "2.0.0"]```

**Clojure CLI/deps.edn:**

```discord.clj {:mvn/version "2.0.0"}```

**Gradle:**

```compile 'discord.clj:discord.clj:2.0.0'```

**Maven:**

```xml
<dependency>
  <groupId>discord.clj</groupId>
  <artifactId>discord.clj</artifactId>
  <version>2.0.0</version>
</dependency>
```

## Quick Start Guide

To get started running a Discord bot, you'll want to do the following:

1. Create a [new application in Discord](https://discord.com/developers/applications)
1. In the "bot" settings for your Discord application, you'll want to retrieve your bot token.
   You'll be placing this in the `resources/bot-config.edn` file.
1. If you plan on using slash commands, you'll also want to retrieve the application ID for your bot
   and store that in the `resources/bot-config.edn` file as well.
1. Determine which intents you'll need from the Discord gateway. Discord has a good primer on
   gateway intents [in their
   documentation](https://discord.com/developers/docs/topics/gateway#gateway-intents). For the time
   being, a good default for handling messages is:
   ```clj
   [:guilds :guild-messages :direct-messages]
   ```
1. Start your bot with `lein run`!

At this point, your `bot-config.edn` file should look similar to [this
template](/resources/bot-config-template.edn).

## Examples

In addition to the [builtin commands](/src/discord/extensions/builtin), this project also ships with
a [series of examples](/examples) that you can include on startup. For example, to include the
`echo_prefix` command example, run the following command within the repo:

```
lein run -e examples/echo_prefix
```

## Compilation

You can create a standalone JAR file for this by running the following:

```Shell
$ lein uberjar
$ java -jar ./target/uberjar/discord.clj-2.0.0-standalone.jar
```

To run the core namespace which contains a basic bot framework, you can run the following:

```Shell
$ lein run
```


## License

Copyright © 2017-2021 Christopher Chapline

Distributed under the MIT License.
