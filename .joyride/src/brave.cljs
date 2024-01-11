(ns brave
  (:require ["axios" :as axios]
            [joyride.core :as joyride]
            [promesa.core :as p]
            ["vscode" :as vscode]))

(defn- ->clj [o] (js->clj o :keywordize-keys true))

(defn store-brave-key []
  (p/let [input (vscode/window.showInputBox #js {:title "Your Brave API Key"
                                                 :password true})]
    (when input
      (-> (joyride/extension-context) .-secrets (.store "brave-api-key" input)))))

(defn get-brave-key []
  (p/let [brave-key (-> (joyride/extension-context) .-secrets (.get "brave-api-key"))]
    brave-key))

(comment
  (store-brave-key)
  (get-brave-key)
  :rcf)

(defn search [query]
  (def query query)
  (-> (p/let [api-key (get-brave-key)]
        (def api-key api-key)
        (when api-key
          (let [url (str "https://api.search.brave.com/res/v1/web/search?q=" (js/encodeURIComponent query))
                headers (clj->js {:headers {:X-Subscription-Token api-key
                                            :Accept "application/json"
                                            :Accept-Encoding "gzip"}})]
            (def url url)
            (def headers headers)
            (p/let [response (axios/get url headers)]
              (def response response)
              response))))
      (p/catch (fn [e]
                 (println "Error in search-brave:" e)))))

(comment
  (p/let [search-result (search "how to use the VS Code Joyride extension")]
    (def search-result search-result))
  (def result (->clj search-result))
  (->> result
       :data
       :web
       :results
       (map #(dissoc % :meta_url :thumbnail :profile :family_friendly :is_source_both :is_source_local))
       )
  :rcf)

