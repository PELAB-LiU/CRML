"""Fuzzing configuration for the Pumping System domain.

.. note:: **Known OMC build limitation**

   The current CRML compiler generates a ``'countinside'`` helper model that
   declares its period parameter as ``CRMLPeriod`` (singular), while the
   enclosing ``'check count<='`` passes it a ``CRMLPeriods`` (plural) produced
   by the ``'during'`` operator.  OMC 1.26 rejects this type mismatch when the
   compiled model is instantiated twice inside the comparison harness.

   Attempting to build the harness raises ``OMCBuildError`` with the message::

       Type mismatch in binding P = candidate.pump1.'check count<=69'.P,
       expected subtype of CRMLtoModelica.Types.CRMLPeriod,
       got type CRMLtoModelica.Types.CRMLPeriods.

   This is a bug in the CRML-to-Modelica compiler output and requires an
   upstream fix to ``OMGenerator``.  The domain configuration below is
   structurally correct; callers should guard against ``OMCBuildError``::

       from experiments.harness import OMCBuildError
       try:
           result = harness.run(...)
       except OMCBuildError as e:
           print(f"Build error (known pumps CRML issue): {e}")


Requirements per pump (pumps.crml):
  R1 — While system is in operation, pump must not be started more than twice.
         ('during' systemInOperation) 'check count' (isStarted 'becomes true') '<=' 2
  R2 — At least 1 time unit must separate two consecutive startups.
         ('after' (isStarted 'becomes true') 'for' 1) 'check count' (isStarted) '==' 0
  R3 — While pump is started, temperature must stay below 50.
         ('during' isStarted) 'ensure' temperature < 50

The parametric mock drives each pump via explicit start/stop times (up to three
pairs per pump).  To trigger R2 violations, set the gap between consecutive
start events to less than 1 time unit; the pump must stop between the two starts
(p_stop < p_start2) so the second `isStarted 'becomes true'` event fires.

Mismatch signal names use underscores (pump1_R1, not pump1.R1) because the
Modelica harness variable cannot contain dots.
"""

from pathlib import Path

from experiments.harness import Binding, DomainSpec, OutputSignal, ParamRange

_REPO = Path(__file__).resolve().parent.parent.parent.parent

CRMLTOMODELICA_PATH = _REPO / "experiments" / "pumps" / "CRMLtoModelica.mo"

PUMPS_REF_CRML = (
    _REPO
    / "submodules"
    / "experiments"
    / "src"
    / "main"
    / "resources"
    / "models"
    / "pumps"
    / "pumps.crml"
)

_system_src = (
    _REPO / "experiments" / "pumps" / "pumps" / "PumpingSystemFuzzMock.mo"
).read_text()

PUMPS_DOMAIN = DomainSpec(
    name="pumps",
    system_model_name="PumpingSystemFuzzMock",
    system_model_source=_system_src,
    req_model_name="PumpingSystem",
    bindings=[
        # System-level operation flag (shared by all three pumps via CRML modification)
        Binding("inOperation",       "inOperation",       is_boolean4=True),
        # Per-pump inputs
        Binding("pump1_isStarted",   "pump1.isStarted",   is_boolean4=True),
        Binding("pump1_temperature", "pump1.temperature", is_boolean4=False),
        Binding("pump2_isStarted",   "pump2.isStarted",   is_boolean4=True),
        Binding("pump2_temperature", "pump2.temperature", is_boolean4=False),
        Binding("pump3_isStarted",   "pump3.isStarted",   is_boolean4=True),
        Binding("pump3_temperature", "pump3.temperature", is_boolean4=False),
    ],
    outputs=[
        # Mismatch variable names use underscores (Modelica identifiers cannot contain dots)
        OutputSignal("pump1.R1", "Boolean4"),
        OutputSignal("pump1.R2", "Boolean4"),
        OutputSignal("pump1.R3", "Boolean4"),
        OutputSignal("pump2.R1", "Boolean4"),
        OutputSignal("pump2.R2", "Boolean4"),
        OutputSignal("pump2.R3", "Boolean4"),
        OutputSignal("pump3.R1", "Boolean4"),
        OutputSignal("pump3.R2", "Boolean4"),
        OutputSignal("pump3.R3", "Boolean4"),
    ],
    param_ranges=[
        # System operation window
        ParamRange("sys_on_start", low=0.0,   high=20.0,  default=5.0,   inactive_prob=0.0),
        ParamRange("sys_on_end",   low=100.0, high=180.0, default=180.0, inactive_prob=0.0),

        # Pump 1 — start/stop pairs; 1e9 means the event never occurs
        ParamRange("p1_start1",     low=5.0,  high=40.0,  default=10.0, inactive_prob=0.0),
        ParamRange("p1_stop1",      low=20.0, high=90.0,  default=50.0, inactive_prob=0.0),
        ParamRange("p1_start2",     low=5.0,  high=150.0, default=1e9,  inactive_prob=0.5),
        ParamRange("p1_stop2",      low=20.0, high=180.0, default=1e9,  inactive_prob=0.5),
        ParamRange("p1_start3",     low=20.0, high=170.0, default=1e9,  inactive_prob=0.8),
        ParamRange("p1_stop3",      low=30.0, high=190.0, default=1e9,  inactive_prob=0.8),
        ParamRange("p1_temp_fault", low=10.0, high=180.0, default=1e9,  inactive_prob=0.7),

        # Pump 2
        ParamRange("p2_start1",     low=5.0,  high=40.0,  default=15.0, inactive_prob=0.0),
        ParamRange("p2_stop1",      low=20.0, high=90.0,  default=55.0, inactive_prob=0.0),
        ParamRange("p2_start2",     low=5.0,  high=150.0, default=1e9,  inactive_prob=0.5),
        ParamRange("p2_stop2",      low=20.0, high=180.0, default=1e9,  inactive_prob=0.5),
        ParamRange("p2_start3",     low=20.0, high=170.0, default=1e9,  inactive_prob=0.8),
        ParamRange("p2_stop3",      low=30.0, high=190.0, default=1e9,  inactive_prob=0.8),
        ParamRange("p2_temp_fault", low=10.0, high=180.0, default=1e9,  inactive_prob=0.7),

        # Pump 3
        ParamRange("p3_start1",     low=5.0,  high=40.0,  default=20.0, inactive_prob=0.0),
        ParamRange("p3_stop1",      low=20.0, high=90.0,  default=60.0, inactive_prob=0.0),
        ParamRange("p3_start2",     low=5.0,  high=150.0, default=1e9,  inactive_prob=0.5),
        ParamRange("p3_stop2",      low=20.0, high=180.0, default=1e9,  inactive_prob=0.5),
        ParamRange("p3_start3",     low=20.0, high=170.0, default=1e9,  inactive_prob=0.8),
        ParamRange("p3_stop3",      low=30.0, high=190.0, default=1e9,  inactive_prob=0.8),
        ParamRange("p3_temp_fault", low=10.0, high=180.0, default=1e9,  inactive_prob=0.7),
    ],
    scenarios={
        # All pumps start once, temperatures stay below 50 — all requirements pass
        "nominal": {},

        # Pump 1 starts three times within the system operation window → R1 fails
        "r1_violation_p1": {
            "p1_start1": 10.0, "p1_stop1": 40.0,
            "p1_start2": 60.0, "p1_stop2": 90.0,
            "p1_start3": 110.0, "p1_stop3": 140.0,
        },

        # Pump 1 restarts 0.5 time units after its previous start → R2 fails
        # (stop1 < start2 so the second 'becomes true' event fires)
        "r2_violation_p1": {
            "p1_start1": 10.0, "p1_stop1": 10.3,
            "p1_start2": 10.5, "p1_stop2": 40.0,
        },

        # Pump 2 temperature rises above 50 while started → R3 fails
        "r3_violation_p2": {
            "p2_start1": 15.0, "p2_stop1": 80.0,
            "p2_temp_fault": 40.0,
        },

        # Pump 3 temperature fault while running
        "r3_violation_p3": {
            "p3_start1": 20.0, "p3_stop1": 90.0,
            "p3_temp_fault": 30.0,
        },

        # All three violations across different pumps simultaneously
        "multi_pump_fault": {
            "p1_start1": 10.0, "p1_stop1": 10.3,
            "p1_start2": 10.5, "p1_stop2": 40.0,   # R2 on pump1
            "p2_start1": 15.0, "p2_stop1": 55.0,
            "p2_temp_fault": 25.0,                  # R3 on pump2
            "p3_start1": 20.0, "p3_stop1": 50.0,
            "p3_start2": 70.0, "p3_stop2": 100.0,
            "p3_start3": 120.0, "p3_stop3": 150.0,  # R1 on pump3
        },
    },
    stop_time=200.0,
    n_intervals=400,
)
