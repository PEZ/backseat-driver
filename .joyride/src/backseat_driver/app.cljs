(ns backseat-driver.app
  (:require [backseat-driver.openai-api :as openai-api]
            ["vscode" :as vscode]
            [backseat-driver.db :as db]
            [backseat-driver.assistants :as assistants]
            [backseat-driver.threads :as threads]
            [joyride.core :as joyride]
            [promesa.core :as p]))

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

(defn init! []
  (clear-disposables!)
  (db/init-db!)
  (swap! db/!db assoc :assistant+ (assistants/get-or-create-assistant!+))
  (p/let [thread (openai-api/openai.beta.threads.create)]
    (swap! db/!db assoc :thread+ thread)
    (threads/save-thread!+ thread nil nil))
  (let [channel (vscode/window.createOutputChannel "Backseat Driver" "markdown")]
    (push-disposable channel)
    (swap! db/!db assoc :channel channel)))

(defn please-advice! []
  (assistants/ask!+))
