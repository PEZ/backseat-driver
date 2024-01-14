(ns backseat-driver.ui
  (:require [backseat-driver.db :as db]))

(defn say-ln [message & messages]
  (let [channel (:channel @db/!db)]
    (-> channel (.append message))
    (doseq [message messages]
      (-> channel (.append " "))
      (-> channel (.append message)))
    (-> channel (.appendLine ""))))

(defn say-one [message]
  (-> (:channel @db/!db) (.append message)))