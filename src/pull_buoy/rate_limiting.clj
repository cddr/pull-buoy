(in-ns 'pull-buoy.core)

;; Github Enterprise is usually not rate limited but sometimes they have deployed rate
;; limited versions by accident and we seem to be on one of those right now :-(

(def ppgh-rate-limited (fn-throttler 5000 :hour))
(def unsubscribe (ppgh-rate-limited gh-core/unsubscribe))
(def delete-branch (ppgh-rate-limited gh-core/delete-branch))
(def add-collaborator (ppgh-rate-limited repo/add-collaborator))
(def create-pr-comment (ppgh-rate-limited pulls/create-comment))
(def merge-pr (ppgh-rate-limited pulls/merge))
(def edit-pr (ppgh-rate-limited pulls/edit-pull))
(def create-pr (ppgh-rate-limited pulls/create-pull))
(def create-issue-comment (ppgh-rate-limited issues/create-comment))
(def create-reference (ppgh-rate-limited git/create-reference))

(def gh-rate-limited (fn-throttler 4500 :hour))
(def list-pulls (gh-rate-limited pulls/pulls))
(def get-pull (gh-rate-limited pulls/specific-pull))
(def get-comments (gh-rate-limited pulls/comments))
(def get-issue-comments (gh-rate-limited issues/issue-comments))



