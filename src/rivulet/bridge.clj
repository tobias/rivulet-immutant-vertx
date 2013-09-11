(ns rivulet.bridge
  (:require [vertx.embed :as vembed]
            [vertx.eventbus :as eb]
            [vertx.http :as http]
            [vertx.http.sockjs :as sockjs]
            [immutant.messaging :as msg]
            [immutant.util :as util]
            [immutant.registry :as registry]
            [immutant.daemons :as daemon]))

(defonce vertx (atom nil))

(defmacro with-vertx [& body]
  `(vembed/with-vertx @vertx ~@body))

(defn endpoint []
  (let [parts ["http"
               (util/public-interface-address)
               (-> :config registry/get :sockjs-port)
               "/bridge"]]
    (assoc (zipmap [:protocol :host :port :path] parts)
      :url (apply format "%s://%s:%s%s" parts))))

(defn dest->eventbus
  ([dest address]
     (dest->eventbus dest address nil))
  ([dest address selector]
     (with-vertx 
       (msg/listen dest (partial eb/publish address) :selector selector))))

(defn eventbus->dest [address dest]
  (with-vertx
    (eb/on-message address (partial msg/publish dest))))

(defn- remember! [state key items]
  (swap! state update-in [key] concat (if (coll? items) items [items])))

(defn- command-watcher [listeners result-dest {:keys [command payload client-id]}]
  (when (and (= "add-filter" command)
             (not (@listeners client-id)))
    (let [listener (dest->eventbus result-dest
                                   (str "results." client-id)
                                   (format "JMSCorrelationID='%s'" client-id))]
      (remember! listeners :messaging listener)
      (remember! listeners client-id listener))))

(defn- start* [listeners server destinations {:keys [incoming outgoing]}]
  (reset! vertx (vembed/vertx))
  (remember! listeners :eventbus 
             (mapv #(eventbus->dest (str %) %)
                   (map destinations incoming)))
  (remember! listeners :messaging
             (mapv #(dest->eventbus % (str %))
                   (map destinations outgoing)))
  (remember! listeners :messaging
             (msg/listen (:command-dest destinations)
                         (partial command-watcher
                                  listeners
                                  (:result-dest destinations))))
  (let [endpoint (endpoint)]
    (println "Starting SockJS bridge at" (:url endpoint))
    (with-vertx 
      (reset! server (http/server))
      (-> @server
          (sockjs/sockjs-server)
          (sockjs/bridge {:prefix (:path endpoint)} [{}] [{}]))
      (http/listen @server (:port endpoint) (:host endpoint)))))

(defn- stop* [listeners server]
  (with-vertx
    (mapv eb/unregister-handler (:eventbus @listeners))
    (mapv msg/unlisten (:messaging @listeners))
    (when @server
      (.close @server))))

(defn- create-daemon [listeners server destinations opts]
  (daemon/daemonize "bridge"
                    #(start* listeners server destinations opts)
                    #(stop* listeners server)))

(defonce daemon (atom nil))

(defn start [destinations & {:as opts}]
  (let [listeners (atom {})
        server (atom nil)]
    (reset! daemon (create-daemon listeners server destinations opts))))

(defn stop []
  (if @daemon
    (.stop @daemon)))
