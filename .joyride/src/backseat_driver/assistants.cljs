(ns backseat-driver.assistants
  (:require ["vscode" :as vscode]
            [backseat-driver.context :as context]
            [backseat-driver.db :as db]
            [backseat-driver.fs :as bd-fs]
            [backseat-driver.openai-api :as openai-api]
            [backseat-driver.prompts :as prompts]
            [backseat-driver.threads :as threads]
            [backseat-driver.ui :as ui]
            [backseat-driver.util :as util]
            [joyride.core :as joyride]
            [promesa.core :as p]
            [clojure.string :as string]))

(def ^:private gpt4 "gpt-4-1106-preview")
(def ^:private gpt3 "gpt-3.5-turbo-1106")


(def context-part->function
  {"current-file-path" context/current-file
   "current-file-content" context/current-file-content
   "current-ns" context/current-ns
   "current-form" context/current-form
   "current-top-level-form" context/current-top-level-form
   "current-enclosing-form" context/current-enclosing-form
   "selection" context/selection})

(def functions [{:type "function",
                 :function
                 {:name "get-context",
                  :description "Get the user's current code context, such as file path, current file content, current namespace, current form, current top level form, current enclosing form, and current selection.",
                  :parameters
                  {:type "object",
                   :properties
                   {:context-part {:type "string", :enum ["current-file-path"
                                                          "current-file-content"
                                                          "current-ns"
                                                          "current-form"
                                                          "current-top-level-form"
                                                          "current-enclosing-form"
                                                          "selection"]}},
                   :required ["context-part"]}}}])

(def assistant-conf {:name "Backseat Driver"
                     :instructions prompts/system-instructions
                     :tools (into [#_{:type "code_interpreter"}
                                   #_{:type "retrieval"}]
                                  functions)
                     :model gpt4})

(defn get-or-create-assistant!+ []
  (p/let [assistant-storage-key "backseat-driver-assistant-id"
          global-state (-> (joyride/extension-context) .-globalState)
          existing-assistant-id (.get global-state assistant-storage-key)
          assistant (if existing-assistant-id
                      (-> (openai-api/openai.beta.assistants.retrieve existing-assistant-id)
                          (.catch (fn [e]
                                    (js/console.error e)
                                    (openai-api/openai.beta.assistants.create (clj->js assistant-conf)))))
                      (openai-api/openai.beta.assistants.create (clj->js assistant-conf)))
          assistant-id (.-id assistant)]
    (.update global-state assistant-storage-key assistant-id)
    assistant))

(defn call-info->tool-output [call-info]
  (let [context-part (-> call-info :arguments :context-part)
        context-fn (context-part->function context-part (str "Not a valid function: " context-part))
        context (context-fn)
        output (string/join "\n" [""
                                  (str "Range: " (:range context))
                                  "Content:"
                                  "```clojure"
                                  (:content context)
                                  "```"])]
    {:tool_call_id (:call-id call-info)
     :output (pr-str output)}))

(defn function-call->call-info [tool-function-call]
  {:call-id (:id tool-function-call)
   :function-name (-> tool-function-call :function :name)
   :arguments (-> (-> tool-function-call :function :arguments)
                  js/JSON.parse
                  util/->clj)})

(comment
  (->> '({:function {:arguments "{\"context-part\":\"current-form\"}", :name "get-context"},
          :id "call_vaRD4ybll9hRLzcttSSiSbnD",
          :type "function"})
       (map function-call->call-info)
       (map call-info->tool-output))
  :rcf)

(defn type-function? [call]
  (= "function" (:type call)))

(comment
  (type-function? {:type "foo"})
  (type-function? {:type "function" :hej "hå"})
  (def function-calls [{:id "call_foo",
                        :type "function",
                        :function
                        {:name "get-context",
                         :arguments "{\"context-part\":\"current-form\"}"}}
                       {:id "call_bar",
                        :type "function",
                        :function
                        {:name "get-context", :arguments "{\"context-part\":\"selection\"}"}}])


  (function-call->call-info {:id "call_foo",
                             :type "function",
                             :function
                             {:name "get-context",
                              :arguments "{\"context-part\":\"current-form\"}"}})

  (map function-call->call-info function-calls)


  (def call-infos [{:call-id "call_foo"
                    :function-name "get-context"
                    :arguments {:context-part "current-enclosing-form"}}
                   {:call-id "call_bar"
                    :function-name "get-context"
                    :arguments {:context-part "current-form"}}])

  (call-info->tool-output {:call-id "call_foo"
                           :function-name "get-context"
                           :arguments {:context-part "current-enclosing-form"}})

  (map call-info->tool-output call-infos)

  (def tool-outputs (map call-info->tool-output call-infos))

  (defn x42 [n]
    (* n 42))

  (map x42 [1 2 3])
  (js/Math.abs -1)

  (map js/Math.abs [-2 -1 0 1 2])

  (-> tool-outputs
      first
      clj->js
      (js/JSON.stringify)
      println)

  ;; Save for later (hopefully not needed)
  (let [retrieve-again (fn []
                         (js/setTimeout
                          #(retriever (inc tries))
                          poll-interval))])

  :rcf)

(def ^:private done-statuses #{"completed" "failed" "expired" "cancelled"})
(def ^:private  poll-interval 100)

(defn- retrieve-poller+ [thread run]
  (def thread thread)
  (def run run)
  (swap! db/!db assoc :thread-running? true)
  (-> (p/create
       (fn [resolve reject]
         (let [retriever (fn retriever [tries]
                           (def tries tries)
                           (ui/say-one ".")
                           (p/let [retrieve-again (fn []
                                                    (js/setTimeout
                                                     #(retriever (inc tries))
                                                     poll-interval))
                                   retrieved-js (openai-api/openai.beta.threads.runs.retrieve
                                                 (.-id thread)
                                                 (.-id run))
                                   _ (def retrieved-js retrieved-js)
                                   retrieved (util/->clj retrieved-js)
                                   messages (openai-api/openai.beta.threads.messages.list (.-id thread))
                                   status (:status retrieved)]
                             (cond
                               (:interrupted? @db/!db)
                               (p/do
                                 (swap! db/!db assoc :interrupted? false)
                                 (openai-api/openai.beta.threads.runs.cancel (.-id thread) (.-id run))
                                 (reject :interrupted))

                               (= "completed" status)
                               (do
                                 (ui/say-ln! "")
                                 (resolve messages))

                               (done-statuses status)
                               (do
                                 (ui/say-ln! "")
                                 (ui/say-ln! "status:" status)
                                 (println "status:" status)
                                 (println retrieved)
                                 (bd-fs/append-to-log (pr-str retrieved))
                                 (reject status))

                               (= "requires_action" status)
                               (do
                                 (ui/say-ln! "")
                                 (ui/say-ln! "status:" status)
                                 (let [required-action (:required_action retrieved)
                                       tool-calls (-> required-action :submit_tool_outputs :tool_calls)
                                       function_calls (filter type-function? tool-calls)
                                       call-infos (map function-call->call-info function_calls)
                                       tool-outputs (map call-info->tool-output call-infos)]
                                   (-> (openai-api/openai.beta.threads.runs.submitToolOutputs
                                        (.-id thread)
                                        (.-id run)
                                        (-> {:tool_outputs tool-outputs}
                                            clj->js))
                                       (p/then (fn [_]
                                                 (retrieve-again)))
                                       (p/catch (fn [error]
                                                  (ui/say-error "Submit function output failed:" error)
                                                  (reject (str "Submit function output failed:" error)))))))

                               :else
                               (retrieve-again))))]
           (retriever 0))))
      (p/finally (fn []
                   (swap! db/!db assoc :thread-running? false)))))

(defn- call-assistance!+ [assistant thread input]
  (p/let
   [include-file-content? (threads/maybe-add-shared-file!?+ thread (:path (context/current-file)))
    augmented-input (prompts/augmented-user-input input include-file-content?)
    _message (openai-api/openai.beta.threads.messages.create (.-id thread)
                                                             (clj->js {:role "user"
                                                                       :content augmented-input}))
    run (openai-api/openai.beta.threads.runs.create
         (.-id thread)
         (clj->js {:assistant_id (.-id assistant)
                   :model gpt4
                   :tools (into [#_{:type "code_interpreter"}
                                 #_{:type "retrieval"}]
                                functions)
                   :instructions (str "The users current file is: `" (context/current-file) "`")}))]
    (retrieve-poller+ thread run)))

(defn- new-assistant-messages [clj-messages last-created-at]
  (cond->> clj-messages
    last-created-at (filter #(and (> (:created_at %) last-created-at)
                                  (= (:role %) "assistant")))
    (not last-created-at) (filter #(= (:role %) "assistant"))
    :always (sort-by :created_at)))

(defn- message-text [m]
  (-> m :content first :text :value))


(defn advice!+ []
  (swap! db/!db assoc :interrupted? false)
  (-> (p/let [assistant (:assistant+ @db/!db)
              thread (:thread+ @db/!db)
              input (vscode/window.showInputBox
                     #js {:prompt "What do you want say to the assistant?"
                          :placeHolder "Something something"
                          :ignoreFocusOut true})]
        (when-not (= js/undefined input)
          (p/let [_ (ui/user-says! input)
                  _ (threads/maybe-add-title!?+ thread input)
                  api-messages (call-assistance!+ assistant thread input)
                  _ (def api-messages api-messages)
                  clj-messages (util/->clj (-> api-messages .-body .-data))
                  last-created-at (some-> @db/!db :last-message :created_at)
                  new-messages (new-assistant-messages clj-messages last-created-at)
                  _ (swap! db/!db assoc :last-message (first clj-messages))
                  message-texts (map message-text new-messages)]
            (ui/assistant-says! message-texts)
            :advice-given+)))
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (backseat-driver.app/init!)
  (util/->clj (-> api-messages .-body .-data))
  (swap! db/!db assoc :interrupted? true)
  (swap! db/!db assoc :interrupted? false)
  @db/!db
  :rcf)


