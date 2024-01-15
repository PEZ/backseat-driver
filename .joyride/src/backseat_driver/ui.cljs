(ns backseat-driver.ui
  (:require [backseat-driver.db :as db]))

(def assistant-name "Backseat Driver")

(defn say-ln! [message & messages]
  (let [channel (:channel @db/!db)]
    (-> channel (.append message))
    (doseq [message messages]
      (-> channel (.append " "))
      (-> channel (.append message)))
    (-> channel (.appendLine ""))))

(defn say-one [message]
  (-> (:channel @db/!db) (.append message)))

(defn assistant-says! [message-texts]
  (say-ln! "")
  (doseq [text message-texts]
    (say-ln! (str assistant-name ":"))
    (say-ln! text)))

(defn user-says! [text]
  (say-ln! "\nMe:" text))