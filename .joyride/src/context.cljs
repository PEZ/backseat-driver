(ns context
  (:require ["vscode" :as vscode]
            ["ext://betterthantomorrow.calva$v1" :as calva]
            [clojure.string :as string]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(defn- range->offsets [range]
  (let [document vscode/window.activeTextEditor.document]
    [(.offsetAt document (.-start range))
     (.offsetAt document (.-end range))]))

(defn range-response->clj [response]
  (let [[range content] response]
    {:range (range->offsets range)
     :content content}))

(defn current-form []
  (range-response->clj (calva/ranges.currentForm)))

(defn current-top-level-form []
  (range-response->clj (calva/ranges.currentTopLevelForm)))

(defn selection-and-current-forms []
  (let [document vscode/window.activeTextEditor.document
        selection vscode/window.activeTextEditor.selection
        [start end :as selection-range] (range->offsets selection)
        current-form (current-form)]
    (merge {:current-top-level-form (current-top-level-form)
            :current-form current-form}
           (cond
             (= start end)
             {:selection :no-selection}
             (= selection-range (:range  current-form))
             {:selection :current-form}
             :else
             {:selection {:range selection-range
                          :content (-> document (.getText selection))}}))))

(defn current-ns []
  (let [[namespace ns-form] (calva/document.getNamespaceAndNsForm)]
    {:namespace namespace
     :ns-form ns-form}))

(defn current-file []
  {:path (vscode/workspace.asRelativePath vscode/window.activeTextEditor.document.uri)})

(comment
  (current-form)
  (current-top-level-form)
  (current-file)
  (current-ns)
  (selection-and-current-forms)
  :rcf)
