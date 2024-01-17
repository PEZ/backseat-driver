(ns backseat-driver.threads
  (:require [backseat-driver.openai-api :as openai-api]
            [joyride.core :as joyride]
            [backseat-driver.util :as util]
            [promesa.core :as p]
            [backseat-driver.ui :as ui]
            [clojure.string :as string]
            [backseat-driver.db :as db]))

(def ^:private threads-storage-key "backseat-driver-threads")
(def ^:private max-title-length 120)

(defn retrieve-saved-threads []
  (let [workspace-state (-> (joyride/extension-context) .-workspaceState)]
    (-> (.get workspace-state threads-storage-key #js {}) util/->clj)))

(defn save-thread!+
  [thread title shared-file-path]
  (p/let [stored-threads (js->clj (retrieve-saved-threads))
          stored-thread (get stored-threads (keyword (.-id thread)))
          shared-files (:shared-files stored-thread #{})
          new-shared-files (when shared-file-path
                             (conj shared-files shared-file-path))
          new-title (if (:title stored-thread) (:title stored-thread) title)
          threads (assoc stored-threads (.-id thread) {:thread-id (.-id thread)
                                                       :created-at (.-created_at thread)
                                                       :updated-at (-> (js/Date.) .getTime)
                                                       :title new-title
                                                       :shared-files (set new-shared-files)})
          workspace-state (-> (joyride/extension-context) .-workspaceState)]
    (.update workspace-state threads-storage-key (clj->js threads))))

(defn retrieve-saved-threads-sorted []
  (-> (retrieve-saved-threads)
      (util/->vec-sort-vals-by :created-at)))

(defn stored-thread [thread]
  (get (retrieve-saved-threads) (keyword (.-id thread))))

(defn maybe-add-title!?+ [thread title]
  (when-not (:title (stored-thread thread))
    (save-thread!+ thread (subs title 0 max-title-length) nil)
    true))

(defn maybe-add-shared-file!?+ [thread file-path]
  (let [shared-file (some #{file-path} (:shared-files (stored-thread thread)))]
    (when-not shared-file
      (save-thread!+ thread nil file-path)
      true)))

(defn fetch-messages!+ [thread-id]
  (def thread-id thread-id)
  (p/let [api-messages (openai-api/openai.beta.threads.messages.list thread-id)]
    (def api-messages api-messages)
    (util/->clj (-> api-messages .-body .-data))))

(defn message-text [message]
  (-> message :content first :text :value))

(defn message-info [message]
  (def message message)
  {:role (:role message)
   :text (message-text message)})

(defn renderable-message [info]
  (def info info)
  (cond
    (= "assistant" (:role info)) (ui/assistant-says (:text info))
    (= "user" (:role info))      (ui/user-says (:text info))
    :else (str "Unknown role: " (:role info))))

(defn messages->string [messages]
  (->> messages
       reverse
       (map message-info)
       (map renderable-message)
       (string/join "\n")
       (string/triml)))

(defn render-thread!+ [thread-id]
  (p/let [thread (openai-api/openai.beta.threads.retrieve thread-id)
          messages (fetch-messages!+ thread-id)]
    (ui/clear!)
    (ui/render! (messages->string messages))
    (swap! db/!db assoc :last-message (first messages))
    (swap! db/!db assoc :thread+ thread)))

(comment
  (:last-message @db/!db)
  (:thread+ @db/!db)
  (render-thread!+ "thread_qhEKFeZHlRcoml9lTnOQMdC4")
  (render-thread!+ "thread_jp4AD8U9i9DY4vCnpuuyTYWT")
  (def messages (util/->clj (-> api-messages .-body .-data)))
  (map message-text messages)
  (message-info (first messages))
  (-> messages first message-info renderable-message)
  (messages->string messages)

  (-> (joyride/extension-context) .-workspaceState (.update "backseat-driver-threads" js/undefined))
  (save-thread!+ #js {:id "foo" :created_at (-> (js/Date.) .getTime) :something "something too"}
                 "My Foo Thread"
                 "/some/path/foo.cljs")
  (stored-thread #js {:id "foo" :created_at (-> (js/Date.) .getTime) :something "something too"})
  (save-thread!+ #js {:id "bar" :created_at (-> (js/Date.) .getTime) :something "something too"}
                 "A BAR THREAD"
                 nil)
  (retrieve-saved-threads)
  (retrieve-saved-threads-sorted)
  :rcf)