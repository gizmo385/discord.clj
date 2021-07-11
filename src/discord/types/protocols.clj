(ns discord.types.protocols)

;;; Discord uses Snowflakes for their distinct IDs for messages, users, etc. The protocol,
;;; originally created by Twitter, is documented (as used by Discord) on their developer
;;; documentation here:
;;;   https://discordapp.com/developers/docs/reference#snowflakes
(defprotocol Snowflake
  "Defining types which are Snowflakes according to the Discord API. These are objects which have
   unique IDs in the Discord API"
  (->snowflake [value] "Generate a Snowflake ID for the Discord APIs"))

;;; Several built-in types can be used as snowflakes
(extend-protocol Snowflake
  Integer
    (->snowflake [i] i)
  Long
    (->snowflake [l] l)
  String
    (->snowflake [s] s)
  nil
    (->snowflake [_]
      (println "AHHHHHHHHHHHHHHHH SNOWFLAKE-ABLE THING OF NIL RECIEVED")))
