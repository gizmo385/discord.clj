(defproject discord.clj "2.0.0"
  :description "A library for creating Discord bots in Clojure."
  :url "https://github.com/gizmo385/discord.clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; Core clojure libraries
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/data.json "0.2.6"]

                 ;; 3rd party libraries for
                 [clj-http "3.6.1"]
                 [clj-time "0.14.4"]

                 ;; Logging libraries
                 [com.taoensso/timbre "4.10.0"]

                 ;; Timing library used for heartbeats
                 [overtone/at-at "1.2.0"]

                 ;; Websocket connection manager
                 [stylefruits/gniazdo "1.0.0"]

                 ;; Libraries used for voice and playing audio
                 [com.sedmelluq/lavaplayer "1.3.17"]]
  :repositories [["jcenter" {:url "https://jcenter.bintray.com"}]]
  :main ^:skip-aot discord.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
