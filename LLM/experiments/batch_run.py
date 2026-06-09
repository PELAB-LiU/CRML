"""Batch processing of all generated CRML files against their reference domains.

Notebook usage (two lines after JVM is started):
    from experiments.batch_run import BatchRunner, DEFAULT_REGISTRY
    from experiments.harness import CRMLCompiler

    compiler = CRMLCompiler()   # JVM must already be started
    runner = BatchRunner(compiler, DEFAULT_REGISTRY, n_iters=20, seed=42)
    results = runner.run()
    BatchRunner.save_csv(results, "results.csv")

CLI usage (handles JVM lifecycle automatically):
    python -m experiments.batch_run --n-iters 20 --seed 42 --out results.csv
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd

from experiments.fuzzing.sri import (
    CRMLTOMODELICA_PATH as _SRI_MO,
    SRI2_REF_CRML,
    SRI_DOMAIN,
)
from experiments.harness import (
    CRMLCompileError,
    CRMLCompiler,
    FuzzHarness,
    OMCBuildError,
    RequirementMapping,
)
from experiments.harness.generator import REFERENCE_PKG
from experiments.loader import GENERATED_DIR, load_generated

_REPO = Path(__file__).resolve().parent.parent.parent

_FIXED_LOADER_COLS = {"llm", "domain", "requirement", "attempt", "crml", "mapping"}


@dataclass
class DomainEntry:
    domain_spec: Any        # DomainSpec
    ref_crml_path: Path
    crmltomodelica_path: Path


DEFAULT_REGISTRY: dict[str, DomainEntry] = {
    "SRI": DomainEntry(SRI_DOMAIN, SRI2_REF_CRML, _SRI_MO),
}


class BatchRunner:
    """Runs the FuzzHarness over every generated CRML file found by the loader.

    Args:
        compiler:      A live CRMLCompiler (JVM must already be started).
        registry:      Domain name → DomainEntry.  Defaults to DEFAULT_REGISTRY.
        n_iters:       Random fuzz iterations per model (default 20).
        seed:          RNG seed for reproducibility.
        work_dir:      Simulation scratch directory.  None → temp dirs.
        generated_dir: Root directory to scan.  Defaults to LLM/generated/.
        verbose:       Print per-iteration progress.
    """

    def __init__(
        self,
        compiler: CRMLCompiler,
        registry: dict[str, DomainEntry] | None = None,
        *,
        n_iters: int = 20,
        seed: int = 42,
        work_dir: Path | None = None,
        keep: bool = False,
        generated_dir: Path = GENERATED_DIR,
        verbose: bool = False,
    ) -> None:
        self.compiler = compiler
        self.registry = registry if registry is not None else DEFAULT_REGISTRY
        self.n_iters = n_iters
        self.seed = seed
        self.work_dir = work_dir
        self.keep = keep
        self.generated_dir = generated_dir
        self.verbose = verbose
        # Cache compiled reference Modelica per domain so it is compiled once,
        # not once per candidate model.
        self._ref_mo_cache: dict[str, str] = {}

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def run(self) -> pd.DataFrame:
        """Process every generated CRML file; return a flat results DataFrame."""
        df = load_generated(self.generated_dir)
        rows = [self.run_row(row) for _, row in df.iterrows()]
        return pd.DataFrame(rows)

    def run_row(self, row: pd.Series | dict) -> dict:
        """Process one generated file and return a flat result dict."""
        base = {
            "llm":         row["llm"],
            "domain":      row["domain"],
            "requirement": row["requirement"],
            "attempt":     row["attempt"],
        }
        # Carry through all metadata fields from the JSON sidecar.
        for k, v in (row.items() if hasattr(row, "items") else row.to_dict().items()):
            if k not in _FIXED_LOADER_COLS:
                base[k] = v

        domain_key = row["domain"]
        if domain_key not in self.registry:
            return {**base, "status": "unknown_domain",
                    "n_total": None, "n_failures": None, "pass_rate": None,
                    "matched_signals": None, "mismatch_signals": None, "error": None}

        if row["mapping"] is None:
            return {**base, "status": "no_mapping",
                    "n_total": None, "n_failures": None, "pass_rate": None,
                    "matched_signals": None, "mismatch_signals": None, "error": None}

        entry = self.registry[domain_key]
        crml_path = Path(row["crml"])
        mapping_path = crml_path.with_name(crml_path.stem + "_mapping.json")

        mapping = RequirementMapping.load(mapping_path)
        adapted = mapping.apply_to_domain(entry.domain_spec)
        matched_signals = len(adapted.outputs)

        candidate_crml = crml_path.read_text()
        ref_crml = entry.ref_crml_path.read_text()
        harness = FuzzHarness(adapted, self.compiler, entry.crmltomodelica_path)

        # Compile the reference once per domain and reuse across all candidates.
        if domain_key not in self._ref_mo_cache:
            ref_mo, _ = self.compiler.compile(ref_crml, REFERENCE_PKG)
            self._ref_mo_cache[domain_key] = ref_mo
        cached_ref_mo = self._ref_mo_cache[domain_key]

        # Give each model its own subdirectory so archived runs don't collide.
        model_work_dir = None
        if self.work_dir is not None:
            model_work_dir = (
                self.work_dir
                / row["llm"]
                / f"{row['domain']}_{row['requirement']}_k{row['attempt']}"
            )

        try:
            result = harness.run(
                candidate_crml=candidate_crml,
                reference_crml=ref_crml,
                n_iters=self.n_iters,
                seed=self.seed,
                verbose=self.verbose,
                work_dir=model_work_dir,
                keep=self.keep,
                _reference_mo=cached_ref_mo,
            )
        except CRMLCompileError as exc:
            return {**base, "status": "compile_error",
                    "n_total": None, "n_failures": None, "pass_rate": None,
                    "matched_signals": matched_signals, "mismatch_signals": None,
                    "error": str(exc)[:300]}
        except OMCBuildError as exc:
            return {**base, "status": "build_error",
                    "n_total": None, "n_failures": None, "pass_rate": None,
                    "matched_signals": matched_signals, "mismatch_signals": None,
                    "error": str(exc)[:300]}
        except Exception as exc:
            return {**base, "status": "error",
                    "n_total": None, "n_failures": None, "pass_rate": None,
                    "matched_signals": matched_signals, "mismatch_signals": None,
                    "error": str(exc)[:300]}

        all_mismatches = sorted({s for f in result.failures for s in f.mismatch_signals})
        return {
            **base,
            "status": "pass" if result.passed else "fail",
            "n_total": result.n_total,
            "n_failures": result.n_failures,
            "pass_rate": round(
                (result.n_total - result.n_failures) / result.n_total, 4
            ) if result.n_total else None,
            "matched_signals": matched_signals,
            "mismatch_signals": json.dumps(all_mismatches) if all_mismatches else "",
            "error": None,
        }

    @staticmethod
    def save_csv(df: pd.DataFrame, path: str | Path) -> None:
        path = Path(path)
        df.to_csv(path, index=False)
        print(f"Saved {len(df)} rows → {path}")


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def _build_arg_parser():
    import argparse

    p = argparse.ArgumentParser(
        prog="python -m experiments.batch_run",
        description="Batch-run FuzzHarness over all generated CRML files.",
    )
    p.add_argument(
        "--generated-dir",
        type=Path,
        default=GENERATED_DIR,
        metavar="DIR",
        help=f"Root directory to scan for generated .crml files (default: {GENERATED_DIR})",
    )
    p.add_argument(
        "--n-iters",
        type=int,
        default=20,
        metavar="N",
        help="Random fuzz iterations per model (default: 20)",
    )
    p.add_argument(
        "--seed",
        type=int,
        default=42,
        metavar="S",
        help="RNG seed (default: 42)",
    )
    p.add_argument(
        "--work-dir",
        type=Path,
        default=None,
        metavar="DIR",
        help="Simulation scratch directory (default: system temp dirs)",
    )
    p.add_argument(
        "--out", "-o",
        type=Path,
        default=Path("results.csv"),
        metavar="CSV",
        help="Output CSV path (default: results.csv)",
    )
    p.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Print per-iteration progress from the harness",
    )
    return p


def _cli_main(argv=None) -> None:
    from experiments.gradle_jvm import GradleJvm

    args = _build_arg_parser().parse_args(argv)

    jvm = GradleJvm(
        project_path=_REPO,
        subproject="experiments",
        subproject_dir="submodules/experiments",
    )
    jvm.build()
    jvm.start()
    try:
        compiler = CRMLCompiler()
        runner = BatchRunner(
            compiler,
            n_iters=args.n_iters,
            seed=args.seed,
            work_dir=args.work_dir,
            generated_dir=args.generated_dir,
            verbose=args.verbose,
        )
        results = runner.run()
        BatchRunner.save_csv(results, args.out)

        total = len(results)
        passed = (results["status"] == "pass").sum()
        failed = (results["status"] == "fail").sum()
        errors = total - passed - failed
        print(f"\nSummary: {passed} pass / {failed} fail / {errors} error  ({total} total)")
    finally:
        jvm.shutdown()


if __name__ == "__main__":
    _cli_main()
