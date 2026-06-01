"""Requirement mapping loader for CRML semantic analysis.

The mapping JSON is produced manually (see LLM/generated/MATCHING_PROMPT.md)
and saved next to each generated .crml file as <name>_mapping.json.

Usage:
    mapping = RequirementMapping.load("generated/claude/SRI_temp_k1_mapping.json")
    adapted_domain = mapping.apply_to_domain(SRI_DOMAIN)
    # then run FuzzHarness(adapted_domain, ...) as usual
"""

import json
from dataclasses import dataclass, replace as dc_replace
from pathlib import Path

from .model import DomainSpec, OutputSignal


@dataclass
class RequirementLink:
    ref_name: str
    gen_name: str | None  # None = requirement not present in the generated model


@dataclass
class RequirementMapping:
    """Pairing of reference output names to generated variable names.

    File format (flat JSON):
        {"R1_T": "R1", "R2_T": "req2", "R_T": null, "R_speed_all": null}
    """
    links: list[RequirementLink]

    @classmethod
    def load(cls, path: str | Path) -> "RequirementMapping":
        data: dict = json.loads(Path(path).read_text())
        return cls([RequirementLink(k, v) for k, v in data.items()])

    def apply_to_domain(self, base_domain: DomainSpec) -> DomainSpec:
        """Return a DomainSpec restricted to matched signals.

        Each matched OutputSignal gets candidate_name set to the generated
        variable name.  Signals whose gen_name is None are dropped.
        """
        gen_map = {l.ref_name: l.gen_name for l in self.links if l.gen_name is not None}
        new_outputs = [
            OutputSignal(
                name=sig.name,
                signal_type=sig.signal_type,
                candidate_name=gen_map[sig.name],
            )
            for sig in base_domain.outputs
            if sig.name in gen_map
        ]
        return dc_replace(base_domain, outputs=new_outputs)

    def report(self) -> str:
        lines = ["Requirement mapping:"]
        for l in self.links:
            arrow = f"→ {l.gen_name}" if l.gen_name else "→ (missing)"
            lines.append(f"  {l.ref_name:<20s} {arrow}")
        matched = sum(1 for l in self.links if l.gen_name)
        lines.append(f"\n{matched}/{len(self.links)} requirements matched.")
        return "\n".join(lines)
