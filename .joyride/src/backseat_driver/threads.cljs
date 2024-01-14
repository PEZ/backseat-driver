(ns backseat-driver.threads
  (:require [joyride.core :as joyride]
            [backseat-driver.util :as util]))


(def ^:private threads-storage-key "backseat-driver-threads")

(defn retrieve-saved-threads []
  (let [workspace-state (-> (joyride/extension-context) .-workspaceState)]
    (-> (.get workspace-state threads-storage-key #js {}) util/->clj)))

(defn save-thread!+
  [thread title]
  (let [stored-threads (js->clj (retrieve-saved-threads))
        threads (assoc stored-threads (.-id thread) {:thread-id (.-id thread)
                                                     :created-at (.-created_at thread)
                                                     :updated-at (-> (js/Date.) .getTime)
                                                     :title title})
        workspace-state (-> (joyride/extension-context) .-workspaceState)]
    (.update workspace-state threads-storage-key (clj->js threads))))


(defn retrieve-saved-threads-sorted []
  (-> (retrieve-saved-threads)
      (util/->vec-sort-vals-by :created-at)))

(comment
  (-> (joyride/extension-context) .-workspaceState (.update "backseat-driver-threads" js/undefined))
  (save-thread!+ #js {:id "foo" :created_at (js/Date.) :something "something too"}
                 "My Foo Thread")
  (save-thread!+ #js {:id "bar" :created_at (js/Date.) :something "something too"}
                 "A BAR THREAD")
  (retrieve-saved-threads)
  (retrieve-saved-threads-sorted)
  :rcf)