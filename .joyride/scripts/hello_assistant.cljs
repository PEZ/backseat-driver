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
                      :content "Which programming language do you prefer?"})

;(defonce !messages (atom start-messages))


(defn ask-chatgpt! []
  (-> (p/let [my-assistant assistant
              my-thread thread
              _ (def my-thread my-thread)
              _message (openai.beta.threads.messages.create (.-id my-thread) (clj->js example-message))
              _ (def _message _message)
              ;input (vscode/window.showInputBox)
              run (openai.beta.threads.runs.create
                   (.-id my-thread)
                   (clj->js {:assistant_id (.-id my-assistant)
                             :instructions "You love Rust, and would never use anything else"}))
              retrieved-js (openai.beta.threads.runs.retrieve (.-id my-thread) (.-id run))
              retrieved (js->clj retrieved-js)
              _ (def retrieved retrieved)
              messages (openai.beta.threads.messages.list (.-id my-thread))]
        #_(println "ChatGPT: " (:content reply-message) "\n")
        (def messages messages)
        (js->clj (-> messages .-body .-data)))
      (p/catch (fn [e] (println e "\n")))))

(comment
  (ask-chatgpt!)
  (js-keys messages)
  )


(when (joyride/invoked-script)
  (ask-chatgpt!))

