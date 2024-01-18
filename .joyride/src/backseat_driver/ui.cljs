(ns backseat-driver.ui
  (:require ["vscode" :as vscode]
            [backseat-driver.db :as db]
            [promesa.core :as p]
            [clojure.string :as string]))

(def assistant-name "Backseat Driver")

(defn clear! []
  (.clear (:channel @db/!db)))

(defn render! [s]
  (.appendLine (:channel @db/!db) s))

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

(defn assistant-says [text]
  (string/join "\n" [""
                     (str assistant-name ":")
                     text]))

(defn user-says [text]
  (string/join "\n" [""
                     (str "Me: " text)]))

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

(def palette-function-name->fn
  {:interrupt-polling! interrupt-polling!
   :ask-for-assistance!+ ask-for-assistance!+
   :create-new-session!+ create-new-session!+
   :show-channel! show-channel!})

(defn- palette-items [db]
  (let [thread-running? (:thread-running? db)]
    (cond-> []
      thread-running?
      (conj {:label "$(sync~spin) Stop waiting for response"
             :function :interrupt-polling!})

      (not thread-running?)
      (conj {:label "Advice Me"
             :function :ask-for-assistance!+})

      (not thread-running?)
      (conj {:label "Start new session"
             :function :create-new-session!+})

      :always
      (conj {:label "Show Output Channel"
             :function :show-channel!}))))

(comment
  (= [{:function :ask-for-assistance!+ :label "Advice Me"}
      {:function :create-new-session!+ :label "Start new session"}
      {:function :show-channel! :label "Show Output Channel"}]
     (palette-items {:thread-running? false}))
  (def menu-while-running (palette-items {:thread-running? true}))
  :rcf)

(defn show-palette! [db]
  (p/let [items (->> (palette-items db)
                     (map (fn [item]
                            (update item :function palette-function-name->fn))))
          pick (vscode/window.showQuickPick
                (clj->js items)
                #js {:title "Backseat Driver"
                     :placeHolder "Gimme the keys, I can drive!"})]
    (when pick
      ((.-function pick)))))

(comment
  (show-palette! @db/!db)
  (show-palette! {:thread-running? false})
  (show-palette! {:thread-running? true})

  db/!db
  :rcf)

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

