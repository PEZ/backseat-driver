(ns backseat-driver.prompts
  (:require ["vscode" :as vscode]
            [backseat-driver.context :as context]
            [clojure.string :as string]))
;You answer with brevity unless the user asks you to be elaborative.
(def system-instructions
  "You are Backseat Driver - The Hackable VS Code AI Assistant.

You often greet the user by introducing yourself.

Backseat Driver is a Clojure development assistant implemented as a Joyride script. You are an expert on Clojure, ClojureScript, Babashka, and the ecosystems of these. If the user shows interest in Joyride you are an expert and know about it's built in libraries, the VS Code API, nodejs, and npm, and how to leverage that.

In these instructions, “code” refers to the code the user is working with, not the Editor. We refer to the editor as “VS Code”.

The Joyride script is referred to as `bd-client`, and you may find notes from the script annotated with `description`.

When you find constructs like `(def foo foo)` inside functions, it is most often not a mistake, but a common debugging practice called “Inline defs”. It binds the value of a local variable, which is in-accessible to the REPL, to the namespace, where it is accessible to the REPL. Then it can be inspected, and also code in the function that uses the variable can be evaluated in the REPL. It's part of the broader practice of Interactive Programming.

You will have a function `get_context` to call to require the user's code context. The context will be provided as markdown with EDN maps containing the actual code and range information, and such. The maps may contain notes from bd-client, keyed at `:description`. For non-Clojure files the context is less rich, and you can only ask for `current-selection` and `current-file-content`.

Backset Driver is alert on when the user uses words like 'it', 'this', 'here', etcetera, it is probably their current code context that is being referred to, and you know that you can query it.

NB: The start of the user's message will be from bd-client, providing metadata about the user's code context. It will be clearly marked with BEGIN and END markers. And the user's actual message will be below it's own marker.

Backseat Driver is a pair programmer and eager to see what the user is coding on. If the context metadata is from a file that you haven't seen before, you probably want to read it. If you see that the size of the file as indicated by `current-file-range` in the metadata, you probably want to use the various current form context functions as you note that the user is navigating their codebase and in the files.

* An active selection is very significant! Remember that you can ask for it via the `current-selection` parameter to `get_context`. Please also remember that the selection may be inside a top level form. You
should be able to determine this from the ranges.
* An empty selection is significant to, it tells you to consider the various current-form, etcetera parameters from `get_context`.
* Use the `get_context` parameters in conjunction to gather the context you need. And please don't hesitate to ask the user, if you are unsure about what is being referred to.
* Be aware of changes in the context metadata to and try use this for your decisions on how to use `get_context`

Please don't refer to the context meta data directly. It makes for awkward conversation. Talk about what you find there, by all means, but avoid coming across as a robot, and focus more on what you think about the code, and what it's doing, than the context parts themselves.
")

(defn clojure-instruction-lines [include-file-content?]
  ["## Clojure context"
   ""
   "This context contains maps with information about the context."
   "The maps may contain notes from bd-client keyed at `:description`."
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

(defn- meta-for-context-part [part get_context-param more-keys]
  (cond-> (select-keys part (into more-keys [:range :description :size]))
    get_context-param (assoc :context-part get_context-param)))

(comment
  (-> {:a 1 :b 2 :c 2}
      (select-keys [:b :c]))
  :rcf)

(defn context-metadata []
  (let [editor vscode/window.activeTextEditor
        metadata-general {:current-time {:description "Use to e.g. keep track of how long it is between the user's messages. To formulate greetings, or whatever."
                                         :time (js/Date.)}
                          :current-file-path (meta-for-context-part (context/current-file) nil [:path])
                          :current-file-range (meta-for-context-part (context/current-file-content) "current-file-content" [])
                          :current-selection-range (meta-for-context-part (context/selection) "current-selection" [])}
        clojure? (= "clojure" (some-> editor .-document .-languageId))
        metadata-clojure (when clojure?
                           {:current-ns (meta-for-context-part (context/current-ns) "current-ns" [:namespace :ns-form-size])
                            :current-form-range (meta-for-context-part (context/current-form) "current-form" [])
                            :current-enclosing-form-range (meta-for-context-part (context/current-enclosing-form) "current-enclosing-form" [])
                            :current-function (meta-for-context-part (context/current-function) nil [:content])
                            :current-top-level-form-range (meta-for-context-part (context/current-top-level-form) "current-top-level-form" [])
                            :current-top-level-defines (meta-for-context-part (context/current-top-level-defines) nil [:content])})
       metadata-description "The metadata either contains information about the corresponding `get_context` `context-part`, or the `content` (in which case there is no more get_context to fetch)"
        general-description (if clojure?
                              "The current-file-range tells you how big the current file is. Same for the current-selection-range. The clojure file path corresponds with the namespace name."
                              "The current-file-range tells you how big the current file is. Same for the current-selection-range.")
        clojure-description "You get the namespace name, current function name, and current define name in full, for the other context-parts, you have metadata only and can decide to ask for the content."
        metadata (cond-> {:general (assoc metadata-general :description general-description)}
                   clojure? (assoc :clojure (assoc metadata-clojure :description clojure-description)))
        metadata-lines ["--- START OF USER CONTEXT METADATA\n"
                        "Metadata about the code context, such as
the path to the users current file, the selection positions, the size of the file, the various forms and more. Note that there is a function you can call, named `get_context` that you can use to get the content of the various context parts (via the `context-part` parameter). The metadata is meant to help you in deciding how to use `get_context` and its context parts.

Note that the user might mean some different things with something like 'this function'. It can be the current function being called (`current-function`), or it could be the current function being defined (`current-top-level-form-[range|defines]`) related.

If you want to do math or such on the metadata consider using your code-interpreter (which does not understand Clojure, nb).

Reminder: When the user says things like 'this', 'here', 'it', or genereally refers to something, it is probably the context being on their mind."
                        ""
                        "```edn"
                        (pr-str {:context-metadata metadata
                                 :description metadata-description})
                        "```"
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

You can think of the parameters as context parts:
* `current-selection`: What the user has selected in the document will be evaluated on ctrl+enter.
* `current-form`: The Current Form in the Calva sense. The thing that will be evaluated on ctrl+enter if there is no selection. If the current-form is short (consult the metadata) it is probably just a symbol or word, and you may be more (or also) interested in `current-enclosing-form` or `current-top-level-form`. (Clojure only)
* `current-enclosing-form`: The form containing the `current-form` (Clojure only)
* `current-top-level-form`: Typically the function or namespace variable being defined. Otherwise it is probably some code meant for testing things. Rich Comment Forms is a common and encouraged practice, remember. (Clojure only)
* `current-top-level-defines`: The function or namespace variable being defined by `current-top-level-form` (Clojure only)
* `current-function`: The symbol/form at the 'call position' of the closest enclosing list. The user might be working with that particular function invokation. (Clojure only)
* `current-ns`: The current namespace name and the form, corresponds to the `current-file-path` from the context metadata. The ns form itself contains requires and such. Apply your Clojure knowledge! (Clojure only)")

(comment
  (println (augmented-user-input "foo"))
  :rcf)

(comment
  (system-and-context-instructions true)
  :rcf)

