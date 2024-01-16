(ns backseat-driver.admin
  (:require ["axios" :as axios]
            ["openai" :as openai]
            [promesa.core :as p]
            [backseat-driver.local-config :as conf]))

(defonce ^:private openai (openai/OpenAI.))

(def ^:private headers #js {"Authorization" (str "Bearer " conf/token),
                            "Openai-Organization" conf/org,
                            "OpenaI-Beta" "assistants=v1"})

(defn- purge-assistants-with-ids! [ids]
  (if (not-empty ids)
    (let [a-id (first ids)]
      (println (count ids) "Deleting: " a-id "\n")
      (p/let [_ (openai.beta.assistants.del a-id)]
        (js/setTimeout #(purge-assistants-with-ids! (rest ids)) 100)))
    (println "Done")))

;; Probably not needed more now that I have stopped creating
;; tons of assistants
(defn purge-assistants! [limit]
  (let [url "https://api.openai.com/v1/assistants"
        params #js {"limit" limit}]
    (p/let [resp (axios/get url #js {:headers headers :params params})
            resp-clj (js->clj resp :keywordize-keys true)
            ids (map #(:id %) (:data (:data resp-clj)))]
      (println "Purge started:" (dec (count ids)) "assistants to delete\n")
      ;; rest so that we don't delete the latest assistant
      (purge-assistants-with-ids! (rest ids)))))


(defn- purge-threads-with-ids! [ids]
  (if (not-empty ids)
    (let [thread-id (first ids)]
      (println (count ids) "Deleting: " thread-id "\n")
      (p/let [_ (openai.beta.threads.del thread-id)]
        (js/setTimeout #(purge-threads-with-ids! (rest ids)) 100)))
    (println "Done")))

(defn purge-threads! [limit]
  (let [url "https://api.openai.com/v1/threads"
        params #js {"limit" limit}]
    (p/let [resp (axios/get url #js {:headers headers :params params})
            resp-clj (js->clj resp :keywordize-keys true)
            ids (drop 10 (map #(:id %) (:data (:data resp-clj))))]
      (println "Purge started:" (count ids) "threads to delete\n")
      (purge-threads-with-ids! ids))))

(comment
  (purge-threads! 10)
  :rcf)