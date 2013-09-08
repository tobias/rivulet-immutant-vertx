(ns rivulet.bridge
  (:require [vertx.embed :as vembed :refer [with-vertx]]
            [vertx.eventbus :as eb]
            [vertx.http :as http]
            [vertx.http.sockjs :as sockjs]
            [immutant.messaging :as msg]
            [immutant.daemons :as daemon]))

(defonce vertx (vembed/vertx))

(defn dest->eventbus [dest address]
  (with-vertx vertx
    (msg/listen dest (partial eb/publish address))))

(defn eventbus->dest [address dest]
  (with-vertx vertx
    (eb/on-message address (partial msg/publish dest))))

(defonce daemon (atom nil))

(defn- start* [incoming-dests outgoing-dests listeners server]
  (swap! listeners assoc :eb-listeners 
         (mapv #(eventbus->dest (str %) %) incoming-dests))

  (swap! listeners assoc :msg-listeners 
         (mapv #(dest->eventbus % (str %)) outgoing-dests))
  
  (with-vertx vertx
    (reset! server (http/server))
    (-> @server
        (sockjs/sockjs-server)
        (sockjs/bridge {:prefix "/bridge"} [{}] [{}]))
    ;;TODO: use the bind interface from immutant?
    (http/listen @server 8081 "0.0.0.0")))

(defn- stop* [listeners server]
  (with-vertx vertx
    (mapv eb/unregister-handler (:eb-listeners @listeners))
    (mapv msg/unlisten (:msg-listeners @listeners))
    (when @server
      (.close @server))))

(defn start [& {:keys [incoming outgoing]}]
  (reset! daemon
          (let [listeners (atom {})
                server (atom nil)]
            (daemon/daemonize "bridge"
                              #(start* incoming
                                       outgoing
                                       listeners
                                       server)
                              #(stop* listeners
                                      server)))))

(defn stop []
  (if @daemon
    (.stop @daemon)))
