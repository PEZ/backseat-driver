(ns backseat-driver.context
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
  (assoc (range-response->clj (calva/ranges.currentForm))
         :description "The Current Form in the Calva sense."))

(defn current-enclosing-form []
  (assoc (range-response->clj (calva/ranges.currentEnclosingForm))
         :description "The current enclosing form is the list/etc that the user is typing into.
Together with the current selection, this can help you figure out what
the user is up to."))

(defn current-function []
  (assoc (range-response->clj (calva/ranges.currentFunction))
         :description "The symbol at call postion of the closest enclosing list. So, the
function call the user is crafting."))

(defn current-top-level-defines []
  (assoc (range-response->clj (calva/ranges.currentTopLevelDef))
         :description "Typically the name of the function or variable being defined."))

(defn current-top-level-form []
  (assoc (range-response->clj (calva/ranges.currentTopLevelForm))
         :description "The current top level form is typically the current function or
variable definition. If it doesn't look like a definition, it could
be some testing code, often inside a rich comment form."))

(defn selection []
  (let [document vscode/window.activeTextEditor.document]
    {:range (range->offsets vscode/window.activeTextEditor.selection)
     :content (-> document (.getText vscode/window.activeTextEditor.selection))
     :description "When the start and end of the range are the same, there is only a
cursor postion and no selection. When there is a selection, it could signify that the
user wants your attention on it."}))

(def max-file-size 10000)

(defn current-file-content []
  (let [text (-> vscode/window.activeTextEditor.document .getText)
        size (count text)]
    (if (<= size max-file-size)
      {:range [0 size]
       :content text}
      {:range [0 size]
       :content (subs text 0 max-file-size)
       :description "The content is truncated to the max file size of 200000 characters."})))

(defn selection-and-current-forms [include-file-content?]
  (let [{selection-range :range selection-content :content} (selection)
        current-form (current-form)]
    (merge {:current-top-level-form (current-top-level-form)
            :current-form current-form}
           (if (= selection-range (:range  current-form))
             {:current-selection :current-form}
             {:current-selection {:range selection-range
                          :content selection-content}})
           (when include-file-content?
             {:file-content (current-file-content)}))))

(defn current-ns []
  (let [[namespace ns-form] (calva/document.getNamespaceAndNsForm)]
    {:description "Apply your knowledge about Clojure namespaces, how they correspond to file paths, etcetera."
     :namespace namespace
     :ns-form-size (count ns-form)
     :ns-form ns-form}))

(defn current-file []
  {:path (vscode/workspace.asRelativePath vscode/window.activeTextEditor.document.uri)})

(comment
  (current-form)
  (current-top-level-form)
  (current-file)
  (current-ns)
  (selection-and-current-forms true)
  :rcf)
