# Model query language: frontend implementation plan

Status: **M0 done, 2026-11-27**. Plan for the `:cql` project of
[QUERY_LANGUAGE_ARCHITECTURE.md](QUERY_LANGUAGE_ARCHITECTURE.md) (the *architecture*):
everything up to the backend boundary. The language is defined in
[QUERY_LANGUAGE_SPEC.md](QUERY_LANGUAGE_SPEC.md) (the *spec*).

In scope: syntax tree, lexer, parser, IR, semantic analysis, `ModelScope`, API and SPI,
the conformance harness. Not in scope: `ir.lowering` (comes with the VQL backend), the
reference interpreter `:cql-java`, `CqlTable` in `:util-test` and the migration from
`*.constraints`.

## 1. Decisions

Settled on 2026-11-27, before implementation. The spec and the architecture are brought
in line in M10.

| # | Question | Decision |
|---|---|---|
| F1 | Parser technology (spec open point 1) | A hand-written recursive-descent parser, one method per grammar rule, as `constraints/Parser.java` is now. |
| F2 | Line breaks (spec L17, §4.2) | The rule of §4.2 is dropped: a line break is whitespace. A statement ends at `;`, at the end of the file, or where the grammar cannot continue (longest match); `;` stays optional. A `!` followed by `error`, `warning`, `info` or a string starts a companion, otherwise a negation. Line breaks are used only to resynchronise after a syntax error. |
| F3 | Shape of `Pairs` | `PairComponent { String name, SetExpr value }`, in the syntax tree and the IR: `Pairs { set, source : PairComponent, target : PairComponent, filters }`. |
| F4 | A statement that does not parse | No node in the syntax tree. The parser returns it beside the tree, as a span and diagnostics (`ParseResult`); the engine reports it as an `error` result (§6.5). |
| F5 | Common supertype when there is no unique least one (D3, L7) | `EObject`. A feature lookup that then fails says in its message to narrow with `/[T]`. |
| F6 | Name uniqueness (spec §5.2) | Every name the file declares is declared once: resources, `let`s, relations, quantifier variables, pair component names. An inner name may not repeat an outer one (no shadowing); sibling scopes may reuse a name (two quantifiers in different statements, `source`/`target` in every relation body). Declaring a relation again is not a second name: it adds to the relation (L7). Names of the metamodels (classes, features, enum literals) are not restricted; a relation that hides a feature still gives the warning of §5.5. |
| F7 | Prelude (spec open point 4) | A host option, `CqlFrontend.builder().prelude(Source)`. Its declarations are visible in the file; F6 applies across both. |
| F8 | Runtime dependencies | EMF only (architecture Q5). The `.xcore` files have no operation bodies, so the generated code needs neither `xcore.lib` nor `xbase.lib` at run time; the build checks it. Xcore needs `xcore.lib` to compile a `.xcore` file (for `@GenModel`), so it is `compileOnly`. (Checked in M0 with a throwaway model: the generated code imports only EMF and the JDK.) |

## 2. Milestones

Sizes: S under a day, M a few days, L about a week.

| # | Milestone | Content | Done when | Size |
|---|---|---|---|---|
| M0 | Project skeleton | `submodules/cql`, in `settings.gradle.kts`. Plugins `java-library`, `java-test-fixtures`, `org.xtext.builder` (Xcore set up as in `:modelica`); Java 8; EMF as the only `api` dependency. `checkRuntimeClasspath` fails the build when the runtime classpath holds anything but EMF (F8). One `package-info.java` per package of architecture §4.2. `javadoc` is skipped for a source set without classes, so `make javadoc` keeps working. | `./gradlew :cql:build` passes, the check included | S |
| M1 | Syntax metamodel | `syntax.xcore` per spec §4.4, with `Node { offset, length }` and F3; no operation bodies (F8). | Generated and compiled; small factory helpers for tests | S |
| M2 | Lexer | Tokens per §4.1 without `NEWLINE` (F2); names in backquotes; `#` comments; line and column per token. Diagnostics for an unterminated string or an unknown character. | Table-driven tests per token kind | M |
| M3 | Parser | One method per rule of §4.3; the bounded lookahead of the §4.3 table; statement ends by F2; spans on every node. Recovery: record the diagnostic, skip to the next `;` or to the first token on a later line that can start a statement, continue (F4). A canonical `SyntaxPrinter`. | One test per rule; §3.1 and §3.2 parse without diagnostics; round trip `parse(print(t)) ≡ t` ignoring spans; each lookahead case both ways; statements over several lines and several statements on one line | L |
| M4 | `Schema` | `EClass` lookup over the given `EPackage`s, `pkg::T`, an error for an ambiguous `T`, inherited features, enum literals, the least common supertype with F5, data-type normalisation (§6.1). | Unit tests on a dynamic-EMF metamodel with multiple inheritance | M |
| M5 | IR metamodel | `ir.xcore` per spec §8 with F3, a span per IR statement, an `IR_VERSION` constant (for `Backend.irVersion()`), an `IrPrinter`. | Generated; printer tests | S |
| M6 | Semantic analysis | See 2.1. | Every row of the §8 table and every diagnostic code has a test; golden IR for §3.1 and §3.2 | L |
| M7 | `ModelScope` | Resource matching (§5.1: whole URI, segment suffix, `*` and `?` within a segment, union), D9 as a diagnostic, a `ResourceProvider` interface (§9.4, interface only), `ModelIndex` (container; referrer index built lazily), deterministic object order by URI and fragment (A9). | Tests on in-memory `ResourceSet`s, D9 and the wildcards included | M |
| M8 | API and SPI | `CqlFrontend` (builder, prelude, cache of `CheckedProgram` by content); `CqlEngine.of(frontend, backend)` with the feature gate, a guard around backend calls, `scope(resourceSet)`; `Backends.load(id)` with `backend-not-found` and `backend-incompatible` (class version, IR version); result classes; diagnostic codes as constants. | A stub backend tests the gate, the guard, the order of results and the `Backends.load` errors | M |
| M9 | Conformance harness (test fixtures) | `BackendConformanceTest` with `@TestFactory`; cases as `.cql` files with the expected status inline (`#= fail`) and models built by Java fixtures with dynamic EMF; a mutant generator on the IR (negate a condition, shift a range or a count); first cases from the gaps of architecture §7 (unset reference, unset enum, `!=` on empty sets, vacuous passes). | Runs against the stub backend; the real run comes with `:cql-java` | M |
| M10 | Documents | Spec: L17, §4.1, §4.2 and the grammar's `end` (F2); §4.4 and §8 (F3); D3 (F5); §5.2 and §5.5 (F6); open point 4 (F7); §8 on elaboration (2.1). Architecture: §4.2 if packages moved. A README for `:cql`; Javadoc of `api` and `spi`. | Documents match the code | S |

### 2.1 Semantic analysis (M6)

Building a `FeatureRel` needs the static type of its source, so typing cannot wait for
the IR as §8 says. It is done by **elaboration**: one pass that resolves names, computes
static types and builds the IR; checks that need only the IR run after it.

1. **Declarations.** Collect the file-level names of the prelude and the file; enforce F6
   (`duplicate-name`); repeated relation declarations form one relation (L7). Build the
   `DependencyGraph`; report `let` cycles (§5.2) and recursive relations (§5.4).
2. **Elaboration.** In dependency order, then the statements: resolve names (§5.5),
   compute static types (D3, F5), give each relation the common supertype of its
   declarations' ends (L7), resolve `.f` to a relation, then a feature (with the §5.5
   warning), and build the IR by the §8 table. Variables are checked against F6 as their
   scopes open.
3. **IR checks.** Comparisons of objects with data values; `featuresOf(statement)` for the
   feature gate; the §7 lint for link constraints whose ends cannot overlap.
4. **Isolation.** A statement with errors of its own, or that uses a declaration with
   errors, is not passed on; it gets `depends-on-error` naming the declaration (A7).

## 3. Order

```
M0 ─► M1 ─► M2 ─► M3 ─┐
      M4 ─────────────┼─► M6 ─► M8 ─► M9 ─► M10
      M5 ─────────────┘           ▲
      M7 ─────────────────────────┘
```

Critical path M0 → M1 → M2 → M3 → M6 → M8. M4, M5 and M7 do not need the parser.

## 4. Definition of done

- §3.1 and §3.2 compile without diagnostics; their IR matches the golden files.
- Every row of the §8 table and every diagnostic code has at least one test.
- A file with one broken statement gives one `error` result; the other statements are
  unaffected.
- `:cql` is built for Java 8 and its runtime classpath holds only EMF (checked by the
  build).
- The conformance harness runs, ready for `:cql-java`.
