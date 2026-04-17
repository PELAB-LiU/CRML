#!/usr/bin/env python3
"""MCP server that exposes CRML language documentation from documentation/llm_hints/."""

from pathlib import Path

from mcp.server.fastmcp import FastMCP

HINTS_DIR = Path(__file__).parent.parent / "documentation" / "llm_hints"

mcp = FastMCP("CRML Language Documentation")


@mcp.resource("docs://llm_hints/{filename}")
def get_hint(filename: str) -> str:
    """Return the content of a CRML language hint file by name (e.g. boolean_type.md)."""
    path = HINTS_DIR / filename
    if path.suffix != ".md" or not path.exists():
        raise ValueError(f"Hint file '{filename}' not found.")
    return path.read_text(encoding="utf-8")


@mcp.tool()
def list_hint_files() -> list[str]:
    """List all available CRML language documentation files in llm_hints."""
    return sorted(f.name for f in HINTS_DIR.glob("*.md"))


@mcp.tool()
def read_hint_file(filename: str) -> str:
    """Read a CRML language documentation file from llm_hints.

    Call list_hint_files() first to see which files are available.
    """
    path = HINTS_DIR / filename
    if not path.exists():
        available = ", ".join(sorted(f.name for f in HINTS_DIR.glob("*.md")))
        return f"File '{filename}' not found. Available files: {available}"
    if path.suffix != ".md":
        return "Only .md files are served."
    return path.read_text(encoding="utf-8")


@mcp.tool()
def search_hints(query: str) -> str:
    """Search for a keyword across all CRML language documentation files.

    Returns matching file names with the lines that contain the query.
    """
    results: list[str] = []
    for md_file in sorted(HINTS_DIR.glob("*.md")):
        matches = [
            f"  line {i + 1}: {line.rstrip()}"
            for i, line in enumerate(md_file.read_text(encoding="utf-8").splitlines())
            if query.lower() in line.lower()
        ]
        if matches:
            results.append(f"{md_file.name}:\n" + "\n".join(matches))
    return "\n\n".join(results) if results else f"No matches found for '{query}'."


if __name__ == "__main__":
    mcp.run()
