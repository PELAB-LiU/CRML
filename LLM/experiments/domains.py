"""Domain specifications for the fuzzing harness.

Each DomainSpec captures:
  - the parametric system mock (Modelica source)
  - how to wire mock outputs to requirement inputs
  - which output signals to compare between candidate and reference
  - parameter ranges for fuzzing
  - named scenarios for deterministic regression runs

Import paths are resolved relative to the repo root so this module works
regardless of the current working directory.
"""

from pathlib import Path

from experiments.harness import Binding, DomainSpec, OutputSignal, ParamRange

_REPO = Path(__file__).resolve().parent.parent.parent

# Shared paths
CRMLTOMODELICA_PATH = _REPO / "experiments" / "sri" / "CRMLtoModelica.mo"

# CRML reference source files
SRI2_REF_CRML = _REPO / "submodules" / "experiments" / "src" / "main" / "resources" / "models" / "sri" / "sri2_ref.crml"

# -----------------------------------------------------------------------
# SRI domain
# -----------------------------------------------------------------------

_sri_system_src = (_REPO / "experiments" / "sri" / "sri2_ref" / "SRISystem.mo").read_text()

SRI_DOMAIN = DomainSpec(
    name="sri",
    system_model_name="SRISystem",
    system_model_source=_sri_system_src,
    bindings=[
        Binding("T",                 "T",                 is_boolean4=False),
        Binding("normal_operation",  "normal_operation",  is_boolean4=True),
        Binding("v1",                "v1",                is_boolean4=False),
        Binding("v2",                "v2",                is_boolean4=False),
        Binding("pump_in_service1",  "pump_in_service1",  is_boolean4=True),
        Binding("pump_in_service2",  "pump_in_service2",  is_boolean4=True),
        Binding("pump_in_service3",  "pump_in_service3",  is_boolean4=True),
        Binding("flow1",             "flow1",             is_boolean4=False),
        Binding("flow2",             "flow2",             is_boolean4=False),
        Binding("flow3",             "flow3",             is_boolean4=False),
    ],
    outputs=[
        OutputSignal("R1_T",        "Boolean4"),
        OutputSignal("R2_T",        "Boolean4"),
        OutputSignal("R_T",         "Boolean4"),
        OutputSignal("R_speed_all", "Boolean4"),
        OutputSignal("R_flow_all",  "Boolean4"),
    ],
    param_ranges=[
        # Temperature fault: goes outside [16, 30] °C
        ParamRange("T_fault_start", low=10.0,  high=170.0, default=1e9, inactive_prob=0.4),
        ParamRange("T_recovery",    low=20.0,  high=190.0, default=1e9, inactive_prob=0.4),

        # Fluid speed faults (limit 6 m/s)
        ParamRange("v1_fault_start", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),
        ParamRange("v2_fault_start", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),

        # Flow faults (must stay >= 700 t/h while pump in service)
        ParamRange("flow1_fault_start", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),
        ParamRange("flow2_fault_start", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),
        ParamRange("flow3_fault_start", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),

        # Pump service windows (go offline at pump_off)
        ParamRange("pump1_off", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),
        ParamRange("pump2_off", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),
        ParamRange("pump3_off", low=10.0, high=180.0, default=1e9, inactive_prob=0.5),
    ],
    scenarios={
        # No faults — both models should agree on nominal behaviour
        "nominal": {},

        # Brief temperature excursion that recovers within 60 s (R2_T should pass)
        "T_brief_fault": {
            "T_fault_start": 30.0,
            "T_recovery":    80.0,   # 50 s fault, within 1-minute window
        },

        # Long temperature excursion — exceeds the 1-minute recovery window
        "T_long_fault": {
            "T_fault_start": 30.0,
            "T_recovery":    1e9,    # never recovers
        },

        # Single speed fault on v1
        "v1_fault": {
            "v1_fault_start": 50.0,
        },

        # Flow drops on pump 1 while it stays in service
        "flow1_fault": {
            "flow1_fault_start": 40.0,
        },

        # Pump 2 goes offline mid-run while its flow was fine
        "pump2_offline": {
            "pump2_off": 60.0,
        },

        # Multi-fault: T + v2 + flow3
        "multi_fault": {
            "T_fault_start":    20.0,
            "T_recovery":       1e9,
            "v2_fault_start":   40.0,
            "flow3_fault_start": 60.0,
        },
    },
    stop_time=200.0,
    n_intervals=400,
)
