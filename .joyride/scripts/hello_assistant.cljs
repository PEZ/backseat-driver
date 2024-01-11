(ns hello-assistant
  (:require ["vscode" :as vscode]
            ["openai" :as openai]
            ["ext://betterthantomorrow.calva$v1" :as calva]
            [code-knowledge :as ck]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(defn ->clj [o] (js->clj o :keywordize-keys true))

(defonce openai (openai/OpenAI.))

(def gpt4 "gpt-4-1106-preview")
(def gpt3 "gpt-3.5-turbo-1106")

(def assistant+ (openai.beta.assistants.create (clj->js {:name "Hejsan"
                                                        :instructions "You are a devoted Clojure expert"
                                                        :tools [{:type "code_interpreter"}]
                                                        :model gpt3})))

(def thread+ (openai.beta.threads.create))

(def done-statuses #{"completed" "failed" "expired" "cancelled"})
(def poll-interval 100)

(defn ask-chatgpt!+ []
  (-> (p/let [assistant assistant+
              thread thread+
              _ (def my-thread thread)
              input (vscode/window.showInputBox)
              _message (openai.beta.threads.messages.create (.-id thread)
                                                            (clj->js {:role "user"
                                                                      :content input}))
              _ (def _message _message)
              run (openai.beta.threads.runs.create
                   (.-id thread)
                   (clj->js {:assistant_id (.-id assistant)
                             :instructions "You love Rust, and would never use anything else"}))
              messages (p/create
                        (fn [resolve reject]
                          (let [retriever (fn retriever [tries]
                                            (println "poll: " tries "\n")
                                            (def tries tries)
                                            (p/let [retrieved-js (openai.beta.threads.runs.retrieve
                                                                  (.-id thread)
                                                                  (.-id run))
                                                    retrieved (->clj retrieved-js)
                                                    _ (def retrieved retrieved)
                                                    messages (openai.beta.threads.messages.list (.-id thread))]
                                              (if (done-statuses (:status retrieved))
                                                (resolve messages)
                                                (js/setTimeout
                                                 #(retriever (inc tries))
                                                 poll-interval))))]
                            (retriever 0))))]
        (def messages messages)
        (->clj (-> messages .-body .-data)))
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (p/let [result (ask-chatgpt!+)]
    (-> result pr-str calva/pprint.prettyPrint .-value println))
  :rcf)


(when (joyride/invoked-script)
  (ask-chatgpt!+))

