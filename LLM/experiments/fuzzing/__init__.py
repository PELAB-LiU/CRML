"""Domain configurations for the CRML fuzzing harness.

Each sub-module provides:
  - A ``DomainSpec`` instance describing the mock system, bindings,
    output signals, parameter ranges, and named scenarios.
  - Path constants for the reference CRML file and the CRMLtoModelica library.

Usage::

    from experiments.fuzzing import FLUID_STORAGE_DOMAIN, FLUID_STORAGE_REF_CRML
    from experiments.fuzzing import PUMPS_DOMAIN, PUMPS_REF_CRML
    from experiments.fuzzing import SRI_DOMAIN, SRI2_REF_CRML
    from experiments.fuzzing import TRAFFIC_DOMAIN, TRAFFIC_REF_CRML
"""

from .fluid_storage import CRMLTOMODELICA_PATH as FLUID_STORAGE_CRMLTOMODELICA_PATH
from .fluid_storage import FLUID_STORAGE_DOMAIN, FLUID_STORAGE_REF_CRML
from .pumps import CRMLTOMODELICA_PATH as PUMPS_CRMLTOMODELICA_PATH
from .pumps import PUMPS_DOMAIN, PUMPS_REF_CRML
from .sri import CRMLTOMODELICA_PATH as SRI_CRMLTOMODELICA_PATH
from .sri import SRI2_REF_CRML, SRI_DOMAIN
from .traffic import CRMLTOMODELICA_PATH as TRAFFIC_CRMLTOMODELICA_PATH
from .traffic import TRAFFIC_DOMAIN, TRAFFIC_REF_CRML

__all__ = [
    "FLUID_STORAGE_DOMAIN",
    "FLUID_STORAGE_REF_CRML",
    "FLUID_STORAGE_CRMLTOMODELICA_PATH",
    "PUMPS_DOMAIN",
    "PUMPS_REF_CRML",
    "PUMPS_CRMLTOMODELICA_PATH",
    "SRI_DOMAIN",
    "SRI2_REF_CRML",
    "SRI_CRMLTOMODELICA_PATH",
    "TRAFFIC_DOMAIN",
    "TRAFFIC_REF_CRML",
    "TRAFFIC_CRMLTOMODELICA_PATH",
]
