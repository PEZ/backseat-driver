(ns backseat-driver.db)

(def ^:private default-db {:disposables []
                           :assistant+ nil
                           :gpt :gpt-4
                           :current-thread nil
                           :threads {}
                           :current-time-fn #(-> (js/Date.) .getTime (/ 1000))
                           :messages nil
                           :channel nil
                           :thread-running? false
                           :interrupted? false
                           :channel-shown? false
                           :ws-activated? false})

(defonce !db (atom nil))

(defn init! []
  (reset! !db default-db))

(comment
  @!db
  ((:current-time-fn @!db))
  :rcf)

