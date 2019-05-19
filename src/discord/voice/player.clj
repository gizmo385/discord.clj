(ns discord.voice.player
  (:import [com.sedmelluq.discord.lavaplayer.player DefaultAudioPlayerManager]
           [com.sedmelluq.discord.lavaplayer.player.event AudioEventAdapter]
           [com.sedmelluq.discord.lavaplayer.player AudioLoadResultHandler]
           [com.sedmelluq.discord.lavaplayer.source AudioSourceManagers]
           [com.sedmelluq.discord.lavaplayer.track.playback MutableAudioFrame]
           [java.util.concurrent TimeUnit LinkedBlockingQueue])
  (:require [taoensso.timbre :as timbre]
            [clojure.reflect :as r]
            [discord.constants :as const]))

(defprotocol AudioPlayer
  (queue-track [this track-identifier])
  (next-track [this])
  (current-track [this])
  (paused? [this])
  (track-info [this])
  (next-audio-frame [this] [this timeout time-unit]))

(defn new-track-scheduler
  [player queue]
  (proxy [AudioEventAdapter] []
    (onTrackEnd [player track end-reason]
      (timbre/infof "The track ended: %s" end-reason)
      (if (.mayStartNext end-reason)
        (.nextTrack this)))))

(defn new-load-result-handler
  [simple-audio-player track-identifier]
  (proxy [AudioLoadResultHandler] []
    (trackLoaded [track]
      (if (not (.startTrack (:player simple-audio-player) track true))
        (.offer (:queue simple-audio-player) track)))

    (playlistLoaded [playlist]
      (doseq [track (.getTracks playlist)]
        (.offer (:queue simple-audio-player) track)))

    (noMatches []
      (let [ex-message (format "Could not find any results for: %s" track-identifier)]
        (throw (ex-info ex-message {:track track-identifier}))))

    (loadFailed [exception]
      (timbre/errorf "Error loading audio results: %s" (.getMessage exception))
      (throw exception))))

(defrecord SimpleAudioPlayer
  [player-manager player track-scheduler queue]
  AudioPlayer
  (queue-track [this track-identifier]
    (let [result-handler (new-load-result-handler this track-identifier)]
      (.loadItem (:player-manager this) track-identifier result-handler)))

  (next-track [this]
    (let [next-in-queue (.poll (:queue this))]
      (.startTrack (:player this) next-in-queue false)))

  (current-track [this]
    (.getPlayingTrack (:player this)))

  (paused? [this]
    (.isPaused (:player this)))

  (track-info [this]
    (let [track (current-track this)
          info (.getInfo track)]
      {:raw-info info
       :title (.title info)
       :author (.author info)
       :length (/ (.length info) const/MILLISECONDS-IN-SECOND)
       :identifier (.identifier info)
       :stream? (.isStream info)
       :current-position (/ (.getPosition track) const/MILLISECONDS-IN-SECOND)
       :uri (.uri info)}))

  (next-audio-frame [this]
    (next-audio-frame this 5000 TimeUnit/MILLISECONDS))

  (next-audio-frame [this timeout time-unit]
    (.provide (:player this) timeout time-unit)))

(defn build-simple-audio-player []
  (let [player-manager (new DefaultAudioPlayerManager)
        music-queue (new LinkedBlockingQueue)]
    ;; This ensures that loading items can happen from various sources, such as local to
    ;; the bot and remote sources like YouTube or Vimeo
    (AudioSourceManagers/registerRemoteSources player-manager)
    (AudioSourceManagers/registerLocalSource player-manager)
    (let [player (.createPlayer player-manager)
          track-scheduler (new-track-scheduler player music-queue)]
      (.addListener player track-scheduler)
      (SimpleAudioPlayer. player-manager
                          player
                          track-scheduler
                          music-queue))))

(comment
  (let [player (build-simple-audio-player)
        audio-frame (new MutableAudioFrame)
        ease-my-mind-url "https://www.youtube.com/watch?v=UCKbw9OJIcg"
        ghost-love-score-url "https://www.youtube.com/watch?v=JYjIlHWBAVo"
        blakus-url "https://soundcloud.com/blakus-mfm/star-wars-the-drone-wars-corridor-digital-ost"]
    ;(.get (queue-track player ease-my-mind-url))
    ;(.get (queue-track player ghost-love-score-url))
    (.get (queue-track player blakus-url))
    ;(timbre/infof "Next track: %s" (next-track player))

    (timbre/infof "Active Track: %s" (current-track player))
    (timbre/infof "Paused? Track: %s" (paused? player))
    (timbre/infof "Current track info %s" (track-info player))
    (timbre/infof "First time frame: %s" (next-audio-frame player))
    )

  )