(ns rivulet.core
  (:require [immutant.messaging :as msg]
            [rivulet.control :as control]
            [rivulet.producer :as producer]
            [rivulet.bridge :as bridge]
            [rivulet.web :as web]))

(def destinations {:stream-dest "topic.stream"
                   :command-dest "topic.commands"
                   :result-dest "topic.matches"})

(defn start []
  (mapv msg/start (vals destinations))
  (control/start destinations)
  (bridge/start destinations
                :incoming [:command-dest]
                :outgoing [:stream-dest])
  (producer/start (:stream-dest destinations))
  (web/start))

(defn stop []
  (web/stop)
  (producer/stop)
  (bridge/stop)
  (control/stop)
  (mapv msg/stop (vals destinations)))

