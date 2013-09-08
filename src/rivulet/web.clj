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
    (form/text-field :sampler)
    (form/submit-button {:id "add-sampler"} "Add Sampler")
    [:div {:id "samplers"}]]))

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
