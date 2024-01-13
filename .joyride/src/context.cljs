(ns context
  (:require ["vscode" :as vscode]
            ["ext://betterthantomorrow.calva$v1" :as calva]
            [clojure.string :as string]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(defn- range->offsets [range]
  (when range
    (let [editor vscode/window.activeTextEditor
          document (some-> editor .-document)]
      (when document
        [(.offsetAt document (.-start range))
         (.offsetAt document (.-end range))]))))

(defn range-response->clj [response]
  (let [[range content] response]
    {:range (range->offsets range)
     :content content}))

(defn current-form []
  (range-response->clj (calva/ranges.currentForm)))

(defn current-top-level-form []
  (range-response->clj (calva/ranges.currentTopLevelForm)))

(defn selection []
  (let [document vscode/window.activeTextEditor.document]
    {:range (range->offsets vscode/window.activeTextEditor.selection)
     :content (-> document (.getText vscode/window.activeTextEditor.selection))}))

(defn selection-and-current-forms []
  (let [{selection-range :range selection-content :content} (selection)
        current-form (current-form)]
    (merge {:current-top-level-form (current-top-level-form)
            :current-form current-form}
           (if (= selection-range (:range  current-form))
             {:selection :current-form}
             {:selection {:range selection-range
                          :content selection-content}}))))

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
