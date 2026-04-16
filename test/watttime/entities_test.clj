(ns watttime.entities-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [watttime.entities :as entities]
            [watttime.entities.schema :as schema]
            [watttime.entities.schema.raw :as raw]))

;; ---------------------------------------------------------------------------
;; Sample raw API responses (from WattTime OpenAPI spec examples)
;; ---------------------------------------------------------------------------

(def sample-historical-response
  {:data [{:point_time "2022-07-15T00:00:00Z"
           :value 870
           :imputed_data_used true
           :last_updated "2023-10-01T00:00:00Z"}
          {:point_time "2022-07-15T00:05:00Z"
           :value 860
           :imputed_data_used true
           :last_updated "2023-10-01T00:05:00Z"}]
   :meta {:data_point_period_seconds 300
          :model {:date "2023-03-01" :type "binned_regression"}
          :region "CAISO_NORTH"
          :signal_type "co2_moer"
          :units "lbs_co2_per_mwh"
          :warnings [{:type "EXAMPLE_WARNING" :message "This is just an example"}]}})

(def sample-signal-index-response
  {:data [{:point_time "2022-07-15T00:00:00Z" :value 37.1}]
   :meta {:data_point_period_seconds 300
          :model {:date "2023-03-01" :type "binned_regression"}
          :region "CAISO_NORTH"
          :signal_type "co2_moer"
          :units "percentile"}})

(def sample-forecast-response
  {:data [{:point_time "2022-07-15T00:00:00Z" :value 870}
          {:point_time "2022-07-15T00:05:00Z" :value 860}]
   :meta {:data_point_period_seconds 300
          :model {:date "2023-03-01" :type "binned_regression"}
          :region "CAISO_NORTH"
          :signal_type "co2_moer"
          :units "lbs_co2_per_mwh"
          :generated_at "2024-01-15T12:00:00Z"}})

(def sample-region-response
  {:region "ISONE_WCMA"
   :region_full_name "ISONE Western/Central Massachusetts"
   :signal_type "co2_moer"})

(def sample-extended-forecast-response
  {:data [{:generated_at "2024-01-15T12:00:00Z"
           :forecast [{:point_time "2024-01-15T12:00:00Z" :value 500}
                      {:point_time "2024-01-15T12:05:00Z" :value 510}]}]
   :meta {:data_point_period_seconds 300
          :model {:date "2023-03-01" :type "binned_regression"}
          :region "CAISO_NORTH"
          :signal_type "co2_moer"
          :units "lbs_co2_per_mwh"}})

;; ---------------------------------------------------------------------------
;; Raw schema validation
;; ---------------------------------------------------------------------------

(deftest raw-schema-validation
  (testing "V3Response validates sample historical data"
    (is (nil? (m/explain raw/V3Response sample-historical-response))))

  (testing "SignalIndexResponse validates sample"
    (is (nil? (m/explain raw/SignalIndexResponse sample-signal-index-response))))

  (testing "ForecastResponse validates sample"
    (is (nil? (m/explain raw/ForecastResponse sample-forecast-response))))

  (testing "RegionResponse validates sample"
    (is (nil? (m/explain raw/RegionResponse sample-region-response))))

  (testing "ExtendedForecastResponse validates sample"
    (is (nil? (m/explain raw/ExtendedForecastResponse sample-extended-forecast-response)))))

;; ---------------------------------------------------------------------------
;; Entity coercion
;; ---------------------------------------------------------------------------

(deftest data-point-coercion
  (let [raw {:point_time "2022-07-15T00:00:00Z" :value 870
             :imputed_data_used true :last_updated "2023-10-01T00:00:00Z"}
        dp (entities/->data-point raw)]
    (testing "point-time is an Instant"
      (is (instance? java.time.Instant (:watttime.data-point/point-time dp))))
    (testing "value preserved"
      (is (= 870 (:watttime.data-point/value dp))))
    (testing "imputed flag"
      (is (true? (:watttime.data-point/imputed? dp))))
    (testing "raw metadata preserved"
      (is (= raw (:watttime/raw (meta dp)))))
    (testing "no tick keys without period"
      (is (nil? (:tick/beginning dp)))
      (is (nil? (:tick/end dp))))))

(deftest data-point-with-period
  (let [raw {:point_time "2022-07-15T00:00:00Z" :value 870}
        period (java.time.Duration/ofMinutes 5)
        dp (entities/->data-point raw period)]
    (testing "tick/beginning equals point-time"
      (is (= (:watttime.data-point/point-time dp) (:tick/beginning dp))))
    (testing "tick/end is point-time + period"
      (is (= (java.time.Instant/parse "2022-07-15T00:05:00Z") (:tick/end dp))))
    (testing "original point-time still present"
      (is (instance? java.time.Instant (:watttime.data-point/point-time dp))))))

(deftest meta-coercion
  (let [raw-meta (:meta sample-historical-response)
        m (entities/->meta raw-meta)]
    (testing "signal-type is a keyword"
      (is (= :watttime.signal-type/co2-moer (:watttime.meta/signal-type m))))
    (testing "data-point-period is a Duration (5 min)"
      (is (= (java.time.Duration/ofMinutes 5) (:watttime.meta/data-point-period m))))
    (testing "region preserved"
      (is (= "CAISO_NORTH" (:watttime.meta/region m))))
    (testing "warnings coerced"
      (is (= 1 (count (:watttime.meta/warnings m)))))))

(deftest region-coercion
  (let [r (entities/->region sample-region-response)]
    (is (= "ISONE_WCMA" (:watttime.region/abbrev r)))
    (is (= "ISONE Western/Central Massachusetts" (:watttime.region/full-name r)))
    (is (= :watttime.signal-type/co2-moer (:watttime.region/signal-type r)))))

(deftest data-response-coercion
  (let [resp (entities/->data-response sample-historical-response)
        dp (first (:watttime.response/data resp))]
    (testing "data points coerced"
      (is (= 2 (count (:watttime.response/data resp)))))
    (testing "meta coerced"
      (is (= "CAISO_NORTH"
             (get-in resp [:watttime.response/meta :watttime.meta/region]))))
    (testing "raw metadata preserved"
      (is (= sample-historical-response (:watttime/raw (meta resp)))))
    (testing "data points have tick interval keys from meta period"
      (is (= (java.time.Instant/parse "2022-07-15T00:00:00Z") (:tick/beginning dp)))
      (is (= (java.time.Instant/parse "2022-07-15T00:05:00Z") (:tick/end dp))))))

(deftest forecast-response-coercion
  (let [resp (entities/->forecast-response sample-forecast-response)]
    (testing "data points coerced"
      (is (= 2 (count (:watttime.response/data resp)))))
    (testing "meta has generated-at"
      (is (instance? java.time.Instant
                     (get-in resp [:watttime.response/meta :watttime.meta/generated-at]))))))

(deftest extended-forecast-coercion
  (let [resp (entities/->extended-forecast-response sample-extended-forecast-response)]
    (testing "forecast entries coerced"
      (is (= 1 (count (:watttime.response/data resp)))))
    (testing "each entry has generated-at and data"
      (let [entry (first (:watttime.response/data resp))]
        (is (instance? java.time.Instant (:watttime.forecast/generated-at entry)))
        (is (= 2 (count (:watttime.forecast/data entry))))))))

;; ---------------------------------------------------------------------------
;; Coerced schema validation
;; ---------------------------------------------------------------------------

(deftest coerced-schema-validation
  (testing "coerced DataResponse validates against schema"
    (let [resp (entities/->data-response sample-historical-response)]
      (is (nil? (m/explain schema/DataResponse resp)))))

  (testing "coerced Region validates against schema"
    (let [r (entities/->region sample-region-response)]
      (is (nil? (m/explain schema/Region r))))))
