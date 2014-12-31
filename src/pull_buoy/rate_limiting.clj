(in-ns 'pull-buoy.core)

;; Github Enterprise is usually not rate limited but sometimes they have deployed rate
;; limited versions by accident and we seem to be on one of those right now :-(

(def rate-limited (fn-throttler 5000 :hour))
(def unsubscribe (rate-limited gh-core/unsubscribe))
(def delete-branch (rate-limited gh-core/delete-branch))
(def add-collaborator (rate-limited repo/add-collaborator))
(def create-pr-comment (rate-limited pulls/create-comment))
(def merge-pr (rate-limited pulls/merge))
(def edit-pr (rate-limited pulls/edit-pull))
(def create-pr (rate-limited pulls/create-pull))
(def create-issue-comment (rate-limited issues/create-comment))
(def create-reference (rate-limited git/create-reference))


