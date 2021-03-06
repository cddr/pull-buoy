(ns pull-buoy.core
  (:require [tentacles.pulls :as pulls]
            [tentacles.issues :as issues]
            [tentacles.core :as gh-core]
            [tentacles.data :as git]
            [tentacles.repos :as repo]
            [clojure.pprint :as pp]
            [environ.core :refer [env]]
            [clojure.tools.reader.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as http]
            [throttler.core :refer [fn-throttler]]
            [clojure.stacktrace :as st])
  (:gen-class))

(defn find-auth [object path user-map]
  (let [default-oauth (env :github-to-token)
        gh-login (get-in object path)]
    (if (contains? user-map gh-login)
      (last (get user-map gh-login))
      (do (println "  Could not find ppgh login for " gh-login " from " path " so using default")
          default-oauth))))

(defn gen-branch [name]
  (let [name (str "pr-" name)]
    {:name name
     :ref (str "refs/heads/" name)}))

(load "patch_tentacles")
(load "rate_limiting")

(defn add-collaborators [user-map]
  (let [{:keys [github-to-base github-to-token github-to-repo]} env
        [user repo] (clojure.string/split github-to-repo #"/")]
    (gh-core/with-url github-to-base
      (doseq [[gh-name [ppgh-name _]] user-map]
        (add-collaborator user repo
                          ppgh-name
                          {:oauth-token github-to-token})
        (unsubscribe user repo {:oauth-token (last (get user-map gh-name))})))))

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
  (from-repo-invoke list-pulls {:state "all",
                                 :sort "created",
                                 :all-pages true}))

(defn pull-request [id]
  (let [{:keys [github-from-base github-from-token github-from-repo]} env]
    (gh-core/with-url github-from-base
      (gh-core/with-defaults {:oauth-token github-from-token}
        (let [[user repo] (clojure.string/split github-from-repo #"/")]
          (get-pull user repo id))))))


(defn authorify [object user-map]
  ;; This creates a comment and attributes it to the original author by appending
  ;; the original message with "--@author". This allows us to correctly attribute
  ;; PRs/Comments when the original user does not exist in the new system.
  (if (contains? user-map (get-in object [:user :login]))
    (get-in object [:body])
    (format "\"%s\"\n\n--%s" (get-in object [:body])
            (get-in object [:user :login]))))

(defn trunc-msg [msg]
  (subs msg 0 (min 20 (count msg))))

(defn merged? [pr]
  (:merged_at pr))

(defn closed? [pr]
  (:closed_at pr))

(defn create-pull-request [pr user-map]
  (let [{:keys [github-to-base github-to-token github-to-repo]} env
        requester-oauth (find-auth pr [:user :login] user-map)
        merger-oauth (find-auth pr [:merged_by :login] user-map)]

    (gh-core/with-url github-to-base
      (let [base-branch (gen-branch (get-in pr [:base :ref]))
            head-branch (gen-branch (get-in pr [:head :ref]))
            [user repo] (clojure.string/split github-to-repo #"/")]

        (println (format "Copying PR #%s -- %s" (:number pr) (:title pr)))
        (println "  requested by: " requester-oauth)
        (println "  merged by: " merger-oauth)
        (println "  base: " (:ref base-branch) " " (get-in pr [:base :sha]))
        (println "  head: " (:ref head-branch) " " (get-in pr [:head :sha]))

        (create-reference user repo (:ref base-branch) (get-in pr [:base :sha])
                          {:oauth-token requester-oauth})
        (create-reference user repo (:ref head-branch) (get-in pr [:head :sha])
                          {:oauth-token requester-oauth})

        (let [msg (authorify pr user-map)
              new-pr (create-pr user repo (:title pr) (:ref base-branch)
                                (:ref head-branch) {:body msg
                                                    :oauth-token requester-oauth})]

          (doseq [comment (from-repo-invoke get-comments (:number pr))]
            (create-pr-comment user repo (:number new-pr)
                               (:commit_id comment)
                               (:path comment)
                               (:original_position comment)
                               (authorify comment user-map)
                               {:oauth-token (find-auth comment [:user :login] user-map)}))

          (doseq [comment (from-repo-invoke get-issue-comments (:number pr))]
            (create-issue-comment user repo (:number new-pr)
                                  (authorify comment user-map)
                                  {:oauth-token (find-auth comment [:user :login] user-map)}))

          (let [cleanup (fn []
                          (delete-branch user repo (:name base-branch) {:oauth-token merger-oauth})
                          (delete-branch user repo (:name head-branch) {:oauth-token merger-oauth}))]
            (cond
              (merged? pr) (do
                             (merge-pr user repo (:number new-pr) {:oauth-token merger-oauth})
                             (cleanup))
              (closed? pr) (do
                             (edit-pr user repo (:number new-pr) {:state "closed"})
                             (cleanup)))))))))

(def cli-options
  [["-f" "--from STARTING-AT" "The first pull request to be copied"
    :default 1
    :parse-fn #(edn/read-string %)
    :validate [#(< 0 % 4000) "Must be an integer between 0 and 4000"]]
   ["-t" "--to ENDING-AT"     "The last pull request to be copied"
    :default 4000
    :parse-fn #(edn/read-string %)
    :validate [#(< 0 % 4000) "Must be an integer between 0 and 4000"]]])

(defn -main [& args]
  (let [user-map (edn/read)
        {:keys [options]} (parse-opts args cli-options)
        drop-pred #(not (<= (:from options) (:number %)))
        take-pred #(not (< (:to options) (:number %)))]
    
    ;; ensure all supplied users are added as project collaborators
    (add-collaborators user-map)

    ;; (pull-requests) returns a lazy sequence so using drop-while/take-while allows
    ;; us to fetch only what we need from the source github
    (doseq [pr (->> (pull-requests)
                    (drop-while drop-pred)
                    (take-while take-pred))]
      (if-not (empty? pr)
        (create-pull-request (pull-request (:number pr)) user-map)))))


