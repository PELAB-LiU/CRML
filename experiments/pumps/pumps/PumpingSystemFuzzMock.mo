within pumps;
model PumpingSystemFuzzMock "Parametric mock of pumping system for fuzzing"

  // ─── System operation window ──────────────────────────────────────────────
  parameter Real sys_on_start = 5.0   "System enters operation [time units]";
  parameter Real sys_on_end   = 180.0 "System leaves operation [time units]";

  // ─── Pump 1 ───────────────────────────────────────────────────────────────
  // Up to three start/stop pairs.  Default 1e9 means the event never occurs.
  // To violate R2: set (p1_start2 − p1_start1) < 1.0 with p1_stop1 < p1_start2.
  // To violate R1: supply a valid p1_start3 inside [sys_on_start, sys_on_end].
  parameter Real p1_start1     = 10.0  "Pump 1 first startup [time units]";
  parameter Real p1_stop1      = 50.0  "Pump 1 first shutdown [time units]";
  parameter Real p1_start2     = 1e9   "Pump 1 second startup (1e9 = no restart)";
  parameter Real p1_stop2      = 1e9   "Pump 1 second shutdown";
  parameter Real p1_start3     = 1e9   "Pump 1 third startup — inside sys_on violates R1";
  parameter Real p1_stop3      = 1e9   "Pump 1 third shutdown";
  parameter Real p1_temp_fault = 1e9   "Time pump 1 temperature rises to 55°C while started (1e9 = never)";

  // ─── Pump 2 ───────────────────────────────────────────────────────────────
  parameter Real p2_start1     = 15.0;
  parameter Real p2_stop1      = 55.0;
  parameter Real p2_start2     = 1e9;
  parameter Real p2_stop2      = 1e9;
  parameter Real p2_start3     = 1e9;
  parameter Real p2_stop3      = 1e9;
  parameter Real p2_temp_fault = 1e9;

  // ─── Pump 3 ───────────────────────────────────────────────────────────────
  parameter Real p3_start1     = 20.0;
  parameter Real p3_stop1      = 60.0;
  parameter Real p3_start2     = 1e9;
  parameter Real p3_stop2      = 1e9;
  parameter Real p3_start3     = 1e9;
  parameter Real p3_stop3      = 1e9;
  parameter Real p3_temp_fault = 1e9;

  // ─── Outputs (wired into requirement inputs) ─────────────────────────────
  Boolean inOperation;
  Boolean pump1_isStarted;
  Real    pump1_temperature;
  Boolean pump2_isStarted;
  Real    pump2_temperature;
  Boolean pump3_isStarted;
  Real    pump3_temperature;

equation
  inOperation = time >= sys_on_start and time < sys_on_end;

  pump1_isStarted = (time >= p1_start1 and time < p1_stop1)
                    or (time >= p1_start2 and time < p1_stop2)
                    or (time >= p1_start3 and time < p1_stop3);

  // Nominal temperature 40°C; fault temperature 55°C (> 50 threshold) when
  // pump is started and the fault activation time has been reached.
  pump1_temperature = if pump1_isStarted and time >= p1_temp_fault then 55.0 else 40.0;

  pump2_isStarted = (time >= p2_start1 and time < p2_stop1)
                    or (time >= p2_start2 and time < p2_stop2)
                    or (time >= p2_start3 and time < p2_stop3);

  pump2_temperature = if pump2_isStarted and time >= p2_temp_fault then 55.0 else 40.0;

  pump3_isStarted = (time >= p3_start1 and time < p3_stop1)
                    or (time >= p3_start2 and time < p3_stop2)
                    or (time >= p3_start3 and time < p3_stop3);

  pump3_temperature = if pump3_isStarted and time >= p3_temp_fault then 55.0 else 40.0;

end PumpingSystemFuzzMock;
