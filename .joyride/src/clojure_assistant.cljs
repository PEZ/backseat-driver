(ns clojure-assistant
  (:require ["vscode" :as vscode]
            ["openai" :as openai]
            ["ext://betterthantomorrow.calva$v1" :as calva]
            [assistant-prompts :as prompts]
            [context :as context]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(def ^:private channel (vscode/window.createOutputChannel "Clojure Assistant" "markdown"))

(defn log-ln [message & messages]
  (-> channel (.append message))
  (doseq [message messages]
    (-> channel (.append " "))
    (-> channel (.append message)))
  (-> channel (.appendLine)))

(defn log-one [message]
  (-> channel (.append message)))

(defn ->clj [o] (js->clj o :keywordize-keys true))

(defonce ^:private openai (openai/OpenAI.))

(def ^:private gpt4 "gpt-4-1106-preview")
(def ^:private gpt3 "gpt-3.5-turbo-1106")

(def ^:private default-db {:disposables []
                           :assistant+ nil
                           :thread+ nil
                           :last-message nil
                           :interrupted? false})

(defonce ^:private  !db (atom nil))

(defn- clear-disposables! []
  (run! (fn [disposable]
          (.dispose disposable))
        (:disposables @!db))
  (swap! !db assoc :disposables []))

(defn init! []
  (clear-disposables!)
  (reset! !db default-db)
  (swap! !db assoc :assistant+ (openai.beta.assistants.create (clj->js {:name "VS Code Clojure Assistant"
                                                                        :instructions prompts/system-instructions
                                                                        :tools [{:type "code_interpreter"}
                                                                                #_{:type "retrieval"}]
                                                                        :model gpt4})))

  (swap! !db assoc :thread+ (openai.beta.threads.create)))

(defn- push-disposable [disposable]
  (swap! !db update :disposables conj disposable)
  (-> (joyride/extension-context)
      .-subscriptions
      (.push disposable)))

(def ^:private done-statuses #{"completed" "failed" "expired" "cancelled"})
(def ^:private  poll-interval 100)

(defn- retrieve-poller+ [thread run]
  (p/create
   (fn [resolve reject]
     (let [retriever (fn retriever [tries]
                       (print ".")
                       (log-one ".")
                       (p/let [retrieved-js (openai.beta.threads.runs.retrieve
                                             (.-id thread)
                                             (.-id run))
                               retrieved (->clj retrieved-js)
                               _ (def retrieved retrieved)
                               messages (openai.beta.threads.messages.list (.-id thread))]
                         (if (done-statuses (:status retrieved))
                           (do
                             (-> channel (.appendLine))
                             (resolve messages))
                           (if (:interrupted? @!db)
                             (do
                               (swap! !db assoc :interrupted? false)
                               (reject :interrupted))
                             (js/setTimeout
                              #(retriever (inc tries))
                              poll-interval)))))]
       (retriever 0)))))

(defn ask!+ []
  (-> channel (.show true))
  (swap! !db assoc :interrupted? false)
  (-> (p/let [assistant (:assistant+ @!db)
              thread (:thread+ @!db)
              #_#__ (def thread thread)
              input (vscode/window.showInputBox)
              _ ()
              _message (openai.beta.threads.messages.create (.-id thread)
                                                            (clj->js {:role "user"
                                                                      :content input}))
              #_#__ (def _message _message)
              run (openai.beta.threads.runs.create
                   (.-id thread)
                   (clj->js {:assistant_id (.-id assistant)
                             :instructions (prompts/system-and-context-instructions)}))
              api-messages (retrieve-poller+ thread run)
              clj-messages (->clj (-> api-messages .-body .-data))
              new-messages (if-let [last-created-at (some-> @!db :last-message :created_at)]
                             (filter #(> (:created_at %) last-created-at) clj-messages)
                             clj-messages)
              _ (swap! !db assoc :next-last-message (:last-message @!db))
              _ (swap! !db assoc :last-message (first clj-messages))
              message-texts (map (fn [m]
                                   (-> m :content first :text :value))
                                 new-messages)
              _ (def message-texts message-texts)
              #_#__ (def clj-messages clj-messages)
              pprinted-messages (-> new-messages
                                    pr-str
                                    calva/pprint.prettyPrint
                                    .-value)]
        #_(def api-messages api-messages)
        (-> channel .appendLine)
        (apply log-ln message-texts)
        #_(def pprinted-messages pprinted-messages)
        new-messages)
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (apply log-ln ["1" "2" "tre"])
  (apply log-ln message-texts)
  (init!)
  (log-ln "hej" 1 2 3)
  (p/let [result (ask!+)]
    (def result result)
    (-> result pr-str calva/pprint.prettyPrint .-value println)
    (log-ln result))
  (swap! !db assoc :interrupted? true)
  (swap! !db assoc :interrupted? false)
  @!db
  :rcf)

(defn- my-main []
  (clear-disposables!)
  (-> channel (.show true))
  #_(push-disposable something)
  #_(push-disposable (something-else)))

(when (= (joyride/invoked-script) joyride/*file*)
  (ask!+))


