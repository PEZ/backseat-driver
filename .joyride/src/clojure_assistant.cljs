(ns clojure-assistant
  (:require ["vscode" :as vscode]
            ["openai" :as openai]
            ["ext://betterthantomorrow.calva$v1" :as calva]
            [assistant-prompts :as prompts]
            [context :as context]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(defonce !db (atom {:disposables []
                    :assistant+ nil
                    :thread+ nil
                    :interrupted? false}))

(defn- clear-disposables! []
  (run! (fn [disposable]
          (.dispose disposable))
        (:disposables @!db))
  (swap! !db assoc :disposables []))

(defn- push-disposable [disposable]
  (swap! !db update :disposables conj disposable)
  (-> (joyride/extension-context)
      .-subscriptions
      (.push disposable)))

(def channel (joyride/output-channel))

(defn ->clj [o] (js->clj o :keywordize-keys true))

(defonce openai (openai/OpenAI.))

(def gpt4 "gpt-4-1106-preview")
(def gpt3 "gpt-3.5-turbo-1106")

(swap! !db assoc :assistant+ (openai.beta.assistants.create (clj->js {:name "VS Code Clojure Assistant"
                                                                      :instructions prompts/system-instructions
                                                                      :tools [{:type "code_interpreter"}
                                                                              #_{:type "retrieval"}]
                                                                      :model gpt4})))

(swap! !db assoc :thread+ (openai.beta.threads.create))

(def done-statuses #{"completed" "failed" "expired" "cancelled"})
(def poll-interval 100)

(defn ask!+ []
  (-> channel (.show true))
  (swap! !db assoc :interrupted? false)
  (-> (p/let [assistant (:assistant+ @!db)
              thread (:thread+ @!db)
              _ (def thread thread)
              input (vscode/window.showInputBox)
              _message (openai.beta.threads.messages.create (.-id thread)
                                                            (clj->js {:role "user"
                                                                      :content input}))
              _ (def _message _message)
              run (openai.beta.threads.runs.create
                   (.-id thread)
                   (clj->js {:assistant_id (.-id assistant)
                             :instructions (prompts/system-and-context-instructions)}))
              messages (p/create
                        (fn [resolve reject]
                          (let [retriever (fn retriever [tries]
                                            (print ".")
                                            (-> channel (.append "."))
                                            (def tries tries)
                                            (p/let [retrieved-js (openai.beta.threads.runs.retrieve
                                                                  (.-id thread)
                                                                  (.-id run))
                                                    retrieved (->clj retrieved-js)
                                                    _ (def retrieved retrieved)
                                                    messages (openai.beta.threads.messages.list (.-id thread))]
                                              (if (done-statuses (:status retrieved))
                                                (resolve messages)
                                                (if (:interrupted? @!db)
                                                  (do
                                                    (swap! !db assoc :interrupted? false)
                                                    (reject :interrupted))
                                                  (js/setTimeout
                                                   #(retriever (inc tries))
                                                   poll-interval)))))]
                            (retriever 0))))]
        (def messages messages)
        (-> channel
            (.appendLine (-> (->clj (-> messages .-body .-data))
                             pr-str
                             calva/pprint.prettyPrint
                             .-value)))
        (->clj (-> messages .-body .-data)))
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (p/let [result (ask!+)]
    (-> result pr-str calva/pprint.prettyPrint .-value println))
  (swap! !db assoc :interrupted? true)
  (swap! !db assoc :interrupted? false)
  :rcf)

(defn- my-main []
  (clear-disposables!)
  (-> channel (.show true))
  #_(push-disposable something)
  #_(push-disposable (something-else)))

(when (= (joyride/invoked-script) joyride/*file*)
  (ask!+))


