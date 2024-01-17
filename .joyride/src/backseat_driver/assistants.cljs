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
        context-fn (context-part->function context-part (str "Not a valid function: " context-part))]
    {:tool_call_id (:call-id call-info)
     :output (pr-str (context-fn))}))

(defn function-call->call-info [tool-function-call]
  {:call-id (:id tool-function-call)
   :function-name (-> tool-function-call :function :name)
   :arguments (-> (-> tool-function-call :function :arguments)
                  js/JSON.parse
                  util/->clj)})

(defn type-function? [call]
  (= "function" (:type call)))

(defn call-tool-functions [run]
  (def run run)
  (->> (get-in run [:required_action :submit_tool_outputs :tool_calls])
       (filter type-function?)
       (map function-call->call-info)
       (map call-info->tool-output)))

(comment
  (->> '({:function {:arguments "{\"context-part\":\"current-form\"}", :name "get-context"},
          :id "call_vaRD4ybll9hRLzcttSSiSbnD",
          :type "function"})
       (map function-call->call-info)
       (map call-info->tool-output))
  :rcf)

(def ^:private done-statuses #{"completed" "failed" "expired" "cancelled"})
(def ^:private continue-statuses #{"queued" "in_progress" "cancelling"})
(def ^:private  poll-interval 100)

(def ^:private status->indicator
  {nil                 "N]\n"
   :poll-started       "["
   :retrieve-timeout   "T]\n"
   :poll-interrupted   "I]\n"
   "queued"            "Q"
   "in_progress"       "."
   "requires_action"   "R"
   "cancelling"        "c"
   "cancelled"         "C]\n"
   "failed"            "F]\n"
   "completed"         "]\n"
   "expired"           "E]\n"})

(defn- report-status! [status]
  (ui/say-one! (status->indicator status "U]\n")))

(defn- retrieve-poller+ [thread-id run-id]
  (def thread-id thread-id)
  (def run-id run-id)
  (swap! db/!db assoc :thread-running? true)
  (-> (p/create
       (fn [resolve reject]
         (let [retriever (fn retriever [tries]
                           (p/let [retrieve-again (fn []
                                                    (js/setTimeout
                                                     #(retriever (inc tries))
                                                     poll-interval))
                                   run-js (openai-api/openai.beta.threads.runs.retrieve
                                           thread-id
                                           run-id)
                                   _ (def retrieved-js run-js)
                                   run (util/->clj run-js)
                                   status (:status run)]
                             (cond
                               (:interrupted? @db/!db)
                               (do
                                 (report-status! :poll-interrupted)
                                 (swap! db/!db assoc :interrupted? false)
                                 (reject [thread-id run-id status] :poll-interrupted))

                               (= "completed" status)
                               (do
                                 (report-status! status)
                                 (p/let [messages (openai-api/openai.beta.threads.messages.list thread-id)]
                                   (resolve messages)))

                               (done-statuses status)
                               (do
                                 (println (pr-str run))
                                 (bd-fs/append-to-log (pr-str run))
                                 (reject [thread-id run-id status] :not-completed))

                               (= "requires_action" status)
                               (do
                                 (report-status! status)
                                 (-> (openai-api/openai.beta.threads.runs.submitToolOutputs
                                      thread-id
                                      run-id
                                      (clj->js {:tool_outputs (call-tool-functions run)}))
                                     (p/then (fn [_]
                                               (retrieve-again)))
                                     (p/catch (fn [error]
                                                (ui/say-error "Submitting function outputs failed:" error)
                                                (reject [thread-id run-id status]
                                                        (str "Submitting function outputs failed:" error))))))

                               (continue-statuses status)
                               (do
                                 (report-status! status)
                                 (retrieve-again))

                               :else
                               (reject [thread-id run-id status] :unknown-status))))]
           (report-status! :poll-started)
           (retriever 0))))
      (p/finally (fn []
                   (swap! db/!db assoc :thread-running? false)))))

(defn- call-assistance!+ [assistant thread input]
  (-> (p/let
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
        (retrieve-poller+ (.-id thread) (.-id run)))
      (p/catch (fn [[thread-id run-id status :as poll-info] & error]
                 (report-status! status)
                 (ui/say-ln! "status:" status)
                 (println "status:" status "\n")
                 (openai-api/openai.beta.threads.runs.cancel thread-id run-id)
                 (p/rejected (pr-str [poll-info error]))))))

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


