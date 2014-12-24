(ns pull-buoy.core
  (:require [tentacles.pulls :as pulls]
            [tentacles.issues :as issues]
            [tentacles.core :as gh-core]
            [tentacles.data :as git]
            [tentacles.repos :as repo]
            [clojure.pprint :as pp]
            [environ.core :refer [env]])
  (:gen-class))

(defn gen-branch []
  (let [sym (gensym)]
    {:name (str sym)
     :ref (str "refs/heads/" sym)}))

(defn delete-branch [user repo branch options]
  (gh-core/api-call :delete "repos/%s/%s/git/refs/heads/%s" [user repo branch]
                    options))

(defn from-repo-invoke [method options]
  (let [{:keys [github-from-base github-from-token github-from-repo]} env]
    (gh-core/with-url github-from-base
      (gh-core/with-defaults {:oauth-token github-from-token}
        (let [[user repo] (clojure.string/split github-from-repo #"/")]
          (filter (complement empty?)
                  (method user repo options)))))))

(defn summarize [pr]
  (map (fn [key]
         (get-in pr key))
       [[:number] [:title] [:state] [:body]
        [:head :sha]
        [:base :sha]]))

(defn pull-requests []
  (from-repo-invoke pulls/pulls {:state "all", :sort "created", :all-pages true}))

(defn pull-request-commits [pr]
  (from-repo-invoke pulls/commits (:number pr)))

(defn pull-request-comments [pr]
  (concat (from-repo-invoke pulls/comments (:number pr))
          (from-repo-invoke issues/issue-comments (:number pr))))

(defn commit-comments [sha]
  (from-repo-invoke repo/specific-commit-comments sha))

(defn authorify [msg author]
  ;; This creates a comment and attributes it to the original author by appending
  ;; the original message with "--@author". It might be possible to use OAUTH and
  ;; create comments on behalf of users but that seems like a lot of effort.
  (format "\"%s\"\n\n--%s" msg author))

(defn trunc-msg [msg]
  (subs msg 0 (min 20 (count msg))))

(defn create-pull-request-comment [pr comment]
  (let [{:keys [github-to-base github-to-token github-to-repo]} env]
    (gh-core/with-url github-to-base
      (gh-core/with-defaults {:oauth-token github-to-token}
        (let [[user repo] (clojure.string/split github-to-repo #"/")
              msg (:body comment)
              attributed-msg (authorify msg (get-in comment [:user :login]))]
          (println (format "  Copying PR comment %s" (:body comment)))
          (pulls/create-comment user repo (:number pr)
                                (:commit_id comment)
                                (:path comment)
                                (:position comment)
                                attributed-msg {}))))))

(defn create-issue-comment [pr comment]
  (let [{:keys [github-to-base github-to-token github-to-repo]} env]
    (gh-core/with-url github-to-base
      (gh-core/with-defaults {:oauth-token github-to-token}
        (let [[user repo] (clojure.string/split github-to-repo #"/")
              msg (:body comment)
              attributed-msg (authorify msg (get-in comment [:user :login]))]
          (println "  Copying Issue comment: " (trunc-msg msg) "...")
          (issues/create-comment user repo (:number pr) attributed-msg {}))))))


(defn create-pull-request [pr]
  (let [{:keys [github-to-base github-to-token github-to-repo]} env]
    (gh-core/with-url github-to-base
      (gh-core/with-defaults {:oauth-token github-to-token}
        (let [base-branch (gen-branch)
              head-branch (gen-branch)
              [user repo] (clojure.string/split github-to-repo #"/")]

          (println (format "Copying PR #%s -- %s" (:number pr) (:title pr)))
          
          (git/create-reference user repo (:ref base-branch) (get-in pr [:base :sha]) {})
          (git/create-reference user repo (:ref head-branch) (get-in pr [:head :sha]) {})

          (let [new-pr (pulls/create-pull user repo (:title pr) (:ref base-branch)
                                          (:ref head-branch) {:body (:body pr)})]

            (doseq [comment (from-repo-invoke pulls/comments (:number pr))]
              (create-pull-request-comment new-pr comment))

            (doseq [comment (from-repo-invoke issues/issue-comments (:number pr))]
              (create-issue-comment new-pr comment))

            (pulls/edit-pull user repo (:number new-pr) {:state "closed"}))

          (delete-branch user repo (:name base-branch) {})
          (delete-branch user repo (:name head-branch) {}))))))

(defn -main [& args]
  (doseq [pr (pull-requests)]
    (if-not (empty? pr)
      (create-pull-request pr))))
