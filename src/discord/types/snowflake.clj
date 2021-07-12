(ns discord.types.snowflake)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining Snowflakes
;;;
;;; Discord uses Snowflakes for their distinct IDs for messages, users, etc. The protocol,
;;; originally created by Twitter, is documented (as used by Discord) on their developer
;;; documentation here:
;;;   https://discordapp.com/developers/docs/reference#snowflakes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Snowflake [id])

(defmulti build-snowflake
  (fn [s] (type s)))

(defmethod build-snowflake :default [v]
  (throw (ex-info (format "Cannot build Snowflake from %s" (type v))
                  {:value v :value-type (type v)})))
(defmethod build-snowflake java.lang.Long [l] (Snowflake. l))
(defmethod build-snowflake java.lang.String [s] (Snowflake. (java.lang.Long/parseLong s)))
(defmethod build-snowflake nil [_] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Extracting information from Snowflakes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def discord-epoch
  "The Discord Epoch time, or the first second of 2015."
  1420070400000)

(defn snowflake->timestamp
  "Extracts the timestamp information from a Snowflake ID."
  [s]
  (some-> s :id (bit-shift-right 22) (+ discord-epoch)))

(defn snowflake->internal-worker-id
  "Extracts the internal worker Id from a Snowflake ID."
  [s]
  (let [worker-id-mask 0x3E0000]
    (some-> s :id (bit-and worker-id-mask) (bit-shift-right 17))))

(defn snowflake->internal-process-id
  "Extracts the internal process Id from a Snowflake ID."
  [s]
  (let [process-id-mask 0x1F000]
    (some-> s :id (bit-and process-id-mask) (bit-shift-right 12))))

(defn snowflake->increment
  "Extracts the 'Increment' value from a Snowflake ID."
  [s]
  (let [increment-mask 0xFFF]
    (some-> s :id (bit-and increment-mask))))
