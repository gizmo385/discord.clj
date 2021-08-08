(ns echo-prefix.core
  (:require
    [discord.extensions.core :as ext]
    [discord.extensions.utils :as ext-utils]))

(ext/install-prefix-commands!
  (ext/prefix-command
    :echo [gateway message & _]
    (ext-utils/reply-in-channel gateway message (:content message))))
