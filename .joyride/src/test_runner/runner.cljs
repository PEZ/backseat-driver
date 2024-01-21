(ns test-runner.runner
  (:require ["vscode" :as vscode]
            ["path" :as path]
            [clojure.string :as string]
            [cljs.test]
            [promesa.core :as p]))

(def ^:private default-db {:runner+ nil
                           :ready-to-run? false
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

(def original-error (get-method cljs.test/report [:cljs.test/default :error]))

(defmethod cljs.test/report [:cljs.test/default :error] [m]
  (binding [*print-fn* write] (original-error m))
  (write "🚫")
  (swap! !state update :error inc))

(def original-end-run-tests (get-method cljs.test/report [:cljs.test/default :end-run-tests]))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (binding [*print-fn* write]
    (original-end-run-tests m)
    (let [{:keys [runner+ pass fail error] :as state} @!state
          passed-minimum-threshold 2
          fail-reason (cond
                        (< 0 (+ fail error)) "test-runner: 👎 FAILURE: Some tests failed or errored"
                        (< pass passed-minimum-threshold) (str "test-runner: 👎 FAILURE: Less than " passed-minimum-threshold " assertions passed. (Passing: " pass ")")
                        :else nil)]
      (println "test-runner: tests run, results:"
               (select-keys state [:pass :fail :error]) "\n")
      (when runner+ ; When not using the runner, there's no promise to resolve or reject
        (if fail-reason
          (p/reject! runner+ fail-reason)
          (p/resolve! runner+ true))))))

(defn- run-tests-impl!+ [test-nss]
  (write "test-runner: Starting tests...")
  (init-counters!)
  (try
    (doseq [test-ns test-nss]
      (require test-ns :reload))
    (apply cljs.test/run-tests test-nss)
    (catch :default e
      (p/reject! (:runner+ @!state) e))))

(defn ready-to-run-tests!
  "The test runner will wait for this to be called before running any tests.
   `ready-message` will be logged when this is called"
  [ready-message]
  (println "test-runner:" ready-message)
  (swap! !state assoc :ready-to-run? true))

(defn run-ns-tests!+
  "Runs the `test-nss` test
   NB: Will wait for `ready-to-run-tests!` to be called before doing so.
   `waiting-message` will be logged if the test runner is waiting."
  [test-nss waiting-message]
  (let [runner+ (p/deferred)]
    (swap! !state assoc :runner+ runner+)
    (if (:ready-to-run? @!state)
      (run-tests-impl!+ test-nss)
      (do
        (println "test-runner: " waiting-message)
        (add-watch !state :runner (fn [k r _o n]
                                       (when (:ready-to-run? n)
                                         (remove-watch r k)
                                         (run-tests-impl!+ test-nss))))))
    runner+))

(defn- uri->ns-symbol [uri]
  (-> uri
      (vscode/workspace.asRelativePath)
      (string/split path/sep)
      (->> (drop 2)
           (string/join "."))
      (string/replace "_" "-")
      (string/replace #"\.clj[cs]$" "")
      symbol))

(defn- glob->ns-symbols [glob]
  (p/let [uris (vscode/workspace.findFiles glob)]
    (map uri->ns-symbol uris)))

(defn run-tests!+
  "Runs the tests in any `_test.cljs` files in `.joyride/src/test/`
   NB: Will wait for `ready-to-run-tests!` to be called before doing so.
  `waiting-message` will be logged if the test runner is waiting."
  [waiting-message]
  (p/let [nss (glob->ns-symbols ".joyride/src/test/**/*_test.clj[sc]")]
    (println "test-runner: Running tests in these" (count nss) "namespaces" (pr-str nss))
    (run-ns-tests!+ nss waiting-message)))

(defn watch!+ [waiting-message]
  (let [glob-pattern "**/.joyride/**/*.cljs"
        watcher (vscode/workspace.createFileSystemWatcher glob-pattern)
        run-fn (fn run-fn
                 ([uri reason]
                  (run-fn uri reason nil))
                 ([uri reason waiting-message]
                  (println reason (vscode/workspace.asRelativePath uri))
                   (println "Running tests...")
                   (-> (run-tests!+ waiting-message)
                       (p/then (fn [_]
                                 (println "YAY!")))
                       (p/catch (fn [e]
                                  (println "NAY!" e)))
                       (p/finally (fn []
                                    (println "Waiting for changes..."))))))]
    (run-fn "." "Watcher started" waiting-message)
    (.onDidChange watcher (fn [uri]
                            (run-fn uri "File changed:")))
    (.onDidCreate watcher (fn [uri]
                            (run-fn uri "File created:")))
    (.onDidDelete watcher (fn [uri]
                            (run-fn uri "File deleted:"))))
  (p/deferred) ; We leave the vscode electron test runner waiting for this one
  )
