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

(defn new-session! []
  (threads/create-thread!+ db/!db)
  (let [channel (:channel @db/!db)]
    (-> channel .clear)))

(defn init! []
  (p/do!
   (clear-disposables!)
   (db/init!)
   (swap! db/!db assoc :assistant+ (assistants/get-or-create-assistant!+))
   (let [channel (vscode/window.createOutputChannel "Backseat Driver" "markdown")]
     (push-disposable channel)
     (swap! db/!db assoc :channel channel)
     (.show channel true))
   (threads/init!+ db/!db threads/persistance-key)
   (threads/render-thread! (:messages @db/!db))
   ; create-thread
   (push-disposable (ui/add-assist-button!))))

(defn please-advice! []
  (assistants/advice!+))

(comment
  (init!)
  :rcf)