within fluid_storage;
model Bindings
  SafeLevelRequirement requirement;
  FluidStorageSystem system;
  Boolean equivalency = requirement.tanksAreSafe == requirement.tanksAreSafe;
equation
  requirement.tank1.waterLevel = system.tank1.level;
  requirement.tank2.waterLevel = system.tank2.level;
  requirement.tank1.maxVolume = system.tank1.maxVolume;
  requirement.tank2.maxVolume = system.tank2.maxVolume;
  annotation(experiment(StartTime = 0, StopTime = 100, Tolerance = 1e-6, Interval = 0.2));
end Bindings;
