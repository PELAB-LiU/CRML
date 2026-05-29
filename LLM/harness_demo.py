"""CRML Fuzzing Harness Demo

Demonstrates the `FuzzHarness` against the SRI requirement model.
The harness compiles two CRML models to Modelica, builds a single
`ComparisonHarness` model that runs both implementations with the
same parametric mock, and checks for output divergence inside the
simulation (no post-hoc time-grid alignment needed).
"""

# %% 1  Build & start JVM
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent  # CRML repo root
from experiments.gradle_jvm import GradleJvm

jvm = GradleJvm(
    project_path=ROOT,
    subproject="experiments",
    subproject_dir="submodules/experiments",
)
jvm.build()
jvm.start()

# %% 2  Initialise harness
from experiments.harness import CRMLCompiler, FuzzHarness
from experiments.domains import CRMLTOMODELICA_PATH, SRI2_REF_CRML, SRI_DOMAIN

compiler = CRMLCompiler()
harness  = FuzzHarness(SRI_DOMAIN, compiler, CRMLTOMODELICA_PATH)

ref_crml = SRI2_REF_CRML.read_text()
print(f"Reference CRML: {len(ref_crml)} chars, {ref_crml.count(chr(10))} lines")

# %% 3  Sanity check — reference vs reference
# Both candidate and reference are the same source.  Every simulation run
# must produce identical output signals, so the harness should report
# zero failures across all named scenarios and random fuzz iterations.

result_sanity = harness.run(
    candidate_crml=ref_crml,
    reference_crml=ref_crml,
    n_iters=20,
    seed=42,
    verbose=True,
)

print(result_sanity.summary())
# Expected: PASS  26/26 runs matched
# (6 named scenarios + 20 random iterations)

# %% 4  Broken candidate — wrong recovery window
# The reference requires the temperature to return to range within 60 s
# of an excursion.  The broken candidate uses 30 s instead.
#
# This only diverges when the fault duration is between 30 s and 60 s — e.g.
# the T_brief_fault scenario (fault at t=30, recovery at t=80 → 50 s fault):
#
#   model     | window | check at | T in range?          | R2_T
#   reference | 60 s   | t = 90   | yes (recovered at 80)| true
#   broken    | 30 s   | t = 60   | no (still faulted)   | false
#
# The T_long_fault scenario (no recovery) makes both models fail R2_T,
# so no mismatch there — demonstrating that not every fault exposes the bug.

broken_crml = ref_crml.replace(
    "('from' (new Clock (not R1_T)) 'for' 60.0) 'check at end' T_in_range",
    "('from' (new Clock (not R1_T)) 'for' 30.0) 'check at end' T_in_range",
)

assert broken_crml != ref_crml, "String replacement had no effect — check the source"
print("Broken CRML prepared.")

result_broken = harness.run(
    candidate_crml=broken_crml,
    reference_crml=ref_crml,
    n_iters=20,
    seed=42,
    verbose=True,
)

print(result_broken.summary())
# Expected (at minimum):
#   FAIL  N/26 runs diverged
#     signals: ['R2_T', 'R_T']
#     params:  {'scenario': 'T_brief_fault'}
# Random iterations with a fault lasting 30–60 s will also be flagged.

# %% 5  Debug — re-run a single failing vector
# run_scenario compiles and builds once for a single parameter dict,
# useful for reproducing a specific failure reported by run().

debug_params = {
    "T_fault_start": 30.0,
    "T_recovery":    80.0,   # 50 s fault — inside reference window, outside broken window
}

result_debug = harness.run_scenario(
    candidate_crml=broken_crml,
    reference_crml=ref_crml,
    params=debug_params,
    verbose=True,
)

print(result_debug.summary())

# Confirm the long-fault scenario does NOT trigger a mismatch
# (both models fail R2_T, so they agree on the wrong answer)
result_long = harness.run_scenario(
    candidate_crml=broken_crml,
    reference_crml=ref_crml,
    params={"T_fault_start": 30.0, "T_recovery": 1e9},
)

print("Long fault:", result_long.summary())

# %% Shutdown
jvm.shutdown()
