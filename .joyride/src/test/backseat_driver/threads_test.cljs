(ns test.backseat-driver.threads-test
  (:require [cljs.test :refer [deftest is testing]]
            [backseat-driver.threads :as threads]))

(defn mk-db [db a-time]
  (-> db
      (merge {:get-time-fn (constantly a-time)})
      atom))

(comment
  (def !a (mk-db {:a 1 :b "b"} "today"))
  ((:get-time-fn @!a)) ;; => "today"
  :rcf)

(deftest update-thread-data-test
  (testing "Updating thread data title"
    (let [old-thread-data {}
          api-thread {:id "123" :created_at :some-creation-time}
          new-title "New Thread Title"
          result (#'threads/update-thread-data
                  api-thread new-title :some-time old-thread-data)]
      (is (= "New Thread Title"
             (:title result))
          "Should update with new title if title is not present in old data"))
    (let [old-thread-data {:id "123" :title "Existing title"}
          api-thread {:id "123" :created_at "same-date"}
          new-title "New Thread Title"
          result (#'threads/update-thread-data
                  api-thread new-title :some-time old-thread-data)]
      (is (= "Existing title" (:title result))
          "Should not update with new title if title is present in old data"))))