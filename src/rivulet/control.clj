(ns rivulet.control
  (:require [immutant.messaging :as msg]))

(defonce command-listener (atom nil))
(defonce sampler-listeners (atom {}))

(defn- match-stream [result-dest sampler data]
  (if (re-find (re-pattern sampler) data)
    (msg/publish result-dest [sampler data])))

(defn- remove-stream-matcher [sampler]
  (when-let [listener (@sampler-listeners sampler)]
    (msg/unlisten listener)
    (swap! sampler-listeners dissoc sampler)))

(defn- add-stream-matcher [stream-dest result-dest sampler]
  (if-not (@sampler-listeners sampler)
    (swap! sampler-listeners assoc sampler
           (msg/listen stream-dest
                       (partial match-stream result-dest sampler)))))

(defn- dispatch [stream-dest result-dest {:keys [command payload] :as msg}]
  (condp = command
    "add-sampler" (add-stream-matcher stream-dest result-dest payload)
    "delete-sampler" (remove-stream-matcher payload)))

(defn start [command-dest stream-dest result-dest]
  (reset! command-listener
          (msg/listen command-dest
                      (partial dispatch stream-dest result-dest))))

(defn stop []
  (when @command-listener
    (msg/unlisten @command-listener)
    (reset! command-listener nil))
  (doseq [sampler (keys @sampler-listeners)]
    (remove-stream-matcher sampler)))
