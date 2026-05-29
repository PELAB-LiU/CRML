within sri2_ref;
model SRISystem "Parametric mock of the SRI physical system for fuzzing"

  // --- Temperature ---
  parameter Real T_normal = 23.0 "Nominal temperature [°C], must be in [16, 30]";
  parameter Real T_fault = 35.0 "Fault temperature [°C], outside [16, 30]";
  parameter Real T_fault_start = 50.0 "Time when temperature leaves normal range [s]";
  parameter Real T_recovery = 100.0 "Time when temperature returns to normal range [s]";

  // --- Heat exchanger fluid speeds ---
  parameter Real v_nominal = 4.0 "Nominal fluid speed [m/s], must be <= 6";
  parameter Real v_fault = 8.0 "Fault fluid speed [m/s], above 6 m/s limit";
  parameter Real v1_fault_start = 1e9 "Time when v1 exceeds limit [s] (default: never)";
  parameter Real v2_fault_start = 1e9 "Time when v2 exceeds limit [s] (default: never)";

  // --- Pump flows ---
  parameter Real flow_nominal = 800.0 "Nominal pump flow [t/h], must be >= 700";
  parameter Real flow_fault = 600.0 "Fault pump flow [t/h], below 700 t/h minimum";
  parameter Real flow1_fault_start = 1e9 "Time when flow1 drops below limit [s] (default: never)";
  parameter Real flow2_fault_start = 1e9 "Time when flow2 drops below limit [s] (default: never)";
  parameter Real flow3_fault_start = 1e9 "Time when flow3 drops below limit [s] (default: never)";

  // --- Pump in-service windows ---
  parameter Real pump1_on = 0.0 "Time pump 1 enters service [s]";
  parameter Real pump1_off = 1e9 "Time pump 1 leaves service [s] (default: always on)";
  parameter Real pump2_on = 0.0 "Time pump 2 enters service [s]";
  parameter Real pump2_off = 1e9 "Time pump 2 leaves service [s]";
  parameter Real pump3_on = 0.0 "Time pump 3 enters service [s]";
  parameter Real pump3_off = 1e9 "Time pump 3 leaves service [s]";

  // --- Outputs ---
  Real T "SRI temperature [°C]";
  Real v1 "Fluid speed in heat exchanger 1 [m/s]";
  Real v2 "Fluid speed in heat exchanger 2 [m/s]";
  Real flow1 "Flow for pump 1 [t/h]";
  Real flow2 "Flow for pump 2 [t/h]";
  Real flow3 "Flow for pump 3 [t/h]";
  Boolean normal_operation "True when SRI is in normal operation";
  Boolean pump_in_service1 "True when pump 1 is in service";
  Boolean pump_in_service2 "True when pump 2 is in service";
  Boolean pump_in_service3 "True when pump 3 is in service";

equation
  T = if time < T_fault_start then T_normal
      else if time < T_recovery then T_fault
      else T_normal;

  normal_operation = (T >= 16) and (T <= 30);

  v1 = if time < v1_fault_start then v_nominal else v_fault;
  v2 = if time < v2_fault_start then v_nominal else v_fault;

  flow1 = if time < flow1_fault_start then flow_nominal else flow_fault;
  flow2 = if time < flow2_fault_start then flow_nominal else flow_fault;
  flow3 = if time < flow3_fault_start then flow_nominal else flow_fault;

  pump_in_service1 = (time >= pump1_on) and (time < pump1_off);
  pump_in_service2 = (time >= pump2_on) and (time < pump2_off);
  pump_in_service3 = (time >= pump3_on) and (time < pump3_off);

  annotation(experiment(StartTime = 0, StopTime = 200, Tolerance = 1e-6, Interval = 0.5));
end SRISystem;
