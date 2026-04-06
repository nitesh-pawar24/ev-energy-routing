# ev-energy-routing

Overview
Eco-Path is a smart routing system designed for electric vehicles (EVs) that prioritizes energy efficiency over shortest distance or fastest time.
Unlike traditional navigation systems, Eco-Path considers road elevation, distance, and traffic conditions to compute a route that minimizes battery consumption — making it ideal for EV users running low on charge.

Problem Statement
Most navigation systems (like Google Maps) optimize for time or distance, but not for energy consumption.
For EVs:
Steep roads drain battery faster
Stop-and-go traffic reduces efficiency
Shortest path is not always the best path

Solution
Eco-Path introduces a custom routing engine that computes the least energy-consuming route using graph-based algorithms.
It compares:
Fastest Route (distance-based)
Eco Route (energy-based)
