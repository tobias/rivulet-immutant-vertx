(ns rivulet.client
  (:require [enfocus.core :as ef]
            [enfocus.events :as events]
            vertx.eventbus)
  (:require-macros [enfocus.macros :as em]))

(def eb (atom nil))

(defn open-eventbus [on-open]
  (reset! eb (js/vertx.EventBus. "http://localhost:8081/bridge"))
  (set! (.-onopen @eb)
        (fn []
          (.log js/console "eventbus opened")
          (on-open))))

(defn send-command [command payload]
  (.publish @eb "topic.commands"
            (clj->js {:command command :payload payload})))

(defn filter-selector [filter]
  (format "div.filter[data-filter=\"%s\"]" filter))

(defn delete-filter [filter]
  (send-command "delete-filter" filter)
  (ef/at (filter-selector filter) (ef/remove-node)))

(em/defaction enable-delete-button [filter]
  (str (filter-selector filter) " button") (events/listen :click
                                                          #(delete-filter filter)))
(defn add-filter [filter]
  (ef/at "#filters"
         (ef/append
          (ef/html [:div {:class "filter"
                          :data-filter filter}
                    [:span {:class "title"} filter]
                    " "
                    [:button "Delete"]
                    [:div {:class "results"}]])))
  (enable-delete-button filter)
  (send-command "add-filter" filter))

(em/defaction enable-add-button []
  "#add-filter" (events/listen :click
                               #(add-filter
                                 (ef/from "#filter" (ef/get-prop :value)))))

(em/defaction result-listener [[filter result]]
  (str (filter-selector filter) " div.results")
  (ef/prepend (ef/html [:div result])))

(defn attach-result-listener []
  (.registerHandler @eb "topic.matches" result-listener))

(em/defaction copy-raw-stream [val]
  "#raw-stream" (ef/prepend (ef/html [:div val])))

(defn toggle-raw-stream []
  (if (ef/from "#raw-stream" (ef/get-attr :id))
    (do
      (ef/at "#raw-stream" (ef/remove-node))
      (.unregisterHandler @eb "topic.stream" copy-raw-stream))
    (do
      (ef/at "#raw" (ef/append (ef/html [:div {:id "raw-stream"}])))
      (.registerHandler @eb "topic.stream" copy-raw-stream))))

(em/defaction enable-raw-button []
  "#toggle-raw" (events/listen :click toggle-raw-stream))

(defn init []
  (open-eventbus
   (fn []
     (attach-result-listener)
     (enable-add-button)
     (enable-raw-button))))

(set! (.-onload js/window) init)
