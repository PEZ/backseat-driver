(ns workspace-activate
  (:require [joyride.core :as joyride]
            [seatbelt.runner] ;; The test runner needs to know when the workspace is activated
            ))

;; This script only initializes the Backseat Driver app,
;; It must be done before any keyboard shortcuts for the app works.
;; See backseat-driver.app for a sample shortcut binding

;; The app is intended to be a global (User) script.
;; Try it out as a workspace script first, and if you want
;; to use it in your projects,s see README.md for how to install
;; it as a User script.

(println "Backseat Driver: Hello World, from workspace_activate.cljs script")

(defn- -main []
  (println "Backseat Driver: -main called")
  (if js/process.env.JOYRIDE_HEADLESS
    (println "HEADLESS TEST RUN: Not Initializing Backseat Driver app.")
    (do
      (println "Initializing Backseat Driver app...")
      (require '[backseat-driver.app])
      ((resolve 'backseat-driver.app/init!))))
  (seatbelt.runner/ready-to-run-tests! "Workspace activated."))

(when (= (joyride/invoked-script) joyride/*file*)
  (-main))
