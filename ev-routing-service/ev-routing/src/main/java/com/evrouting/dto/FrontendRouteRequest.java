package com.evrouting.dto;

/**
 * Incoming request from the Leaflet.js frontend.
 */
public record FrontendRouteRequest(
        double startLat,
        double startLon,
        double endLat,
        double endLon
) {}
