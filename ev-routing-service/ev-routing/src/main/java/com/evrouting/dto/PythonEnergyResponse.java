package com.evrouting.dto;

import java.util.List;

/**
 * Response received from the Python physics engine.
 * Field names use snake_case to match Python JSON conventions.
 */
public record PythonEnergyResponse(
        double total_energy_kwh,
        List<SegmentResult> segments
) {
    public record SegmentResult(
            double energy_kwh,
            boolean is_regen_zone
    ) {}
}
