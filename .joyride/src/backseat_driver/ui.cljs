(ns backseat-driver.ui
  (:require ["vscode" :as vscode]
            [backseat-driver.db :as db]))

(def assistant-name "Backseat Driver")

(defn show-channel! []
  (let [channel (:channel @db/!db)]
    (-> channel (.show true))
    (swap! db/!db assoc :channel-shown? true)))

(defn- maybe-show-channel! []
  (let [channel (:channel @db/!db)]
    (when-not (:channel-shown? @db/!db)
      (show-channel!))))

(defn say-ln! [message & messages]
  (maybe-show-channel!)
  (let [channel (:channel @db/!db)]
    (-> channel (.append message))
    (doseq [message messages]
      (-> channel (.append " "))
      (-> channel (.append message)))
    (-> channel (.appendLine ""))))

(defn say-one [message]
  (maybe-show-channel!)
  (-> (:channel @db/!db) (.append message)))

(defn assistant-says! [message-texts]
  (maybe-show-channel!)
  (say-ln! "")
  (doseq [text message-texts]
    (say-ln! (str assistant-name ":"))
    (say-ln! text)))

(defn user-says! [text]
  (say-ln! "\nMe:" text))

(def assist-button-id "backseat-driver-assist-me-button")

(defn add-assist-button! []
  (let [item (vscode/window.createStatusBarItem assist-button-id
                                                vscode/StatusBarAlignment.Right
                                                10000)]
    (set! (.-text item) "BD Assist!")
    (set! (.-command item)
          (clj->js {:command "joyride.runCode"
                    :arguments ["(backseat-driver.app/please-advice!)"]}))
    (.show item)
    item))

(comment
  (add-assist-button!)
  :rcf)

