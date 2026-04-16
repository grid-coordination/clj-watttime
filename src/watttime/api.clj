(ns watttime.api
  "Raw HTTP access to the WattTime API.

  Stateless functions that take a config map and query params, returning
  raw hato HTTP responses. No coercion, no rate limiting, no auth management.

  Config map shape:
    {:base-url \"https://api.watttime.org\"  ; optional, this is the default
     :token    \"jwt-token-string\"           ; required for data endpoints
     :username \"user\"                       ; required for login/register
     :password \"pass\"}                      ; required for login/register"
  (:require [hato.client :as hc])
  (:import [java.util Base64]))

;; ---------------------------------------------------------------------------
;; Defaults
;; ---------------------------------------------------------------------------

(def default-base-url "https://api.watttime.org")

(def lib-version "0.1.0")

(def default-user-agent (str "clj-watttime/" lib-version))

;; ---------------------------------------------------------------------------
;; Internal helpers
;; ---------------------------------------------------------------------------

(defn- base-url [cfg]
  (or (:base-url cfg) default-base-url))

(defn- bearer-headers [cfg]
  {"Authorization" (str "Bearer " (:token cfg))
   "User-Agent"    (or (:user-agent cfg) default-user-agent)})

(defn- kebab->snake [k]
  (-> (name k)
      (.replace "-" "_")))

(defn- snake-case-params
  "Convert a kebab-case keyword map to snake_case string keys,
  removing nil values."
  [params]
  (into {}
        (comp (filter (comp some? val))
              (map (fn [[k v]] [(kebab->snake k) v])))
        params))

;; ---------------------------------------------------------------------------
;; Auth endpoints (no token required)
;; ---------------------------------------------------------------------------

(defn- basic-auth-header
  "Encode username:password as a Basic auth header value."
  [username password]
  (let [creds (str username ":" password)]
    (str "Basic " (.encodeToString (Base64/getEncoder) (.getBytes creds "UTF-8")))))

(defn login
  "GET /login with HTTP Basic auth.
  Returns the full HTTP response. On success, body contains {:token \"...\"}."
  [cfg]
  (hc/get (str (base-url cfg) "/login")
          {:headers      {"User-Agent"    (or (:user-agent cfg) default-user-agent)
                          "Authorization" (basic-auth-header (:username cfg) (:password cfg))}
           :as           :json
           :throw-exceptions? false}))

(defn register
  "POST /register to create a new WattTime account.
  params: {:username :password :email :org (optional)}
  Note: despite the OpenAPI spec listing these as query params, the API
  requires them as a JSON body per the documentation."
  [cfg params]
  (hc/post (str (base-url cfg) "/register")
           {:headers      {"User-Agent" (or (:user-agent cfg) default-user-agent)}
            :content-type :json
            :form-params  (snake-case-params params)
            :as           :json
            :throw-exceptions? false}))

(defn password-reset
  "GET /password to trigger a password reset email.
  params: {:username \"...\"}"
  [cfg params]
  (hc/get (str (base-url cfg) "/password")
          {:headers      {"User-Agent" (or (:user-agent cfg) default-user-agent)}
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

;; ---------------------------------------------------------------------------
;; Data endpoints (token required)
;; ---------------------------------------------------------------------------

(defn region-from-loc
  "GET /v3/region-from-loc — Determine grid region from coordinates.
  params: {:signal-type \"co2_moer\" :latitude 37.7 :longitude -122.4}"
  [cfg params]
  (hc/get (str (base-url cfg) "/v3/region-from-loc")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn maps
  "GET /v3/maps — Grid region map geometry (GeoJSON).
  params: {:signal-type \"co2_moer\"}"
  [cfg params]
  (hc/get (str (base-url cfg) "/v3/maps")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn signal-index
  "GET /v3/signal-index — Current CO2 MOER index (0-100 percentile).
  params: {:region \"CAISO_NORTH\" :signal-type \"co2_moer\"}"
  [cfg params]
  (hc/get (str (base-url cfg) "/v3/signal-index")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn forecast
  "GET /v3/forecast — Most recent forecast.
  params: {:region \"CAISO_NORTH\" :signal-type \"co2_moer\"
           :model nil :horizon-hours 24}"
  [cfg params]
  (hc/get (str (base-url cfg) "/v3/forecast")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn forecast-historical
  "GET /v3/forecast/historical — Historical forecast data.
  params: {:region \"CAISO_NORTH\" :signal-type \"co2_moer\"
           :start \"2024-01-01T00:00Z\" :end \"2024-01-02T00:00Z\"
           :model nil :horizon-hours 24}"
  [cfg params]
  (hc/get (str (base-url cfg) "/v3/forecast/historical")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn historical
  "GET /v3/historical — Historical signal data.
  params: {:region \"CAISO_NORTH\" :signal-type \"co2_moer\"
           :start \"2024-01-01T00:00Z\" :end \"2024-01-02T00:00Z\"
           :model nil :include-imputed-marker false :updated-since nil}"
  [cfg params]
  (hc/get (str (base-url cfg) "/v3/historical")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

(defn my-access
  "GET /v3/my-access — Account access information (signal types, regions, models).
  params: {:region nil :signal-type nil}  (both optional)"
  [cfg params]
  (hc/get (str (base-url cfg) "/v3/my-access")
          {:headers      (bearer-headers cfg)
           :query-params (snake-case-params params)
           :as           :json
           :throw-exceptions? false}))

;; ---------------------------------------------------------------------------
;; Response helpers
;; ---------------------------------------------------------------------------

(defn success?
  "True if the HTTP response has a 2xx status code."
  [response]
  (<= 200 (:status response) 299))

(defn body
  "Extract the :body from an HTTP response."
  [response]
  (:body response))
