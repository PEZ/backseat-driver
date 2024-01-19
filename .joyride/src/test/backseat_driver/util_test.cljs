(ns test.backseat-driver.util-test
  (:require [cljs.test :refer [deftest is testing]]
            [backseat-driver.util :as util]))

(deftest map-matches-spec?
  (testing "Matching"
    (is (= true
           (util/map-matches-spec? {:get_context {:context-part "current-ns"}}
                                   {:get_context {:context-part #{"current-form" "current-ns"}}}))
        "Two levels deep, value in spec set")
    (is (= true
           (util/map-matches-spec? {:a {:b {:c {:d 9}}}}
                                   {:a {:b {:c {:d 9}
                                            :e {:f 5}}}}))
        "Many levels deep, open spec"))
  (testing "Not matching"
    (is (= false
           (util/map-matches-spec? {:get_context {:context-part "current-invalid-thing"}}
                                   {:get_context {:context-part #{"current-form" "current-ns"}}}))
        "Two levels deep, invalid value")
    (is (= false
           (util/map-matches-spec? {:a {:b {:c {:e 9}}}}
                                   {:a {:b {:c {:d 9}
                                            :e {:f 5}}}}))
        "Many levels deep, invalid key")
    (is (= false
           (util/map-matches-spec? {:a {:b {:c {:d 0}}}}
                                   {:a {:b {:c {:d 9}
                                            :e {:f 5}}}}))
        "Many levels deep, invalid value")))

(comment
  (map-matches-spec?)
  (cljs.test/run-tests)
  ; (ns-unmap *ns* 'palette-items)
  :rcf)