(ns discord.embeds
  "This namespace contains functions to facilitate the creation of Embeds, which allow for richer
   messages to be sent by the bot to users."
  (:require [clj-time.format :as f]
            [clj-time.coerce :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Embedded Fields
;;;
;;; An embed in a message can contain a number of things, each of which has optional properties
;;; that can be present of missing. Below is a series of records that captures these.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord EmbedFooter [text icon_url proxy_icon_url])
(defrecord EmbedField [name value inline])
(defrecord EmbedVideo [url height width])
(defrecord EmbedImage [url proxy_url height width])
(defrecord EmbedProvider [name url])
(defrecord EmbedAuthor [name url icon_url proxy_icon_url])
(defrecord EmbedThumbnail [url proxy_url height width])

(defrecord Embed [title type description url timestamp color footer image thumbnail video
                  provider author fields])

(defn- get-iso-timestamp []
  (let [iso-formatter   (f/formatters :date-hour-minute-second-ms)]
    (->> (System/currentTimeMillis)
         (c/from-long)
         (f/unparse iso-formatter))))

(defn create-embed
  "Creates an Discord embed object that can be included in a message. These are built by
   successively applying modifier functions."
  [& {:keys [title description url color] :as embed-options}]
  (let [timestamp (get-iso-timestamp)]
    (map->Embed
      {:title        title
       :type         "rich"
       :description  description
       :url          url
       :color        color
       :timestamp    timestamp})))

(defn +footer
  "Sets the footer in the supplied message embed."
  [embed text & {:keys [icon-url proxy-icon-url]}]
  (assoc embed :footer (EmbedFooter. text icon-url proxy-icon-url)))

(defn +field
  "Adds a field to the supplied message embed."
  [embed name value & {:keys [inline] :or {inline false}}]
  (update embed :fields conj (EmbedField. name value inline)))

(defn +video
  "Adds a video to the embed message"
  [embed & {:keys [url height width]}]
  (assoc embed :video (EmbedVideo. url height width)))

(defn +image
  "Adds an image to the embed message"
  [embed & {:keys [url proxy-url height width]}]
  (assoc embed :image (EmbedImage. url proxy-url height width)))

(defn +provider
  "Sets the provider in the supplied message embed."
  [embed & {:keys [name url]}]
  (assoc embed :provider (EmbedProvider. name url)))

(defn +author
  "Sets the author in the supplied message embed."
  [embed & {:keys [name url icon-url proxy-icon-url]}]
  (assoc embed :author (EmbedAuthor. name url icon-url proxy-icon-url)))

(defn +thumbnail
  "Sets the thumbnail in the supplied message embed."
  [embed & {:keys [url proxy-url height width]}]
  (assoc embed :thumbnail (EmbedThumbnail. url proxy-url height width)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Converting Embeds into maps for the Discord API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- prune-into-map
  "Removes keys from a map that have nil valules."
  [m]
  (->> m
       (into {})
       (remove (comp nil? second))
       (into {})))

(defn embed->map
  "Converts the Embed record into a map with no nil valued fields."
  [embed]
  (as-> embed embed
    (if (:image embed)      (assoc embed :image (prune-into-map (:image embed)))          embed)
    (if (:author embed)     (assoc embed :author (prune-into-map (:author embed)))        embed)
    (if (:video embed)      (assoc embed :video (prune-into-map (:video embed)))          embed)
    (if (:provider embed)   (assoc embed :provider (prune-into-map (:provider embed)))    embed)
    (if (:thumbnail embed)  (assoc embed :thumbnail (prune-into-map (:thumbnail embed)))  embed)
    (if (:fields embed)     (assoc embed :fields (map prune-into-map (:fields embed)))    embed)
    (prune-into-map embed)))

(defn embed?
  "Returns whether or not 'maybe-embed' is an Embed record."
  [maybe-embed]
  (instance? Embed maybe-embed))
