(ns test-watch.hiccup
  (:require [hiccup.core :refer [html]]))

(defn generate [& _]
  (spit "resources/public/index.html"
        (html [:html
               [:head [:link {:rel "stylesheet" :type "text/css" :href "screen.css"}]]
               [:body [:div [:span "Hello world"]]]])))

