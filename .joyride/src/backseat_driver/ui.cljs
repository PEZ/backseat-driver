(ns backseat-driver.ui
  (:require ["vscode" :as vscode]
            [backseat-driver.db :as db]
            [promesa.core :as p]))

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

(defn say-error [message & messages]
  (show-channel!)
  (apply say-ln! (into [(str "ERROR: " message)] messages)))

(defn say-one! [message]
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

(defn ask-for-assistance!+ []
  (vscode/commands.executeCommand "joyride.runCode" "(backseat-driver.app/please-advice!)"))

(defn create-new-session!+ []
  (vscode/commands.executeCommand "joyride.runCode" "(backseat-driver.app/new-session!)"))

(defn interrupt-polling! []
  (swap! db/!db assoc :interrupted? true))

(def assist-button-id "backseat-driver-assist-me-button")

(defn show-palette! []
  (p/let [items (let [thread-running? (:thread-running? @db/!db)]
                  (cond-> []
                    thread-running?
                    (conj {:label "$(sync~spin) Stop waiting for response"
                           :function interrupt-polling!})

                    (not thread-running?)
                    (conj {:label "Advice Me"
                           :function ask-for-assistance!+})

                    (not thread-running?)
                    (conj {:label "Start new session"
                           :function create-new-session!+})

                    :always
                    (conj {:label "Show Output Channel"
                           :function show-channel!})))
          pick (vscode/window.showQuickPick
                (clj->js items)
                #js {:title "Backseat Driver"
                     :placeHolder "Gimme the keys, I can drive!"})]
    (when pick
      ((.-function pick)))))

(defn add-assist-button! []
  (let [item (vscode/window.createStatusBarItem assist-button-id
                                                vscode/StatusBarAlignment.Right
                                                10000)]
    (set! (.-text item) "Backseat")
    (set! (.-command item)
          (clj->js {:command "joyride.runCode"
                    :arguments ["(backseat-driver.ui/show-palette!)"]}))
    (.show item)
    item))

(comment
  (add-assist-button!)
  :rcf)

