(ns backseat-driver.util)

(defn ->clj [o] (js->clj o :keywordize-keys true))

(defn ->vec-sort-vals-by [m f]
  (->> m
       vals
       (sort-by f)
       vec))

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
  (->vec-sort-vals-by {:a 7 :d 1 :e 0 :b 3} identity)
  ;; => [0 1 3 7]

  (->vec-sort-vals-by {:a "aaaaa" :d "d" :e "" :b "bbb"} identity)
  ;; => ["" "aaaaa" "bbb" "d"]

  (->vec-sort-vals-by {:a "aaaaa" :d "d" :e "" :b "bbb"} count)
  ;; => ["" "d" "bbb" "aaaaa"]

  (count "aaa")
  (count [1 2 4])

  :rcf)


