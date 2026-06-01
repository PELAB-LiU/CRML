"""Backward-compatible re-exports from the experiments.fuzzing subpackage.

New code should import directly from ``experiments.fuzzing``.
"""

from experiments.fuzzing.fluid_storage import (
    CRMLTOMODELICA_PATH as _FLUID_CRMLTOMODELICA,
    FLUID_STORAGE_DOMAIN,
    FLUID_STORAGE_REF_CRML,
)
from experiments.fuzzing.sri import (
    CRMLTOMODELICA_PATH,
    SRI2_REF_CRML,
    SRI_DOMAIN,
)

__all__ = [
    "CRMLTOMODELICA_PATH",
    "SRI2_REF_CRML",
    "SRI_DOMAIN",
    "FLUID_STORAGE_DOMAIN",
    "FLUID_STORAGE_REF_CRML",
]
