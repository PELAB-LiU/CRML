"""Parameter vector generation for the system mock."""

import random
from collections.abc import Iterator

from .model import DomainSpec, ParamRange


class Fuzzer:
    """Generates parameter dicts for a DomainSpec's system mock.

    Each ParamRange has an `inactive_prob`: with that probability the fuzzer
    picks the range's `default` value (typically 1e9 = "never") instead of
    sampling uniformly from [low, high].  This produces a mix of faults and
    clean runs.
    """

    def __init__(self, domain: DomainSpec, seed: int | None = None) -> None:
        self.domain = domain
        self.rng = random.Random(seed)

    def random_params(self) -> dict[str, float]:
        out: dict[str, float] = {}
        for r in self.domain.param_ranges:
            if self.rng.random() < r.inactive_prob:
                out[r.name] = r.default
            else:
                out[r.name] = self.rng.uniform(r.low, r.high)
        return out

    def default_params(self) -> dict[str, float]:
        """All parameters at their inactive (nominal) defaults."""
        return {r.name: r.default for r in self.domain.param_ranges}

    def scenario(self, name: str) -> dict[str, float]:
        """Named scenario from domain.scenarios; falls back to defaults for missing keys."""
        base = self.default_params()
        base.update(self.domain.scenarios.get(name, {}))
        return base

    def scenario_names(self) -> list[str]:
        return list(self.domain.scenarios.keys())

    def __call__(self, n: int) -> Iterator[dict[str, float]]:
        """Yield n random parameter dicts."""
        for _ in range(n):
            yield self.random_params()
