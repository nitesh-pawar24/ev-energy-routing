package com.evrouting.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Partial mapping of the OpenRouteService GeoJSON response.
 * Unknown fields are ignored to keep the mapping lean.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrsRouteResponse(
        List<Feature> features
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(
            Geometry geometry,
            Properties properties
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            // Each coordinate is [lon, lat, elevation_meters]
            List<List<Double>> coordinates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            List<Segment> segments,
            Summary summary,
            Map<String, ExtraInfo> extra_info
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Segment(
                double distance,
                double duration
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Summary(
                double distance,
                double duration
        ) {}
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ExtraInfo(
                List<List<Double>> values
        ) {}
}
