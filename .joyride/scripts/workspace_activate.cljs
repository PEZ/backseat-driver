(ns workspace-activate
  (:require [joyride.core :as joyride]
            [backseat-driver.app]
            [test-runner.runner] ;; The test runner needs to know when the workspace is activated
            ))

;; This script only initializes the Backseat Driver app,
;; It must be done before any keyboard shortcuts for the app works.
;; See backseat-driver.app for a sample shortcut binding

;; The app is intended to be a global (User) script.
;; Try it out as a workspace script first, and if you want
;; to use it in your projects, see README.md for how to install
;; it as a User script.

(defn- -main []
  (println "Hello World, from Backseat Driver demo workspace_activate.cljs script")
  (if js/process.env.JOYRIDE_HEADLESS
    (println "HEADLESS TEST RUN: Not Initializing Backseat Driver app.")
    (do
      (println "Initializing Backseat Driver app...")
      (backseat-driver.app/init!)))
  (test-runner.runner/workspace-activated!))

(when (= (joyride/invoked-script) joyride/*file*)
  (-main))
