# clj-watttime

A Clojure client library for the [WattTime API](https://docs.watttime.org), providing access to grid emissions data including marginal emissions rates (MOER), average emissions rates (AOER), health damage indices, and forecasts.

[![Clojars](https://img.shields.io/clojars/v/energy.grid-coordination/clj-watttime.svg)](https://clojars.org/energy.grid-coordination/clj-watttime)
[![Lint & Test](https://github.com/grid-coordination/clj-watttime/actions/workflows/lint-test.yml/badge.svg)](https://github.com/grid-coordination/clj-watttime/actions/workflows/lint-test.yml)

## Features

- **Raw API access** — stateless HTTP functions returning full hato responses
- **Entity coercion** — raw snake_case JSON responses coerced to namespaced Clojure maps with Instants, Durations, and keyword enums (following the [clj-oa3](https://github.com/grid-coordination/clj-oa3) entity pattern)
- **Malli schemas** — two-layer validation: raw API shapes and coerced entity shapes
- **JWT auth management** — automatic login and token refresh (30-minute tokens, refresh at 25 minutes)
- **Rate limiting** — sliding-window rate limiter (default 10 requests/second per WattTime policy)
- **Stateful client** — composes auth + rate limiting for convenient use

## Installation

Add to your `deps.edn`:

```clojure
energy.grid-coordination/clj-watttime {:mvn/version "0.1.0-SNAPSHOT"}
```

Or use a git dependency:

```clojure
io.github.grid-coordination/clj-watttime
  {:git/sha "..."}
```

## Quick Start

```clojure
(require '[watttime.client :as wt])

;; Create a client (credentials from env vars WATTTIME_USER / WATTTIME_PASSWORD)
(def client (wt/create-client))

;; Or provide credentials explicitly
(def client (wt/create-client {:username "myuser" :password "mypass"}))

;; Get the current emissions index for a region
(wt/signal-index client {:region "CAISO_NORTH" :signal-type "co2_moer"})
;; => {:status 200, :body {:data [...], :meta {...}}, ...}

;; Get a coerced entity (namespaced keywords, Instants, Durations)
(wt/signal-index* client {:region "CAISO_NORTH" :signal-type "co2_moer"})
;; => {:watttime.response/data [{:watttime.data-point/point-time #inst "2024-...",
;;                                :watttime.data-point/value 37.1} ...],
;;     :watttime.response/meta {:watttime.meta/region "CAISO_NORTH",
;;                               :watttime.meta/signal-type :watttime.signal-type/co2-moer,
;;                               :watttime.meta/units "percentile", ...}}
```

## API Coverage

All [WattTime v3 API](https://docs.watttime.org) endpoints are supported:

| Function | Endpoint | Description |
|----------|----------|-------------|
| `region-from-loc` | `GET /v3/region-from-loc` | Determine grid region from lat/lng |
| `maps` | `GET /v3/maps` | Grid region map geometry (GeoJSON) |
| `signal-index` | `GET /v3/signal-index` | Current CO2 MOER index (0-100 percentile) |
| `forecast` | `GET /v3/forecast` | Most recent emissions forecast |
| `forecast-historical` | `GET /v3/forecast/historical` | Historical forecast data |
| `historical` | `GET /v3/historical` | Historical signal data |
| `my-access` | `GET /v3/my-access` | Account access info (regions, signal types, models) |

Each function has a coerced variant (suffixed with `*`) that returns namespaced entities instead of raw HTTP responses.

## Architecture

The library is organized in five layers, each usable independently:

```
watttime.client        ← Stateful client (most users start here)
  ├── watttime.auth        ← JWT token management
  ├── watttime.rate-limit  ← Sliding-window rate limiter
  ├── watttime.api         ← Raw HTTP functions (stateless)
  └── watttime.entities    ← Coercion: raw JSON → namespaced entities
        ├── watttime.entities.schema      ← Malli schemas (coerced)
        └── watttime.entities.schema.raw  ← Malli schemas (raw API)
```

### Layer 1: `watttime.api` — Raw HTTP

Stateless functions taking a config map `{:token "..." :base-url "..."}` and returning hato responses. No auth management, no rate limiting.

```clojure
(require '[watttime.api :as api])

;; Login to get a token
(def resp (api/login {:username "user" :password "pass"}))
(def token (get-in resp [:body :token]))

;; Use the token for data requests
(api/historical {:token token}
                {:region "CAISO_NORTH" :signal-type "co2_moer"
                 :start "2024-01-01T00:00Z" :end "2024-01-02T00:00Z"})
```

### Layer 2: `watttime.entities` — Coercion

Transforms raw API responses into namespaced Clojure entities. Every coerced entity carries the original raw data as `:watttime/raw` metadata.

```clojure
(require '[watttime.entities :as entities])

(def raw-response (:body (api/historical cfg params)))
(def coerced (entities/->data-response raw-response))

(:watttime.response/data coerced)
;; => [{:watttime.data-point/point-time #inst "2024-01-01T00:00:00Z"
;;      :watttime.data-point/value 870} ...]

;; Access original raw data
(:watttime/raw (meta coerced))
;; => {:data [{:point_time "2024-01-01T00:00:00Z" :value 870} ...] ...}
```

### Layer 3: `watttime.auth` — Token Management

```clojure
(require '[watttime.auth :as auth])

(def auth-mgr (auth/create-auth {:username "user" :password "pass"}))

;; Get a valid token (auto-refreshes when < 5 min remaining)
(auth/token auth-mgr) ;; => "eyJ..."
```

### Layer 4: `watttime.rate-limit` — Rate Limiter

Composable sliding-window rate limiter. Usable standalone.

```clojure
(require '[watttime.rate-limit :as rl])

(def limiter (rl/create-limiter {:max-per-second 10}))

;; Block until a slot is available
(rl/acquire! limiter)

;; Or wrap any function
(def rate-limited-f (rl/wrap-rate-limit my-fn limiter))
```

## Entity Types

| Coercion Function | Source | Entity Namespace |
|-------------------|--------|-----------------|
| `->data-point` | Data arrays | `:watttime.data-point/*` |
| `->meta` | Response metadata | `:watttime.meta/*` |
| `->region` | Region lookup | `:watttime.region/*` |
| `->data-response` | Historical / signal-index | `:watttime.response/*` |
| `->forecast-response` | Forecast | `:watttime.response/*` |
| `->extended-forecast-response` | Historical forecast | `:watttime.response/*` + `:watttime.forecast/*` |
| `->my-access` | Account access | `:watttime.access/*` |

## Signal Types

WattTime provides several signal types, represented as keywords after coercion:

| Signal Type | Keyword | Description |
|-------------|---------|-------------|
| `co2_moer` | `:watttime.signal-type/co2-moer` | Marginal Operating Emissions Rate |
| `co2_aoer` | `:watttime.signal-type/co2-aoer` | Average Operating Emissions Rate |
| `health_damage` | `:watttime.signal-type/health-damage` | Health damage index |

## Configuration

### Environment Variables

| Variable | Description |
|----------|-------------|
| `WATTTIME_USER` | WattTime username |
| `WATTTIME_PASSWORD` | WattTime password |

### Client Options

```clojure
(wt/create-client
  {:username       "user"          ;; or env WATTTIME_USER
   :password       "pass"          ;; or env WATTTIME_PASSWORD
   :base-url       "https://api.watttime.org"  ;; default
   :max-per-second 10              ;; rate limit, default 10
   :user-agent     "my-app/1.0"}) ;; custom User-Agent
```

## Development

```bash
# Start nREPL (dynamic port, written to .nrepl-port)
clojure -M:nrepl

# Run tests
clojure -M:test

# Lint
clj-kondo --lint src test

# Build JAR
clojure -T:build ci

# Install locally
clojure -T:build install
```

## WattTime Account

You need a WattTime account to use this library. [Register here](https://watttime.org/get-the-data/data-plans/) — a free tier is available with access to the `CAISO_NORTH` region.

## License

Copyright (c) Clark Communications Corporation. All rights reserved.

Distributed under the MIT License. See [LICENSE](LICENSE) for details.
