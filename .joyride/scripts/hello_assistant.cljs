(ns hello-assistant
  (:require ["vscode" :as vscode]
            ["openai" :as openai]
            [clojure.string :as string]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(defn ->clj [o] (js->clj o :keywordize-keys true))

(defonce openai (openai/OpenAI.))

(def gpt4 "gpt-4-1106-preview")
(def gpt3 "gpt-3.5-turbo-1106")

(def assistant (p/do
                 (openai.beta.assistants.create (clj->js {:name "Hejsan"
                                                          :instructions "You are a devoted Clojure expert"
                                                          :tools [{:type "code_interpreter"}]
                                                          :model gpt3}))))

(comment
  openai.beta
  :rcf)

(def thread (p/do
              (openai.beta.threads.create)))

(def example-message {:role "user"
                      :content "Please compare Rust with Clojure, and tell me why I should choose one over the other"})

;(defonce !messages (atom start-messages))

(def done-statuses #{"completed" "failed" "expired" "cancelled"})
(def poll-interval 100)

(defn ask-chatgpt! []
  (-> (p/let [my-assistant assistant
              my-thread thread
              _ (def my-thread my-thread)
              input (vscode/window.showInputBox)
              _message (openai.beta.threads.messages.create (.-id my-thread) (clj->js {:role "user"
                                                                                       :content input}))
              _ (def _message _message)
              run (openai.beta.threads.runs.create
                   (.-id my-thread)
                   (clj->js {:assistant_id (.-id my-assistant)
                             :instructions "You love Rust, and would never use anything else"}))
              retriever-p
              (p/create
               (fn [resolve reject]
                 (let [retriever (fn retriever [tries]
                                   (println "poll: " tries "\n")
                                   (def tries tries)
                                   (p/let [retrieved-js (openai.beta.threads.runs.retrieve (.-id my-thread) (.-id run))
                                           retrieved (->clj retrieved-js)
                                           _ (def retrieved retrieved)
                                           messages (openai.beta.threads.messages.list (.-id my-thread))]
                                     (if (done-statuses (:status retrieved))
                                       (resolve messages)
                                       (js/setTimeout
                                        #(retriever (inc tries))
                                        poll-interval))))]
                   (retriever 0))))

              messages retriever-p]
        (def messages messages)
        (println (js->clj (-> messages .-body .-data)) "\n"))
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (ask-chatgpt!)
  (js-keys messages)
  )


(when (joyride/invoked-script)
  (ask-chatgpt!))

