(ns rivulet.bridge
  (:require [vertx.embed :as vembed]
            [vertx.eventbus :as eb]
            [vertx.http :as http]
            [vertx.http.sockjs :as sockjs]
            [immutant.messaging :as msg]
            [immutant.util :as util]
            [immutant.daemons :as daemon]))

(defonce vertx (atom nil))

(defmacro with-vertx [&body]
  `(vembed/with-vertx @vertx ~@body))

(defn dest->eventbus [dest address]
  (with-vertx 
    (msg/listen dest (partial eb/publish address))))

(defn eventbus->dest [address dest]
  (with-vertx
    (eb/on-message address (partial msg/publish dest))))

(defonce daemon (atom nil))

(defn- start* [incoming-dests outgoing-dests listeners server]
  (reset! vertx (vembed/vertx))
  
  (swap! listeners assoc :eb-listeners 
         (mapv #(eventbus->dest (str %) %) incoming-dests))

  (swap! listeners assoc :msg-listeners 
         (mapv #(dest->eventbus % (str %)) outgoing-dests))
  
  (with-vertx 
    (reset! server (http/server))
    (-> @server
        (sockjs/sockjs-server)
        (sockjs/bridge {:prefix "/bridge"} [{}] [{}]))
    (http/listen @server 8081 (util/public-interface-address))))

(defn- stop* [listeners server]
  (with-vertx
    (mapv eb/unregister-handler (:eb-listeners @listeners))
    (mapv msg/unlisten (:msg-listeners @listeners))
    (when @server
      (.close @server))))

(defn- create-daemon [{:keys [incoming outgoing]} listeners server]
  (daemon/daemonize "bridge"
                    #(start* incoming outgoing listeners server)
                    #(stop* listeners server)))

(defn start [& {:as opts}]
  (let [listeners (atom {})
        server (atom nil)]
    (reset! daemon (create-daemon opts listeners server))))

(defn stop []
  (if @daemon
    (.stop @daemon)))
