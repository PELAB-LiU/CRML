within traffic;
model TrafficLightSystemMock "Parametric mock of a traffic light system for fuzzing"

  // ─── Green phase ────────────────────────────────────────────────────────────
  parameter Real green_start    = 5.0  "Time green turns on [s]";
  parameter Real green_duration = 30.1 "Duration of green phase [s] (< 30 violates req2)";

  // ─── Yellow phase ───────────────────────────────────────────────────────────
  // For req3: yellow must be active at (green_start + 30.2 s).
  // So green_duration + yellow_delay must be ≤ 30.2 s for req3 to pass.
  // A yellow_delay > (30.2 − green_duration) violates req3.
  parameter Real yellow_delay    = 0.05 "Delay from green-off to yellow-on [s] (> 0.15 violates req3 with default green_duration)";
  parameter Real yellow_duration = 5.0  "Duration of yellow phase [s]";

  // ─── req1 fault injection ────────────────────────────────────────────────────
  // A 0.5 s red intrusion starting at red_intrusion_start forces red on
  // during the green→yellow transition window, violating req1.
  // Default 1e9 means no intrusion.
  parameter Real red_intrusion_start = 1e9 "Start of a brief forced red [s] (1e9 = never)";

  // ─── Outputs ─────────────────────────────────────────────────────────────────
  Boolean green;
  Boolean yellow;
  Boolean red;

protected
  Boolean red_forced "Injected red signal for req1 fault scenarios";

equation
  green = time >= green_start and time < green_start + green_duration;

  yellow = time >= green_start + green_duration + yellow_delay
           and time < green_start + green_duration + yellow_delay + yellow_duration;

  // Forced-red intrusion (brief pulse to violate req1)
  red_forced = time >= red_intrusion_start and time < red_intrusion_start + 0.5;

  // red is the default state (not green, not yellow) plus any forced intrusion
  red = (not green and not yellow) or red_forced;

end TrafficLightSystemMock;
