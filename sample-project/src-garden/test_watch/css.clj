(ns test-watch.css
  (:require [garden.def :refer [defstylesheet defstyles]]
            [garden.units :refer [px]]))

(defstyles screen
  [:body
   {:font-family "sans-serif"
    :font-size (px 20)
    :line-height 1.5}])
