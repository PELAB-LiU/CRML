"""Top-level fuzzing orchestrator."""

from pathlib import Path

from .compiler import CRMLCompiler
from .fuzzer import Fuzzer
from .generator import CANDIDATE_PKG, REFERENCE_PKG
from .model import DomainSpec, Failure, HarnessResult
from .runner import SimRunner


class FuzzHarness:
    """Compiles two CRML models, builds a ComparisonHarness, and fuzzes the mock.

    Usage:
        harness = FuzzHarness(domain, compiler, crmltomodelica_path)
        result = harness.run(candidate_crml, reference_crml, n_iters=50)
        print(result.summary())
    """

    def __init__(
        self,
        domain: DomainSpec,
        compiler: CRMLCompiler,
        crmltomodelica_path: Path | str,
    ) -> None:
        self.domain = domain
        self.compiler = compiler
        self.crmltomodelica_path = Path(crmltomodelica_path)

    def run(
        self,
        candidate_crml: str,
        reference_crml: str,
        n_iters: int = 50,
        seed: int | None = None,
        verbose: bool = True,
        work_dir: Path | None = None,
        keep: bool = False,
        _reference_mo: str | None = None,
    ) -> HarnessResult:
        """Full fuzz run: compile both models, build once, simulate n_iters times.

        Pass ``_reference_mo`` to skip recompiling the reference when the caller
        already holds the compiled Modelica string (e.g. a batch runner that
        processes many candidates against the same reference).
        """
        if verbose:
            print(f"[harness] Compiling candidate...")
        candidate_mo, _ = self.compiler.compile(candidate_crml, CANDIDATE_PKG)

        if _reference_mo is not None:
            reference_mo = _reference_mo
        else:
            if verbose:
                print(f"[harness] Compiling reference...")
            reference_mo, _ = self.compiler.compile(reference_crml, REFERENCE_PKG)

        fuzzer = Fuzzer(self.domain, seed=seed)
        failures: list[Failure] = []

        with SimRunner(self.domain, self.crmltomodelica_path, work_dir=work_dir, keep=keep) as runner:
            if verbose:
                print(f"[harness] Building ComparisonHarness... {work_dir}")
            runner.setup(candidate_mo, reference_mo)

            # Fixed scenarios first (deterministic regression cases)
            scenario_runs = [(name, runner.simulate(fuzzer.scenario(name)))
                             for name in fuzzer.scenario_names()]
            for name, data in scenario_runs:
                mismatches = _check_mismatches(data)
                if mismatches:
                    failures.append(Failure(
                        params={"scenario": name},  # type: ignore[dict-item]
                        mismatch_signals=mismatches,
                    ))

            # Random fuzzing
            for i, params in enumerate(fuzzer(n_iters)):
                if verbose:
                    print(f"\r[harness] Fuzz {i + 1}/{n_iters}...", end="", flush=True)
                data = runner.simulate(params)
                mismatches = _check_mismatches(data)
                if mismatches:
                    failures.append(Failure(params=params, mismatch_signals=mismatches))

        if verbose:
            print()

        return HarnessResult(n_total=n_iters + len(scenario_runs), failures=failures)

    def run_scenario(
        self,
        candidate_crml: str,
        reference_crml: str,
        params: dict[str, float],
        verbose: bool = False,
        work_dir: Path | None = None,
        keep: bool = False,
    ) -> HarnessResult:
        """Run a single parameter vector, useful for debugging a known failure."""
        candidate_mo, _ = self.compiler.compile(candidate_crml, CANDIDATE_PKG)
        reference_mo, _ = self.compiler.compile(reference_crml, REFERENCE_PKG)

        with SimRunner(self.domain, self.crmltomodelica_path, work_dir=work_dir, keep=keep) as runner:
            if verbose:
                print("[harness] Building ComparisonHarness...")
            runner.setup(candidate_mo, reference_mo)
            data = runner.simulate(params)

        mismatches = _check_mismatches(data)
        failures = [Failure(params=params, mismatch_signals=mismatches)] if mismatches else []
        return HarnessResult(n_total=1, failures=failures)


def _check_mismatches(data: dict[str, list[float]]) -> list[str]:
    return [
        col[len("mismatch_"):]
        for col, values in data.items()
        if col.startswith("mismatch_") and any(v != 0.0 for v in values)
    ]
