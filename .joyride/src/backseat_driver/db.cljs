(ns backseat-driver.db)

(def ^:private default-db {:disposables []
                           :assistant+ nil
                           :thread+ nil
                           :last-message nil
                           :channel nil
                           :interrupted? false
                           :channel-shown? false})

(defonce !db (atom nil))

(defn init-db! []
  (reset! !db default-db))