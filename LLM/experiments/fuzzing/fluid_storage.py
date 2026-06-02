"""Fuzzing configuration for the Fluid Storage domain.

The requirement checks that both tanks stay below 80 % of their capacity:
    levelSafe  = waterLevel < 0.8 * maxVolume   (per tank)
    tanksAreSafe = tank1.levelSafe AND tank2.levelSafe

The parametric mock uses proportional levels so the safety threshold is
consistent across all sampled volumes:
    nominal level = 0.4 * maxVolume  (safely below 80 %)
    fault level   = 0.9 * maxVolume  (above 80 % for any volume)
"""

from pathlib import Path

from experiments.harness import Binding, DomainSpec, OutputSignal, ParamRange

_REPO = Path(__file__).resolve().parent.parent.parent.parent

CRMLTOMODELICA_PATH = _REPO / "experiments" / "fluid_storage" / "CRMLtoModelica.mo"

FLUID_STORAGE_REF_CRML = (
    _REPO
    / "submodules"
    / "experiments"
    / "src"
    / "main"
    / "resources"
    / "models"
    / "fluid_storage"
    / "fluid_storage.crml"
)

_system_src = (
    _REPO / "experiments" / "fluid_storage" / "FluidStorageSystemMock.mo"
).read_text()

FLUID_STORAGE_DOMAIN = DomainSpec(
    name="fluid_storage",
    system_model_name="FluidStorageSystemMock",
    system_model_source=_system_src,
    req_model_name="SafeLevelRequirement",
    bindings=[
        Binding("tank1_waterLevel", "tank1.waterLevel", is_boolean4=False),
        Binding("tank1_maxVolume",  "tank1.maxVolume",  is_boolean4=False),
        Binding("tank2_waterLevel", "tank2.waterLevel", is_boolean4=False),
        Binding("tank2_maxVolume",  "tank2.maxVolume",  is_boolean4=False),
    ],
    outputs=[
        OutputSignal("tanksAreSafe", "Boolean4"),
    ],
    param_ranges=[
        # Tank capacities — fault/nominal levels scale proportionally so the
        # 80 % threshold is always correctly positioned regardless of volume.
        ParamRange("maxVolume1", low=0.5, high=5.0, default=2.0, inactive_prob=0.0),
        ParamRange("maxVolume2", low=0.5, high=5.0, default=2.0, inactive_prob=0.0),

        # Fault timing for tank 1 (1e9 = tank never overflows)
        ParamRange("tank1_fault_start", low=10.0, high=160.0, default=1e9, inactive_prob=0.5),
        ParamRange("tank1_recovery",    low=20.0, high=180.0, default=1e9, inactive_prob=0.4),

        # Fault timing for tank 2
        ParamRange("tank2_fault_start", low=10.0, high=160.0, default=1e9, inactive_prob=0.5),
        ParamRange("tank2_recovery",    low=20.0, high=180.0, default=1e9, inactive_prob=0.4),
    ],
    scenarios={
        "nominal": {},

        # Only tank 1 overflows — key divergence scenario for AND vs OR bugs
        "tank1_overflow": {
            "tank1_fault_start": 30.0,
        },

        # Only tank 2 overflows
        "tank2_overflow": {
            "tank2_fault_start": 50.0,
        },

        # Tank 1 overflows briefly then recovers
        "tank1_brief": {
            "tank1_fault_start": 30.0,
            "tank1_recovery":    80.0,
        },

        # Both tanks overflow simultaneously — both models agree, no mismatch
        "both_overflow": {
            "tank1_fault_start": 20.0,
            "tank2_fault_start": 20.0,
        },

        # Different volumes with one overflow — tests threshold scaling
        "varied_volumes": {
            "maxVolume1": 4.0,
            "maxVolume2": 1.0,
            "tank2_fault_start": 60.0,
        },
    },
    stop_time=200.0,
    n_intervals=400,
)
