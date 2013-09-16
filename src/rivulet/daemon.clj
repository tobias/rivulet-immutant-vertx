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

(defn- bridge-client-results
  "Sets up a bridge between the results topic and the results address
   requested by the client, with a selector based on the client id."
  [state vertx result-dest address]
  (if-let [id (second (re-find #"^results\.(.*)$" address))]
    (when-not (@state id)
      (swap! state assoc id
             (bridge/dest->eventbus vertx
                                    result-dest
                                    address
                                    (format "JMSCorrelationID='%s'" id))))))

(defn- start
  "Starts the SockJS server, and bridges the requested incoming and
   outgoing destionations to like named addresses. Registers a hook on
   the SockJS server that bridges the requested result address to the
   results topic, filtered by the client-id."
  [state destinations
   {:keys [incoming outgoing]}]
  (let [vertx (bridge/create-vertx)]
    (swap! state assoc
           :vertx vertx
           :server
           (bridge/create-sockjs-server vertx
                                        (endpoint)
                                        :post-register
                                        (fn [_ address]
                                          (bridge-client-results
                                           state vertx
                                           (:result-dest destinations)
                                           address))))
    (mapv #(bridge/eventbus->dest vertx % %)
          (map destinations incoming))
    (mapv #(bridge/dest->eventbus vertx % %)
          (map destinations outgoing))))

(defn- stop [state]
  (.close (:server @state))
  (.stop (:vertx @state)))

(defn init [destinations & {:as opts}]
  (let [state (atom {})]
    (daemon/daemonize "bridge"
                      #(start state destinations opts)
                      #(stop state))))

