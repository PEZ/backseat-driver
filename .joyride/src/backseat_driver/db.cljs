(ns backseat-driver.db)

(def ^:private default-db {:disposables []
                           :assistant+ nil
                           :thread+ nil
                           :last-message nil
                           :channel nil
                           :thread-running? false
                           :interrupted? false
                           :channel-shown? false
                           :ws-activated? false})

(defonce !db (atom nil))

(defn init-db! []
  (reset! !db default-db))