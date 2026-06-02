"""Generates Modelica source for the comparison harness packages."""

from .model import DomainSpec

HARNESS_PKG = "crml_harness"
CANDIDATE_SUBPKG = "Candidate"
REFERENCE_SUBPKG = "Reference"
CANDIDATE_PKG = f"{HARNESS_PKG}.{CANDIDATE_SUBPKG}"
REFERENCE_PKG = f"{HARNESS_PKG}.{REFERENCE_SUBPKG}"


def package_mo(pkg_name: str, within: str = "", description: str = "") -> str:
    within_clause = f"within {within};" if within else "within ;"
    desc = f' "{description}"' if description else ""
    return f"{within_clause}\npackage {pkg_name}{desc}\nend {pkg_name};\n"


def _mismatch_var(sig_name: str) -> str:
    """Modelica variable name for a mismatch signal (dots → underscores)."""
    return f"mismatch_{sig_name.replace('.', '_')}"


def comparison_harness_mo(domain: DomainSpec) -> str:
    req = domain.req_model_name
    lines = [
        f"within {HARNESS_PKG};",
        f"model ComparisonHarness",
        f"  {CANDIDATE_SUBPKG}.{req} candidate;",
        f"  {REFERENCE_SUBPKG}.{req} reference;",
        f"  {domain.system_model_name} system;",
        "",
    ]

    for sig in domain.outputs:
        lines.append(f"  Boolean {_mismatch_var(sig.name)};")
    lines.append("  Boolean any_mismatch;")
    lines.append("")
    lines.append("equation")

    for b in domain.bindings:
        if b.is_boolean4:
            cv = "CRMLtoModelica.Functions.cvBooleanToBoolean4"
            lines.append(f"  candidate.{b.req_var} = {cv}(system.{b.system_var});")
            lines.append(f"  reference.{b.req_var} = {cv}(system.{b.system_var});")
        else:
            lines.append(f"  candidate.{b.req_var} = system.{b.system_var};")
            lines.append(f"  reference.{b.req_var} = system.{b.system_var};")

    lines.append("")

    for sig in domain.outputs:
        cand = sig.candidate_name if sig.candidate_name is not None else sig.name
        var = _mismatch_var(sig.name)
        if sig.signal_type == "Boolean4":
            lines.append(f"  {var} = candidate.{cand} <> reference.{sig.name};")
        else:
            tol = domain.real_tolerance
            lines.append(f"  {var} = abs(candidate.{cand} - reference.{sig.name}) > {tol};")

    any_expr = " or ".join(_mismatch_var(s.name) for s in domain.outputs)
    lines.append(f"  any_mismatch = {any_expr};")
    lines.append("")

    interval = domain.stop_time / domain.n_intervals
    lines.append(
        f"  annotation(experiment(StartTime=0, StopTime={domain.stop_time},"
        f" Tolerance=1e-6, Interval={interval}));"
    )
    lines.append("end ComparisonHarness;")

    return "\n".join(lines) + "\n"


def rewrite_within(source: str, new_pkg: str) -> str:
    """Replace the first `within ...;` line with `within <new_pkg>;`."""
    lines = source.split("\n")
    for i, line in enumerate(lines):
        if line.strip().startswith("within "):
            lines[i] = f"within {new_pkg};"
            return "\n".join(lines)
    return f"within {new_pkg};\n" + source
