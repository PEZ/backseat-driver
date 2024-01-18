(ns backseat-driver.fs
  (:require ["fs" :as fs]
            ["os" :as os]
            ["vscode" :as vscode]
            ["path" :as path]))

(defn path-local-to-workspace? [file-path]
  (when-let [workspace-folders (.-workspaceFolders vscode/workspace)]
    (let [workspace-root (-> workspace-folders first .-uri .-fsPath) ; Assuming single-root workspace
          normalized-file-path (path/resolve workspace-root file-path)
          normalized-workspace-root (path/resolve workspace-root)]
      (.startsWith normalized-file-path normalized-workspace-root))))

(comment
  (path-local-to-workspace? "/User/anders/file")
  ;; => false
  (path-local-to-workspace? "/Users/pez/Projects/openai/a")
  ;; => false
  (path-local-to-workspace? "/Users/pez/Projects/openai/kurshowcrisp")
  ;; => true
  (path-local-to-workspace? ".joyride/src/backseat_driver/prompts.cljs")
  ;; => true
  (path-local-to-workspace? "/Users/pez/Projects/openai/kurshowcrisp/.joyride/")
  ;; => true
  :rcf)

(defn append-to-log [content]
  (fs/appendFile
   (path/join (os/tmpdir) "backseat-driver.log")
   (str content "\n")
   (fn [err]
     (when err
       (js/console.error "Error appending file: " (.message err))))))

(defn write-to-file!+
  [file-path content]
  (let [uri (vscode/Uri.file file-path)
        buffer (js/Buffer.from content "utf-8")]
    (vscode/workspace.fs.writeFile uri buffer)))

(comment
  (def content "hello")
  (write-to-file!+ (path/join (os/tmpdir) "backseat-driver.log") content)
  (append-to-log "This is a log entry.\n")
  :rcf)