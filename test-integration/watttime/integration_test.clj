(ns watttime.integration-test
  "Live integration tests against the WattTime API.

  Requires WATTTIME_USER and WATTTIME_PASSWORD environment variables.
  Run with: clojure -M:test-integration

  These tests make real HTTP calls and validate the full pipeline:
  raw response -> schema validation -> entity coercion -> coerced schema validation."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [malli.core :as m]
            [watttime.api :as api]
            [watttime.client :as client]
            [watttime.entities :as entities]
            [watttime.entities.schema :as schema]
            [watttime.entities.schema.raw :as raw]))

;; ---------------------------------------------------------------------------
;; Fixture: skip if credentials not available
;; ---------------------------------------------------------------------------

(def ^:private credentials-available?
  (and (System/getenv "WATTTIME_USER")
       (System/getenv "WATTTIME_PASSWORD")))

(defn- require-credentials [f]
  (if credentials-available?
    (f)
    (println "SKIPPING integration tests: WATTTIME_USER/WATTTIME_PASSWORD not set")))

(use-fixtures :once require-credentials)

;; Shared client — created once per test run
(def ^:private test-client
  (delay
    (client/create-client)))

;; ---------------------------------------------------------------------------
;; Login
;; ---------------------------------------------------------------------------

(deftest login-test
  (testing "login returns a valid token"
    (let [resp (api/login {:username (System/getenv "WATTTIME_USER")
                           :password (System/getenv "WATTTIME_PASSWORD")})]
      (is (api/success? resp))
      (is (string? (get-in resp [:body :token])))
      (is (nil? (m/explain raw/LoginResponse (:body resp)))))))

;; ---------------------------------------------------------------------------
;; Signal Index
;; ---------------------------------------------------------------------------

(deftest signal-index-test
  (testing "/v3/signal-index returns current MOER index"
    (let [resp (client/signal-index @test-client
                                    {:region "CAISO_NORTH"
                                     :signal-type "co2_moer"})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates against schema"
          (is (nil? (m/explain raw/SignalIndexResponse body))))
        (testing "coerces to valid entity"
          (let [coerced (entities/->data-response body)]
            (is (pos? (count (:watttime.response/data coerced))))
            (is (nil? (m/explain schema/DataResponse coerced)))
            (let [dp (first (:watttime.response/data coerced))]
              (is (instance? java.time.Instant (:watttime.data-point/point-time dp)))
              (is (number? (:watttime.data-point/value dp)))
              (is (some? (:tick/beginning dp)))
              (is (some? (:tick/end dp))))))))))

;; ---------------------------------------------------------------------------
;; Signal Index* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest signal-index-star-test
  (testing "signal-index* returns coerced entity directly"
    (let [result (client/signal-index* @test-client
                                       {:region "CAISO_NORTH"
                                        :signal-type "co2_moer"})]
      (is (some? result))
      (is (nil? (m/explain schema/DataResponse result)))
      (is (some? (:watttime/raw (meta result)))))))

;; ---------------------------------------------------------------------------
;; Forecast
;; ---------------------------------------------------------------------------

(deftest forecast-test
  (testing "/v3/forecast returns forecast with generated_at"
    (let [resp (client/forecast @test-client
                                {:region "CAISO_NORTH"
                                 :signal-type "co2_moer"})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates"
          (is (nil? (m/explain raw/ForecastResponse body))))
        (testing "coerces to valid entity"
          (let [coerced (entities/->forecast-response body)]
            (is (pos? (count (:watttime.response/data coerced))))
            (is (some? (get-in coerced [:watttime.response/meta :watttime.meta/generated-at])))
            (is (nil? (m/explain schema/ForecastDataResponse coerced)))
            (let [dp (first (:watttime.response/data coerced))]
              (is (instance? java.time.Instant (:watttime.data-point/point-time dp)))
              (is (number? (:watttime.data-point/value dp)))
              (is (some? (:tick/beginning dp)))
              (is (some? (:tick/end dp))))))))))

;; ---------------------------------------------------------------------------
;; Forecast* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest forecast-star-test
  (testing "forecast* returns coerced entity directly"
    (let [result (client/forecast* @test-client
                                   {:region "CAISO_NORTH"
                                    :signal-type "co2_moer"})]
      (is (some? result))
      (is (nil? (m/explain schema/ForecastDataResponse result)))
      (is (some? (:watttime/raw (meta result)))))))

;; ---------------------------------------------------------------------------
;; Historical
;; ---------------------------------------------------------------------------

(deftest historical-test
  (testing "/v3/historical returns data points with time range"
    (let [now (java.time.Instant/now)
          one-hour-ago (.minus now (java.time.Duration/ofHours 1))
          resp (client/historical @test-client
                                  {:region "CAISO_NORTH"
                                   :signal-type "co2_moer"
                                   :start (str one-hour-ago)
                                   :end (str now)})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates"
          (is (nil? (m/explain raw/V3Response body))))
        (testing "coerces to valid entity with multiple points"
          (let [coerced (entities/->data-response body)]
            (is (< 1 (count (:watttime.response/data coerced))))
            (is (nil? (m/explain schema/DataResponse coerced)))))))))

;; ---------------------------------------------------------------------------
;; Historical* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest historical-star-test
  (testing "historical* returns coerced entity directly"
    (let [now (java.time.Instant/now)
          one-hour-ago (.minus now (java.time.Duration/ofHours 1))
          result (client/historical* @test-client
                                     {:region "CAISO_NORTH"
                                      :signal-type "co2_moer"
                                      :start (str one-hour-ago)
                                      :end (str now)})]
      (is (some? result))
      (is (nil? (m/explain schema/DataResponse result)))
      (is (some? (:watttime/raw (meta result)))))))

;; ---------------------------------------------------------------------------
;; Region from location
;; ---------------------------------------------------------------------------

(deftest region-from-loc-test
  (testing "/v3/region-from-loc resolves coordinates to a region"
    (let [resp (client/region-from-loc @test-client
                                       {:signal-type "co2_moer"
                                        :latitude 37.7749
                                        :longitude -122.4194})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates"
          (is (nil? (m/explain raw/RegionResponse body))))
        (testing "coerces to valid entity"
          (let [coerced (entities/->region body)]
            (is (string? (:watttime.region/abbrev coerced)))
            (is (string? (:watttime.region/full-name coerced)))
            (is (keyword? (:watttime.region/signal-type coerced)))
            (is (nil? (m/explain schema/Region coerced)))))))))

;; ---------------------------------------------------------------------------
;; Region from location* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest region-from-loc-star-test
  (testing "region-from-loc* returns coerced entity directly"
    (let [result (client/region-from-loc* @test-client
                                          {:signal-type "co2_moer"
                                           :latitude 37.7749
                                           :longitude -122.4194})]
      (is (some? result))
      (is (nil? (m/explain schema/Region result)))
      (is (some? (:watttime/raw (meta result)))))))

;; ---------------------------------------------------------------------------
;; My access
;; ---------------------------------------------------------------------------

(deftest my-access-test
  (testing "/v3/my-access returns account access hypercube"
    (let [resp (client/my-access @test-client {})]
      (is (api/success? resp))
      (let [body (api/body resp)]
        (testing "raw response validates"
          (is (nil? (m/explain raw/MyAccessResponse body))))
        (testing "coerces to valid entity"
          (let [coerced (entities/->my-access body)]
            (is (pos? (count (:watttime.access/signal-types coerced))))
            (is (some? (:watttime/raw (meta coerced))))))))))

;; ---------------------------------------------------------------------------
;; My access* — coerced convenience function
;; ---------------------------------------------------------------------------

(deftest my-access-star-test
  (testing "my-access* returns coerced entity directly"
    (let [result (client/my-access* @test-client {})]
      (is (some? result))
      (is (pos? (count (:watttime.access/signal-types result))))
      (is (some? (:watttime/raw (meta result)))))))
