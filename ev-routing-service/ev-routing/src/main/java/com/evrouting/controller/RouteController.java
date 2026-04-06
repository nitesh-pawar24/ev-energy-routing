package com.evrouting.controller;

import com.evrouting.dto.FrontendRouteRequest;
import com.evrouting.dto.RouteResponse;
import com.evrouting.service.RoutingOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RoutingOrchestratorService orchestratorService;

    public RouteController(RoutingOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> calculateRoute(@RequestBody FrontendRouteRequest request) {
        // Assume orchestratorService.orchestrate(...) returns your GeoJSON FeatureCollection
        Object geoJson = orchestratorService.orchestrate(request);

        // TODO: fill these from your energy/distance logic
        double totalDistanceKm = 12.5;      // ← replace with actual value
        double totalEnergyKwh = 4.27;       // ← replace with actual value

        RouteResponse response = new RouteResponse(totalDistanceKm, totalEnergyKwh, geoJson);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("EV Routing Service is UP");
    }
}