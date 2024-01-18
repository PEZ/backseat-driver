(ns test-runner.runner
  (:require [clojure.string :as string]
            [cljs.test]
            [test-runner.db :as db]
            [promesa.core :as p]))

(defn- write [& xs]
  (js/process.stdout.write (string/join " " xs)))

(defmethod cljs.test/report [:cljs.test/default :begin-test-var] [m]
  (write "===" (str (-> m :var meta :name) ": ")))

(defmethod cljs.test/report [:cljs.test/default :end-test-var] [m]
  (write " ===\n"))

(def old-pass (get-method cljs.test/report [:cljs.test/default :pass]))

(defmethod cljs.test/report [:cljs.test/default :pass] [m]
  (binding [*print-fn* write] (old-pass m))
  (write "✅")
  (swap! db/!state update :pass inc))

(def old-fail (get-method cljs.test/report [:cljs.test/default :fail]))

(defmethod cljs.test/report [:cljs.test/default :fail] [m]
  (binding [*print-fn* write] (old-fail m))
  (write "❌")
  (swap! db/!state update :fail inc))

(def old-error (get-method cljs.test/report [:cljs.test/default :fail]))

(defmethod cljs.test/report [:cljs.test/default :error] [m]
  (binding [*print-fn* write] (old-error m))
  (write "🚫")
  (swap! db/!state update :error inc))

(def old-end-run-tests (get-method cljs.test/report [:cljs.test/default :end-run-tests]))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (binding [*print-fn* write]
    (old-end-run-tests m)
    (let [{:keys [running pass fail error]} @db/!state
          passed-minimum-threshold 2
          fail-reason (cond
                        (< 0 (+ fail error)) "test-runner: FAILURE: Some tests failed or errored"
                        (< pass passed-minimum-threshold) (str "test-runner: FAILURE: Less than " passed-minimum-threshold " assertions passed")
                        :else nil)]
      (println "test-runner: tests run, results:" (select-keys  @db/!state [:pass :fail :error]))
      (if fail-reason
        (p/reject! running fail-reason)
        (p/resolve! running true)))))

(defn- run-tests-impl!+ [ns-syms]
  (try
    (doseq [ns-sym ns-syms]
      (require ns-sym :reload)
      (cljs.test/run-tests ns-sym))
    (catch :default e
      (p/reject! (:running @db/!state) e))))

(defn workspace-activated! []
  (println "test-runner: Workspace activated.")
  (swap! db/!state assoc :ws-activated? true))

(defn run-ns-tests!+ [ns-syms]
  (let [running (p/deferred)]
    (swap! db/!state assoc :running running)
    (if (:ws-activated? @db/!state)
      (run-tests-impl!+ ns-syms)
      (do
        (println "test-runner: Waiting for workspace to activate...")
        (add-watch db/!state :runner (fn [k r _o n]
                                       (when (:ws-activated? n)
                                         (remove-watch r k)
                                         (run-tests-impl!+ ns-syms))))))
    running))

(comment
  (run-ns-tests!+ ['test.backseat-driver.ui-test])
  @db/!state
  (swap! db/!state assoc :ws-activated? false)
  (swap! db/!state assoc :ws-activated? true)
  (add-watch db/!state :foo (fn [k r o n]
                              (println "BOOM!"
                               [k r o n])
                              (remove-watch r k)))
  :rcf)

