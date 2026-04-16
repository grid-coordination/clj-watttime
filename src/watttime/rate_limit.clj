(ns watttime.rate-limit
  "Sliding-window rate limiter for WattTime API requests.

  WattTime's API allows 10 requests per second. This namespace provides
  a composable rate limiter that can be used standalone or wrapped into
  a client."
  (:import [java.util.concurrent.locks ReentrantLock]
           [java.util ArrayDeque]))

(defn create-limiter
  "Create a sliding-window rate limiter.

  Options:
    :max-per-second — maximum requests per second (default 10)"
  ([] (create-limiter {}))
  ([{:keys [max-per-second] :or {max-per-second 10}}]
   {:max-per-second max-per-second
    :timestamps     (ArrayDeque.)
    :lock           (ReentrantLock.)}))

(defn acquire!
  "Block until a request slot is available.

  Uses a sliding window: evicts timestamps older than 1 second,
  then sleeps if the window is full. Returns nil."
  [{:keys [^long max-per-second ^ArrayDeque timestamps ^ReentrantLock lock]}]
  (.lock lock)
  (try
    (loop []
      (let [now (System/nanoTime)
            cutoff (- now 1000000000)] ;; 1 second in nanos
        ;; Evict expired entries
        (while (and (not (.isEmpty timestamps))
                    (< ^long (.peekFirst timestamps) cutoff))
          (.pollFirst timestamps))
        (if (< (.size timestamps) max-per-second)
          ;; Slot available
          (.addLast timestamps now)
          ;; Window full — compute wait time
          (let [oldest ^long (.peekFirst timestamps)
                wait-nanos (- (+ oldest 1000000000) now)
                wait-ms (max 1 (/ wait-nanos 1000000))]
            (.unlock lock)
            (Thread/sleep (long wait-ms))
            (.lock lock)
            (recur)))))
    (finally
      (.unlock lock)))
  nil)

(defn wrap-rate-limit
  "Middleware: wrap a function with rate limiting.
  The returned function calls acquire! before delegating to f."
  [f limiter]
  (fn [& args]
    (acquire! limiter)
    (apply f args)))
