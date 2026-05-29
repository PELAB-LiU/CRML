within sri2_ref;
model Bindings
  ics_reqs requirement;
  SRISystem system;
equation
  requirement.T = system.T;
  requirement.normal_operation = CRMLtoModelica.Functions.cvBooleanToBoolean4(system.normal_operation);
  requirement.v1 = system.v1;
  requirement.v2 = system.v2;
  requirement.pump_in_service1 = CRMLtoModelica.Functions.cvBooleanToBoolean4(system.pump_in_service1);
  requirement.pump_in_service2 = CRMLtoModelica.Functions.cvBooleanToBoolean4(system.pump_in_service2);
  requirement.pump_in_service3 = CRMLtoModelica.Functions.cvBooleanToBoolean4(system.pump_in_service3);
  requirement.flow1 = system.flow1;
  requirement.flow2 = system.flow2;
  requirement.flow3 = system.flow3;
  annotation(experiment(StartTime = 0, StopTime = 200, Tolerance = 1e-6, Interval = 0.5));
end Bindings;
