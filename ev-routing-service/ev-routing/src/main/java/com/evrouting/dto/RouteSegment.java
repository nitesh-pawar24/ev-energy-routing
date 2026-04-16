package com.evrouting.dto;

/**
 * Internal transfer object representing a single route segment
 * parsed from ORS and forwarded to the Python physics engine.
 *
 * geometry: array of [lon, lat] pairs — the two endpoints of this segment.
 */
public record RouteSegment(
        double distanceMeters,
        double slopeDegrees,
        double speedMs,
        double[][] geometry
) {}
