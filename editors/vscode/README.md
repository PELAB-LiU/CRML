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
- **Completion** with `Ctrl+Space`: what can be written at the cursor, also
  while the model is incomplete.
  - Operators in scope, from the model and the libraries it uses, inserted
    with a tab stop for each operand: `'during' b` inserts `'during' ${1:b}`.
    Where a value starts, operators that take an operand on their left come
    first; after a value, they continue it.
  - Variables, the Boolean values (`true`, `false`, `undecided`,
    `undefined`), classes, built-in forms such as `if … then … else …`, and
    the keywords the grammar allows.
  - After `model M is` or `X union`: the libraries and models to depend on,
    inserted with their `union`.
  - Operators and values of the type being defined are listed first, then
    the model's own before those of libraries.
  - Details show the type and the library; the documentation shows the
    declaration and the comment above it.

  Suggestions do not pop up while typing; ask for them with `Ctrl+Space`.
- Comment toggling, bracket matching and auto-closing pairs.

## Libraries

The language server comes with the ETL and FORM-L libraries, so a model can use
them with `ETL union` or `FORML union`. These are simplified copies that the
current grammar accepts; the differences are listed at the top of each file
(`submodules/lsp/src/main/resources/org/openmodelica/crml/lsp/libraries`).
Note that FORM-L declares itself `FORML`, not `FORM_L`.

Other open files are seen as well: a model can use a library that is open in
the editor.

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
