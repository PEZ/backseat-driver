(ns backseat-driver.assistants
  (:require ["vscode" :as vscode]
            [backseat-driver.context :as context]
            [backseat-driver.db :as db]
            [backseat-driver.fs :as bd-fs]
            [backseat-driver.openai-api :as openai-api]
            [backseat-driver.prompts :as prompts]
            [backseat-driver.threads :as threads]
            [backseat-driver.ui :as ui]
            [backseat-driver.util :as util]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(def ^:private gpt4 "gpt-4-1106-preview")
(def ^:private gpt3 "gpt-3.5-turbo-1106")

(def assistant-conf {:name "Backseat Driver"
                     :instructions prompts/system-instructions
                     :tools [{:type "code_interpreter"}
                             #_{:type "retrieval"}]
                     :model gpt4})

(defn get-or-create-assistant!+ []
  (p/let [assistant-storage-key "backseat-driver-assistant-id"
          global-state (-> (joyride/extension-context) .-globalState)
          existing-assistant-id (.get global-state assistant-storage-key)
          assistant (if existing-assistant-id
                      (-> (openai-api/openai.beta.assistants.retrieve existing-assistant-id)
                          (.catch (fn [e]
                                    (js/console.error e)
                                    (openai-api/openai.beta.assistants.create (clj->js assistant-conf)))))
                      (openai-api/openai.beta.assistants.create (clj->js assistant-conf)))
          assistant-id (.-id assistant)]
    (.update global-state assistant-storage-key assistant-id)
    assistant))

(def ^:private done-statuses #{"completed" "failed" "expired" "cancelled"})
(def ^:private  poll-interval 100)

(defn- retrieve-poller+ [thread run]
  (p/create
   (fn [resolve reject]
     (let [retriever (fn retriever [tries]
                       (ui/say-one ".")
                       (p/let [retrieved-js (openai-api/openai.beta.threads.runs.retrieve
                                             (.-id thread)
                                             (.-id run))
                               retrieved (util/->clj retrieved-js)
                               messages (openai-api/openai.beta.threads.messages.list (.-id thread))
                               status (:status retrieved)]
                         (if (done-statuses status)
                           (do
                             (ui/say-ln! "")
                             (when-not (= "completed" status)
                               (ui/say-ln! "status:" status)
                               (println "status:" status)
                               (println retrieved)
                               (bd-fs/append-to-log (pr-str retrieved)))
                             (resolve messages))
                           (if (:interrupted? @db/!db)
                             (do
                               (swap! db/!db assoc :interrupted? false)
                               (reject :interrupted))
                             (js/setTimeout
                              #(retriever (inc tries))
                              poll-interval)))))]
       (retriever 0)))))

(defn- call-assistance!+ [assistant thread input]
  (p/let
   [include-file-content? (threads/maybe-add-shared-file!? thread (:path (context/current-file)))
    augmented-input (prompts/augmented-user-input input include-file-content?)
    _message (openai-api/openai.beta.threads.messages.create (.-id thread)
                                                             (clj->js {:role "user"
                                                                       :content augmented-input}))
    run (openai-api/openai.beta.threads.runs.create
         (.-id thread)
         (clj->js {:assistant_id (.-id assistant)
                   :model gpt4}))]
    (retrieve-poller+ thread run)))

(defn- new-assistant-messages [clj-messages last-created-at]
  (if last-created-at
    (filter #(and (> (:created_at %) last-created-at)
                  (= (:role %) "assistant"))
            clj-messages)
    (filter #(= (:role %) "assistant")
            clj-messages)))

(defn- message-text [m]
  (-> m :content first :text :value))


(defn advice!+ []
  (swap! db/!db assoc :interrupted? false)
  (-> (p/let [assistant (:assistant+ @db/!db)
              thread (:thread+ @db/!db)
              input (vscode/window.showInputBox #js {:prompt "What do you want say to the assistant?"
                                                     :placeHolder "Something something"
                                                     :ignoreFocusOut true})]
        (when-not (= js/undefined input)
          (p/let [_ (ui/user-says! input)
                  _ (threads/maybe-add-title!? thread input)
                  api-messages (call-assistance!+ assistant thread input)
                  _ (def api-messages api-messages)
                  clj-messages (util/->clj (-> api-messages .-body .-data))
                  last-created-at (some-> @db/!db :last-message :created_at)
                  new-messages (new-assistant-messages clj-messages last-created-at)
                  _ (swap! db/!db assoc :last-message (first clj-messages))
                  message-texts (map message-text new-messages)]
            (ui/assistant-says! message-texts)
            :advice-given+)))
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (backseat-driver.app/init!)
  (util/->clj (-> api-messages .-body .-data))
  (swap! db/!db assoc :interrupted? true)
  (swap! db/!db assoc :interrupted? false)
  @db/!db
  :rcf)


