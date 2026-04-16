(ns watttime.entities.schema
  "Malli schemas for coerced WattTime entities.

  These describe the Clojure-native shape produced by `watttime.entities` coercion:
  namespaced keywords, Instants, Durations, and keyword signal types."
  (:import [java.time Duration]))

(def DataPoint
  "A coerced data point with Instant timestamps."
  [:map
   [:watttime.data-point/point-time inst?]
   [:watttime.data-point/value number?]
   [:watttime.data-point/imputed? {:optional true} [:maybe :boolean]]
   [:watttime.data-point/last-updated {:optional true} [:maybe inst?]]])

(def Model
  [:map
   [:watttime.model/date :string]
   [:watttime.model/type {:optional true} :string]])

(def Warning
  [:map
   [:watttime.warning/type :string]
   [:watttime.warning/message :string]])

(def Meta
  "Coerced metadata accompanying data responses."
  [:map
   [:watttime.meta/region :string]
   [:watttime.meta/signal-type :keyword]
   [:watttime.meta/units :string]
   [:watttime.meta/data-point-period [:fn #(instance? Duration %)]]
   [:watttime.meta/model Model]
   [:watttime.meta/warnings {:optional true} [:maybe [:vector Warning]]]])

(def ForecastMeta
  "Coerced metadata for forecast responses (includes generated-at)."
  [:map
   [:watttime.meta/region :string]
   [:watttime.meta/signal-type :keyword]
   [:watttime.meta/units :string]
   [:watttime.meta/data-point-period [:fn #(instance? Duration %)]]
   [:watttime.meta/model Model]
   [:watttime.meta/generated-at inst?]
   [:watttime.meta/generated-at-period {:optional true} [:maybe [:fn #(instance? Duration %)]]]
   [:watttime.meta/warnings {:optional true} [:maybe [:vector Warning]]]])

(def Region
  "Coerced region lookup result."
  [:map
   [:watttime.region/abbrev :string]
   [:watttime.region/full-name :string]
   [:watttime.region/signal-type :keyword]])

(def DataResponse
  "Coerced historical data or signal-index response."
  [:map
   [:watttime.response/data [:vector DataPoint]]
   [:watttime.response/meta Meta]])

(def ForecastDataResponse
  "Coerced single forecast response."
  [:map
   [:watttime.response/data [:vector DataPoint]]
   [:watttime.response/meta ForecastMeta]])

(def ForecastEntry
  "A single forecast within a historical forecast response."
  [:map
   [:watttime.forecast/generated-at inst?]
   [:watttime.forecast/data [:vector DataPoint]]])

(def ExtendedForecastResponse
  "Coerced historical forecast response."
  [:map
   [:watttime.response/data [:vector ForecastEntry]]
   [:watttime.response/meta Meta]])
