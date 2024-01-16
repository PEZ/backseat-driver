(ns backseat-driver.threads
  (:require [joyride.core :as joyride]
            [backseat-driver.util :as util]
            [promesa.core :as p]))

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

(comment
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