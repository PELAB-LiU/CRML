import contextlib
import io
import sys
from pathlib import Path

# Source: https://github.com/PELAB-LiU/Text2VQL/blob/extension/dataset_construction/text2vql/seed/util.py
class AttrDict:
    def __init__(self, data=None):
        object.__setattr__(self, "_data", {})
        if data:
            object.__setattr__(self, "_data", data)

    def wrap(self, value):
        if isinstance(value, AttrDict):
            return value
        if isinstance(value, dict):
            return AttrDict(value)
        if isinstance(value, list):
            return [self.wrap(x) for x in value]
        return value
    
    def __getattr__(self, name):
        # Called when attribute is not found normally
        if name in self._data:
            value = self._data[name]
            return self.wrap(value)
        raise AttributeError(f"No attribute named '{name}'")
    
    def __getitem__(self, key):
        return self.wrap(self._data[key])
    
    def keys(self):
        return self._data.keys()

    def values(self):
        return (self.wrap(v) for v in self._data.values())

    def items(self):
        return ((k, self.wrap(v)) for k, v in self._data.items())

    def get(self, key, default=None):
        try:
            return self[key]
        except KeyError:
            return default

    def __contains__(self, key):
        return key in self._data

    def __iter__(self):
        return iter(self._data)


import re


class Tee:
    """Context manager that writes stdout to both the terminal and a file simultaneously."""

    def __init__(self, path: str | Path):
        self._path = Path(path)

    def __enter__(self):
        self._file = self._path.open("w")
        self._orig = sys.stdout

        class _Stream:
            def __init__(self, *streams):
                self._streams = streams
            def write(self, data):
                for s in self._streams:
                    s.write(data)
            def flush(self):
                for s in self._streams:
                    s.flush()

        sys.stdout = _Stream(self._orig, self._file)
        return self

    def __exit__(self, *_):
        sys.stdout = self._orig
        self._file.close()


def extract_crml_block(text: str) -> str | None:
    """Extract CRML source from a fenced code block in LLM output."""
    for pattern in [r"```crml\s*\n(.*?)```", r"```\s*\n(.*?)```"]:
        m = re.search(pattern, text, re.DOTALL)
        if m:
            return m.group(1).strip()
    return None


def get_req_text(interaction, lang: str = "en") -> str:
    """Return the requirement string from an interaction dict (raw dict or AttrDict)."""
    req = interaction["req"]
    # Flat {"en": "..."} form used by traffic / pumps
    if lang in req:
        return req[lang]
    if "en" in req:
        return req["en"]
    # Nested {"nl": {"en": "..."}, "snl": {...}} form used by SRI
    for key in ("nl", "snl"):
        if key in req:
            nl = req[key]
            if lang in nl:
                return nl[lang]
            if "en" in nl:
                return nl["en"]
    return str(req)


async def generate_crml_sequence(agent, seed: str, interactions, lang: str = "en") -> list[str]:
    """Grow a CRML model from *seed* by adding requirements one by one via *agent*.

    The agent is expected to have access to the CRML MCP tools (check_syntax etc.).
    Returns a list of extracted CRML model strings — one per interaction step.
    """
    agent.reset()
    results = []

    await agent.chat(
        "You are a modelling assistant translating natural language requirements into Common Requirement Modeling Language. "
        "I will give you a seed CRML model and then add requirements one by one. "
        "After each requirement, extend the model with the CRML formalization of the requirement and return the complete updated model "
        "in a ```crml``` code block. The final model must be syntactically valid.\n\n"
        "Explain your solution in comments. "
        "Tools are available for looking up the CRML coding guidelines, language syntax, as well as for checking model syntax.\n\n"
        f"Seed model:\n```crml\n{seed}\n```\n\nAcknowledge and wait for the first requirement."
    )

    for i, interaction in enumerate(interactions):
        text = get_req_text(interaction, lang)
        response = await agent.chat(f"Requirement {i + 1}: {text}")
        results.append(extract_crml_block(response) or response)

    return results