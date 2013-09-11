(ns rivulet.client
  (:require [enfocus.core :as ef]
            [enfocus.events :as events]
            [vertx.client.eventbus :as eb]
            [cljs-uuid.core :as uuid])
  (:require-macros [enfocus.macros :as em]))

(def eb (atom nil))

(def client-id (.-uuid (uuid/make-random)))

(defn open-eventbus [on-open]
  (reset! eb (eb/eventbus js/sockjs_endpoint))
  (eb/on-open @eb #(.log js/console "eventbus opened"))
  (eb/on-open @eb on-open))

(defn send-command [command payload]
  (eb/publish @eb "topic.commands"
              {:command command :client-id client-id :payload payload}))

(defn filter-selector [filter]
  (format "div.filter[data-filter=\"%s\"]" filter))

(defn delete-filter [filter]
  (send-command "delete-filter" filter)
  (ef/at (filter-selector filter) (ef/remove-node)))

(defn on-click [id f]
  (ef/at id (events/listen :click f)))

(defn add-html [loc id html]
  (ef/at id ((if (= :append loc) ef/append ef/prepend) (ef/html html))))

(def append-html (partial add-html :append))

(def prepend-html (partial add-html :prepend))

(defn add-filter [filter]
  (append-html "#filters"
               [:div {:class "filter"
                      :data-filter filter}
                [:span {:class "title"} filter]
                " "
                [:button "Delete"]
                [:div {:class "results"}]])
  (on-click (str (filter-selector filter) " button") #(delete-filter filter))
  (send-command "add-filter" filter))

(defn result-listener [[filter result]]
  (prepend-html (str (filter-selector filter) " div.results")
                [:div result]))

(defn attach-result-listener []
  (eb/on-message @eb (str "results." client-id) result-listener))

(let [raw-handler (atom nil)]
  (defn toggle-raw-stream []
    (if @raw-handler
      (do
        (ef/at "#raw-stream" (ef/remove-node))
        (eb/unregister-handler @raw-handler)
        (reset! raw-handler nil))
      (do
        (append-html "#raw-wrapper" [:div {:id "raw-stream"}])
        (reset! raw-handler (eb/on-message @eb "topic.stream"
                                           #(prepend-html "#raw-stream" [:div %])))))))

(defn init []
  (open-eventbus attach-result-listener)
  (on-click "#add-filter" #(add-filter
                            (ef/from "#filter" (ef/get-prop :value))))
  (on-click "#toggle-raw" toggle-raw-stream))

(set! (.-onload js/window) init)
