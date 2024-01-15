(ns backseat-driver.completions-api-hello-world
  (:require ["axios" :as axios]
            ["vscode" :as vscode]
            ["openai" :as openai]
            [clojure.string :as string]
            [joyride.core :as joyride]
            [promesa.core :as p]
            [backseat-driver.brave :as brave]))

;; Basically the OpenAI API Quick Start stuff
;; It is not part of the app

(defn ->clj [o] (js->clj o :keywordize-keys true))

(comment
  (def editor vscode/window.activeTextEditor)
  (:document editor)
  (-> editor
      ->clj
      :document
      :positionAt
      (apply [561])
      .-e)
  :rcf)

(defonce openai (openai/OpenAI.))

(defn browse [url]
  (def url url)
  (-> (p/let [response (axios/get url)]
        (println (->clj response))
        (def response response))
      (p/catch (fn [e]
                 (println "Error in browse:" e)))))

(comment

  (browse "https://brave.com")
  (-> (p/let [response (axios/get "https://brave.com")]
        (def response response))
      (p/catch (fn [e] (println (.-message e)))))
  (->clj response)
  :rcf)

(def function->fn {"brave_search" brave/search
                   "browse" browse})


(def functions [{:name "brave_search"
                 :description "Search the web using the Brave Search Engine. The response will be in JSON. Use your best judgement to select which results may be of most interest"}])

(def start-messages [{:role "system"
                      :content "You are an assistant that acts as if you were Buffy the Vampire"}])

(defonce !messages (atom start-messages))


(defn ask-chatgpt! []
  (-> (p/let [input (vscode/window.showInputBox)
              _ (println "Me:" input "\n")
              _ (when input
                  (swap! !messages conj {:role "user"
                                         :content (string/replace input #"\"" "\\”")}))
              completion-js (openai.chat.completions.create
                             (clj->js {:messages @!messages
                                       :model "gpt-4"
                                       :temperature 1}))
              _ (def completion-js completion-js)
              completion (->clj completion-js)
              _ (def completion completion)
              reply-message (-> completion :choices first :message)
              _ (def reply-message reply-message)
              _ (swap! !messages conj reply-message)]
        (println "ChatGPT: " (:content reply-message) "\n"))
      (p/catch (fn [e] (println e "\n")))))

(comment
  (ask-chatgpt!)
  @!messages
  (reset! !messages (filterv #(not-empty (:content %)) @!messages))
  (reset! !messages start-messages)
  (-> completion .-choices first)
  (->clj completion)
  :rcf)


(when (joyride/invoked-script)
  (ask-chatgpt!))
