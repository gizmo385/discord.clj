(ns discord.types.snowflake)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining Snowflakes
;;;
;;; Discord uses Snowflakes for their distinct IDs for messages, users, etc. The protocol,
;;; originally created by Twitter, is documented (as used by Discord) on their developer
;;; documentation here:
;;;   https://discordapp.com/developers/docs/reference#snowflakes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti ->snowflake
  (fn [s] (type s)))

(defmethod ->snowflake :default [v]
  (throw (ex-info (format "Cannot build Snowflake from %s" (type v))
                  {:value v :value-type (type v)})))
(defmethod ->snowflake java.lang.Long [l] l)
(defmethod ->snowflake java.lang.Integer [i] i)
(defmethod ->snowflake java.lang.String [s] (Long/parseLong s))
(defmethod ->snowflake nil [_] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Extracting information from Snowflakes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def discord-epoch
  "The Discord Epoch time, or the first second of 2015."
  1420070400000)

(defn snowflake->timestamp
  "Extracts the timestamp information from a Snowflake ID."
  [s]
  (some-> s ->snowflake (bit-shift-right 22) (+ discord-epoch)))

(defn snowflake->internal-worker-id
  "Extracts the internal worker Id from a Snowflake ID."
  [s]
  (let [worker-id-mask 0x3E0000]
    (some-> s ->snowflake  (bit-and worker-id-mask) (bit-shift-right 17))))

(defn snowflake->internal-process-id
  "Extracts the internal process Id from a Snowflake ID."
  [s]
  (let [process-id-mask 0x1F000]
    (some-> s ->snowflake  (bit-and process-id-mask) (bit-shift-right 12))))

(defn snowflake->increment
  "Extracts the 'Increment' value from a Snowflake ID."
  [s]
  (let [increment-mask 0xFFF]
    (some-> s ->snowflake  (bit-and increment-mask))))
