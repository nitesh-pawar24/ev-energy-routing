"""
EV Physics Engine Microservice
FastAPI server running on localhost:8000
Endpoint: POST /calculate-energy
"""

import math
from typing import List
from fastapi import FastAPI
from pydantic import BaseModel
import uvicorn
from fastapi.responses import HTMLResponse
import os

# ---------------------------------------------------------------------------
# Pydantic Schemas
# ---------------------------------------------------------------------------

class SegmentRequest(BaseModel):
    distance_meters: float
    slope_degrees: float
    speed_ms: float


class EnergyRequest(BaseModel):
    segments: List[SegmentRequest]


class SegmentResponse(BaseModel):
    energy_kwh: float
    is_regen_zone: bool


class EnergyResponse(BaseModel):
    total_energy_kwh: float
    segments: List[SegmentResponse]


# ---------------------------------------------------------------------------
# Physics Engine
# ---------------------------------------------------------------------------

class EVPhysicsEngine:
    # Baseline EV parameters
    MASS_KG            = 1800.0   # vehicle mass
    FRONTAL_AREA_M2    = 2.2      # frontal area
    DRAG_COEFFICIENT   = 0.24     # aerodynamic drag coefficient (Cd)
    ROLLING_RESISTANCE = 0.015    # rolling resistance coefficient (μ)
    REGEN_EFFICIENCY   = 0.70     # regenerative braking efficiency
    GRAVITY_MS2        = 9.81     # gravitational acceleration
    AIR_DENSITY_KGM3   = 1.225    # air density at sea level (ρ)
    JOULES_PER_KWH     = 3_600_000.0

    def calculate_segment(self, segment: SegmentRequest) -> SegmentResponse:
        theta_rad = math.radians(segment.slope_degrees)
        v         = segment.speed_ms
        d         = segment.distance_meters
        m         = self.MASS_KG
        g         = self.GRAVITY_MS2
        rho       = self.AIR_DENSITY_KGM3
        Cd        = self.DRAG_COEFFICIENT
        A         = self.FRONTAL_AREA_M2
        mu        = self.ROLLING_RESISTANCE

        # Force components
        gradient_force      = m * g * math.sin(theta_rad)          # positive uphill, negative downhill
        aero_drag_force     = 0.5 * rho * v**2 * Cd * A            # always opposes motion (positive)
        rolling_resist_force = mu * m * g * math.cos(theta_rad)    # always opposes motion (positive)

        # Net force (negative means gravity dominates → regen possible)
        net_force = gradient_force + aero_drag_force + rolling_resist_force

        # Work done over the segment (Joules)
        work_joules = net_force * d

        # Regen logic: only if net_force is negative (gravity overpowers drag + friction)
        is_regen_zone = net_force < 0.0

        if is_regen_zone:
            # work_joules is negative; recovered energy is positive
            energy_joules = work_joules * self.REGEN_EFFICIENCY  # negative * efficiency → still negative
            energy_kwh    = energy_joules / self.JOULES_PER_KWH  # negative kWh (energy recovered)
        else:
            energy_kwh = work_joules / self.JOULES_PER_KWH       # positive kWh (energy consumed)

        return SegmentResponse(
            energy_kwh=round(energy_kwh, 6),
            is_regen_zone=is_regen_zone,
        )

    def calculate_route(self, request: EnergyRequest) -> EnergyResponse:
        segment_results = [self.calculate_segment(seg) for seg in request.segments]
        total_energy    = sum(s.energy_kwh for s in segment_results)

        return EnergyResponse(
            total_energy_kwh=round(total_energy, 6),
            segments=segment_results,
        )


# ---------------------------------------------------------------------------
# FastAPI App
# ---------------------------------------------------------------------------

app    = FastAPI(title="EV Physics Engine", version="1.0.0")
engine = EVPhysicsEngine()


@app.post("/calculate-energy", response_model=EnergyResponse)
def calculate_energy(request: EnergyRequest) -> EnergyResponse:
    """
    Accepts a list of route segments and returns total energy consumption
    with a per-segment breakdown including regenerative braking zones.
    """
    return engine.calculate_route(request)


@app.get("/health")
def health_check():
    return {"status": "ok", "service": "EV Physics Engine"}

@app.get("/", response_class=HTMLResponse)
def root():
    file_path = os.path.join(os.path.dirname(__file__), "templates", "index.html")
    with open(file_path, "r", encoding="utf-8") as f:
        return f.read()

# ---------------------------------------------------------------------------
# Entry Point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
