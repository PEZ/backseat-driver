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
         :bsd-client-note "Apply your knowledge about the significance of the current form."))

(defn current-enclosing-form []
  (assoc (range-response->clj (calva/ranges.currentEnclosingForm))
         :bsd-client-note "The current enclosing form is the list/etc that the user is typing into.
                           Together with the current selection, this can help you figure out what
                           the user is up to."))

(defn current-top-level-form []
  (assoc (range-response->clj (calva/ranges.currentTopLevelForm))
         :bsd-client-note "The current top level form is typically the current function definition.
                           Or some other definition (`def` or, often some macro starting with `def`).
                           If it doesn't look like a definition, it could be some testing code,
                           often inside a rich comment form."))

(defn selection []
  (let [document vscode/window.activeTextEditor.document]
    {:range (range->offsets vscode/window.activeTextEditor.selection)
     :content (-> document (.getText vscode/window.activeTextEditor.selection))
     :bsd-client-note "If the selection is larger than the top level form,
                       the current forms are probably less significant"}))

(def max-file-size 20000)

(defn current-file-content []
  (let [text (-> vscode/window.activeTextEditor.document .getText)
        size (count text)]
    (if (<= size max-file-size)
      {:range [0 size]
       :content text}
      {:range [0 size]
       :content (subs text 0 max-file-size)
       :bsd-client-note "The content is truncated to the max file size of 200000 characters."})))

(defn selection-and-current-forms [include-file-content?]
  (let [{selection-range :range selection-content :content} (selection)
        current-form (current-form)]
    (merge {:current-top-level-form (current-top-level-form)
            :current-form current-form}
           (if (= selection-range (:range  current-form))
             {:selection :current-form}
             {:selection {:range selection-range
                          :content selection-content}})
           (when include-file-content?
             {:file-content (current-file-content)}))))

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
  (selection-and-current-forms true)
  :rcf)
