# EV Routing Orchestration Service

A Spring Boot 3.x service that orchestrates:
- **Leaflet.js frontend** → receives route requests
- **OpenRouteService (ORS)** → fetches route geometry + elevation
- **Python physics engine** → calculates EV energy consumption and regen zones
- Returns a **GeoJSON FeatureCollection** with color-coded segments back to the frontend

---

## Project Structure

```
ev-routing/
├── src/main/java/com/evrouting/
│   ├── EvRoutingApplication.java          # Entry point
│   ├── config/
│   │   ├── CorsConfig.java                # Global CORS
│   │   └── WebClientConfig.java           # ORS + Python WebClient beans
│   ├── controller/
│   │   └── RouteController.java           # POST /api/route
│   ├── service/
│   │   └── RoutingOrchestratorService.java # Full pipeline logic
│   └── dto/
│       ├── FrontendRouteRequest.java
│       ├── RouteSegment.java
│       ├── OrsRouteResponse.java
│       ├── PythonEnergyRequest.java
│       └── PythonEnergyResponse.java
├── src/main/resources/
│   └── application.properties
├── Dockerfile
├── docker-compose.yml
├── .env.example
└── pom.xml
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for containerised deployment)
- A free ORS API key from https://openrouteservice.org/dev/#/signup

---

## Quick Start (Local)

### 1. Set your ORS API key

Edit `src/main/resources/application.properties`:
```properties
ors.api-key=YOUR_ORS_API_KEY_HERE
```

### 2. Build and run

```bash
cd ev-routing
mvn clean package -DskipTests
java -jar target/ev-routing-service-1.0.0.jar
```

The service starts on **http://localhost:8080**

### 3. Make sure your Python engine is running

```bash
# From your Python project directory
uvicorn main:app --host 0.0.0.0 --port 8000
```

---

## Docker Deployment

### 1. Set up environment variables

```bash
cp .env.example .env
# Edit .env and add your ORS_API_KEY
```

### 2. Place your Python engine in ./python-engine/

Your Python service needs its own `Dockerfile`.

### 3. Launch both services

```bash
docker-compose up --build
```

| Service         | URL                        |
|-----------------|----------------------------|
| Spring Boot API | http://localhost:8080       |
| Python Engine   | http://localhost:8000       |
| Health check    | GET /api/route/health       |

---

## API Reference

### POST /api/route

**Request:**
```json
{
  "startLat": 37.7749,
  "startLon": -122.4194,
  "endLat":   37.8044,
  "endLon":   -122.2712
}
```

**Response:** GeoJSON FeatureCollection
```json
{
  "type": "FeatureCollection",
  "total_energy_kwh": 4.27,
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "LineString",
        "coordinates": [[-122.4194, 37.7749], [-122.4180, 37.7761]]
      },
      "properties": {
        "segment_index": 0,
        "distance_meters": 178.4,
        "slope_degrees": -1.2,
        "energy_kwh": -0.03,
        "is_regen_zone": true,
        "color": "green"
      }
    }
  ]
}
```

**Color mapping for Leaflet:**
- `"green"` → regenerative braking zone (`is_regen_zone: true`)
- `"blue"` → standard driving segment

---

## Leaflet.js Integration Snippet

```javascript
fetch('http://localhost:8080/api/route', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ startLat, startLon, endLat, endLon })
})
.then(r => r.json())
.then(geojson => {
  L.geoJSON(geojson, {
    style: feature => ({
      color: feature.properties.color,
      weight: 5,
      opacity: 0.8
    })
  }).addTo(map);
});
```

---

## Configuration Reference

| Property              | Default                    | Description                  |
|-----------------------|----------------------------|------------------------------|
| `server.port`         | `8080`                     | HTTP port                    |
| `ors.api-key`         | *(required)*               | ORS API key                  |
| `python.engine.url`   | `http://localhost:8000`    | Python service base URL      |

---

## Deployment Notes

- In production, change `allowedOrigins("*")` in `CorsConfig.java` to your frontend's domain.
- Use environment variables or a secrets manager for `ors.api-key` — never hardcode it.
- For high-throughput scenarios, replace `.block()` calls with a fully reactive chain using Project Reactor.
