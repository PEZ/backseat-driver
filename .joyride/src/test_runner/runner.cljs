(ns test-runner.runner
  (:require [clojure.string :as string]
            [cljs.test]
            [promesa.core :as p]))

(def ^:private default-db {:runner+ nil
                           :ws-activated? false
                           :pass 0
                           :fail 0
                           :error 0})

(def ^:private !state (atom default-db))

(defn ^:private init-counters! []
  (swap! !state merge (select-keys default-db [:pass :fail :error])))

(defn- write [& xs]
  (js/process.stdout.write (string/join " " xs)))

(defmethod cljs.test/report [:cljs.test/default :begin-test-var] [m]
  (write "===" (str (-> m :var meta :name) ": ")))

(defmethod cljs.test/report [:cljs.test/default :end-test-var] [m]
  (write " ===\n"))

(def original-pass (get-method cljs.test/report [:cljs.test/default :pass]))

(defmethod cljs.test/report [:cljs.test/default :pass] [m]
  (binding [*print-fn* write] (original-pass m))
  (write "✅")
  (swap! !state update :pass inc))

(def original-fail (get-method cljs.test/report [:cljs.test/default :fail]))

(defmethod cljs.test/report [:cljs.test/default :fail] [m]
  (binding [*print-fn* write] (original-fail m))
  (write "❌")
  (swap! !state update :fail inc))

(def original-error (get-method cljs.test/report [:cljs.test/default :fail]))

(defmethod cljs.test/report [:cljs.test/default :error] [m]
  (binding [*print-fn* write] (original-error m))
  (write "🚫")
  (swap! !state update :error inc))

(def original-start-run-tests (get-method cljs.test/report [:cljs.test/default :start-run-tests]))

(defmethod cljs.test/report [:cljs.test/default :start-run-tests] [m]
  (binding [*print-fn* write] (original-error m))
  (write "test-runner: Starting tests...")
  (init-counters!))

(def original-end-run-tests (get-method cljs.test/report [:cljs.test/default :end-run-tests]))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (binding [*print-fn* write]
    (original-end-run-tests m)
    (let [{:keys [runner+ pass fail error] :as state} @!state
          passed-minimum-threshold 2
          fail-reason (cond
                        (< 0 (+ fail error)) "test-runner: FAILURE: Some tests failed or errored"
                        (< pass passed-minimum-threshold) (str "test-runner: FAILURE: Less than " passed-minimum-threshold " assertions passed. (Passing: " pass ")")
                        :else nil)]
      (println "test-runner: tests run, results:" (select-keys state [:pass :fail :error]) "\n")
      (if fail-reason
        (p/reject! runner+ fail-reason)
        (p/resolve! runner+ true)))))

(defn- run-tests-impl!+ [test-nss]
  (try
    (doseq [test-ns test-nss]
      (require test-ns :reload))
    (apply cljs.test/run-tests test-nss)
    (catch :default e
      (p/reject! (:runner+ @!state) e))))

(defn workspace-activated! []
  (println "test-runner: Workspace activated.")
  (swap! !state assoc :ws-activated? true))

(defn run-ns-tests!+ [test-nss]
  (let [runner+ (p/deferred)]
    (swap! !state assoc :runner+ runner+)
    (if (:ws-activated? @!state)
      (run-tests-impl!+ test-nss)
      (do
        (println "test-runner: Waiting for workspace to activate...")
        (add-watch !state :runner (fn [k r _o n]
                                       (when (:ws-activated? n)
                                         (remove-watch r k)
                                         (run-tests-impl!+ test-nss))))))
    runner+))

(comment
  (run-ns-tests!+ ['test.backseat-driver.ui-test
                   'test.backseat-driver.util-test])
  @!state
  (swap! !state assoc :ws-activated? false)
  (swap! !state assoc :ws-activated? true)

  :rcf)

