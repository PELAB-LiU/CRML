# Modelica metamodel: verification checklist (M6)

The read-through called for in §10 of `modelica-metamodel-plan.md`, carried out
against the corpus after M1–M5. One row per construct:
**reached** (it appears in the trace or the diagnostics — no silent gap),
**plausible** (what it produced matches its §7 row), and
**contract-checked** (it survives the `*_verif.mo` interface check, where one
exists).

The read-through itself is not automated, per the plan's non-goals. The numbers
below come from translating all 213 models under
`submodules/test-resources/src/main/resources/testModels/**` and reading the
generated Modelica, the diagnostics and the trace.

## 0. Corpus status

| | Models |
|---|---|
| Translated | **153** |
| Failed to parse (`crml.g4`) | 57 |
| Failed in the DOM build or the linker | 3 |
| **Aborted during translation** | **0** |

No model aborts translation: every one of the 153 produces a Modelica class,
and everything unmapped inside it is a diagnostic plus a placeholder comment.
That is decision #3 of the plan, met.

The 60 models that never reach the translator fail upstream of this work:

| Cause | Models | Example |
|---|---|---|
| `crml.g4` rejects `X filter (...)` | 16 | `spec-doc-examples/ClockFilter.crml` |
| `crml.g4` rejects `Requirement`, `Probability`, `estimator`, `while` | 17 | `spec-doc-examples/ProbabilityExample1.crml` |
| `crml.g4` rejects `and`, `union`, `from`, `[`, `Periods`, `external` in some position | 17 | `spec-doc-examples/SetOperatorsExample3.crml` |
| other syntax | 7 | `use-cases/TwoTanks.crml` |
| linker cannot resolve a variable reference | 2 | `libraries/FORML_test/AfterBefore.crml` |
| `ExpressionBuilder`: "Unable to process value: tankm.level" | 1 | `use-cases/requirements_tank.crml` |

## 1. Trace coverage

| Link kind | Links | Distinct CRML sources |
|---|---|---|
| `VARIABLE_TO_COMPONENT` | 756 | `Variable` |
| `MODEL_TO_CLASS` | 153 | `Model` |
| `VALUE_TO_COMPONENT` | 139 | `ConstructorValue`, `PeriodsValue`, `IntegrateValue`, `ComputedValue`, `UnaryOperator`, `BinaryOperator` |
| `OPERATOR_TO_FUNCTION` | 42 | `UserOperator`, `Template` |
| `VALUE_TO_EQUATION` | 35 | block wiring |
| `UNSUPPORTED` | 27 | every placeholder |
| `OPERATOR_TO_BLOCK` | 13 | `UserOperator` |
| `CLASS_TO_MODEL` | 0 | no parseable model declares a class |
| `SET_TO_COMPONENT` | 0 | no model declares a set on the model itself; sets appear only as values |

## 2. Per-construct checklist

Legend: **yes** — verified on the corpus. **unit** — verified by a unit test on
a hand-built object model, because the corpus cannot reach it (see §4).
**n/a** — no `*_verif.mo` harness covers it.

### 2.1 Structural

| Construct | Reached | Plausible | Contract | Notes |
|---|---|---|---|---|
| `Model` | yes | yes | yes | 153 classes; `partial` iff the model has an unbound variable |
| `Class` | unit | unit | n/a | every corpus model declaring a class fails to parse |
| `Variable` (unbound) | yes | yes | yes | public component, no binding |
| `Variable` (bound) | yes | yes | yes | component plus binding |
| `Variable` (`constant`) | yes | yes | n/a | `constant` prefix; the string generator dropped it |
| `Variable` (class-typed) | unit | unit | n/a | component of the nested model |
| `Template` | yes | yes | yes | `TemplateOr`, `TemplateXor` |
| `UserOperator` | yes | yes | yes | 42 function forms, 13 block forms |
| `Keyword` | yes | yes | n/a | consumed into the generated class name |
| `Binding` | yes | yes | yes | matched by element identity |
| `Set` (as a value) | yes | yes | n/a | array component; INFO recorded, see §3 |
| `Set` (on the model) | unit | unit | n/a | no corpus model declares one |
| `Object` | — | — | — | never built by any DOM builder; diagnosed if one appears |
| `Package`, `PowerClass`, `Category`, `ModelDependency` | — | — | — | same; `Category` and dependencies are diagnosed |
| `Model.frame` | — | — | — | diagnosed |

### 2.2 Values

| Construct | Reached | Plausible | Contract | Notes |
|---|---|---|---|---|
| `VariableReference` | yes | yes | yes | |
| `BooleanConstant` | yes | yes | yes | `Types.Boolean4.*` |
| `IntegerConstant`, `RealConstant` | yes | yes | yes | source spelling kept where there was one |
| `StringConstant` | yes | yes | n/a | |
| `TimeValue` | yes | yes | n/a | `time`, in 23 models |
| `IfValue` | yes | yes | n/a | condition compared against `Boolean4.true4` |
| `ConstructorValue` → CLOCK | yes | yes | yes | `CRMLClock_build`, 18 models |
| `ConstructorValue` → EVENT | yes | yes | yes | `CRMLEvent_build`, 9 models |
| `ConstructorValue` → BOOLEAN | yes | yes | n/a | `Functions.Event2Boolean`, 7 models |
| `ConstructorValue` → INTEGER | yes | yes | n/a | `Integer()`, narrowed to enum and Real sources |
| `ConstructorValue` → REAL | yes | yes | n/a | operand unchanged; see §3 |
| `ConstructorValue` → STRING | yes | partial | n/a | `String()` works; the Boolean4 case is a library gap, §3 |
| `ConstructorValue` with bindings | unit | unit | n/a | component with modifications |
| `PeriodsValue` | yes | yes | yes | 6 `CRMLPeriod`, 10 `CRMLPeriods` |
| `IntegrateValue` | yes | yes | n/a | `Blocks.Integrate`, 4 models |
| `ComputedValue` | yes | yes | yes | function call or block instantiation |
| `DurationValue` | yes | — | — | diagnosed: no library implementation |
| `ProjectionValue` | yes | — | — | diagnosed: no library implementation |
| `SequenceValue` / `SequenceKeyword` | yes | — | — | diagnosed: an unresolved mixfix call, §4 |

### 2.3 Operators

| Operator | Reached | Plausible | Notes |
|---|---|---|---|
| ADD / SUB / MUL / DIV / POW / MOD (numeric) | yes | yes | infix; `mod()` in 1 model |
| ADD (String) | yes | yes | infix `+` |
| ADD (Boolean4) | no | — | `Functions.add4` is never emitted: the operands' types are unresolved, so the numeric branch wins. See §3 |
| MUL (Boolean4) | no | — | same, `Functions.mul4` |
| ADD (Clock / Period) | yes | — | diagnosed: `Blocks.ClockAdd` has an empty body |
| AND / OR | yes | yes | `Functions.and4` in 39 models, `or4` in 17 |
| NOT | yes | yes | `Functions.not4` in 21 models |
| LT / LE / GT / GE / EQ / NEQ (numeric) | yes | yes | `Functions.cvBooleanToBoolean4`, 44 models |
| LE / GE (Event) | no | — | `lEV` / `gEV` never reached; and `lEV` is a copy of `gEV` upstream, §3 |
| FILTER | unit | unit | `Blocks.EventFilter`; unreachable, `crml.g4` rejects `filter` |
| AT | yes | — | diagnosed in 9 models: no library implementation |
| WITH / MASTER / ON / LOG-2 | yes | — | diagnosed; undefined in `typeinference.csv` too |
| START / END | yes | yes | `Functions.PStart` 2, `PEnd` 7 |
| CARD (Clock) | yes | yes | `Blocks.CardClock`, 2 models |
| CARD (set) | yes | — | diagnosed in 2 models: set cardinality has no implementation |
| TICK | yes | yes | `Blocks.ClockTick`, 2 models |
| SIN / ASIN / COS / ACOS / LOG / LOG10 / EXP | yes | yes | `Modelica.Math.*` in 8 models — needs MSL on the load path, which `CRMLtoModelica.mo` does not itself require |
| SUBEXPRESSION | yes | yes | the only producer of `ParenthesizedExpression` |
| PRE / PAR | yes | — | diagnosed; undefined in `typeinference.csv` too |

## 3. Diagnostics raised across the corpus

| Severity | Construct | Models | Meaning |
|---|---|---|---|
| WARNING | `BinaryOperator.AT` | 9 | `CRMLtoModelica.mo` has no `at` |
| WARNING | `Sequence` | 5 | mixfix call naming no operator in scope |
| WARNING | `ProjectionValue` | 4 | no library implementation |
| WARNING | `ConstructorValue.STRING` | 4 | `Functions.Bool4toString` does not exist |
| INFO | `Set` | 4 | emitted, but `Blocks.setAnd` has an empty body |
| WARNING | `UnaryOperator.CARD` | 2 | set cardinality has no implementation |
| WARNING | `DurationValue` | 2 | no library implementation |
| ERROR | `BinaryOperator.ADD` | 1 | `StringConcatenationExample2.crml`, see §4 |

One ERROR in 153 models. Every other gap is a WARNING or an INFO, which is the
plan's severity convention: an ERROR means a construct that is mapped and
implemented here still failed to translate.

## 4. Contract check against `*_verif.mo`

66 `*_verif.mo` files exist (the plan said 28; the extra ones are copies of the
same harness under `lib/`). They cover **23 distinct CRML models**.

| Outcome | Harnesses |
|---|---|
| Class name matches `extends`, class is `partial`, every bound variable present | **16** |
| Bound variable not in the generated class | 2 |
| No generated output to check | 5 |

The five with no output — `BecomesFalseInside`, `BecomesTrueInside`,
`CheckOver`, `CountInside`, `Inside` — are the `filter` parse failures.

The two mismatches are harness drift, not generator defects:

* `BecomesFalse_verif.mo` binds `obs_b1` and then `b1 = obs_b1`. `obs_b1` is an
  observer variable of the hand-written `CRML_test/**` reference
  implementation; `BecomesFalse.crml` declares only `b1`. The harness is
  written against the hand-written model, not against this model's interface.
* `BooleanAtEvent_verif.mo` binds `b1` and `b`. `BooleanAtEvent.crml` declares
  `b1` and `c`, and defines `b_at_event`. Neither `b` nor the model's own `c`
  lines up. (`b_at_event` is dropped anyway — `at` has no implementation.)

Every harness that does line up passes all three checks, including the
`partial` one, which is the fix from §8.1 defect 2: the harnesses `extends` the
generated class and then bind its externals, which is only legal if the
generated class is partial.

## 5. What the corpus cannot reach

These are implemented and unit-tested, but no corpus model exercises them:

| Construct | Why |
|---|---|
| `FILTER` | `crml.g4` rejects `C filter (...)` — 16 models |
| CRML `Class`, class-typed variables, constructors with bindings | every model declaring a class fails to parse |
| `Set` declared on a model | the corpus only ever uses sets as values |
| `Functions.add4` / `mul4` | reachable only when both operand types resolve to BOOLEAN, and type inference populates `Value.getReturnType()` for constants only |
| `Functions.lEV` / `gEV` | same, for EVENT operands |

The last two are not blocked by the grammar but by type inference. The operator
dispatch reads `Value.getReturnType()`, which the DOM builders fill in for
constants and nothing else, so `b1 + b2` over two Boolean variables takes the
numeric branch and emits `b1 + b2` instead of `Functions.add4(b1, b2)`. The
dispatch itself is correct against `typeinference.csv`; it is the input that is
underspecified. `TypeResolver.inferBuiltin` (added in M4) looks through a
variable reference to the variable's declared domain and would fix this, but
turning it on for the whole operator dispatch is a behaviour change with its own
consequences and is listed below rather than made here.

## 6. Issues to file

Priority order, by corpus impact. None of these is fixed in this work.

### In `crml.g4` / `:language` — these block the most

1. **`X filter (...)` does not parse.** 16 models, including 5 with
   verification harnesses. It is the single largest cause of corpus loss and
   the only thing standing between `FILTER` and corpus coverage.
2. **`class` bodies do not parse.** All eight models that declare a class -
   `Contract`, `TwoTanks`, `CoolingSystem_flattened_simple`,
   `Reqs_sri_CRML`, `ProbabilityExample1`, `SetOperatorsExample3`,
   `SetOperatorsExample7` and its `_no_ext` twin. Blocks the whole class and
   object half of the language. (`requirements_tank.crml` also declares one
   but fails later, in `ExpressionBuilder`.)
3. **`MixfixParser` resolves calls but nothing typed them.** Now wired as
   `DOMVisitor.resolveOperatorCalls()`; 5 models still have calls it cannot
   match, and it does no type checking (`checkArg` returns true
   unconditionally).
4. **Constructor operand precedence.** In
   `StringConcatenationExample2.crml`, `new String undecided + ", " + ...`
   parses with the constructor swallowing the rest of the expression, giving
   `ADD(BOOLEAN, STRING)` — a combination `typeinference.csv` does not define.
   This is the corpus's one ERROR diagnostic.
5. **Type inference only populates constants.** See §5. Everything downstream
   of `Value.getReturnType()` is weaker than it needs to be.
6. **Linker failures** on `FORML_test/AfterBefore.crml` and
   `FORML_test/EnsureAtEnd.crml`, and `ExpressionBuilder` on
   `use-cases/requirements_tank.crml`.
7. **A set-typed declaration loses its set-ness.** `Real {} S is external;`
   reaches the generator as a plain `Real` variable, which is why `card S`
   cannot be distinguished from `card` over a scalar.
8. **`Set.domain` is never populated**, so a set's element type has to be
   inferred from its elements.

### In `CRMLtoModelica.mo` — the emission target

9. **`at` has no implementation.** Exercised by 9 corpus models, one of them
   with a verification harness. Highest-impact library gap.
10. **`Blocks.ClockAdd` has an empty equation section**, and neither the
    Period-plus-Real nor the Clock-plus-Integer overload of `typeinference.csv`
    has a block at all.
11. **`Functions.lEV` is a copy of `gEV`.** Both compute
    `cvBooleanToBoolean4(r1.t > r2.t)`, so `<=` on events implements `>` and
    `>=` implements strict `>`. A correct call site still yields wrong
    behaviour.
12. **`Functions.Bool4toString` does not exist**, though shipped compiler code
    used to call it. 4 corpus models want it.
13. **Boolean4 comparisons and `diff4`** are legal per `typeinference.csv` and
    have no implementation.
14. **Real/Period comparisons** (`realPeriodeq`, `realPeriodleq`) likewise.
15. **`Blocks.setAnd` has an empty equation section**, and is declared `model`
    with `input`/`output` prefixes rather than `block`.
16. **`Blocks.Integrate`'s third input `a`** is marked `//FIXME` upstream and
    left unbound here.

### In this work

17. **CRML source positions.** Diagnostics and trace links anchor on
    object-model elements only, because the DOM builders discard their ANTLR
    contexts. Adding positions to `crml.xcore`, or a side table, is the
    prerequisite for traces reaching file/line/column.
18. **`BecomesFalse_verif.mo` and `BooleanAtEvent_verif.mo`** bind variables
    their CRML models do not declare. Either the harnesses or the models need
    updating before those two contracts mean anything.
