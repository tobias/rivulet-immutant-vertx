(ns rivulet.bridge
  (:require [vertx.embed :as vembed :refer [with-vertx]]
            [vertx.eventbus :as eb]
            [vertx.http :as http]
            [vertx.http.sockjs :as sockjs]
            [immutant.messaging :as msg]))

(defn dest->eventbus
  "Sets up a bridge to copy messages from an Immutant messaging dest to a Vertx address.
   If a selector string is provided, it will be applied to the
   listener, and only messages matching the selector will be copied."
  ([vertx dest address]
     (dest->eventbus vertx dest address nil))
  ([vertx dest address selector]
     (msg/listen dest #(with-vertx vertx
                         (eb/publish address %))
                 :selector selector)))

(defn eventbus->dest
  "Sets up a bridge to copy messages from a Vertx address to an Immutant messaging dest."
  [vertx address dest]
  (with-vertx vertx
    (eb/on-message address (partial msg/publish dest))))

(def create-vertx vembed/vertx)

(defn create-sockjs-server
  "Start a SockJS server at endpoint, and bridge the EventBus across it."
  [vertx {:keys [host port path url]} & hooks]
  (println "Starting SockJS bridge at" url)
  (with-vertx vertx
    (let [server (http/server)]
      (-> server
          (sockjs/sockjs-server)
          (as-> sock-serv
                (apply sockjs/set-hooks sock-serv hooks))
          (sockjs/bridge {:prefix path} [{}] [{}]))
      (http/listen server port host))))
