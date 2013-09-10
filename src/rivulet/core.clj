(ns rivulet.core
  (:require [immutant.messaging :as msg]
            [rivulet.control :as control]
            [rivulet.producer :as producer]
            [rivulet.bridge :as bridge]
            [rivulet.web :as web]))

(def stream-dest "topic.stream")
(def command-dest "topic.commands")
(def result-dest "topic.matches")

(defn start []
  (mapv msg/start [stream-dest command-dest result-dest])
  (control/start command-dest stream-dest result-dest)
  (bridge/start :incoming [command-dest]
                :outgoing [result-dest stream-dest])
  (producer/start stream-dest)
  (web/start))

(defn stop []
  (web/stop)
  (producer/stop)
  (bridge/stop)
  (control/stop)
  (mapv msg/stop [stream-dest command-dest result-dest]))

