(ns backseat-driver.ui
  (:require ["vscode" :as vscode]
            [backseat-driver.db :as db]
            [promesa.core :as p]
            [clojure.string :as string]
            [backseat-driver.util :as util]))

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

(defn ask-for-assistance!+
  ([]
   (vscode/commands.executeCommand "joyride.runCode"
                                   "(backseat-driver.app/please-advice!)"))
  ([gpt]
   (vscode/commands.executeCommand "joyride.runCode"
                                   (str "(backseat-driver.app/please-advice! " gpt ")"))))

(defn create-new-session!+ []
  (vscode/commands.executeCommand "joyride.runCode"
                                  "(backseat-driver.app/new-session!)"))

(defn switch-session!+ []
  (vscode/commands.executeCommand "joyride.runCode"
                                  "(backseat-driver.app/switch-session!)"))

(defn interrupt-polling! []
  (swap! db/!db assoc :interrupted? true))

(def assist-button-id "backseat-driver-assist-me-button")

(def palette-function-name->fn
  {:interrupt-polling! interrupt-polling!
   :ask-gpt-3!+ (partial ask-for-assistance!+ :gpt-3)
   :ask-gpt-4!+ (partial ask-for-assistance!+ :gpt-4)
   :create-new-session!+ create-new-session!+
   :show-channel! show-channel!
   :switch-session!+ switch-session!+})

(defn- palette-items [db]
  (let [thread-running? (:thread-running? db)]
    (cond-> []
      thread-running?
      (conj {:label "$(sync~spin) Stop waiting for response"
             :function :interrupt-polling!})

      (not thread-running?)
      (conj {:label "Ask GPT-3"
             :function :ask-gpt-3!+})

      (not thread-running?)
      (conj {:label "Ask GPT-4"
             :function :ask-gpt-4!+})

      :always
      (conj {:label "Show current session"
             :function :show-channel!})

      (not thread-running?)
      (conj {:label "Switch session"
             :function :switch-session!+})


      (not thread-running?)
      (conj {:label "Start new session"
             :function :create-new-session!+}))))

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

(defn show-sessions-picker+ [ threads-data]
  (p/let [items (->> threads-data
                     (map (fn [thread-data]
                            (def thread-data thread-data)
                            {:label (:title thread-data)
                             :thread-id (:thread-id thread-data)
                             :detail (str (js/Date. (* 1000 (:updated-at thread-data))))})))
          pick (vscode/window.showQuickPick
                (clj->js items)
                #js {:title "Backseat Driver: Switch session"
                     :placeHolder "Select a session"})]
    (when pick
      (:thread-id (util/->clj pick)))))


(defn add-assist-button! []
  (let [item (vscode/window.createStatusBarItem assist-button-id
                                                vscode/StatusBarAlignment.Left
                                                -10000)]
    (set! (.-text item) "Backseat")
    (set! (.-command item)
          (clj->js {:command "joyride.runCode"
                    :arguments ["(backseat-driver.ui/show-palette!)"]}))
    (.show item)
    item))

(comment
  (add-assist-button!)
  :rcf)

