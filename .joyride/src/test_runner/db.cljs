(ns test-runner.db
  (:require [cljs.test]))

(def default-db {:running nil
                 :ws-activated? false
                 :pass 0
                 :fail 0
                 :error 0})

(def !state (atom default-db))

(defn init-counters! []
  (swap! !state merge (select-keys default-db [:pass :fail :error])))
