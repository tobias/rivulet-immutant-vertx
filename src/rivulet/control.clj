(ns rivulet.control
  (:require [immutant.messaging :as msg]))

(defonce command-listener (atom nil))
(defonce filter-listeners (atom {}))

(defn- filter-stream [result-dest id filter data]
  (when (re-find (re-pattern filter) data)
    (msg/publish result-dest [filter data] :correlation-id id)))

(defn- remove-stream-filter [id filter]
  (let [id (str id filter)]
    (when-let [listener (@filter-listeners id)]
      (msg/unlisten listener)
      (swap! filter-listeners dissoc id))))

(defn- add-stream-filter [stream-dest result-dest client-id filter]
  (let [id (str client-id filter)]
    (when-not (@filter-listeners id)
      (swap! filter-listeners assoc id
             (msg/listen stream-dest
                         (partial filter-stream result-dest client-id filter))))))

(defn- dispatch [stream-dest result-dest {:keys [command payload client-id]}]
  (condp = command
    "add-filter" (add-stream-filter stream-dest result-dest client-id payload)
    "delete-filter" (remove-stream-filter client-id payload)))

(defn start [{:keys [command-dest stream-dest result-dest]}]
  (reset! command-listener
          (msg/listen command-dest
                      (partial dispatch stream-dest result-dest))))

(defn stop []
  (when @command-listener
    (msg/unlisten @command-listener)
    (reset! command-listener nil))
  (doseq [filter (keys @filter-listeners)]
    (remove-stream-filter filter)))
