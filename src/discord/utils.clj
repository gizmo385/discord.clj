(ns discord.utils
  "Simple utility functions for use across the codebase.")

(defn rgb->integer
  "Given red/green/blue values, converts those to an integer representing that color."
  [r g b]
  (if (and (< 0 r 256) (< 0 g 256) (< 0 b 256))
    (-> r
        (bit-shift-left 8)
        (+ g)
        (bit-shift-left 8)
        (+ b))
    (throw (ex-info "RGB values must be between 0 and 255 (inclusive)"))))

(defn index-of
  "Calculates the index of a value v within a collection coll, using `.indexOf`."
  [coll v]
  (.indexOf coll v))
