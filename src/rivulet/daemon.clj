(ns rivulet.daemon
  (:require [immutant.daemons :as daemon]
            [immutant.util :as util]
            [immutant.registry :as registry]
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
    (when-not (@state id)
      (swap! state assoc id
             (bridge/dest->eventbus vertx
                                    result-dest
                                    address
                                    (format "JMSCorrelationID='%s'" id))))))

(defn- start [state destinations {:keys [incoming outgoing]}]
  (let [vertx (bridge/create-vertx)]
    (swap! state assoc
           :vertx vertx
           :server
           (bridge/create-sockjs-server vertx
                                        (endpoint)
                                        :post-register
                                        (partial bridge-client-results
                                                 state vertx
                                                 (:result-dest destinations))))
    (mapv #(bridge/eventbus->dest vertx (str %) %)
          (map destinations incoming))
    (mapv #(bridge/dest->eventbus vertx % (str %))
          (map destinations outgoing))))

(defn- stop [state]
  (.close (:server @state))
  (.stop (:vertx @state)))

(defn init [destinations & {:as opts}]
  (let [state (atom {})]
    (daemon/daemonize "bridge"
                      #(start state destinations opts)
                      #(stop state))))

