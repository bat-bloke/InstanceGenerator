# InstanceGenerator
VRP Test Instance Generator using real world road maps

Designed for use in the Time-Constrained Heterogeneous Specimen Colelction Problem, though can be easily adapted to fit other VRPs.
Uses a GraphHopper (locally hosted) engine to generate distances and times for travel (traffic free), before a simple a traffic model is applied (based on DfT data).
Timings and other inputs are fed in via a text file.
