# Introduction

There are 2 primary mechanisms by which you can define extensions in discord.clj, each serving a
different purpose for different use cases

1. Defining a command that exists on it's own
2. Defining an extension that is made up of a series of subcommands.

Both of these are discussed below. Additionally, there is also the ability to create more complex
message handlers, which is discussed [here](message-handlers.md).

For the purposes of this tutorial, we will assume that you are creating these extensions in a file
that is being loaded into your bot as defined in your configuration files. If you have not yet
started a bot, please refer to the [bot configuration](bot-configuration.md) tutorial for
information on how to get a bot up and running. Additionally, for simplicity we will make the
assumption that you are using `!` as your bot prefix.


# Command Definitions

There are many circumstances in which you need to define a command, but you want that command to
exist separate from a large extension infrastructure. As an example, let's say that you wanted to
create a command that would let you link a gif of the ["It's
working!"](https://www.youtube.com/watch?v=_ElWlUr_tw8) scene from Star Wars: Episode I - The
Phantom Menace.

![It's working!](https://media.giphy.com/media/9K2nFglCAQClO/giphy.gif)

To do that, we'll define the following command in our code

```Clojure
(ns discord.core
  (:require [discord.bot :as bot])

(bot/defcommand working
  [client message]
  "Posts the Star Wars Episode 1 'It's working' gif in the channel"
  (bot/say "https://giphy.com/gifs/9K2nFglCAQClO")
```

The first argument to `bot/defcommand` is going to be the name of the command that you want to
create. In this case, we're creating a command called working, meaning that you would invoke this
command from Discord by sending the message `!working` to a channel that you bot has read
permissions in.

The second argument to `bot/defcommand` is a vector declaring the names that we will bind the two
arguments to every extension to. When an extension is invoked in the bot, two arguments are passed
to the command:

1. The [Client](https://github.com/gizmo385/discord.clj/blob/develop/src/discord/client.clj) object,
   which facilitates communication with Discord and its APIs.

2. The
   [Message](https://github.com/gizmo385/discord.clj/blob/develop/src/discord/gateway.clj#L14-L17)
   object that was received by the bot, triggering the extension. There is a lot of information
   available inside of the Message object:

| Message Field | Description |
|---|---|
| content | The text of the message that was received, **minus the name of the command**. |
| attachments | Any attachments that were included with the message |
| embeds | Any Embed objects that were sent †. |
| channel | An object repesenting the channel in which the message was sent * |
| author | An object representing the user that sent the message. ** |
| user-mentions | Any users that were mentioned in the message |
| role-mentions | Any roles that were mentioned in the message |
| pinned? | A boolean indicating if the message has been pinned |
| everyone-mentioned? | A boolean indicating whether or not `@everyone` was mentioned. |
| id | The unique Snowflake ID  for the message |

†: Discussion of how to create
[Embed](https://github.com/gizmo385/discord.clj/blob/master/src/discord/embeds.clj) objects is
available [here](embeds.md).

\* Definition of the
[Channel](https://github.com/gizmo385/discord.clj/blob/master/src/discord/http.clj#L56) object.

\*\* Definition of the
[User](https://github.com/gizmo385/discord.clj/blob/master/src/discord/http.clj#L36-L37) object.

The  third argument to `bot/defcommand` is optional documentation for the command that you're
defining. The bot comes with a builtin `help` command that will PM the user with information about
all of the available commmands.

The rest of the `bot/defcommand` body is the command. This body is executed inside of an implicit
`do`, meaning you can supply multiple operations for the command to perform.

In the body of our command definition, we're using a shortcut defined by the library: `bot/say`.
This helper function simplifies the act of sending messages to the server by ensuring that it is
sent to the correct channel and putting it onto the asynchronous channel being processed to send
messages out. There are 2 other helper functions provided by the `bot` namespace that are worth
mentioning:

* `bot/pm`: This will send a private message back to the user who send the message triggering the
  your command/extension. The call format for this is the same as the calling format for `bot/say`.
* `bot/delete`: This will delete a message. Instead of sending text or an
[Embed](https://github.com/gizmo385/discord.clj/blob/master/src/discord/embeds.clj#L19-L20) object
like you might for `bot/say` or `bot/pm`, the `bot/delete` call expects to be given the entire
message object, so that it can access the ID necessary to delete the message.


# Extension Definitions

Extensions allow you to essentially define a grouping of similar commands under a similar header.
For example, say you're defining a series of commands that are used by admins and you want all of
those admin commands to take the form of `!admin ban`, `!admin kick`, etc. To achieve that, you'll
want to use an extension. Here is an example of how you might do that:

```Clojure
(ns discord.core
  (:require [discord.bot :as bot]
            [discord.http :as http])


(bot/defextension admin [client message]
  "Commands for common server adminstrative tasks."
  (:kick
    "Kicks the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id])]
        (http/kick client guild-id user-id))))

  (:ban
    "Bans the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [guild-id (get-in message [:channel :guild-id])]
        (http/ban client guild-id user)))))
```

Most of the lessons that we learned when defining commands above extends directly into extension
definitions. The arguments are almost identical, the only difference is that the body of
`bot/defextension` expects each form below it to be what is essentially a self contained command
definition. It is important to note that the `[client message]` argument definition that is defined
after the name of the extension extends into *all* of the defined subcommands, so you don't need to
repeatedly redeclare those. Outside of that small difference, you can define each of the subcommands as you might define
a single command.

Now you might be looking at this extension and be wondering, "Hey we can't have random users calling
`!admin ban <officer-name>` and starting mob rule in my server!". And yes strawman developer you're
absolutely right! I would encourage you to check out the [bot permissions
documentation](permissions.md) for information on how to restrict which users can perform what
commands on your bot!

The last thing that I want to draw attention to in this extension definition is the inclusion and
use of the `http` namespace. The
[http](https://github.com/gizmo385/discord.clj/blob/master/src/discord/http.clj) namespace contains
helpers functions for most of the exposed Discord API endpoints and can be used to perform a whole
slew of tasks, including guild, channel, and user maintenance.

# Extension Setting Management

TODO
