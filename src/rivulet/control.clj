(ns rivulet.control
  (:require [immutant.messaging :as msg]
            [rivulet.state :refer [remember! recall forget!]]))

(defonce state (atom {}))

(defn- listen [& args]
  (let [l (apply msg/listen args)]
    #(msg/unlisten l)))

(defn- filter-stream [result-dest id filter data]
  (when (re-find (re-pattern filter) data)
    (msg/publish result-dest [filter data] :correlation-id id)))

(defn- filter-id [client-id filter]
  (str client-id filter))

(defn- remove-stream-filter [client-id filter]
  (when-let [unlisten-fn (forget! state (filter-id client-id filter))]
    (unlisten-fn)))

(defn- add-stream-filter [stream-dest result-dest client-id filter]
  (let [id (filter-id client-id filter)]
    (when-not (recall state id)
      (remember! state id
                 (listen stream-dest
                         (partial filter-stream result-dest client-id filter))))))

(defn- dispatch [stream-dest result-dest {:keys [command payload client-id]}]
  (condp = command
    "add-filter" (add-stream-filter stream-dest result-dest client-id payload)
    "delete-filter" (remove-stream-filter client-id payload)))

(defn start [{:keys [command-dest stream-dest result-dest]}]
  (remember! state :command-listener
             (msg/listen command-dest
                         (partial dispatch stream-dest result-dest))))

(defn stop []
  (mapv #(%) (vals @state))
  (reset! state {}))
