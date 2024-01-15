(ns backseat-driver.fs
  (:require ["fs" :as fs]
            ["os" :as os]
            ["vscode" :as vscode]
            ["path" :as path]))

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