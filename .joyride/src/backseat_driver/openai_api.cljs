(ns backseat-driver.openai-api
  (:require ["openai" :as openai]))

(def openai (openai/OpenAI.))