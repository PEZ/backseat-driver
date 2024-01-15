(ns backseat-driver.app
  (:require [backseat-driver.openai-api :as openai-api]
            ["vscode" :as vscode]
            [backseat-driver.db :as db]
            [backseat-driver.assistants :as assistants]
            [backseat-driver.threads :as threads]
            [joyride.core :as joyride]
            [promesa.core :as p]
            [backseat-driver.ui :as ui]))

;; == Keyboard shortcuts
    ;; {
    ;;     "key": "ctrl+alt+,",
    ;;     "command": "joyride.runCode",
    ;;     "args": "(backseat-driver.app/please-advice!)",
    ;; },

(defn- clear-disposables! []
  (run! (fn [disposable]
          (.dispose disposable))
        (:disposables @db/!db))
  (swap! db/!db assoc :disposables []))

(defn- push-disposable [disposable]
  (swap! db/!db update :disposables conj disposable)
  (-> (joyride/extension-context)
      .-subscriptions
      (.push disposable)))

(defn- create-channel! []
  (let [channel (vscode/window.createOutputChannel "Backseat Driver" "markdown")
        old-channel (:channel @db/!db)]
    (when old-channel
      (.dispose old-channel))
    (push-disposable channel)
    (swap! db/!db assoc :channel channel)))

(defn new-thread! []
  (p/let [thread (openai-api/openai.beta.threads.create)]
    (swap! db/!db assoc :thread+ thread)
    (threads/save-thread!+ thread nil nil)
    (create-channel!)))

(defn init! []
  (clear-disposables!)
  (db/init-db!)
  (swap! db/!db assoc :assistant+ (assistants/get-or-create-assistant!+))
  (new-thread!)
  (push-disposable (ui/add-assist-button!)))

(defn please-advice! []
  (assistants/advice!+))

(comment
  (init!)
  :rcf)