(ns rivulet.web
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [hiccup.page :as page]
            [hiccup.form :as form]
            [clojure.java.io :as io]
            [immutant.web :as web]))

(defn home []
  (page/html4
   [:head
    (page/include-js "http://cdn.sockjs.org/sockjs-0.3.4.min.js"
                     "client.js")
    (page/include-css "client.css")]
   [:body
    [:h2 "Rivulet"]
    (form/text-field :filter)
    [:button {:id "add-filter"} "Add Filter"]
    [:button {:id "toggle-raw"} "Toggle Raw Stream"]
    [:div
     [:div {:id "filters"}
      [:h4 "Filters"]]
     [:div {:id "raw"}
      [:h4 "Raw Stream"]]]]))

(defroutes routes
  (GET "/" []
       (home))
  (route/resources "/")
  (route/not-found "<h1>Not Found</h1>"))

(def app (-> routes handler/site))

(defn start []
  (web/start app))

(defn stop []
  (web/stop))
