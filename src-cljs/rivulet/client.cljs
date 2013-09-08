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

(defn sampler-selector [sampler]
  (format "div.sampler[data-sampler=\"%s\"]" sampler))

(defn delete-sampler [sampler]
  (send-command "delete-sampler" sampler)
  (ef/at (sampler-selector sampler) (ef/remove-node)))

(em/defaction enable-delete-button [sampler]
  (str (sampler-selector sampler) " button") (events/listen :click
                                                      #(delete-sampler sampler)))
(defn add-sampler [sampler]
  (ef/at "#samplers"
         (ef/append
          (ef/html [:div {:class "sampler"
                          :data-sampler sampler}
                    [:span {:class "title"} sampler]
                    " "
                    [:button "Delete"]
                    [:div {:class "results"}]])))
  (enable-delete-button sampler)
  (send-command "add-sampler" sampler))

(em/defaction enable-add-button []
  "#add-sampler" (events/listen :click
                             #(add-sampler
                               (ef/from "#sampler" (ef/get-prop :value)))))

(em/defaction result-listener [[sampler result]]
  (str (sampler-selector sampler) " div.results")
  (ef/prepend (ef/html [:div result])))

(defn attach-result-listener []
  (.registerHandler @eb "topic.matches" result-listener))

(defn init []
  (open-eventbus
   (fn []
     (attach-result-listener)))
  (enable-add-button))

(set! (.-onload js/window) init)
