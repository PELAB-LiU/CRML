within pumps;

model MockPumpingSystem "Physical pumping system with three pumps"

  // ── Inner Pump model ────────────────────────────────────────────────────
  model Pump "Single pump with inOperation and temperature"
    parameter Real tempBase     = 42.0  "Baseline temperature [°C]";
    parameter Real tempAmp      = 12.0  "Temperature oscillation amplitude [°C]";
    parameter Real tempFreq     = 0.1   "Temperature oscillation frequency [1/min]";
    parameter Real switchPeriod = 25.0  "Toggle period of inOperation [min]";
    parameter Real phaseOffset  = 0.0   "Phase offset to de-correlate pumps";

    Modelica.Blocks.Interfaces.BooleanOutput inOperation
      "True if pump is in operation";
    Modelica.Blocks.Interfaces.RealOutput temperature
      "Pump temperature [°C]";
  equation
    temperature = tempBase + tempAmp * sin(2 * Modelica.Constants.pi
                    * tempFreq * time + phaseOffset);
    inOperation = sin(2 * Modelica.Constants.pi / switchPeriod
                    * time + phaseOffset) > 0;
  end Pump;

  // ── Pump instances ───────────────────────────────────────────────────────
  Pump pump1(phaseOffset = 0.0,   switchPeriod = 25.0, tempBase = 42.0, tempAmp = 12.0, tempFreq = 0.08);
  Pump pump2(phaseOffset = 2.094, switchPeriod = 70.0, tempBase = 38.0, tempAmp =  8.0, tempFreq = 0.13);
  Pump pump3(phaseOffset = 4.189, switchPeriod = 18.0, tempBase = 45.0, tempAmp = 10.0, tempFreq = 0.19);

  // ── System-level output ──────────────────────────────────────────────────
  Modelica.Blocks.Interfaces.BooleanOutput systemInOperation
    "True if the overall system is in operation";
equation
  systemInOperation = sin(2 * Modelica.Constants.pi / 47.0 * time) > 0;


  // ════════════════════════════════════════════════════════════════════════
  //  REQUIREMENTS
  // ════════════════════════════════════════════════════════════════════════

protected
  Boolean sysNotOperating = not systemInOperation;
  Boolean sysRising       = edge(systemInOperation);
  Boolean sysFalling      = edge(sysNotOperating);

  // Pump-on/-off helpers
  Boolean p1On  = pump1.inOperation;
  Boolean p1Off = not pump1.inOperation;
  Boolean p2On  = pump2.inOperation;
  Boolean p2Off = not pump2.inOperation;
  Boolean p3On  = pump3.inOperation;
  Boolean p3Off = not pump3.inOperation;

  // ── Discrete time latches for CRMLPeriod event timestamps ────────────────
  //    CRMLPeriod.start_event.t / close_event.t are continuous Real fields;
  //    we drive them from discrete Real latches updated in algorithm blocks.
  discrete Real t_sysOn_start(start = 0);
  discrete Real t_sysOn_close(start = 0);

  discrete Real t_p1_start(start = 0);
  discrete Real t_p1_close(start = 0);
  discrete Real t_p2_start(start = 0);
  discrete Real t_p2_close(start = 0);
  discrete Real t_p3_start(start = 0);
  discrete Real t_p3_close(start = 0);

  // ── System-on period ─────────────────────────────────────────────────────
  CRMLtoModelica.Types.CRMLPeriod sysOnPeriod(
    isLeftBoundaryIncluded  = true,
    isRightBoundaryIncluded = true);

algorithm
  when sysRising  then t_sysOn_start := time; end when;
  when sysFalling then t_sysOn_close := time; end when;

equation
  sysOnPeriod.is_open       = systemInOperation;
  sysOnPeriod.start_event.b = CRMLtoModelica.Functions.cvBooleanToBoolean4(sysRising);
  sysOnPeriod.start_event.t = t_sysOn_start;
  sysOnPeriod.close_event.b = CRMLtoModelica.Functions.cvBooleanToBoolean4(sysFalling);
  sysOnPeriod.close_event.t = t_sysOn_close;

  // ────────────────────────────────────────────────────────────────────────
  //  R1  While system is in operation, pump must not start more than twice.
  // ────────────────────────────────────────────────────────────────────────

protected
  Integer startupCount_p1(start = 0);
  Integer startupCount_p2(start = 0);
  Integer startupCount_p3(start = 0);
public
  Boolean r1_p1 "R1 satisfied for pump1";
  Boolean r1_p2 "R1 satisfied for pump2";
  Boolean r1_p3 "R1 satisfied for pump3";

algorithm
  when edge(systemInOperation) then
    startupCount_p1 := 0;
    startupCount_p2 := 0;
    startupCount_p3 := 0;
  end when;
  when edge(pump1.inOperation) and systemInOperation then
    startupCount_p1 := startupCount_p1 + 1;
  end when;
  when edge(pump2.inOperation) and systemInOperation then
    startupCount_p2 := startupCount_p2 + 1;
  end when;
  when edge(pump3.inOperation) and systemInOperation then
    startupCount_p3 := startupCount_p3 + 1;
  end when;

equation
  r1_p1 = startupCount_p1 <= 2;
  r1_p2 = startupCount_p2 <= 2;
  r1_p3 = startupCount_p3 <= 2;

  // ────────────────────────────────────────────────────────────────────────
  //  R2  At least 60 min must separate two consecutive pump startups.
  // ────────────────────────────────────────────────────────────────────────

protected
  discrete Real lastStartup_p1(start = -1e9);
  discrete Real lastStartup_p2(start = -1e9);
  discrete Real lastStartup_p3(start = -1e9);
  Boolean r2_violation_p1(start = false);
  Boolean r2_violation_p2(start = false);
  Boolean r2_violation_p3(start = false);
public
  Boolean r2_p1 "R2 satisfied for pump1";
  Boolean r2_p2 "R2 satisfied for pump2";
  Boolean r2_p3 "R2 satisfied for pump3";

algorithm
  when edge(pump1.inOperation) then
    r2_violation_p1 := (time - lastStartup_p1) < 60.0;
    lastStartup_p1  := time;
  end when;
  when edge(pump2.inOperation) then
    r2_violation_p2 := (time - lastStartup_p2) < 60.0;
    lastStartup_p2  := time;
  end when;
  when edge(pump3.inOperation) then
    r2_violation_p3 := (time - lastStartup_p3) < 60.0;
    lastStartup_p3  := time;
  end when;

equation
  r2_p1 = not r2_violation_p1;
  r2_p2 = not r2_violation_p2;
  r2_p3 = not r2_violation_p3;

  // ────────────────────────────────────────────────────────────────────────
  //  R3  While pump is in operation, temperature must always stay below 50°C.
  // ────────────────────────────────────────────────────────────────────────

protected
  CRMLtoModelica.Types.CRMLPeriod pumpOnPeriod_p1(
    isLeftBoundaryIncluded  = true,
    isRightBoundaryIncluded = true);
  CRMLtoModelica.Types.CRMLPeriod pumpOnPeriod_p2(
    isLeftBoundaryIncluded  = true,
    isRightBoundaryIncluded = true);
  CRMLtoModelica.Types.CRMLPeriod pumpOnPeriod_p3(
    isLeftBoundaryIncluded  = true,
    isRightBoundaryIncluded = true);

  CRMLtoModelica.Types.Boolean4 tempOk_p1;
  CRMLtoModelica.Types.Boolean4 tempOk_p2;
  CRMLtoModelica.Types.Boolean4 tempOk_p3;

  CRMLtoModelica.Blocks.Integrate intR3_p1;
  CRMLtoModelica.Blocks.Integrate intR3_p2;
  CRMLtoModelica.Blocks.Integrate intR3_p3;

  CRMLtoModelica.Types.Boolean4 r3_p1_b4;
  CRMLtoModelica.Types.Boolean4 r3_p2_b4;
  CRMLtoModelica.Types.Boolean4 r3_p3_b4;

public
  Boolean r3_p1 "R3 satisfied for pump1";
  Boolean r3_p2 "R3 satisfied for pump2";
  Boolean r3_p3 "R3 satisfied for pump3";

algorithm
  when edge(p1On)  then t_p1_start := time; end when;
  when edge(p1Off) then t_p1_close := time; end when;
  when edge(p2On)  then t_p2_start := time; end when;
  when edge(p2Off) then t_p2_close := time; end when;
  when edge(p3On)  then t_p3_start := time; end when;
  when edge(p3Off) then t_p3_close := time; end when;

equation
  pumpOnPeriod_p1.is_open         = p1On;
  pumpOnPeriod_p1.start_event.b   = CRMLtoModelica.Functions.cvBooleanToBoolean4(edge(p1On));
  pumpOnPeriod_p1.start_event.t   = t_p1_start;
  pumpOnPeriod_p1.close_event.b   = CRMLtoModelica.Functions.cvBooleanToBoolean4(edge(p1Off));
  pumpOnPeriod_p1.close_event.t   = t_p1_close;

  pumpOnPeriod_p2.is_open         = p2On;
  pumpOnPeriod_p2.start_event.b   = CRMLtoModelica.Functions.cvBooleanToBoolean4(edge(p2On));
  pumpOnPeriod_p2.start_event.t   = t_p2_start;
  pumpOnPeriod_p2.close_event.b   = CRMLtoModelica.Functions.cvBooleanToBoolean4(edge(p2Off));
  pumpOnPeriod_p2.close_event.t   = t_p2_close;

  pumpOnPeriod_p3.is_open         = p3On;
  pumpOnPeriod_p3.start_event.b   = CRMLtoModelica.Functions.cvBooleanToBoolean4(edge(p3On));
  pumpOnPeriod_p3.start_event.t   = t_p3_start;
  pumpOnPeriod_p3.close_event.b   = CRMLtoModelica.Functions.cvBooleanToBoolean4(edge(p3Off));
  pumpOnPeriod_p3.close_event.t   = t_p3_close;

  tempOk_p1 = CRMLtoModelica.Functions.cvBooleanToBoolean4(pump1.temperature < 50.0);
  tempOk_p2 = CRMLtoModelica.Functions.cvBooleanToBoolean4(pump2.temperature < 50.0);
  tempOk_p3 = CRMLtoModelica.Functions.cvBooleanToBoolean4(pump3.temperature < 50.0);

  intR3_p1.r1 = tempOk_p1;
  intR3_p1.r2 = pumpOnPeriod_p1;
  r3_p1_b4    = intR3_p1.out;

  intR3_p2.r1 = tempOk_p2;
  intR3_p2.r2 = pumpOnPeriod_p2;
  r3_p2_b4    = intR3_p2.out;

  intR3_p3.r1 = tempOk_p3;
  intR3_p3.r2 = pumpOnPeriod_p3;
  r3_p3_b4    = intR3_p3.out;

  r3_p1 = (r3_p1_b4 <> CRMLtoModelica.Types.Boolean4.false4);
  r3_p2 = (r3_p2_b4 <> CRMLtoModelica.Types.Boolean4.false4);
  r3_p3 = (r3_p3_b4 <> CRMLtoModelica.Types.Boolean4.false4);

  // ────────────────────────────────────────────────────────────────────────
  //  R4  While system is on, after temp > 40°C, cumulated duration above
  //      40°C must not exceed 1 min over the next 15 min.
  // ────────────────────────────────────────────────────────────────────────

protected
  Boolean p1Above40 = pump1.temperature > 40.0;
  Boolean p2Above40 = pump2.temperature > 40.0;
  Boolean p3Above40 = pump3.temperature > 40.0;

  discrete Real window_start_p1(start = -1e9);
  discrete Real window_start_p2(start = -1e9);
  discrete Real window_start_p3(start = -1e9);

  Real cumAbove40_p1(start = 0, fixed = true);
  Real cumAbove40_p2(start = 0, fixed = true);
  Real cumAbove40_p3(start = 0, fixed = true);

  Boolean inWindow_p1;
  Boolean inWindow_p2;
  Boolean inWindow_p3;

public
  Boolean r4_p1 "R4 satisfied for pump1";
  Boolean r4_p2 "R4 satisfied for pump2";
  Boolean r4_p3 "R4 satisfied for pump3";

algorithm
  when edge(p1Above40) and systemInOperation then
    window_start_p1 := time;
  end when;
  when edge(p2Above40) and systemInOperation then
    window_start_p2 := time;
  end when;
  when edge(p3Above40) and systemInOperation then
    window_start_p3 := time;
  end when;

equation
  inWindow_p1 = systemInOperation and (time - window_start_p1 <= 15.0)
                  and (window_start_p1 > -1e8);
  inWindow_p2 = systemInOperation and (time - window_start_p2 <= 15.0)
                  and (window_start_p2 > -1e8);
  inWindow_p3 = systemInOperation and (time - window_start_p3 <= 15.0)
                  and (window_start_p3 > -1e8);

  der(cumAbove40_p1) = if inWindow_p1 and pump1.temperature > 40.0 then 1.0 else 0.0;
  der(cumAbove40_p2) = if inWindow_p2 and pump2.temperature > 40.0 then 1.0 else 0.0;
  der(cumAbove40_p3) = if inWindow_p3 and pump3.temperature > 40.0 then 1.0 else 0.0;

  when edge(p1Above40) and systemInOperation then
    reinit(cumAbove40_p1, 0);
  end when;
  when edge(p2Above40) and systemInOperation then
    reinit(cumAbove40_p2, 0);
  end when;
  when edge(p3Above40) and systemInOperation then
    reinit(cumAbove40_p3, 0);
  end when;

  r4_p1 = not (inWindow_p1 and cumAbove40_p1 > 1.0);
  r4_p2 = not (inWindow_p2 and cumAbove40_p2 > 1.0);
  r4_p3 = not (inWindow_p3 and cumAbove40_p3 > 1.0);

  // ════════════════════════════════════════════════════════════════════════
  //  SUMMARY OUTPUTS
  // ════════════════════════════════════════════════════════════════════════
public
  Boolean req_R1_ok = r1_p1 and r1_p2 and r1_p3
    "R1: no pump started more than twice during system-on";
  Boolean req_R2_ok = r2_p1 and r2_p2 and r2_p3
    "R2: at least 60 min between consecutive startups";
  Boolean req_R3_ok = r3_p1 and r3_p2 and r3_p3
    "R3: temperature always below 50°C while pump running";
  Boolean req_R4_ok = r4_p1 and r4_p2 and r4_p3
    "R4: cumulated time above 40°C does not exceed 1 min per 15-min window";

end MockPumpingSystem;
