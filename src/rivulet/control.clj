(ns rivulet.control
  (:require [immutant.messaging :as msg]))

(defn- filter-stream [result-dest id filter data]
  (when (re-find (re-pattern filter) data)
    (msg/publish result-dest [filter data] :correlation-id id)))

(defn- filter-id [client-id filter]
  (str client-id filter))

(defn- remove-stream-filter [state client-id filter]
  (let [id (filter-id client-id filter)]
    (when-let [listener (@state id)]
      (msg/unlisten listener)
      (swap! state dissoc id))))

(defn- add-stream-filter [state stream-dest result-dest client-id filter]
  (let [id (filter-id client-id filter)]
    (when-not (@state id)
      (swap! state assoc id
             (msg/listen stream-dest
                         (partial filter-stream result-dest client-id filter))))))

(defn- dispatch [state stream-dest result-dest {:keys [command payload client-id]}]
  (condp = command
    "add-filter" (add-stream-filter state stream-dest result-dest client-id payload)
    "delete-filter" (remove-stream-filter state client-id payload)))

(defn init [{:keys [command-dest stream-dest result-dest]}]
  (let [state (atom {})]
    (msg/listen command-dest
                (partial dispatch state stream-dest result-dest))))

