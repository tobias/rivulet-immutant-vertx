(ns rivulet.daemon
  (:require [immutant.daemons :as daemon]
            [immutant.util :as util]
            [immutant.registry :as registry]
            [rivulet.state :refer [remember! recall forget!]]
            [rivulet.bridge :as bridge]))

(defn endpoint []
  (let [parts ["http"
               (util/public-interface-address)
               (-> :config registry/get :sockjs-port)
               "/bridge"]]
    (assoc (zipmap [:protocol :host :port :path] parts)
      :url (apply format "%s://%s:%s%s" parts))))

(defn- bridge-client-results [state vertx result-dest _ address]
  (if-let [id (second (re-find #"^results\.(.*)$" address))]
    (when-not (recall state id)
      (let [listener (bridge/dest->eventbus vertx
                                            result-dest
                                            address
                                            (format "JMSCorrelationID='%s'" id))]
        (remember! state :listeners listener)
        (remember! state id listener)))))

(defn- daemon-start [state destinations {:keys [incoming outgoing]}]
  (let [vertx (bridge/create-vertx)
        server (bridge/create-sockjs-server vertx
                                            (endpoint)
                                            :post-register
                                            (partial bridge-client-results
                                                     state vertx
                                                     (:result-dest destinations)))]
    (remember! state :vertx vertx)
    (remember! state :server server)
    (remember! state :listeners
               (mapv #(bridge/eventbus->dest vertx (str %) %)
                     (map destinations incoming)))
    (remember! state :listeners
               (mapv #(bridge/dest->eventbus vertx % (str %))
                     (map destinations outgoing)))))

(defn- daemon-stop [state]
  (when-let [listeners (forget! state :listeners)]
    (mapv #(%) listeners))
  (when-let [server (forget! state :server)]
    (.close server))
  (when-let [vertx (forget! state :vertx)]
    (.stop vertx)))

(defn- create-daemon [state destinations opts]
  (daemon/daemonize "bridge"
                    #(daemon-start state destinations opts)
                    #(daemon-stop state)))

(defonce daemon (atom nil))

(defn start [destinations & {:as opts}]
  (let [state (atom {})]
    (reset! daemon (create-daemon state destinations opts))))

(defn stop []
  (if @daemon
    (.stop @daemon)))
