import json
import re
from pathlib import Path

import pandas as pd

GENERATED_DIR = Path(__file__).parent.parent / "generated"

# Filename pattern: {Domain}_{Requirement}_k{Attempt}
_FILENAME_RE = re.compile(r"^(.+)_(.+)_k(\d+)$")


def load_generated(generated_dir: Path = GENERATED_DIR) -> pd.DataFrame:
    rows = []

    for llm_dir in sorted(generated_dir.iterdir()):
        if not llm_dir.is_dir():
            continue
        llm_name = llm_dir.name

        crml_files = list(llm_dir.glob("*.crml"))
        for crml_path in sorted(crml_files):
            stem = crml_path.stem
            m = _FILENAME_RE.match(stem)
            if not m:
                continue
            domain, requirement, attempt = m.group(1), m.group(2), int(m.group(3))

            metadata_path = crml_path.with_suffix(".json")
            mapping_path = llm_dir / f"{stem}_mapping.json"

            metadata = {}
            if metadata_path.exists():
                with open(metadata_path) as f:
                    metadata = json.load(f)

            mapping = None
            if mapping_path.exists():
                with open(mapping_path) as f:
                    mapping = json.load(f)

            row = {
                "llm": llm_name,
                "mapping": mapping,
                "crml": str(crml_path.resolve()),
                "domain": domain,
                "requirement": requirement,
                "attempt": attempt,
                **metadata,
            }
            rows.append(row)

    return pd.DataFrame(rows)
