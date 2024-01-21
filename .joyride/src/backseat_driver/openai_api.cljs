(ns backseat-driver.openai-api
  (:require ["openai" :as openai]))

(def openai (if js/process.env.JOYRIDE_HEADLESS
              (do
                (println "HEADLESS TEST RUN: Providing a dummy openai object")
                #js {})
              (openai/OpenAI.)))