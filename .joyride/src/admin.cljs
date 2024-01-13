(ns admin
  (:require ["axios" :as axios]
            ["openai" :as openai]
            [promesa.core :as p]
            [local-config :as conf]))

(defonce ^:private openai (openai/OpenAI.))

(def ^:private headers #js {"Authorization" (str "Bearer " conf/token),
                            "Openai-Organization" conf/org,
                            "OpenaI-Beta" "assistants=v1"})

(defn- purge-assistants-ids! [ids]
  (if (not-empty ids)
    (let [a-id (first ids)]
      (println (count ids) "Deleting: " a-id "\n")
      (p/let [_ (openai.beta.assistants.del a-id)]
        (js/setTimeout #(purge-assistants-ids! (rest ids)) 100)))
    (println "Done")))

(defn purge-assistants! [limit]
  (let [url "https://api.openai.com/v1/assistants"
        params #js {"limit" limit}]
    (p/let [resp (axios/get url #js {:headers headers :params params})
            resp-clj (js->clj resp :keywordize-keys true)
            ids (map #(:id %) (:data (:data resp-clj)))]
      (println "Purge started:" (count ids) "assistants to delete\n")
      (purge-assistants-ids! ids))))

(comment
  (purge-assistants! 20)
  :rcf)