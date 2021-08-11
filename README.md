# discord.clj

**IMPORTANT NOTE**: Most new development on this project is happening on the [version 3.0 branch](https://github.com/gizmo385/discord.clj/tree/version3.0), which has support for interactions (slash commands, user/message commands) as well as message components. However, that branch does include a large number of changes that I believe simplify the library overall, but are indeed breaking changes for existing extensions.

[![Clojars Project](https://img.shields.io/clojars/v/discord.clj.svg)](https://clojars.org/discord.clj)

discord.clj is a [Clojure](https://clojure.org/) wrapper around the [Discord
APIs](https://discordapp.com/developers/docs/intro). This library was written to ease in the
creation of Discord bots in Clojure.

The goal is to implement a [fully-compliant](https://gist.github.com/meew0/bbbbd5348967dee5f7e84c0cd58983fd) Discord API wrapper. This library is heavily influenced by [discord.py](https://github.com/Rapptz/discord.py). I'm working on constantly improving the feature set in this library would welcome contributors who want to help make that a reality :)!

## Documentation

For information about the various features in discord.clj, please check [the docs](/docs):
 * [Getting Started & Bot Configuration](/docs/bot-configuration.md)
 * [Creating Extensions](/docs/extensions.md)
 * [Message Handlers](/docs/message-handlers.md)
 * [Message Embeds](/docs/embeds.md)
 * [Extension Permissions](/docs/permissions.md)

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

Copyright © 2017 Christopher Chapline

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
