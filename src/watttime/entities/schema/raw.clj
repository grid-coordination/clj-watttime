(ns watttime.entities.schema.raw
  "Malli schemas for the raw WattTime API response shapes.

  These mirror the JSON exactly: snake_case keys, string values.
  Most consumers should use `watttime.entities.schema` (the coerced schemas)
  instead — these are primarily useful for boundary validation.")

;; ---------------------------------------------------------------------------
;; Shared
;; ---------------------------------------------------------------------------

(def Warning
  [:map
   [:type :string]
   [:message :string]])

(def Model
  [:map
   [:date :string]
   [:type {:optional true} :string]])

;; ---------------------------------------------------------------------------
;; Data points
;; ---------------------------------------------------------------------------

(def DataPoint
  "A single data point from historical or forecast responses."
  [:map
   [:point_time :string]
   [:value number?]
   [:imputed_data_used {:optional true} [:maybe :boolean]]
   [:last_updated {:optional true} [:maybe :string]]])

(def BaseDataPoint
  "A data point without optional imputed/last_updated fields (signal-index, forecast)."
  [:map
   [:point_time :string]
   [:value number?]])

;; ---------------------------------------------------------------------------
;; Metadata variants
;; ---------------------------------------------------------------------------

(def V3Metadata
  "Metadata accompanying historical data responses."
  [:map
   [:region :string]
   [:signal_type :string]
   [:data_point_period_seconds :int]
   [:model Model]
   [:units :string]
   [:warnings {:optional true} [:maybe [:vector Warning]]]])

(def SignalIndexMetadata
  "Metadata for signal-index responses."
  [:map
   [:region :string]
   [:signal_type :string]
   [:data_point_period_seconds :int]
   [:model Model]
   [:units :string]
   [:warnings {:optional true} [:maybe [:vector Warning]]]])

(def ForecastMetadata
  "Metadata for single forecast responses."
  [:map
   [:region :string]
   [:signal_type :string]
   [:data_point_period_seconds :int]
   [:model Model]
   [:units :string]
   [:generated_at :string]
   [:generated_at_period_seconds {:optional true} [:maybe :int]]
   [:warnings {:optional true} [:maybe [:vector Warning]]]])

;; ---------------------------------------------------------------------------
;; Response shapes
;; ---------------------------------------------------------------------------

(def LoginResponse
  [:map
   [:token :string]])

(def RegisterResponse
  [:map
   [:ok {:optional true} :string]
   [:user {:optional true} :string]
   [:error {:optional true} :string]])

(def RegionResponse
  [:map
   [:region :string]
   [:region_full_name :string]
   [:signal_type :string]])

(def V3Response
  "Historical data response."
  [:map
   [:data [:vector DataPoint]]
   [:meta V3Metadata]])

(def SignalIndexResponse
  "Signal index response (current MOER percentile)."
  [:map
   [:data [:vector BaseDataPoint]]
   [:meta SignalIndexMetadata]])

(def ForecastResponse
  "Single forecast response."
  [:map
   [:data [:vector BaseDataPoint]]
   [:meta ForecastMetadata]])

(def ForecastDataEntry
  "An individual forecast within a historical forecast response."
  [:map
   [:generated_at :string]
   [:forecast [:vector BaseDataPoint]]])

(def ExtendedForecastMeta
  "Metadata for extended (historical) forecast responses."
  [:map
   [:region :string]
   [:signal_type :string]
   [:data_point_period_seconds :int]
   [:model Model]
   [:units :string]
   [:generated_at_period_seconds {:optional true} [:maybe :int]]
   [:warnings {:optional true} [:maybe [:vector Warning]]]])

(def ExtendedForecastResponse
  "Historical forecast response (multiple forecasts over a time range)."
  [:map
   [:data [:vector ForecastDataEntry]]
   [:meta ExtendedForecastMeta]])

;; ---------------------------------------------------------------------------
;; My-access hypercube
;; ---------------------------------------------------------------------------

(def HCModel
  [:map
   [:date {:optional true} :string]
   [:type {:optional true} :string]
   [:model {:optional true} :string]
   [:data_start {:optional true} [:maybe :string]]
   [:data_end {:optional true} [:maybe :string]]
   [:train_start {:optional true} [:maybe :string]]
   [:train_end {:optional true} [:maybe :string]]
   [:historical_model {:optional true} :string]])

(def HCEndpoint
  [:map
   [:endpoint :string]
   [:models [:vector HCModel]]])

(def HCRegion
  [:map
   [:region :string]
   [:region_full_name :string]
   [:endpoints [:vector HCEndpoint]]])

(def HCSignal
  [:map
   [:signal_type :string]
   [:regions [:vector HCRegion]]])

(def MyAccessResponse
  [:map
   [:signal_types [:vector HCSignal]]])
