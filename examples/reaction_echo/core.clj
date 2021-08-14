(ns reaction-echo.core
  (:require
    [discord.api.channels :as channels]
    [discord.extensions.reactions :as react]))

(defmethod react/handle-message-reaction-by-name :doggolul
  [reaction auth]
  (channels/create-reaction auth (:channel-id reaction) (:message-id reaction) (:emoji reaction)))
