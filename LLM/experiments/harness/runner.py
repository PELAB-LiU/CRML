"""Writes Modelica files to a temp dir, compiles with omc, runs simulations."""

import csv
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

from .generator import (
    CANDIDATE_PKG,
    CANDIDATE_SUBPKG,
    HARNESS_PKG,
    REFERENCE_PKG,
    REFERENCE_SUBPKG,
    comparison_harness_mo,
    package_mo,
    rewrite_within,
)
from .model import DomainSpec


class OMCBuildError(Exception):
    pass


class SimulationError(Exception):
    pass


class SimRunner:
    """One-shot build + multi-shot simulate for a ComparisonHarness model.

    Typical use:
        runner = SimRunner(domain, crmltomodelica_path)
        runner.setup(candidate_mo, reference_mo)
        result = runner.simulate({"T_fault_start": 50.0})
        runner.cleanup()

    Or as a context manager.
    """

    def __init__(
        self,
        domain: DomainSpec,
        crmltomodelica_path: Path,
        work_dir: Path | None = None,
        keep: bool = False,
    ) -> None:
        self.domain = domain
        self.crmltomodelica_path = Path(crmltomodelica_path)
        self._provided_work_dir = Path(work_dir) if work_dir is not None else None
        self._keep = keep
        self._work_dir: Path | None = None
        self._sim_binary: Path | None = None
        self._runs_dir: Path | None = None
        self._run_index: int = 0

    # ------------------------------------------------------------------
    # Setup (compile once)
    # ------------------------------------------------------------------

    def setup(self, candidate_mo: str, reference_mo: str) -> None:
        if self._provided_work_dir is not None:
            self._provided_work_dir.mkdir(parents=True, exist_ok=True)
            self._work_dir = self._provided_work_dir
        else:
            self._work_dir = Path(tempfile.mkdtemp(prefix="crml_harness_"))
        print(f"[runner] Setup... {self._work_dir}")

        self._runs_dir = self._work_dir / "runs"
        self._runs_dir.mkdir(exist_ok=True)
        self._run_index = 0

        self._write_files(candidate_mo, reference_mo)
        self._build(candidate_mo, reference_mo)

    def _write_files(self, candidate_mo: str, reference_mo: str) -> None:
        wd = self._work_dir

        shutil.copy(self.crmltomodelica_path, wd / "CRMLtoModelica.mo")

        harness_dir = wd / HARNESS_PKG
        harness_dir.mkdir(exist_ok=True)
        (harness_dir / "package.mo").write_text(package_mo(HARNESS_PKG))

        # Write Candidate and Reference as subpackages of crml_harness.
        # package.mo files are written for OM import; req models are loaded via
        # loadString in the build script to avoid the OMC 1.26 auto-scan bug.
        for subpkg, mo_src in (
            (CANDIDATE_SUBPKG, candidate_mo),
            (REFERENCE_SUBPKG, reference_mo),
        ):
            sub_dir = harness_dir / subpkg
            sub_dir.mkdir(exist_ok=True)
            (sub_dir / "package.mo").write_text(package_mo(subpkg, within=HARNESS_PKG))
            (sub_dir / f"{self.domain.req_model_name}.mo").write_text(mo_src)

        sys_src = rewrite_within(self.domain.system_model_source, HARNESS_PKG)
        (harness_dir / f"{self.domain.system_model_name}.mo").write_text(sys_src)
        (harness_dir / "ComparisonHarness.mo").write_text(comparison_harness_mo(self.domain))

    def _build(self, candidate_mo: str, reference_mo: str) -> None:
        wd = self._work_dir
        mos_path = wd / "build.mos"
        mos_path.write_text(self._build_script(candidate_mo, reference_mo))

        result = subprocess.run(
            ["omc", str(mos_path)],
            cwd=str(wd),
            capture_output=True,
            text=True,
        )

        binary = wd / f"{HARNESS_PKG}.ComparisonHarness"
        if not binary.exists():
            errors = _extract_omc_errors(result.stdout)
            if errors:
                print("OMC errors:")
                for e in errors:
                    print(" ", e)
            raise OMCBuildError(
                f"omc build failed — binary not produced.\n"
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )
        self._sim_binary = binary

    @staticmethod
    def _mos_escape(s: str) -> str:
        return s.replace("\\", "\\\\").replace('"', '\\"')

    def _build_script(self, candidate_mo: str, reference_mo: str) -> str:
        d = self.domain
        cand_esc = self._mos_escape(candidate_mo)
        ref_esc = self._mos_escape(reference_mo)
        lines = [
            'loadFile("CRMLtoModelica.mo"); getErrorString();',
            # Declare all packages via loadString — avoids OMC 1.26 auto-scan
            # bug where loadFile("package.mo") scans the directory and chokes
            # on req model .mo files with nested models with quoted identifiers.
            f'loadString("within ;\\npackage {HARNESS_PKG}\\nend {HARNESS_PKG};\\n"); getErrorString();',
            f'loadString("within {HARNESS_PKG};\\npackage {CANDIDATE_SUBPKG}\\nend {CANDIDATE_SUBPKG};\\n"); getErrorString();',
            f'loadString("{cand_esc}"); getErrorString();',
            f'loadString("within {HARNESS_PKG};\\npackage {REFERENCE_SUBPKG}\\nend {REFERENCE_SUBPKG};\\n"); getErrorString();',
            f'loadString("{ref_esc}"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/{d.system_model_name}.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/ComparisonHarness.mo"); getErrorString();',
            f'buildModel({HARNESS_PKG}.ComparisonHarness,'
            f' stopTime={d.stop_time},'
            f' numberOfIntervals={d.n_intervals},'
            f' outputFormat="csv"); getErrorString();',
        ]
        return "\n".join(lines) + "\n"

    # ------------------------------------------------------------------
    # Simulate (run many times with different params)
    # ------------------------------------------------------------------

    def simulate(self, params: dict[str, float]) -> dict[str, list[float]]:
        """Run one simulation. params keys are system parameter names (no 'system.' prefix)."""
        if self._sim_binary is None:
            raise RuntimeError("Call setup() before simulate().")

        cmd = [str(self._sim_binary)]
        if params:
            override = ",".join(f"system.{k}={v}" for k, v in params.items())
            cmd.append(f"-override={override}")

        result = subprocess.run(
            cmd, cwd=str(self._work_dir), capture_output=True, text=True
        )
        if result.returncode != 0:
            raise SimulationError(f"Simulation failed:\n{result.stderr}")

        csv_path = self._work_dir / f"{HARNESS_PKG}.ComparisonHarness_res.csv"
        if not csv_path.exists():
            # OMC sometimes names it differently; fall back to first CSV in workdir
            candidates = list(self._work_dir.glob("*_res.csv"))
            if not candidates:
                raise SimulationError("No result CSV found after simulation.")
            csv_path = candidates[0]

        data = _read_csv(csv_path)
        self._archive_run(params, csv_path)
        return data

    # ------------------------------------------------------------------
    # Per-run archive
    # ------------------------------------------------------------------

    def _archive_run(self, params: dict[str, float], csv_path: Path) -> None:
        """Save result CSV, params, and a re-runnable .mos into runs/run_NNNN/."""
        run_dir = self._runs_dir / f"run_{self._run_index:04d}"
        run_dir.mkdir(parents=True, exist_ok=True)

        (run_dir / "params.json").write_text(json.dumps(params, indent=2))
        shutil.copy(csv_path, run_dir / "result.csv")
        (run_dir / "run.mos").write_text(self._run_script(params))

        self._run_index += 1

    def _run_script(self, params: dict[str, float]) -> str:
        """Generate a .mos script that re-runs this scenario in OpenModelica.

        The script uses paths relative to the parent work directory so it can be
        opened with 'omc run.mos' from inside runs/run_NNNN/, or pasted into the
        OpenModelica script editor after adjusting the cd() call.
        """
        d = self.domain
        lines = [
            "// Re-run this scenario in OpenModelica.",
            "// From inside runs/run_NNNN/:  omc run.mos",
            "// Or open in the OM GUI script editor and adjust the cd() path.",
            f'cd("../..");',
            f'loadFile("CRMLtoModelica.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/package.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/{CANDIDATE_SUBPKG}/package.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/{CANDIDATE_SUBPKG}/{d.req_model_name}.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/{REFERENCE_SUBPKG}/package.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/{REFERENCE_SUBPKG}/{d.req_model_name}.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/{d.system_model_name}.mo"); getErrorString();',
            f'loadFile("{HARNESS_PKG}/ComparisonHarness.mo"); getErrorString();',
        ]
        simflags = ""
        if params:
            override = ",".join(f"system.{k}={v}" for k, v in params.items())
            simflags = f', simflags="-override={override}"'
        lines.append(
            f'simulate({HARNESS_PKG}.ComparisonHarness,'
            f' stopTime={d.stop_time},'
            f' numberOfIntervals={d.n_intervals},'
            f' outputFormat="csv"'
            f'{simflags}); getErrorString();'
        )
        return "\n".join(lines) + "\n"

    # ------------------------------------------------------------------
    # Cleanup
    # ------------------------------------------------------------------

    def cleanup(self) -> None:
        if self._work_dir and self._work_dir.exists() and not self._keep:
            shutil.rmtree(self._work_dir)
        self._work_dir = None
        self._sim_binary = None

    def __enter__(self) -> "SimRunner":
        return self

    def __exit__(self, *_) -> None:
        self.cleanup()


# ------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------

def _extract_omc_errors(stdout: str) -> list[str]:
    """Pull OMC error/warning lines from getErrorString() output."""
    import re
    errors = []
    for m in re.finditer(r'"(\[.*?\].*?)"', stdout, re.DOTALL):
        text = m.group(1).strip()
        if text:
            errors.append(text)
    return errors


def _read_csv(path: Path) -> dict[str, list[float]]:
    columns: dict[str, list[float]] = {}
    with open(path, newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            for key, val in row.items():
                columns.setdefault(key, []).append(float(val))
    return columns
