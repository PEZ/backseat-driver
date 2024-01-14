(ns backseat-driver.util)

(defn ->clj [o] (js->clj o :keywordize-keys true))

(defn ->vec-sort-vals-by [m f]
  (->> m
       vals
       (sort-by f)
       vec))
