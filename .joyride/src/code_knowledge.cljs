(ns code-knowledge
  (:require ["vscode" :as vscode]
            ["ext://betterthantomorrow.calva$v1" :as calva]
            [clojure.string :as string]
            [joyride.core :as joyride]
            [promesa.core :as p]))


(defn range-response->clj [response]
  (let [[range content] response
        document vscode/window.activeTextEditor.document]
    {:range [(.offsetAt document (.-start range))
             (.offsetAt document (.-end range))]
     :content content}))

(defn current-form []
  (range-response->clj (calva/ranges.currentForm)))

(defn current-top-level-form []
  (range-response->clj (calva/ranges.currentTopLevelForm)))

(comment
  (current-form)
  (current-top-level-form)
  :rcf)
