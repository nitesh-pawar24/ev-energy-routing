package com.evrouting.service;

import com.evrouting.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class RoutingOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(RoutingOrchestratorService.class);

    // Default vehicle speed assumed when ORS doesn't give per-segment speed
    // 40 km/h expressed in m/s
    private static final double DEFAULT_SPEED_MS = 11.11;

    private final WebClient orsWebClient;
    private final WebClient pythonWebClient;

    @Value("${ors.api-key}")
    private String orsApiKey;

    public RoutingOrchestratorService(WebClient orsWebClient, WebClient pythonWebClient) {
        this.orsWebClient   = orsWebClient;
        this.pythonWebClient = pythonWebClient;
    }

    // ──────────────────────────────────────────────────────────────
    // PUBLIC: Full orchestration pipeline
    // ──────────────────────────────────────────────────────────────

    public Object orchestrate(FrontendRouteRequest request) {
        log.info("Route request: ({}, {}) → ({}, {})",
                request.startLat(), request.startLon(),
                request.endLat(),   request.endLon());

        // Step 1 — Fetch route geometry from ORS
        OrsRouteResponse orsResponse = fetchOrsRoute(request);

        // Step 2 — Flatten coordinate list [lon, lat, elev]
        List<double[]> coords = flattenCoordinates(orsResponse);

        // Step 3 — Build per-segment DTOs (distance, slope, speed, geometry)
        List<RouteSegment> segments = buildSegments(coords);

        // Step 4 — Call Python physics engine
        PythonEnergyResponse energyResponse = callPythonEngine(segments);

        // Step 5 — Assemble and return GeoJSON FeatureCollection
        return buildGeoJsonResponse(segments, energyResponse);
    }

    // ──────────────────────────────────────────────────────────────
    // STEP 1: Call OpenRouteService
    // ──────────────────────────────────────────────────────────────

    private OrsRouteResponse fetchOrsRoute(FrontendRouteRequest req) {
        // ORS expects coordinates in [longitude, latitude] order
        String body = """
                {
                  "coordinates": [[%f, %f], [%f, %f]],
                  "elevation": true,
                  "instructions": false,
                  "extra_info": ["steepness"]
                }
                """.formatted(
                req.startLon(), req.startLat(),
                req.endLon(),   req.endLat()
        );

        OrsRouteResponse response = orsWebClient.post()
                .uri("/v2/directions/driving-car/geojson")
                .header("Authorization", orsApiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OrsRouteResponse.class)
                .doOnError(e -> log.error("ORS call failed: {}", e.getMessage()))
                .block();

        if (response == null || response.features() == null || response.features().isEmpty()) {
            throw new RuntimeException("ORS returned no route features for the given coordinates.");
        }

        log.debug("ORS returned {} feature(s)", response.features().size());
        return response;
    }

    // ──────────────────────────────────────────────────────────────
    // STEP 2: Flatten ORS coordinates → [lon, lat, elevation]
    // ──────────────────────────────────────────────────────────────

    private List<double[]> flattenCoordinates(OrsRouteResponse orsResponse) {
        var feature = orsResponse.features().get(0);
        var coords = feature.geometry().coordinates();
        // var steepness = feature.properties().extra_info() != null
        //         ? feature.properties().extra_info().get("steepness")
        //         : null;

        // var steepness = null; // TEMP FIX

        // List<Double> steepness = null;

        var extraInfo = feature.properties().extra_info();
        List<List<Double>> steepness = null;

        if (extraInfo != null && extraInfo.containsKey("steepness")) 
        {
            steepness = extraInfo.get("steepness").values();
        }

        List<double[]> flat = new ArrayList<>();
        for (int i = 0; i < coords.size(); i++) {
            double lon = coords.get(i).get(0);
            double lat = coords.get(i).get(1);
            double elev;
            if (coords.get(i).size() > 2) {
                elev = coords.get(i).get(2);
            
            // } else if (steepness != null && i < steepness.size()) {
            //     // Accumulate elevation from previous point using steepness %
            //     elev = (i == 0 ? 0.0 : flat.get(i - 1)[2] + steepness.get(i));
            } else {
                elev = 0.0;
            }
            flat.add(new double[]{lon, lat, elev});
        }
        return flat;
    }

    // ──────────────────────────────────────────────────────────────
    // STEP 3: Build RouteSegments from consecutive coordinate pairs
    // ──────────────────────────────────────────────────────────────

    private List<RouteSegment> buildSegments(List<double[]> coords) {
        List<RouteSegment> segments = new ArrayList<>();

        for (int i = 0; i < coords.size() - 1; i++) {
            double[] from = coords.get(i);
            double[] to   = coords.get(i + 1);

            // Haversine distance between the two points
            double distance = haversineMeters(from[1], from[0], to[1], to[0]);

            // Slope in degrees from elevation delta and horizontal distance
            double elevDelta = to[2] - from[2];
            double slope = (distance > 0.0)
                    ? Math.toDegrees(Math.atan(elevDelta / distance))
                    : 0.0;

            // Geometry: the two [lon, lat] endpoints of this segment
            double[][] segGeom = {
                    {from[0], from[1]},
                    {to[0],   to[1]}
            };

            segments.add(new RouteSegment(distance, slope, DEFAULT_SPEED_MS, segGeom));
        }

        log.debug("Built {} route segments", segments.size());
        return segments;
    }

    // ──────────────────────────────────────────────────────────────
    // STEP 4: POST segments to Python physics engine
    // ──────────────────────────────────────────────────────────────

    private PythonEnergyResponse callPythonEngine(List<RouteSegment> segments) {
        List<PythonEnergyRequest.SegmentInput> inputs = segments.stream()
                .map(s -> new PythonEnergyRequest.SegmentInput(
                        s.distanceMeters(),
                        s.slopeDegrees(),
                        s.speedMs()
                ))
                .toList();

        PythonEnergyRequest pythonRequest = new PythonEnergyRequest(inputs);

        PythonEnergyResponse response = pythonWebClient.post()
                .uri("/calculate-energy")
                .bodyValue(pythonRequest)
                .retrieve()
                .bodyToMono(PythonEnergyResponse.class)
                .doOnError(e -> log.error("Python engine call failed: {}", e.getMessage()))
                .block();

        if (response == null) {
            throw new RuntimeException("Python energy engine returned a null response.");
        }

        log.debug("Python engine total energy: {} kWh", response.total_energy_kwh());
        return response;
    }

    // ──────────────────────────────────────────────────────────────
    // STEP 5: Build GeoJSON FeatureCollection
    // ──────────────────────────────────────────────────────────────

    private Object buildGeoJsonResponse(
            List<RouteSegment> segments,
            PythonEnergyResponse energyResponse) {

        List<PythonEnergyResponse.SegmentResult> results = energyResponse.segments();
        List<Map<String, Object>> features = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            RouteSegment seg = segments.get(i);

            // Safely get the matching Python result (guard against size mismatch)
            PythonEnergyResponse.SegmentResult result =
                    (results != null && i < results.size()) ? results.get(i) : null;

            boolean isRegen = result != null && result.is_regen_zone();
            double  energy  = result != null ? result.energy_kwh() : 0.0;

            // GeoJSON LineString geometry for this segment
            Map<String, Object> geometry = Map.of(
                    "type", "LineString",
                    "coordinates", List.of(
                            List.of(seg.geometry()[0][0], seg.geometry()[0][1]),
                            List.of(seg.geometry()[1][0], seg.geometry()[1][1])
                    )
            );

            // Properties — color drives Leaflet polyline styling
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("segment_index",   i);
            properties.put("distance_meters", seg.distanceMeters());
            properties.put("slope_degrees",   seg.slopeDegrees());
            properties.put("energy_kwh",      energy);
            properties.put("is_regen_zone",   isRegen);
            properties.put("color",           isRegen ? "green" : "blue");

            features.add(Map.of(
                    "type",       "Feature",
                    "geometry",   geometry,
                    "properties", properties
            ));
        }

        // Top-level FeatureCollection with summary metadata
        Map<String, Object> featureCollection = new LinkedHashMap<>();
        featureCollection.put("type",             "FeatureCollection");
        featureCollection.put("features",         features);
        featureCollection.put("total_energy_kwh", energyResponse.total_energy_kwh());

        return featureCollection;
    }

    // ──────────────────────────────────────────────────────────────
    // UTILITY: Haversine great-circle distance in meters
    // ──────────────────────────────────────────────────────────────

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0; // Earth's mean radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}