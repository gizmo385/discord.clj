# discord.clj

[![Clojars Project](https://img.shields.io/clojars/v/discord.clj.svg)](https://clojars.org/discord.clj)

discord.clj is a [Clojure](https://clojure.org/) wrapper around the [Discord
APIs](https://discordapp.com/developers/docs/intro). This library was written to ease in the
creation of Discord bots in Clojure.

The goal is to implement a [fully-compliant](https://gist.github.com/meew0/bbbbd5348967dee5f7e84c0cd58983fd) Discord API wrapper. This library is heavily influenced by [discord.py](https://github.com/Rapptz/discord.py). I'm working on constantly improving the feature set in this library would welcome contributors who want to help make that a reality :)!

## Documentation

For documentation, please check [the docs](/docs), where I'm compiling tutorials on the various features available in discord.clj!

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
