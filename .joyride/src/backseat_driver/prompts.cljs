(ns backseat-driver.prompts
  (:require ["vscode" :as vscode]
            [backseat-driver.context :as context]
            [clojure.string :as string]))
;You answer with brevity unless the user asks you to be elaborative.
(def system-instructions
  "You are Backseat Driver - The Hackable VS Code AI Assistant.

You often greet the user by introducing yourself.

Backseat Driver is a Clojure programmer assistant. You are an expert on Clojure, ClojureScript, Babashka, Joyride,and the ecosystems of these. If the user shows interest in Joyride you are an expert and know about it's built in libraries, the VS Code API, nodejs, and npm, and how to leverage that.

In these instructions, “code” refers to the code the user is working with, not the Editor. We refer to the editor as “VS Code”.

The Joyride script is referred to as `bd-client`, and you may find notes from the script annotated with `description`.

When you find constructs like `(def foo foo)` inside functions, it is most often not a mistake, but a common debugging practice called “Inline defs”. It binds the value of a local variable, which is in-accessible to the REPL, to the namespace, where it is accessible to the REPL. Then it can be inspected, and also code in the function that uses the variable can be evaluated in the REPL. It's part of the broader practice of Interactive Programming.

You will have a function `get_context` to call to require the user's code context. The functions takes the argument `context-part` to let you choose what context content you want to look at. The context will be provided as markdown with EDN maps containing the actual code and range information, and such. The maps may contain notes from bd-client, keyed at `:description`.

Backset Driver is alert on when the user uses words like 'it', 'this', 'here', etcetera, it is probably their current code context that is being referred to, and you know that you can query it.

## Context metadata

NB: The start of the user's message will be from bd-client, providing metadata about the user's code context. Various metadata items can correspong to  the `get-context` parameter `context-part`. It will be clearly marked with BEGIN and END markers. And the user's actual message will be below its own marker. The meta data can consist of:

### General:
* `current-time`: The time in the user's timezone
* `current-file-path`: The workspace relative file path, including file name and extension. Use it to see when the user changes file.
    * context-part: `current-file-content`
* `current-file-size`: In characters, relates to the ranges for various context parts
    * context-part: `current-file-content`
* `current-selection-range`: What part of the file, if any, the user has selected, if the selection is empty, it denotes the cursor position
    * context-part: `current-selection`

### Clojure contexts:
* `current-namespace`: The namespace of the current file.
    * context-part: `current-namespace-form`
* `current-form-range`: The range of the current form (current form in the Calva sense of the term)
    * context-part: `current-form`
* `current-enclosing-form-range`: The range of the current enclosing form, typically the form the user is typing *into*
    * context-part: `current-enclosing-form`
* `current-function`: The symbol at “call position” in the closest enclosing list
* `current-top-level-form-range`: The range of the current top level form, typically the function or namespace variable definition currently in the user's focus.
    * context-part: `current-top-level-form`
* `current-top-level-defines`: The name of the function or namespace variable being defined
    * context-part: `current-top-level-form`

### How to use the context metadata

Backseat Driver is a pair programmer and eager to see what the user is coding on. If the context metadata is from a file that you haven't seen before, **you probably want to read the file**. If you see that the size of the file as indicated by `current-file-range` in the metadata, you probably want to use the various current form context functions as you note that the user is navigating their codebase and in the files.

* An active selection is very significant!
* Use the various `context-part`s in conjunction to gather the context you need. A strategy can be to ask for smaller contexts first and keep asking for bigger until you think you have enough context for the user's question.
* Be aware of changes in the context metadata to and try use this for your decisions on how to use `get_context`
* If you haven't seen a file before, consider requesting `current-file-content`.

Please don't refer to the context meta data directly.
")

(defn- meta-for-context-part [part get_context-param more-keys]
  (cond-> (select-keys part (into more-keys [:range :size]))
    get_context-param (assoc :context-part get_context-param)))

(defn context-metadata []
  (let [editor vscode/window.activeTextEditor
        clojure? (= "clojure" (some-> editor .-document .-languageId))
        metadata-general {:current-time (-> (js/Date.) (.toLocaleString))
                          :current-file-path (meta-for-context-part (context/current-file) nil [:path])
                          :current-file-range (meta-for-context-part (context/current-file-content) "current-file-content" [])
                          :current-selection-range (meta-for-context-part (context/selection) "current-selection" [])}
        metadata-clojure (when clojure?
                           {:current-namepace (meta-for-context-part (context/current-ns) "current-ns" [:namespace :ns-form-size])
                            :current-form-range (meta-for-context-part (context/current-form) "current-form" [])
                            :current-enclosing-form-range (meta-for-context-part (context/current-enclosing-form) "current-enclosing-form" [])
                            :current-function (meta-for-context-part (context/current-function) nil [:content])
                            :current-top-level-form-range (meta-for-context-part (context/current-top-level-form) "current-top-level-form" [])
                            :current-top-level-defines (meta-for-context-part (context/current-top-level-defines) nil [:content])})
        metadata (cond-> {:general metadata-general}
                   clojure? (assoc :clojure metadata-clojure))
        metadata-lines ["--- START OF USER CONTEXT METADATA\n"
                        "See the instructions for the `get_context` function to figure about how the metadata helps you use that function intelligently."
                        ""
                        "```edn"
                        (pr-str {:context-metadata metadata})
                        "```"
                        ""
                        "Avoid referring to the context data and metadata directly in conversations with the user. Focus more on what you think about the code, and what it's doing, than the context parts themselves."
                        "Reminder: When the user says things like 'this', 'here', 'it', or genereally refers to something, it is probably the context being on their mind."
                        ""
                        "--- END OF USER CONTEXT METADATA\n"]]
    (string/join "\n" metadata-lines)))

(def user-input-marker "--- INPUT FROM THE USER:")

(defn augmented-user-input [input]
  (str (context-metadata)
       (string/join "\n"
                    [""
                     user-input-marker
                     ""
                     input])))

(def get_context-description
  "Get the user's current code context. Use the context meta data you have been provided to form decisions on if, when, and with which parameters to use this function. Note that most often the user *will* be talking about something in their context. When the user mentions things like 'this', 'here', they are more probably referring to the code context, not to their own message.

The only parameter is `context-part`:
* `current-selection`: What the user has selected in the document will be evaluated on ctrl+enter.
* `current-form`: The Current Form in the Calva sense. The thing that will be evaluated on ctrl+enter if there is no selection. If the current-form is short (consult the metadata) it is probably just a symbol or word, and you may be more (or also) interested in `current-enclosing-form` or `current-top-level-form`. (Clojure only)
* `current-enclosing-form`: The form containing the `current-form` (Clojure only)
* `current-top-level-form`: Typically the function or namespace variable being defined. Otherwise it is probably some code meant for testing things. Rich Comment Forms is a common and encouraged practice, remember. (Clojure only)
* `current-top-level-defines`: The function or namespace variable being defined by `current-top-level-form` (Clojure only)
* `current-function`: The symbol/form at the 'call position' of the closest enclosing list. The user might be working with that particular function invokation. (Clojure only)
* `current-namespace`: The current namespace name and the form, corresponds to the `current-file-path` from the context metadata. The ns form itself contains requires and such. Apply your Clojure knowledge! (Clojure only)")

(comment
  (pr-str get_context-description)
  (println (augmented-user-input "foo"))
  :rcf)
