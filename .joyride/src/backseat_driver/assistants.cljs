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
            [promesa.core :as p]))

(def ^:private gpt4 "gpt-4-1106-preview")
(def ^:private gpt3 "gpt-3.5-turbo-1106")


(defn context-part->function [tool]
  (case tool
    "current-file-path" context/current-file
    "current-file-content" context/current-file-content
    "current-ns" context/current-ns
    "current-form" context/current-form
    "current-top-level-form" context/current-top-level-form
    "current-enclosing-form" context/current-enclosing-form
    "selection" context/selection
    (fn [] (str "function doesn't exist: " tool))))

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

(def ^:private done-statuses #{"completed" "failed" "expired" "cancelled"})
(def ^:private  poll-interval 100)

(defn- retrieve-poller+ [thread run]
  (def thread thread)
  (def run run)
  (swap! db/!db assoc :thread-running? true)
  (-> (p/create
       (fn [resolve reject]
         (let [retriever (fn retriever [tries]
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
                                   _ (def retrieved retrieved)
                                   messages (openai-api/openai.beta.threads.messages.list (.-id thread))
                                   _ (def messages messages)
                                   status (:status retrieved)
                                   _ (def status status)]
                             (cond
                               (done-statuses status)
                               (do
                                 (ui/say-ln! "")
                                 (when-not (= "completed" status)
                                   (ui/say-ln! "status:" status)
                                   (println "status:" status)
                                   (println retrieved)
                                   (bd-fs/append-to-log (pr-str retrieved)))
                                 (resolve messages))

                               (= "requires_action" status)
                               (do
                                 (ui/say-ln! "")
                                 (ui/say-ln! "status:" status)
                                 (p/let [required-action (:required_action retrieved)
                                         tool-calls (-> required-action :submit_tool_outputs :tool_calls)
                                         function_calls (filter (fn [call]
                                                                  (def call call)
                                                                  (= "function" (:type call)))
                                                                tool-calls)
                                         call-data (map (fn [call]
                                                          {:call-id (:id call)
                                                           :function-name (-> call :function :name)
                                                           :arguments (-> (-> call :function :arguments)
                                                                          js/JSON.parse
                                                                          util/->clj)})
                                                        function_calls)
                                         tool-outputs (map (fn [call-datum]
                                                             (let [f (context-part->function (-> call-datum :arguments :context-part))]
                                                               {:tool_call_id (:call-id call-datum)
                                                                :output (pr-str (f))}))
                                                           call-data)]
                                   (def required-action required-action)
                                   (def tool-calls tool-calls)
                                   (def function_calls function_calls)
                                   (def call-data call-data)
                                   (def tool-outputs tool-outputs)
                                   (openai-api/openai.beta.threads.runs.submitToolOutputs
                                    (.-id thread)
                                    (.-id run)
                                    (clj->js tool-outputs))
                                   (retrieve-again)))

                               (:interrupted? @db/!db)
                               (p/do
                                 (swap! db/!db assoc :interrupted? false)
                                 (openai-api/openai.beta.threads.runs.cancel (.-id thread) (.-id run))
                                 (reject :interrupted))

                               :else
                               (retrieve-again))))]
           (retriever 0))))
      (p/finally (fn []
                   (swap! db/!db assoc :thread-running? false)))))

(comment
  (def call-data-correct
    [{:call-id "call_aZ1DGlNjCK22ji8O7dEYl892"
      :function-name "get-context"
      :arguments {:context-part "current-enclosing-form"}}])
  (-> "{\"context-part\":\"current-enclosing-form\"}"
      js/JSON.parse
      util/->clj)


  (def call-data
    (map (fn [call]
           {:call-id (:id call)
            :function-name (-> call :function :name)
            :arguments (-> (-> call :function :arguments)
                           js/JSON.parse
                           util/->clj)})
         function_calls))

  (def tool-outputs (map (fn [call-datum]
                           (let [f (context-part->function (-> call-datum :arguments :context-part))]
                             {:tool_call_id (:call-id call-datum)
                              :output (pr-str (f))}))
                         call-data))

  (defn alltid [] (fn [] (str "function doesn't exist: " "foo")))
  (constantly "nånting")

  :rcf)

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


