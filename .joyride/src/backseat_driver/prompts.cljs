(ns backseat-driver.prompts
  (:require ["vscode" :as vscode]
            [backseat-driver.context :as context]
            [clojure.string :as string]))

(def system-instructions
  "You are Backseat Driver - A VS Code Clojure Coding Assistant, Riding Shotgun.

Backseat Driver is a Clojure development assistant implemented as a Joyride script.
You are an expert on Clojure, ClojureScript, Babashka, and the ecosystems of these.
If the user shows interest in Joyride you are an expert and know about it's built
in libraries, the VS Code API, nodejs, and npm, and how to leverage that.

In these instructions, “code” refers to the code the user is working with, not the
Editor. We refer to the editor as “VS Code”.

The Joyride script is referred to as `bsd-client`, and you may find notes from the
script annotated with `bsd-client-note`.

When you find constructs like `(def foo foo)` inside functions, it is most often
not a mistake, but a common debugging practice called “Inline defs”. It binds
the value of a local variable, which is in-accessible to the REPL, to the namespace,
where it is accessible to the REPL. Then it can be inspected, and also code in
the function that uses the variable can be evaluated in the REPL. It's part of
the broader practice of Interactive Programming.

The user's input will be prepended by a section containing the current code
context.

The code context will be enclosed in clear BEGIN and END markers.

The current code context will be richer for Clojure files, than for non-Clojure files.

When the user refers to things like 'it', 'this', 'here',
etcetera, it is probably the current code context that is referred to.

The context will be provided as markdown with EDN maps containing the actual
code and range information, and such. The maps may contain notes from bsd-client,
keyed at `:bsd-client-note`.
")

(defn clojure-instruction-lines [include-file-content?]
  ["## Clojure context"
   ""
   "This context contains maps with information about the context."
   "The maps may contain notes from bsd-client keyed at `:bsd-client-note`."
   ""
   "The users current Clojure code context is:"
   ""
   "### Current namespace"
   ""
   "NB: the actual namespaces are more significant than their aliases."
   ""
   "```edn"
   (pr-str (context/current-ns))
   "```"
   ""
   "### Current forms"
   ""
   "```edn"
   (pr-str (context/selection-and-current-forms include-file-content?))
   "```"])

(defn non-clojure-instruction-lines [include-file-content?]
  (cond-> ["## Code context"
           ""
           "The current file/document is not a Clojure file."]
    include-file-content? (into [""
                                 "## File content"
                                 ""
                                 "```edn"
                                 {:file-content (context/current-file-content)}
                                 "```"])
    :always (into [""
                   "### Current selection"
                   ""
                   "```edn"
                   (pr-str (context/selection))
                   "```"])))

(defn context-instructions [include-file-content?]
  (let [lines ["--- START OF USER CONTEXT\n"
               "# User Context"
               ""
               "The user is using VS Code and Calva."
               "You like to help with those two tools as well."
               ""
               "## Current file"
               "```edn"
               (pr-str (context/current-file))
               "```"
               ""]
        editor vscode/window.activeTextEditor
        more-lines (cond
                     (= "clojure" (some-> editor .-document .-languageId))
                     (clojure-instruction-lines include-file-content?)

                     (some-> editor .-document)
                     (non-clojure-instruction-lines include-file-content?)

                     :else
                     ["There is no document for the active editor."])]
    (str (string/join "\n" (into lines more-lines)) "\n--- END OF USER CONTEXT\n")))

(defn system-and-context-instructions [include-file-content?]
  (str system-instructions
       "\n\n"
       (context-instructions include-file-content?)))

(defn augmented-user-input [input include-file-content?]
  (str (context-instructions include-file-content?)
       (string/join "\n"
                    [""
                     "--- INPUT FROM THE USER:"
                     ""
                     input])))

(comment
  (println (augmented-user-input "foo" true))
  :rcf)

(comment
  (system-and-context-instructions true)
  :rcf)

