(ns backseat-driver.util
  (:require [promesa.core :as p]
            [clojure.set :as set]))

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

(defn valid-shallow-map? [m spec]
  (every? (fn [[k v]]
            (and (spec k)
                 ((spec k) v)))
          m))

(comment
  (valid-shallow-map? {:get-context "current-ns"}
                      {:get-context #{"current-form" "current-ns"}})
  ;; => true
  (valid-shallow-map? {:function-1 "valid-f1-arg-1"
                       :function-2 "valid-f2-arg-3"}
                      {:function-1 #{"valid-f1-arg-1" "valid-f1-arg-2"}
                       :function-2 #{"valid-f2-arg-1" "valid-f2-arg-2" "valid-f2-arg-3"}})
  ;; => true

  (valid-shallow-map? {:get-context "current-ns"
                       :invalid "current-ns"}
                      {:get-context #{"current-form" "current-ns"}})
  ;; => false

  (valid-shallow-map? {:get-context "invalid"}
                      {:get-context #{"current-form" "current-ns"}})
  ;; => false
  (valid-shallow-map? {:x "current-ns"}
                      {:get-context #{"current-form" "current-ns"}})
  ;; => false


  :rcf)

(comment


  ;; I don't know how to test this properly, but it works where we are using it =)
  (with-timeout+
    (-> (js/Promise. (fn [resolve, _]
                       (js/setTimeout #(do (println "Resolving promise") (resolve :made-it)) 3000)))
        (.then (fn [v] (println "Our promise won with:" v)))
        (.catch (fn [e] (println "ERROR! (the timeout promise won?)" e))))
    100)
  :rcf)

