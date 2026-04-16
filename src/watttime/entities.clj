(ns watttime.entities
  "Coercion from raw WattTime API responses to namespaced Clojure entities.

  Raw layer: snake_case keys, string values — direct from the API JSON.
  Coerced layer: namespaced keywords, Instants, Durations, keyword enums.

  Every coerced entity preserves the original raw data as :watttime/raw metadata."
  (:import [java.time Duration Instant]))

;; ---------------------------------------------------------------------------
;; Parsing helpers
;; ---------------------------------------------------------------------------

(defn- parse-instant
  "Parse an ISO 8601 datetime string to a UTC Instant."
  ^Instant [^String s]
  (Instant/parse s))

(defn- parse-instant-maybe
  "Parse an ISO 8601 datetime string to Instant, or nil if nil/blank."
  [s]
  (when (and s (not (.isBlank ^String (str s))))
    (parse-instant s)))

(defn- seconds->duration
  "Convert seconds (integer) to a java.time.Duration."
  ^Duration [seconds]
  (Duration/ofSeconds seconds))

(defn- signal-type-keyword
  "Convert a signal_type string like \"co2_moer\" to :watttime.signal-type/co2-moer."
  [^String s]
  (keyword "watttime.signal-type" (.replace s "_" "-")))

;; ---------------------------------------------------------------------------
;; Coercion: Warning
;; ---------------------------------------------------------------------------

(defn ->warning
  "Coerce a raw warning map."
  [raw]
  (-> {:watttime.warning/type    (:type raw)
       :watttime.warning/message (:message raw)}
      (with-meta {:watttime/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Model
;; ---------------------------------------------------------------------------

(defn ->model
  "Coerce a raw model map."
  [raw]
  (-> (cond-> {:watttime.model/date (:date raw)}
        (:type raw) (assoc :watttime.model/type (:type raw)))
      (with-meta {:watttime/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Data point
;; ---------------------------------------------------------------------------

(defn ->data-point
  "Coerce a raw data point from historical, forecast, or signal-index responses.

  Returns a namespaced map with :watttime/raw metadata."
  [raw]
  (-> (cond-> {:watttime.data-point/point-time (parse-instant (:point_time raw))
               :watttime.data-point/value      (:value raw)}
        (contains? raw :imputed_data_used)
        (assoc :watttime.data-point/imputed? (:imputed_data_used raw))
        (contains? raw :last_updated)
        (assoc :watttime.data-point/last-updated (parse-instant-maybe (:last_updated raw))))
      (with-meta {:watttime/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Metadata
;; ---------------------------------------------------------------------------

(defn ->meta
  "Coerce raw metadata from a data response.

  Attaches :watttime/raw metadata."
  [raw]
  (-> (cond-> {:watttime.meta/region           (:region raw)
               :watttime.meta/signal-type      (signal-type-keyword (:signal_type raw))
               :watttime.meta/units            (:units raw)
               :watttime.meta/data-point-period (seconds->duration (:data_point_period_seconds raw))
               :watttime.meta/model            (->model (:model raw))}
        (:warnings raw)
        (assoc :watttime.meta/warnings (mapv ->warning (:warnings raw)))
        (:generated_at raw)
        (assoc :watttime.meta/generated-at (parse-instant (:generated_at raw)))
        (:generated_at_period_seconds raw)
        (assoc :watttime.meta/generated-at-period
               (seconds->duration (:generated_at_period_seconds raw))))
      (with-meta {:watttime/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Region
;; ---------------------------------------------------------------------------

(defn ->region
  "Coerce a raw region-from-loc response.

  Attaches :watttime/raw metadata."
  [raw]
  (-> {:watttime.region/abbrev      (:region raw)
       :watttime.region/full-name   (:region_full_name raw)
       :watttime.region/signal-type (signal-type-keyword (:signal_type raw))}
      (with-meta {:watttime/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Data responses (historical, signal-index)
;; ---------------------------------------------------------------------------

(defn ->data-response
  "Coerce a raw V3 data response (historical or signal-index).

  Returns:
    {:watttime.response/data [DataPoint ...]
     :watttime.response/meta Meta}

  Attaches :watttime/raw metadata."
  [raw]
  (-> {:watttime.response/data (mapv ->data-point (:data raw))
       :watttime.response/meta (->meta (:meta raw))}
      (with-meta {:watttime/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: Forecast responses
;; ---------------------------------------------------------------------------

(defn ->forecast-response
  "Coerce a raw single forecast response.

  Same structure as data-response but meta includes generated-at.
  Attaches :watttime/raw metadata."
  [raw]
  (-> {:watttime.response/data (mapv ->data-point (:data raw))
       :watttime.response/meta (->meta (:meta raw))}
      (with-meta {:watttime/raw raw})))

(defn ->forecast-entry
  "Coerce a single forecast entry from a historical forecast response."
  [raw]
  (-> {:watttime.forecast/generated-at (parse-instant (:generated_at raw))
       :watttime.forecast/data         (mapv ->data-point (:forecast raw))}
      (with-meta {:watttime/raw raw})))

(defn ->extended-forecast-response
  "Coerce a raw historical (extended) forecast response.

  Each entry in :data contains a generated_at timestamp and a vector of forecasted points.
  Attaches :watttime/raw metadata."
  [raw]
  (-> {:watttime.response/data (mapv ->forecast-entry (:data raw))
       :watttime.response/meta (->meta (:meta raw))}
      (with-meta {:watttime/raw raw})))

;; ---------------------------------------------------------------------------
;; Coercion: My-access hypercube
;; ---------------------------------------------------------------------------

(defn ->access-model
  "Coerce a raw hypercube model entry."
  [raw]
  (-> (cond-> {:watttime.model/type (:type raw)}
        (:date raw)       (assoc :watttime.model/date (:date raw))
        (:data_start raw) (assoc :watttime.access-model/data-start (:data_start raw))
        (:train_start raw) (assoc :watttime.access-model/train-start (:train_start raw))
        (:train_end raw)   (assoc :watttime.access-model/train-end (:train_end raw)))
      (with-meta {:watttime/raw raw})))

(defn ->access-endpoint
  "Coerce a raw hypercube endpoint entry."
  [raw]
  (-> {:watttime.access-endpoint/endpoint (:endpoint raw)
       :watttime.access-endpoint/models   (mapv ->access-model (:models raw))}
      (with-meta {:watttime/raw raw})))

(defn ->access-region
  "Coerce a raw hypercube region entry."
  [raw]
  (-> {:watttime.region/abbrev    (:region raw)
       :watttime.region/full-name (:region_full_name raw)
       :watttime.access-region/endpoints (mapv ->access-endpoint (:endpoints raw))}
      (with-meta {:watttime/raw raw})))

(defn ->access-signal
  "Coerce a raw hypercube signal entry."
  [raw]
  (-> {:watttime.access-signal/signal-type (signal-type-keyword (:signal_type raw))
       :watttime.access-signal/regions     (mapv ->access-region (:regions raw))}
      (with-meta {:watttime/raw raw})))

(defn ->my-access
  "Coerce a raw my-access hypercube response.

  Attaches :watttime/raw metadata."
  [raw]
  (-> {:watttime.access/signal-types (mapv ->access-signal (:signal_types raw))}
      (with-meta {:watttime/raw raw})))
