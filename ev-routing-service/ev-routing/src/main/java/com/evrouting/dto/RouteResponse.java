// RouteResponse.java
package com.evrouting.dto;

public record RouteResponse(
    double totalDistanceKm,
    double totalEnergyKwh,
    Object routeGeoJson
) {}