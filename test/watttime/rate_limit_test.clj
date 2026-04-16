(ns watttime.rate-limit-test
  (:require [clojure.test :refer [deftest is testing]]
            [watttime.rate-limit :as rl]))

(deftest create-limiter-defaults
  (let [l (rl/create-limiter)]
    (is (= 10 (:max-per-second l)))
    (is (some? (:timestamps l)))
    (is (some? (:lock l)))))

(deftest create-limiter-custom
  (let [l (rl/create-limiter {:max-per-second 5})]
    (is (= 5 (:max-per-second l)))))

(deftest acquire-permits-under-limit
  (testing "can acquire up to max-per-second without blocking"
    (let [l (rl/create-limiter {:max-per-second 5})]
      (dotimes [_ 5]
        (rl/acquire! l))
      ;; All 5 acquired within a second — should succeed without timeout
      (is true))))

(deftest wrap-rate-limit-test
  (testing "wrapped function is called"
    (let [l (rl/create-limiter {:max-per-second 10})
          calls (atom 0)
          f (rl/wrap-rate-limit (fn [x] (swap! calls inc) (* x 2)) l)]
      (is (= 10 (f 5)))
      (is (= 1 @calls)))))

(deftest acquire-blocks-when-full
  (testing "acquire blocks when window is full, completes within ~1s"
    (let [l (rl/create-limiter {:max-per-second 3})
          start (System/nanoTime)]
      ;; Fill the window
      (dotimes [_ 3]
        (rl/acquire! l))
      ;; This should block until the window slides
      (rl/acquire! l)
      (let [elapsed-ms (/ (- (System/nanoTime) start) 1e6)]
        ;; Should have waited roughly 1 second (with some tolerance)
        (is (>= elapsed-ms 800) (str "Expected >=800ms, got " elapsed-ms))
        (is (<= elapsed-ms 2000) (str "Expected <=2000ms, got " elapsed-ms))))))
