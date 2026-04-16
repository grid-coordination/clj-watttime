(ns watttime.client
  "Stateful WattTime client composing auth and rate limiting.

  This is the primary entry point for most consumers. Create a client
  with `create-client`, then call the convenience functions which handle
  authentication and rate limiting automatically.

  The client is a plain map, not a Component — wrap it in a Component
  in your application if needed (e.g. in clj-price-server)."
  (:require [watttime.api :as api]
            [watttime.auth :as auth]
            [watttime.entities :as entities]
            [watttime.rate-limit :as rl]))

;; ---------------------------------------------------------------------------
;; Client creation
;; ---------------------------------------------------------------------------

(defn create-client
  "Create a WattTime client with automatic auth and rate limiting.

  Options:
    :username       — WattTime username (or env WATTTIME_USER)
    :password       — WattTime password (or env WATTTIME_PASSWORD)
    :base-url       — API base URL (default https://api.watttime.org)
    :max-per-second — Rate limit (default 10)
    :user-agent     — Custom User-Agent string"
  ([] (create-client {}))
  ([opts]
   (let [auth-mgr (auth/create-auth opts)
         limiter  (rl/create-limiter (select-keys opts [:max-per-second]))]
     {:auth       auth-mgr
      :limiter    limiter
      :base-url   (or (:base-url opts) api/default-base-url)
      :user-agent (:user-agent opts)})))

(defn- client-cfg
  "Build an API config map from a client, obtaining a fresh token."
  [client]
  {:token      (auth/token (:auth client))
   :base-url   (:base-url client)
   :user-agent (:user-agent client)})

(defn- rate-limited-call
  "Execute an API call with rate limiting."
  [client api-fn params]
  (rl/acquire! (:limiter client))
  (api-fn (client-cfg client) params))

;; ---------------------------------------------------------------------------
;; Raw API access (returns HTTP responses)
;; ---------------------------------------------------------------------------

(defn region-from-loc
  "Determine grid region from coordinates.
  params: {:signal-type \"co2_moer\" :latitude 37.7 :longitude -122.4}"
  [client params]
  (rate-limited-call client api/region-from-loc params))

(defn maps
  "Get grid region map geometry.
  params: {:signal-type \"co2_moer\"}"
  [client params]
  (rate-limited-call client api/maps params))

(defn signal-index
  "Get the current CO2 MOER index.
  params: {:region \"CAISO_NORTH\" :signal-type \"co2_moer\"}"
  [client params]
  (rate-limited-call client api/signal-index params))

(defn forecast
  "Get the most recent forecast.
  params: {:region \"CAISO_NORTH\" :signal-type \"co2_moer\" :horizon-hours 24}"
  [client params]
  (rate-limited-call client api/forecast params))

(defn forecast-historical
  "Get historical forecast data.
  params: {:region ... :signal-type ... :start ... :end ... :horizon-hours 24}"
  [client params]
  (rate-limited-call client api/forecast-historical params))

(defn historical
  "Get historical signal data.
  params: {:region ... :signal-type ... :start ... :end ...}"
  [client params]
  (rate-limited-call client api/historical params))

(defn my-access
  "Get account access information.
  params: {:region nil :signal-type nil}  (both optional)"
  [client params]
  (rate-limited-call client api/my-access params))

;; ---------------------------------------------------------------------------
;; Coerced entity access (returns namespaced entities with :watttime/raw metadata)
;; ---------------------------------------------------------------------------

(defn region-from-loc*
  "Like `region-from-loc` but returns a coerced Region entity."
  [client params]
  (let [resp (region-from-loc client params)]
    (when (api/success? resp)
      (entities/->region (api/body resp)))))

(defn signal-index*
  "Like `signal-index` but returns a coerced DataResponse entity."
  [client params]
  (let [resp (signal-index client params)]
    (when (api/success? resp)
      (entities/->data-response (api/body resp)))))

(defn forecast*
  "Like `forecast` but returns a coerced ForecastResponse entity."
  [client params]
  (let [resp (forecast client params)]
    (when (api/success? resp)
      (entities/->forecast-response (api/body resp)))))

(defn forecast-historical*
  "Like `forecast-historical` but returns a coerced ExtendedForecastResponse entity."
  [client params]
  (let [resp (forecast-historical client params)]
    (when (api/success? resp)
      (entities/->extended-forecast-response (api/body resp)))))

(defn historical*
  "Like `historical` but returns a coerced DataResponse entity."
  [client params]
  (let [resp (historical client params)]
    (when (api/success? resp)
      (entities/->data-response (api/body resp)))))

(defn my-access*
  "Like `my-access` but returns a coerced MyAccess entity."
  [client params]
  (let [resp (my-access client params)]
    (when (api/success? resp)
      (entities/->my-access (api/body resp)))))
