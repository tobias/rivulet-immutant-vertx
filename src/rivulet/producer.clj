(ns rivulet.producer
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [immutant.messaging :as msg]
            [immutant.jobs :as job]))

(defonce data (-> (io/resource "data") slurp (str/split #"\n")))

(defn start [output-dest]
  (job/schedule :producer
                #(msg/publish output-dest (rand-nth data))
                :every 200))

(defn stop []
  (job/unschedule :producer))
