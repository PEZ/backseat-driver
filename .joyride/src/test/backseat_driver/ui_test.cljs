#_{:clj-kondo/ignore [:private-call]}
(ns test.backseat-driver.ui-test
  (:require [cljs.test :refer [deftest is testing]]
            [backseat-driver.ui :as ui]))

(deftest palette-items
  (testing "Active run menu"
    (is (= [:interrupt-polling! :show-channel!]
           (map :function (#'ui/palette-items {:thread-running? true})))
        "shows active run menu correct(!) content in correct order")))

(comment
  (cljs.test/run-tests)
  ; (ns-unmap *ns* 'palette-items)
  :rcf)