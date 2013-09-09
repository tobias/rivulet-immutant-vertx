(ns rivulet.control
  (:require [immutant.messaging :as msg]))

(defonce command-listener (atom nil))
(defonce filter-listeners (atom {}))

(defn- filter-stream [result-dest filter data]
  (if (re-find (re-pattern filter) data)
    (msg/publish result-dest [filter data])))

(defn- remove-stream-filter [filter]
  (when-let [listener (@filter-listeners filter)]
    (msg/unlisten listener)
    (swap! filter-listeners dissoc filter)))

(defn- add-stream-filter [stream-dest result-dest filter]
  (if-not (@filter-listeners filter)
    (swap! filter-listeners assoc filter
           (msg/listen stream-dest
                       (partial filter-stream result-dest filter)))))

(defn- dispatch [stream-dest result-dest {:keys [command payload] :as msg}]
  (condp = command
    "add-filter" (add-stream-filter stream-dest result-dest payload)
    "delete-filter" (remove-stream-filter payload)))

(defn start [command-dest stream-dest result-dest]
  (reset! command-listener
          (msg/listen command-dest
                      (partial dispatch stream-dest result-dest))))

(defn stop []
  (when @command-listener
    (msg/unlisten @command-listener)
    (reset! command-listener nil))
  (doseq [filter (keys @filter-listeners)]
    (remove-stream-filter filter)))
