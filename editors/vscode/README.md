# CRML for Visual Studio Code

Language support for CRML, the Common Requirement Modeling Language.

## Features

- **Highlighting** in the style of the CRML publications: keywords, built-in
  types, user-defined operators such as `'during'`, strings and comments. It
  works as soon as a file opens, without the language server.
- **Errors** as you type: syntax errors on the offending token, and problems
  found while building the model, such as names that cannot be resolved.
- **Outline** of models, classes, types, variables, operators and templates,
  also in the breadcrumbs and through *Go to Symbol in Editor* (`Ctrl+Shift+O`).
- **Keyword completion** with the keywords the grammar allows at the cursor.
- Comment toggling, bracket matching and auto-closing pairs.

Library dependencies (`FORM_L union`, `ETL union`) are not resolved yet, so
operators from libraries are not checked.

## Requirements

Everything except highlighting comes from the CRML language server, which runs
on Java 11 or later.

- The platform-specific packages of this extension include a Java runtime and
  need nothing else.
- The universal package uses the Java set in `crml.java.home`, or else the one
  in `JAVA_HOME`, or else `java` on the `PATH`.

If no suitable Java is found, highlighting keeps working, and the
*CRML Language Server* output channel says what was tried.

## Settings

| Setting | Description |
|---|---|
| `crml.java.home` | Folder of the Java 11+ installation to run the language server with. |
| `crml.trace.server` | Logs the messages between VS Code and the language server: `off`, `messages` or `verbose`. |

The command *CRML: Restart Language Server* restarts the server, for example
after changing `crml.java.home`.

## License

OSMC Public License 1.8; see `OSMC-License.txt`. Third-party components are
listed in `THIRD-PARTY-NOTICES.md`.
