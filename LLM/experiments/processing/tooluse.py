"""Process LLM experiment log files to extract tool call statistics."""

import re
from pathlib import Path

import pandas as pd

KEY_COLS = ['model', 'domain', 'requirement_id', 'shot_number']

# Extra derived columns and their fill defaults for merge
_SPECIAL_DEFAULTS: dict = {
    'search_hints_no_matches': 0,
    'check_syntax_no_errors': 0,
    'read_hint_file_params': {},
}

_TOOL_CALL_RE = re.compile(r"^→ Tool call: '([^']+)' args=(.*)")
_HINT_FILE_RE = re.compile(r"'filename':\s*'([^']+)'")


def parse_log_file(filepath: str | Path) -> pd.DataFrame:
    """Parse a single log file and return a one-row DataFrame with tool call stats.

    Key columns (model, domain, requirement_id, shot_number) are derived from
    the file path.  One column per encountered tool holds its call count.
    Special extra columns are added only when the corresponding tool appeared:
      - search_hints_no_matches  : int
      - check_syntax_no_errors   : int
      - read_hint_file_params    : dict mapping filename -> call count
    """
    path = Path(filepath)
    model = path.parent.name
    domain, req_id, shot = path.stem.rsplit('_', 2)

    tool_counts: dict[str, int] = {}
    search_no_matches = 0
    hint_file_params: dict[str, int] = {}
    syntax_no_errors = 0

    lines = path.read_text(encoding='utf-8').splitlines()
    for i, line in enumerate(lines):
        m = _TOOL_CALL_RE.match(line)
        if not m:
            continue

        tool_name = m.group(1)
        args_str = m.group(2)
        tool_counts[tool_name] = tool_counts.get(tool_name, 0) + 1

        if tool_name == 'read_hint_file':
            fm = _HINT_FILE_RE.search(args_str)
            if fm:
                filename = fm.group(1)
                hint_file_params[filename] = hint_file_params.get(filename, 0) + 1

        # Peek at next line to classify the result
        if i + 1 < len(lines):
            result_line = lines[i + 1]
            if tool_name == 'search_hints' and 'No matches found' in result_line:
                search_no_matches += 1
            elif tool_name == 'check_syntax' and 'No syntax errors found' in result_line:
                syntax_no_errors += 1

    row: dict = {
        'model': model,
        'domain': domain,
        'requirement_id': req_id,
        'shot_number': shot,
        **tool_counts,
    }

    if 'search_hints' in tool_counts:
        row['search_hints_no_matches'] = search_no_matches
    if 'read_hint_file' in tool_counts:
        row['read_hint_file_params'] = hint_file_params
    if 'check_syntax' in tool_counts:
        row['check_syntax_no_errors'] = syntax_no_errors

    return pd.DataFrame([row])


def merge_log_dataframes(dfs: list[pd.DataFrame]) -> pd.DataFrame:
    """Concatenate per-file DataFrames and fill missing endpoint columns with defaults.

    - Tool count columns (plain tool names) are filled with 0.
    - search_hints_no_matches and check_syntax_no_errors are filled with 0.
    - read_hint_file_params is filled with an empty dict {}.
    """
    if not dfs:
        return pd.DataFrame()

    merged = pd.concat(dfs, ignore_index=True)

    # Separate columns into dict-fill and int-fill groups
    dict_cols = [
        col for col in merged.columns
        if col in _SPECIAL_DEFAULTS and isinstance(_SPECIAL_DEFAULTS[col], dict)
    ]
    int_fill: dict[str, int] = {
        col: (_SPECIAL_DEFAULTS[col] if col in _SPECIAL_DEFAULTS else 0)  # type: ignore[assignment]
        for col in merged.columns
        if col not in KEY_COLS and col not in dict_cols
    }

    # .assign() returns a new frame (never triggers CoW warnings).
    # Operate on individual Series so we avoid DataFrame.fillna(dict) internals.
    merged = merged.assign(**{
        col: merged[col].fillna(fill).astype(int)
        for col, fill in int_fill.items()
    })
    if dict_cols:
        merged = merged.assign(**{
            col: merged[col].apply(lambda x: x if isinstance(x, dict) else {})
            for col in dict_cols
        })

    return merged
