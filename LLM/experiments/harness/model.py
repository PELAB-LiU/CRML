from dataclasses import dataclass, field


@dataclass
class Binding:
    """One wire from system mock output to requirement input."""
    system_var: str
    req_var: str
    is_boolean4: bool = False  # apply cvBooleanToBoolean4 conversion


@dataclass
class OutputSignal:
    """A requirement output signal to compare between candidate and reference."""
    name: str
    signal_type: str = "Boolean4"  # "Boolean4" or "Real"


@dataclass
class ParamRange:
    """Fuzzable parameter in the system mock."""
    name: str
    low: float
    high: float
    default: float          # value that means "inactive" (e.g. 1e9 = never)
    inactive_prob: float = 0.4  # probability of picking default instead of sampling range


@dataclass
class DomainSpec:
    name: str
    system_model_name: str       # e.g. "SRISystem"
    system_model_source: str     # Modelica source without `within` clause (or with any; runner rewrites it)
    bindings: list[Binding]
    outputs: list[OutputSignal]
    param_ranges: list[ParamRange]
    scenarios: dict[str, dict[str, float]] = field(default_factory=dict)
    stop_time: float = 200.0
    n_intervals: int = 400
    real_tolerance: float = 1e-6


@dataclass
class Failure:
    params: dict[str, float]
    mismatch_signals: list[str]


@dataclass
class HarnessResult:
    n_total: int
    failures: list[Failure]

    @property
    def n_failures(self) -> int:
        return len(self.failures)

    @property
    def passed(self) -> bool:
        return len(self.failures) == 0

    def summary(self) -> str:
        if self.passed:
            return f"PASS  {self.n_total}/{self.n_total} runs matched"
        lines = [f"FAIL  {self.n_failures}/{self.n_total} runs diverged"]
        for f in self.failures:
            lines.append(f"  signals: {f.mismatch_signals}")
            lines.append(f"  params:  {f.params}")
        return "\n".join(lines)
