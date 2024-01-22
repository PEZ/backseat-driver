(ns backseat-driver.threads
  (:require [backseat-driver.openai-api :as openai-api]
            [joyride.core :as joyride]
            [backseat-driver.util :as util]
            [promesa.core :as p]
            [backseat-driver.ui :as ui]
            [clojure.string :as string]
            [backseat-driver.db :as db]
            [backseat-driver.prompts :as prompts]))

(def persistance-key "backseat-driver-threads")
(def ^:private max-title-length 120)

(defn- persisted-threads-data [p-key]
  (let [workspace-state (-> (joyride/extension-context) .-workspaceState)]
    (-> (.get workspace-state p-key #js {}) util/->clj)))

(defn- persist-threads-data!+ [threads-data p-key]
  (let [workspace-state (-> (joyride/extension-context) .-workspaceState)]
    (.update workspace-state p-key (clj->js threads-data))))

(defn- update-thread-data
  [api-thread new-title current-time old-thread-data]
  (let [new-title (or (:title old-thread-data) new-title)]
    (doto {:thread-id (.-id api-thread)
           :created-at (.-created_at api-thread)
           :updated-at current-time
           :title new-title})))

(defn- save-thread-data!+
  [!db api-thread title]
  (let [thread-id (keyword (.-id api-thread))
        current-time ((:current-time-fn @!db))]
    (swap! !db update-in [:threads thread-id]
           (partial update-thread-data api-thread title current-time))))

(defn- all-threads-sorted [db]
  (-> (:threads db)
      (util/->vec-sort-vals-by :updated-at)))

(defn fetch-messages!+ [thread-id]
  (p/let [api-messages (openai-api/openai.beta.threads.messages.list thread-id)]
    (def api-messages-debug api-messages)
    (util/->clj (-> api-messages .-body .-data))))

(defn message-text [message]
  (-> message :content first :text :value))

(defn- message-info [message]
  {:role (:role message)
   :text (message-text message)})

(defn- trim-user-text [text start-marker]
  (let [[_ user-message] (string/split text start-marker)]
    (string/triml user-message)))

(defn- renderable-message [info]
  (cond
    (= "assistant" (:role info)) (ui/assistant-says (:text info))
    (= "user" (:role info))      (ui/user-says (trim-user-text (:text info) prompts/user-input-marker))
    :else (str "Unknown role: " (:role info))))

(defn- messages->string [messages]
  (->> messages
       reverse
       (map message-info)
       (map renderable-message)
       (string/join "\n")
       (string/triml)))

(defn render-thread! [messages]
  (ui/clear!)
  (ui/render! (messages->string messages)))

(defn switch-to-thread!+ [!db thread-id]
  (p/let [[messages api-thread] (p/all
                                 [(fetch-messages!+ thread-id)
                                  (openai-api/openai.beta.threads.retrieve thread-id)])]
    (render-thread! messages)
    (swap! !db assoc
           :current-thread api-thread
           :messages messages)
    (save-thread-data!+ !db api-thread nil)))

(defn create-thread!+ [!db]
  (p/let [api-thread (openai-api/openai.beta.threads.create)]
    (swap! !db assoc
           :current-thread api-thread
           :messages [])
    (save-thread-data!+ !db api-thread nil)
    api-thread))

(defn init!+ [!db p-key]
  (let [persisted (persisted-threads-data p-key)]
    (swap! !db assoc :threads persisted)
    (remove-watch !db :persist-thread)
    (add-watch !db :persist-thread (fn [_k _r o n]
                                     (when-not (= (:threads o) (:threads n))
                                       (persist-threads-data!+ (:threads n) p-key))))
    (let [latest-thread-id (some-> (all-threads-sorted @!db) last :thread-id)]
      (p/let [api-thread (if latest-thread-id
                           (openai-api/openai.beta.threads.retrieve latest-thread-id)
                           (create-thread!+ !db))]

        (when latest-thread-id
          (swap! !db assoc :current-thread api-thread)
          (p/let [messages (fetch-messages!+ latest-thread-id)]
            (swap! !db assoc :messages messages)
            (save-thread-data!+ !db api-thread nil)))
        api-thread))))

(comment
  (:current-time-fn @db/!db)
  (:messages @db/!db)
  (:current-thread @db/!db)
  (:threads @db/!db)
  (all-threads-sorted @db/!db)
  (switch-to-thread!+ db/!db "thread_9H3iW9A6OnwmGKHH2yIDGt6c")
  (switch-to-thread!+ db/!db "thread_YpCijPby7r37fzgyfkvNVGK8") ; 1705419622782

  (def messages (util/->clj (-> api-messages-debug .-body .-data)))

  ;; Tread carefully!
  (-> (joyride/extension-context) .-workspaceState (.update "backseat-driver-threads" js/undefined))
  (persisted-threads-data persistance-key)
  (all-threads-sorted @db/!db)

  (def forty-thread-ids ["thread_XVWebAdeGFXgB7rSuAgQlHUr"
                         "thread_RuDNwNwNAQAi9Jx6iPJWLGoQ"
                         "thread_s0s2vi5Th3bGOyUTCM4k8yH0"
                         "thread_Vu8Ua7KNCb8w1cqdD3Shhb9Q"
                         "thread_iFVxlXA56BTfExhma0fUoMwA"
                         "thread_PApld6L5yEL5WY6XuHl6iEaR"
                         "thread_JcCDAAMobAaxugD4APRqwNfU"
                         "thread_wu3fNFbIUxd3SDBUkSbxmiZT"
                         "thread_9H3iW9A6OnwmGKHH2yIDGt6c"
                         "thread_YpCijPby7r37fzgyfkvNVGK8"
                         "thread_EASAD9IfcQDrNQ7M7xClU9pt"
                         "thread_9sSZKIHHW2IRFnKwADZ0vchi"
                         "thread_GrOO2CtR3XoRiAIix7y5e9M8"
                         "thread_LwdjuznJiGD4VT3tKXahYVy9"
                         "thread_SrjJdXgLXJ4P5P2TvpKCHbOI"
                         "thread_4htNZ2HSYO9Geakl1lEOgNB9"
                         "thread_38QQldO5IosziJ8z31oBRL9c"
                         "thread_sTPXjPiWJiaum0IlpnIgdoII"
                         "thread_9VIxtYHrFCV4ZQKmiBeK3AXl"
                         "thread_UukTP54yc7BLYNJQrOAvbCZs"
                         "thread_q19P0tW4METk3WYbIO8aRHjE"
                         "thread_PML578JSF0EAjIYMHJzABx9D"
                         "thread_0GuggpVs3HqimD7TJvzNKAFU"
                         "thread_MtAmIEu0asFoWtLZKHzoIICH"
                         "thread_PGHEFydrehLqvnCxrSegw38F"
                         "thread_uFgIYCMu9EyJCiWVpdEetWkY"
                         "thread_YacWc4FHhlzWNCUtBE0fTMvn"
                         "thread_qhEKFeZHlRcoml9lTnOQMdC4"
                         "thread_Xxc5VzJeHZlqMeVWYGTV164G"
                         "thread_daPmzGOnzXPw8y1HauEdAMAn"
                         "thread_X1hgfMiyfqLOR4nHUaaYZcTr"
                         "thread_DdYM41eKpOS1etRkmv9vOwrU"
                         "thread_kQBggsqH1zneOemzq6nPutsk"
                         "thread_a2d2JLLYzhfPlczxtKUrbuLc"
                         "thread_99QJBl6NXdz3OnD6OAL9johA"
                         "thread_w1DfxHRxojyo8mRyPWOVDPqL"
                         "thread_jp4AD8U9i9DY4vCnpuuyTYWT"
                         "thread_UVWAIhwK7uRg0EBsL6N4EwUi"
                         "thread_i17OBaMGboC4e6Co9JEsVE3t"
                         "thread_tZD39MBeOl8lPnOQCn8Tfg2R"])

  :rcf)