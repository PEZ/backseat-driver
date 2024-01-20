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

(def available-functions #{"get_context"})

(def context-part->function
  {"current-file-content" context/current-file-content
   "current-selection" context/selection
   "current-namespace-form" context/current-ns
   "current-form" context/current-form
   "current-enclosing-form" context/current-enclosing-form
   "current-top-level-form" context/current-top-level-form
   "current-top-level-defines" context/current-top-level-defines})

(def function-arguments
  {:get_context {:context-part (set (keys context-part->function))}})

(def functions-conf [{:type "function",
                      :function
                      {:name "get_context",
                       :description prompts/get_context-description,
                       :parameters
                       {:type "object",
                        :properties
                        {:context-part {:type "string", :enum (-> function-arguments
                                                                  :get_context
                                                                  :context-part)}},
                        :required ["context-part"]}}}])

(def assistant-conf {:name "Backseat Driver"
                     :instructions prompts/system-instructions
                     :tools (into [{:type "code_interpreter"}
                                   #_{:type "retrieval"}]
                                  functions-conf)
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

(comment
  (.get (-> (joyride/extension-context) .-globalState) "backseat-driver-assistant-id")
  ;; => "asst_hoDaLXFpudx5qynGkoTDraAU"

  ;; Tread carefully!
  #_ (.update (-> (joyride/extension-context) .-globalState) "backseat-driver-assistant-id" js/undefined)
  :rcf)

(defn call-info->tool-output [call-info]
  (let [context-part (-> call-info :arguments :context-part)
        context-fn (context-part->function context-part (str "Not a valid function: " context-part))]
    {:tool_call_id (:call-id call-info)
     :output (pr-str (context-fn))}))

(defn function-call->call-info [tool-function-call]
  (let [function-name (-> tool-function-call :function :name)
        args-json (-> tool-function-call :function :arguments)
        call-id (:id tool-function-call)]
    (cond
      (not (available-functions function-name))
      {:error (ex-info "Bad function name"
                       {:causes #{:function-name :non-existant}
                        :function-name function-name
                        :call-id call-id
                        :valid-functions available-functions})}

      (or (not args-json) (empty? args-json))
      {:error (ex-info "Arguments missing"
                       {:causes #{:arguments :malformed}
                        :arguments args-json
                        :call-id call-id
                        :hint "Non-empty arguments required"})}

      :else
      (let [arguments (-> args-json
                          js/JSON.parse
                          util/->clj)]
        (def arguments arguments)
        (def function-name function-name)
        (if (util/map-matches-spec? {(keyword function-name) arguments} function-arguments)
          {:call-id call-id
           :function-name function-name
           :arguments arguments}
          {:error (ex-info "Bad arguments"
                           {:causes #{:arguments :malformed}
                            :arguments args-json
                            :call-id call-id
                            :valid-arguments function-arguments})})))))

(comment
  (js/JSON.parse "{\"get_context\":\"current-top-level-form\"}")
  (pr-str (js/JSON.stringify (clj->js {:get_context "current-top-level-form"})))
  :rcf)

(defn type-function? [call]
  (= "function" (:type call)))

(defn tool-calls->outputs [run]
  (def run run)
  (->> (get-in run [:required_action :submit_tool_outputs :tool_calls])
       (filter type-function?)
       (map function-call->call-info)
       (map (fn [call-info]
              (def call-info call-info)
              (if (:error call-info)
                (let [error (:error call-info)]
                  {:tool_call_id (:call-id (ex-data error))
                   :output (pr-str error)})
                (call-info->tool-output call-info))))))

(comment
  (tool-calls->outputs run)

  (->> {:required_action
        {:submit_tool_outputs
         {:tool_calls [{:function {:arguments "{\"context-part\":\"current-form\"}"
                                   :name "get_context"}
                        :id "call_vaRD4ybll9hRLzcttSSiSbnD"
                        :type "function"}]}}}
       (tool-calls->outputs))

  (pr-str (ex-info "BOO" {:causes "Ditt fel ju!"}))
  :rcf)

(def ^:private done-statuses #{"completed" "failed" "expired" "cancelled"})
(def ^:private continue-statuses #{"queued" "in_progress" "cancelling"})
(def ^:private poll-interval 100)
(def ^:private poll-timeout 10000) ; 10 s, is that too low?

(def ^:private status->indicator
  {nil                 "N]\n"
   :poll-started       "["
   :poll-timeout       "T]\n"
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
                                   run-js (-> (p/timeout
                                                (openai-api/openai.beta.threads.runs.retrieve
                                                 thread-id
                                                 run-id)
                                                poll-timeout)
                                              (.catch (fn [e]
                                                        (reject [thread-id run-id :poll-timeout (ex-cause e)]))))
                                   _ (def retrieved-js run-js)
                                   run (util/->clj run-js)
                                   status (:status run)]
                             (cond
                               (:interrupted? @db/!db)
                               (do
                                 (report-status! :poll-interrupted)
                                 (swap! db/!db assoc :interrupted? false)
                                 (reject [thread-id run-id status :poll-interrupted]))

                               (= "completed" status)
                               (do
                                 (report-status! status)
                                 (resolve (threads/fetch-messages!+ thread-id)))

                               (done-statuses status)
                               (do
                                 (println (pr-str run))
                                 (bd-fs/append-to-log (pr-str run))
                                 (reject [thread-id run-id status :not-completed]))

                               (= "requires_action" status)
                               (do
                                 (report-status! status)
                                 (-> (openai-api/openai.beta.threads.runs.submitToolOutputs
                                      thread-id
                                      run-id
                                      (clj->js {:tool_outputs (tool-calls->outputs run)}))
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
                               (reject [thread-id run-id status :unknown-status]))))]
           (report-status! :poll-started)
           (retriever 0))))
      (p/finally (fn []
                   (swap! db/!db assoc :thread-running? false)))))

(defn- call-assistance!+ [assistant thread input]
  (-> (p/let
       [augmented-input (prompts/augmented-user-input input)
        _message (openai-api/openai.beta.threads.messages.create (.-id thread)
                                                                 (clj->js {:role "user"
                                                                           :content augmented-input}))
        run (openai-api/openai.beta.threads.runs.create
             (.-id thread)
             (clj->js {:assistant_id (.-id assistant)
                       :model gpt4}))]
        (retrieve-poller+ (.-id thread) (.-id run)))
      (p/catch (fn [[thread-id run-id status :as poll-info]]
                 (report-status! status)
                 (ui/say-ln! "status:" (str status))
                 (println "status:" status "\n")
                 (openai-api/openai.beta.threads.runs.cancel thread-id run-id)
                 (p/rejected (pr-str [poll-info]))))))

(defn- new-assistant-messages [clj-messages last-created-at]
  (cond->> clj-messages
    last-created-at (filter #(and (> (:created_at %) last-created-at)
                                  (= (:role %) "assistant")))
    (not last-created-at) (filter #(= (:role %) "assistant"))
    :always (sort-by :created_at)))

(defn advice!+ []
  (swap! db/!db assoc :interrupted? false)
  (-> (p/let [assistant (:assistant+ @db/!db)
              thread (:current-thread @db/!db)
              input (vscode/window.showInputBox
                     #js {:prompt "What do you want say to the assistant?"
                          :placeHolder "Something something"
                          :ignoreFocusOut true})]
        (when-not (= js/undefined input)
          (p/let [_ (ui/user-says! input)
                  _ (threads/save-thread-data!+ db/!db thread input)
                  messages (call-assistance!+ assistant thread input)
                  last-created-at (some-> @db/!db :messages first :created_at)
                  new-messages (new-assistant-messages messages last-created-at)
                  _ (swap! db/!db assoc :messages messages)
                  message-texts (map threads/message-text new-messages)]
            (ui/assistant-says! message-texts)
            :advice-given+)))
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (backseat-driver.app/init!)
  (swap! db/!db assoc :interrupted? true)
  (swap! db/!db assoc :interrupted? false)
  @db/!db
  :rcf)


