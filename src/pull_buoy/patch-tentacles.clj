(in-ns tentacles.core)

;; These changes could be merged upstream but I don't have time right now

(defn extract-useful-meta
  [h]
  (let [{:strs [etag last-modified x-ratelimit-limit x-ratelimit-remaining x-ratelimit-reset
                x-poll-interval]}
        h]
    {:etag etag :last-modified last-modified
     :call-limit (when x-ratelimit-limit (Long/parseLong x-ratelimit-limit))
     :call-remaining (when x-ratelimit-remaining (Long/parseLong x-ratelimit-remaining))
     :call-reset (when x-ratelimit-reset (Long/parseLong x-ratelimit-reset))
     :poll-interval (when x-poll-interval (Long/parseLong x-poll-interval))}))
