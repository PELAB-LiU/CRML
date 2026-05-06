within fluid_storage;
model FluidStorageSystem "Physical fluid storage system with two tanks"

  model Tank "Single tank with inflow and outflow"
    parameter Real maxVolume = 2.0;
    parameter Real initialLevel = 1.0;

    Real level(start = initialLevel);

    Modelica.Blocks.Interfaces.RealInput flowIn annotation(
      Placement(transformation(origin = {0, 120}, extent = {{-20, -20}, {20, 20}}, rotation = -90)));
    Modelica.Blocks.Interfaces.RealInput flowOut annotation(
      Placement(transformation(origin = {0, -120}, extent = {{-20, -20}, {20, 20}}, rotation = -90)));
    Modelica.Blocks.Interfaces.RealOutput levelOut annotation(
      Placement(transformation(origin = {110, 0}, extent = {{-10, -10}, {10, 10}})));

  equation
    der(level) = flowIn - flowOut;
    levelOut = level;
  end Tank;

  Tank tank1(maxVolume = 2.0, initialLevel = 1.0);
  Tank tank2(maxVolume = 2.0, initialLevel = 0.5);

  // Sinusoidal pump into tank1: oscillates between 0.1 and 0.3, period = 50 s
  Modelica.Blocks.Sources.Sine pump(amplitude = 0.1, f = 0.02, offset = 0.2) annotation(
    Placement(transformation(origin = {-80, 60}, extent = {{-10, -10}, {10, 10}})));

  // Tank1 gravity drain: outflow = k * level (Torricelli-like)
  Modelica.Blocks.Math.Gain drain1(k = 0.15) annotation(
    Placement(transformation(origin = {-40, -50}, extent = {{-10, -10}, {10, 10}})));

  // Faster periodic disturbance into tank2 (period = 25 s), always positive
  Modelica.Blocks.Sources.Sine disturbance(amplitude = 0.05, f = 0.04, offset = 0.05) annotation(
    Placement(transformation(origin = {40, 80}, extent = {{-10, -10}, {10, 10}})));

  // Tank2 inflow = gravity drain from tank1 + disturbance
  Modelica.Blocks.Math.Add tank2Inflow annotation(
    Placement(transformation(origin = {20, 40}, extent = {{-10, -10}, {10, 10}})));

  // Tank2 gravity drain: outflow = k * level
  Modelica.Blocks.Math.Gain drain2(k = 0.2) annotation(
    Placement(transformation(origin = {60, -50}, extent = {{-10, -10}, {10, 10}})));

equation
  connect(pump.y, tank1.flowIn);
  connect(tank1.levelOut, drain1.u);
  connect(drain1.y, tank1.flowOut);
  connect(drain1.y, tank2Inflow.u1);
  connect(disturbance.y, tank2Inflow.u2);
  connect(tank2Inflow.y, tank2.flowIn);
  connect(tank2.levelOut, drain2.u);
  connect(drain2.y, tank2.flowOut);

  annotation(
    experiment(StartTime = 0, StopTime = 100, Tolerance = 1e-6, Interval = 0.2),
    uses(Modelica(version = "4.0.0")));
end FluidStorageSystem;
