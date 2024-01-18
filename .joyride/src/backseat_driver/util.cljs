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

(defn map-matches-spec? [m spec]
  (if (map? m)
    (every? (fn [[k v]]
              (let [spec-value (get spec k)]
                (and spec-value
                     (if (map? v)
                       (map-matches-spec? v spec-value)
                       (if (set? spec-value)
                         (contains? spec-value v)
                         (= spec-value v))))))
            m)
    (= m spec)))

(comment
  (map-matches-spec? {:get_context {:context-part "current-ns"}}
                      {:get_context {:context-part #{"current-form" "current-ns"}}})
  ;; => true
  (map-matches-spec? {:a {:b {:c {:d 9}}}}
                     {:a {:b {:c {:d 9}
                              :e {:f 5}}}})
  ;; => true
  (map-matches-spec? {:a {:b {:c {:d 2}}}}
                     {:a {:b {:c {:d #{1 3}}
                              :e {:f 5}}}})
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

