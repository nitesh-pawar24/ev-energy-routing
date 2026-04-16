package com.evrouting.controller;

import com.evrouting.dto.FrontendRouteRequest;
import com.evrouting.dto.RouteResponse;
import com.evrouting.service.RoutingOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;


@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RoutingOrchestratorService orchestratorService;

    public RouteController(RoutingOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> calculateRoute(@RequestBody FrontendRouteRequest request) 
    {
        System.out.println("START: " + request.startLat() + ", " + request.startLon());
        System.out.println("END: " + request.endLat() + ", " + request.endLon());

        Map<String, Object> geoJson = (Map<String, Object>) orchestratorService.orchestrate(request);

        double totalEnergyKwh = (double) geoJson.get("total_energy_kwh");

        List<Map<String, Object>> features =
                (List<Map<String, Object>>) geoJson.get("features");

        double totalDistanceMeters = 0.0;

        for (Map<String, Object> f : features) {
            Map<String, Object> props = (Map<String, Object>) f.get("properties");
            totalDistanceMeters += (double) props.get("distance_meters");
        }

        double totalDistanceKm = totalDistanceMeters / 1000.0;

        RouteResponse response = new RouteResponse(
                totalDistanceKm,
                totalEnergyKwh,
                geoJson
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("EV Routing Service is UP");
    }
}