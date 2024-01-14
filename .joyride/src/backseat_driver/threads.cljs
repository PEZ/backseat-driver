(ns backseat-driver.threads
  (:require ["vscode" :as vscode]
            [joyride.core :as joyride]
            [backseat-driver.util :as util]))


(def ^:private threads-storage-key "backseat-driver-threads")

(defn retrieve-saved-threads []
  (let [workspace-state (-> (joyride/extension-context) .-workspaceState)]
    (-> (.get workspace-state threads-storage-key #js {}) util/->clj)))

(defn save-thread!+
  [thread title shared-file-path]
  (let [stored-threads (js->clj (retrieve-saved-threads))
        _ (def stored-threads stored-threads)
        stored-thread (get stored-threads (keyword (.-id thread)))
        _ (def stored-thread stored-thread)
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

(comment
  (-> (joyride/extension-context) .-workspaceState (.update "backseat-driver-threads" js/undefined))
  (save-thread!+ #js {:id "foo" :created_at (-> (js/Date.) .getTime) :something "something too"}
                 "My Foo Thread"
                 "/some/path/foo.cljs")
  (save-thread!+ #js {:id "bar" :created_at (-> (js/Date.) .getTime) :something "something too"}
                 "A BAR THREAD"
                 nil)
  (retrieve-saved-threads)
  (retrieve-saved-threads-sorted)
  :rcf)