package com.evrouting.dto;

import java.util.List;

/**
 * Payload sent to the local Python physics engine at POST /calculate-energy.
 * Field names use snake_case to match Python conventions.
 */
public record PythonEnergyRequest(
        List<SegmentInput> segments
) {
    public record SegmentInput(
            double distance_meters,
            double slope_degrees,
            double speed_ms
    ) {}
}
