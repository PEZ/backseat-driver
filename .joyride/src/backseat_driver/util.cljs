(ns backseat-driver.util
  (:require [promesa.core :as p]))

(defn ->clj [o] (js->clj o :keywordize-keys true))

(defn ->vec-sort-vals-by [m f]
  (->> m
       vals
       (sort-by f)
       vec))

(defn with-timeout+ [promise+ ms]
  (let [!timeout (atom nil)
        timeout-promise+ (js/Promise. (fn [_ reject]
                                        (let [timeout (js/setTimeout
                                                       (fn []
                                                         (reject [:timeout ms]))
                                                       ms)]
                                          (reset! !timeout timeout))))]
    (->
     (js/Promise.race [promise+ timeout-promise+])
     (.catch (fn [e]
               (throw e)))
     (.finally (fn []
                 (js/clearTimeout @!timeout))))))

(comment

  ;; I don't know how to test this properly, but it works where we are using it =)
  (with-timeout+
    (-> (js/Promise. (fn [resolve, _]
                       (js/setTimeout #(do (println "Resolving promise") (resolve :made-it)) 3000)))
        (.then (fn [v] (println "Our promise won with:" v)))
        (.catch (fn [e] (println "ERROR! (the timeout promise won?)" e))))
    100)
  :rcf)

