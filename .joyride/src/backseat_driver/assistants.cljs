(ns backseat-driver.assistants
  (:require ["ext://betterthantomorrow.calva$v1" :as calva]
            ["vscode" :as vscode]
            [backseat-driver.db :as db]
            [backseat-driver.fs :as bd-fs]
            [backseat-driver.openai-api :as openai-api]
            [backseat-driver.prompts :as prompts]
            [backseat-driver.threads :as threads]
            [backseat-driver.ui :as ui]
            [backseat-driver.util :as util]
            [joyride.core :as joyride]
            [promesa.core :as p]))

(def assistant-name "Backseat Driver")

(def ^:private gpt4 "gpt-4-1106-preview")
(def ^:private gpt3 "gpt-3.5-turbo-1106")

(defn get-or-create-assistant!+ []
  (p/let [assistant-storage-key "backseat-driver-assistant-id"
          global-state (-> (joyride/extension-context) .-globalState)
          existing-assistant-id (.get global-state assistant-storage-key)
          assistant (if existing-assistant-id
                      (openai-api/openai.beta.assistants.retrieve existing-assistant-id)
                      (openai-api/openai.beta.assistants.create (clj->js {:name "Backseat Driver"
                                                               :instructions prompts/system-instructions
                                                               :tools [{:type "code_interpreter"}
                                                                       #_{:type "retrieval"}]
                                                               :model gpt4})))
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
                               _ (def retrieved retrieved)
                               messages (openai-api/openai.beta.threads.messages.list (.-id thread))
                               status (:status retrieved)]
                         (if (done-statuses status)
                           (do
                             (ui/say-ln "")
                             (when-not (= "completed" status)
                               (ui/say-ln "status:" status)
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

(defn ask!+ []
  (def instructions prompts/system-instructions)
  (-> (:channel @db/!db) (.show true))
  (swap! db/!db assoc :interrupted? false)
  (-> (p/let [assistant (:assistant+ @db/!db)
              thread (:thread+ @db/!db)
              storage-thread (get (threads/retrieve-saved-threads) (keyword (.-id thread)))
              _ (def thread thread)
              input (vscode/window.showInputBox #js {:prompt "What do you want say to the assistant?"
                                                     :placeHolder "Something something"
                                                     :ignoreFocusOut true})]
        (def input input)
        (when-not (= js/undefined input)
          (p/let
           [_ (ui/say-ln "\nMe:" input)
            include-file-content? (not (:title storage-thread))
            _ (when-not (:title storage-thread)
                (threads/save-thread!+ thread (subs input 0 120)))
            augmented-input (prompts/augmented-user-input input include-file-content?)
            _ (def augmented-input augmented-input)
            _message (openai-api/openai.beta.threads.messages.create (.-id thread)
                                                          (clj->js {:role "user"
                                                                    :content augmented-input}))
            #_#__ (def _message _message)
            run (openai-api/openai.beta.threads.runs.create
                 (.-id thread)
                 (clj->js {:assistant_id (.-id assistant)
                           :instructions (prompts/system-and-context-instructions
                                          include-file-content?)
                           :model gpt4}))
            api-messages (retrieve-poller+ thread run)
            _ (def api-messages api-messages)
            clj-messages (util/->clj (-> api-messages .-body .-data))
            _ (def clj-messages clj-messages)
            new-messages (if-let [last-created-at (some-> @db/!db :last-message :created_at)]
                           (filter #(and (> (:created_at %) last-created-at)
                                         (= (:role %) "assistant"))
                                   clj-messages)
                           (filter #(= (:role %) "assistant")
                                   clj-messages))
            _ (def new-messages new-messages)
            _ (swap! db/!db assoc :next-last-message (:last-message @db/!db))
            _ (swap! db/!db assoc :last-message (first clj-messages))
            message-texts (map (fn [m]
                                 (-> m :content first :text :value))
                               new-messages)
            _ (def message-texts message-texts)]
            (ui/say-ln "")
            (doseq [text message-texts]
              (ui/say-ln (str assistant-name ":"))
              (ui/say-ln text))
            new-messages)))
      (p/catch (fn [e] (println "ERROR: " e "\n")))))

(comment
  (backseat-driver.app/init!)
  (p/let [result (ask!+)]
    (def result result)
    (-> result pr-str calva/pprint.prettyPrint .-value println))
  (swap! db/!db assoc :interrupted? true)
  (swap! db/!db assoc :interrupted? false)
  @db/!db
  (println (-> @db/!db :next-last-message :content first :text :value))
  (println (-> @db/!db :last-message :content first :text :value))
  :rcf)




