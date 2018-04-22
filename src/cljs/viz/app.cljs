(ns viz.app
  (:require
    [reagent.core :as r]
    [viz.core :as viz]
    [viz.d3 :as d3]
    [clojure.spec.alpha :as s]
    [cognitect.transit :as transit]))

(defprotocol Source
  (snapshots [this])                                        ; a sorted (:generated desc) seq of snapshot-summary
  (snapshot [this id]))                                     ; a snapshot

(defn app-component
  "return a react component which shows the timeline and size chart"
  [source title]
  (let [snaps (snapshots source)
        app-state (r/atom {:snapshot/id (:id (first snaps))})] ; default to showing the most recent snapshot
    (fn [source title]
      ; TODO calc chart sizes by measuring container size. maximise width!
      (let [chart-sizes {:bar-width  200
                         :tree-width 1200}]
        (if-let [explain (s/explain-data ::viz/summaries snaps)]
          [:div {}
           "Invalid summary data!"
           [:p {} explain]]
          [:div {}
           [:h3 {:style {:width  (str (count title) "rem")
                         :margin "1rem auto"}} title]
           (when (> (count snaps) 1)
             [:div {:style {:float       "left"
                            :marginRight "1rem"}}
              (let [click-handler (fn [e]
                                    (swap! app-state assoc :snapshot/id (.-id e)))
                    chart (d3/bar-chart-horizontal! (:bar-width chart-sizes) (+ 50 (* 20 (count snaps)))
                                                    (mapv (fn [snap] (update snap :when #(js/Date. %))) snaps)
                                                    {:on-click click-handler})]
                [:div {}
                 [:style {} ".bar {fill: steelblue;} .bar:hover {fill: brown;cursor:pointer;}"]
                 (d3/container {:d3fn            chart
                                :animateDuration 1000})])])
           [:div {:style {:float "left"}}
            (let [snap (snapshot source (:snapshot/id @app-state))]
              (if-let [explain (s/explain-data ::viz/snapshot snap)]
                [:div {} "Invalid snapshot data"
                 [:p {} explain]]
                (d3/container {:d3fn (d3/tree-map! (:tree-width chart-sizes)
                                                   (int (* (:tree-width chart-sizes) .6))
                                                   (:tree snap) {})})))]
           [:div {:style {:clear "both"}}]])))))

(defrecord GlobalsSource [summaries snapshots]
  Source
  (snapshots [this] summaries)
  (snapshot [this id] (let [reader (transit/reader :json)]
                        (transit/read reader (get snapshots id)))))

(defn source-from-globals
  []
  (map->GlobalsSource {:summaries (js->clj js/summaries :keywordize-keys true)
                       :snapshots (js->clj js/snapshots)}))

(defn ^:export init []
  (r/render [app-component (source-from-globals) "TODO title"]
            (js/document.getElementById "app")))
