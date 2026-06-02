"""Fuzzing configuration for the Traffic Light domain.

.. note:: **Known OMC build limitation**

   The traffic CRML uses ``'check count =='`` which internally maps to the
   same ``'countinside'`` / ``CRMLPeriod`` (singular) helper that is compiled
   with ``CRMLPeriods`` (plural) from ``'during'`` / ``'afterbefore'``.
   OMC 1.26 rejects the type mismatch when the model is instantiated twice
   inside the comparison harness.  Guard callers with ``OMCBuildError``::

       from experiments.harness import OMCBuildError
       try:
           result = harness.run(...)
       except OMCBuildError as e:
           print(f"Build error (known traffic CRML issue): {e}")

   This is a bug in the CRML-to-Modelica compiler output and will be patched
   upstream.  The domain configuration below is structurally correct.


Requirements (reference.crml):
  req1 — After green rises, before yellow rises, red must NOT rise.
  req2 — Green must stay active for at least 30 s after it turns on.
  req3 — Yellow must be active at exactly (t_green_on + 30.2 s).
          i.e. green_duration + yellow_delay ≤ 30.2 s for req3 to pass.

The parametric mock controls the timing of phase transitions and can
inject a brief red intrusion to trigger req1 violations.
"""

from pathlib import Path

from experiments.harness import Binding, DomainSpec, OutputSignal, ParamRange

_REPO = Path(__file__).resolve().parent.parent.parent.parent

CRMLTOMODELICA_PATH = _REPO / "experiments" / "traffic" / "CRMLtoModelica.mo"

TRAFFIC_REF_CRML = (
    _REPO
    / "submodules"
    / "experiments"
    / "src"
    / "main"
    / "resources"
    / "models"
    / "traffic"
    / "reference.crml"
)

_system_src = (
    _REPO / "experiments" / "traffic" / "traffic" / "TrafficLightSystemMock.mo"
).read_text()

TRAFFIC_DOMAIN = DomainSpec(
    name="traffic",
    system_model_name="TrafficLightSystemMock",
    system_model_source=_system_src,
    req_model_name="TrafficLightSpecification",
    bindings=[
        Binding("green",  "green",  is_boolean4=True),
        Binding("yellow", "yellow", is_boolean4=True),
        Binding("red",    "red",    is_boolean4=True),
    ],
    outputs=[
        OutputSignal("req1", "Boolean4"),
        OutputSignal("req2", "Boolean4"),
        OutputSignal("req3", "Boolean4"),
    ],
    param_ranges=[
        # When green first turns on
        ParamRange("green_start",    low=0.0,  high=20.0, default=5.0,  inactive_prob=0.0),
        # Duration of green: below 30 s violates req2; above 30.2 s violates req3
        ParamRange("green_duration", low=25.0, high=35.0, default=30.1, inactive_prob=0.0),
        # Delay before yellow: (green_duration + yellow_delay) > 30.2 violates req3
        ParamRange("yellow_delay",   low=0.0,  high=0.5,  default=0.0,  inactive_prob=0.3),
        # Red intrusion during green→yellow window violates req1 (1e9 = no intrusion)
        ParamRange("red_intrusion_start", low=5.0, high=40.0, default=1e9, inactive_prob=0.7),
    ],
    scenarios={
        # All three requirements pass
        "nominal": {
            "green_start":    5.0,
            "green_duration": 30.1,
            "yellow_delay":   0.05,
        },

        # req2 fails: green phase too short (25 s < 30 s)
        "green_too_short": {
            "green_duration": 25.0,
            "yellow_delay":   0.05,
        },

        # req3 fails: yellow appears 0.3 s after green ends
        # (green_duration + yellow_delay = 30.1 + 0.3 = 30.4 > 30.2)
        "yellow_late": {
            "green_duration": 30.1,
            "yellow_delay":   0.3,
        },

        # req1 fails: red appears at t=8 s, inside the green→yellow window
        "red_intrusion": {
            "green_start":         5.0,
            "green_duration":      30.1,
            "yellow_delay":        0.05,
            "red_intrusion_start": 8.0,
        },

        # req2 + req3 both fail
        "short_and_late": {
            "green_duration": 25.0,
            "yellow_delay":   0.5,
        },

        # All three fail simultaneously
        "all_faults": {
            "green_duration":      25.0,
            "yellow_delay":        0.5,
            "red_intrusion_start": 8.0,
        },
    },
    stop_time=100.0,
    n_intervals=1000,
)
