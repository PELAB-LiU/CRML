within fluid_storage;
model FluidStorageSystemMock "Parametric mock of fluid storage system for fuzzing"

  // --- Tank 1 ---
  parameter Real maxVolume1 = 2.0 "Tank 1 capacity [m3]";
  parameter Real tank1_fault_start = 1e9 "Time tank 1 enters overflow [s] (1e9 = never)";
  parameter Real tank1_recovery    = 1e9 "Time tank 1 returns to safe [s] (1e9 = stays faulted)";

  // --- Tank 2 ---
  parameter Real maxVolume2 = 2.0 "Tank 2 capacity [m3]";
  parameter Real tank2_fault_start = 1e9 "Time tank 2 enters overflow [s] (1e9 = never)";
  parameter Real tank2_recovery    = 1e9 "Time tank 2 returns to safe [s] (1e9 = stays faulted)";

  // --- Outputs wired into requirement inputs ---
  Real tank1_waterLevel;
  Real tank1_maxVolume;
  Real tank2_waterLevel;
  Real tank2_maxVolume;

equation
  tank1_maxVolume = maxVolume1;
  tank2_maxVolume = maxVolume2;

  // Nominal = 40 % of capacity (safely below 80 % threshold)
  // Fault   = 90 % of capacity (above 80 % threshold regardless of volume)
  tank1_waterLevel = if time < tank1_fault_start then 0.4 * maxVolume1
                     else if time < tank1_recovery then 0.9 * maxVolume1
                     else 0.4 * maxVolume1;

  tank2_waterLevel = if time < tank2_fault_start then 0.4 * maxVolume2
                     else if time < tank2_recovery then 0.9 * maxVolume2
                     else 0.4 * maxVolume2;

end FluidStorageSystemMock;
