# Reference resolution in the Modelica object model

**Status:** Draft — adaptation of the general Reference Resolution Specification
to this codebase.
**Scope:** `crml.model.modelica` (the `:modelica` submodule), the transformation
in `:compiler`'s `crml.compiler.crmlcv2` that produces it, and the serializer in
`crml.modelica.print`.

Companion to `modelica-metamodel-plan.md`; section numbers below mirror the
source specification so the two can be read side by side.

---

## 0. What this adapts, and why

The source specification was written against assumptions that do not all hold
here. Each divergence below is a deliberate ruling, not an omission.

| Source spec says | Here | Ruling |
|---|---|---|
| Scope is "the Modelica model representation used by the `:language` project" | `:language` owns the **CRML** model (`crml.model.language`). The **Modelica** model is `crml.model.modelica`, built by `:compiler` and printed by `crml.modelica.print`. | Scope corrected to `:modelica` + `:compiler`. The build/link *conventions* are still borrowed from `:language`'s `DOMVisitor`, which is what the parenthetical in the source header was pointing at. |
| A parser builds the model, so there is a build phase over source text | **Nothing parses Modelica in this project.** The Modelica model is only ever generated from the CRML object model. | "Build phase" becomes the transformation. No symbol table over source text. |
| `Reason.PARSE_ERROR` for malformed input | No Modelica parser, so no producer for it. | **Cut.** Per the plan's governing rule (§3): a feature with no named producer is cut, not kept cheaply. |
| Raw references are the exception, "never the default outcome" | Raw is the **majority by an order of magnitude** — see §1.1. | Reframed: raw is the main road and is permanent by design; the point of the work is the minority that *do* have targets. |
| `ConnectEquation.a` / `.b` referring to `PortDecl` | The metamodel has no `connect` and no ports. §5.6 of the plan wires block instances with plain equations, and §3.6 cut connect clauses outright. | **Dropped.** The analogous hard case here is a dotted `ComponentReference` such as `inst.out`, handled in §6.2. |
| `Reference<T>` as a Java `sealed interface` with `record` cases | The project compiles at **Java 8** (`options.release.set(8)` in the root build), and the model is Xcore-generated EMF. Records and sealed interfaces are unavailable, and a model feature must be expressible in `.xcore`. | Re-encoded as an EMF class with an optional `refers` target — §4. |
| Resolution needs a symbol table and deferred link tasks | The transformation almost always **has the target object in hand** when it writes the reference. | The link phase shrinks to one case, and §5.2 proposes removing even that. |

---

## 1. Purpose

Replace the flat dotted-name strings in the Modelica object model —
`ComponentDeclaration.typeName`, `ExtendsClause.typeName`,
`FunctionCall.functionName` — with real cross-references wherever the target is
an element of the generated tree, while keeping explicit raw text for the many
names that refer outside it.

The plan justified those strings deliberately (§3.2: "`typeName` is a **flat
dotted string**, not a `TypeSpecifier` class … `TypeResolver` already yields
`"CRMLtoModelica.Types.Boolean4"` as a literal"). That reasoning is sound for
library names and wrong for the handful of names that point at classes this
compiler itself generated.

### 1.1 What is actually at stake

Counted over the 153 corpus models the transformation currently translates:

| | Occurrences |
|---|---|
| **Has an in-tree target** | |
| calls to a generated operator function | 126 |
| components of a generated operator block | 12 |
| `extends` a generated class | 0 — no corpus model declaring a CRML class parses |
| **Permanently outside the tree** | |
| `CRMLtoModelica.*` | 1472 |
| Modelica builtin scalar types | 203 |
| `Modelica.Math.*` | 17 |

So this is not a sweeping change: 138 references gain a real target, ~1700 stay
text. The value is not volume, it is two concrete defects that become
unrepresentable.

**Defect 1 — inconsistent identifier quoting.** `ModelicaPrinter` quotes a class
declaration (`:96`) and an `extends` type name (`:168`) through
`Identifiers.quote`, but prints `component.getTypeName()` **verbatim** (`:190`).
A CRML class whose name is not a plain Modelica identifier — a reserved word
such as `input`, or a name with a space — therefore declares as
`model 'my class'` and is referenced as `my class inst1;`. That is not valid
Modelica. It is latent rather than observed only because no corpus model that
declares a CRML class parses. A reference resolved to the class object lets the
printer derive *and quote* the name in one place, the way it already does for
declarations, so the two cannot disagree.

**Defect 2 — names that can silently drift apart.**
`TransformationContext.allocateClassName` appends `_2` on collision, while
`TypeResolver.resolve(UserTypereference)` returns `clazz.getName()` unchanged.
If those ever disagree the output names a class that does not exist, with no
error anywhere. M5 worked around it by reserving every CRML class name before
generating anything (`reserveClassName`); a reference makes the failure mode
impossible rather than avoided.

### Non-goals

As in the source spec: no `connect` semantics (there are none), no `outer`/
`inner` search, no cross-model linking. Additionally out of scope here: parsing
Modelica, and resolving into `CRMLtoModelica.mo`. See §9.

---

## 2. Terminology

Unchanged from the source spec, with one addition: **generated class** means a
`ClassDefinition` the transformation itself created — the model class, a nested
class for a CRML class, or a nested function/block for a CRML operator. Only
generated classes can ever be the target of a resolved reference.

---

## 3. Containment

Native EMF, exactly as the source spec requires and as the metamodel already
does: `eContainer`, `contains`, no hand-rolled parent field. Every reference
feature below is non-containment. Nothing to adapt.

One consequence already learned the hard way in this codebase, worth restating
because §7 depends on it: a contained EObject has exactly one parent, so handing
the same node to a second container silently removes it from the first. That bug
appeared twice during M2 and M4 (the `_build` companion losing its modification
value, and a shared `UserTypereference`). Reference *targets* are non-containment
and so are immune, which is a further argument for them.

---

## 4. The `Reference` type

Java 8 has no records and no sealed interfaces, and the type has to live in
`.xcore`. The source spec's two-case sum type becomes one EMF class with an
invariant:

```
enum RawReason {
    EXTERNAL_LIBRARY    // CRMLtoModelica.mo - never parsed, no object will exist
    MODELICA_BUILTIN    // Real, Integer, String, Boolean, mod, integer, time
    STANDARD_LIBRARY    // Modelica.Math.* - MSL, not on the compiler's load path
    UNRESOLVED          // lookup was attempted and found nothing
}

class Reference extends ModelicaElement {
    refers ClassDefinition target   // set iff this is a resolved reference
    String rawText                  // set iff target is null
    RawReason reason                // set iff target is null
}
```

**Invariant:** exactly one of `target` and `rawText` is set. It is enforced by
construction (§4.1), not by validation, because nothing else constructs these.

### 4.1 Construction rules

The source spec's rules carry over, expressed as the only two factories:

```java
Modelica.resolvedRef(ClassDefinition target)      // no String overload exists
Modelica.rawRef(String text, RawReason reason)    // reason is not optional
```

There is deliberately no factory taking a name and searching for it: a resolved
reference can only be built from an object already in hand.

### 4.2 Where references replace strings

| Feature | Today | Becomes | Typical outcome |
|---|---|---|---|
| `ComponentDeclaration.typeName` | `String` | `contains Reference type` | decided by `TypeResolver` (§5.4): raw for CRML builtin and Modelica primitive types, resolved for CRML classes. Operator-block instances are resolved by `OperatorTransformer`, library blocks are raw. |
| `ExtendsClause.typeName` | `String` | `contains Reference superClass` | always resolved — the only producer is `ClassTransformer`, over a CRML super-class |
| `FunctionCall.functionName` | `String` | `contains Reference function` | raw for `CRMLtoModelica.Functions.*`, `Modelica.Math.*` and builtins; resolved for a generated operator function |

**Unchanged, and deliberately so:**

- `ModificationElement.name` stays a `String`. It names a member of the type
  being modified — `b`, `clock`, `start_event`, `isLeftBoundaryIncluded` — and
  in every current producer that type is a `CRMLtoModelica.Types` record, which
  has no object in the tree. Making it a reference would produce a raw value at
  every single call site.
- `ReferencePart.name` stays a `String`, except for the head of a
  `ComponentReference` — see §6.2.

`RawReason` is finer-grained than the source spec's single external reason
because the distinction is already needed: the plan (§7.3) records that
`Modelica.Math.*` "adds an MSL dependency absent from `CRMLtoModelica.mo`; the
generated `.mo` will not compile without MSL on the load path". With
`STANDARD_LIBRARY` recorded on the reference, the compiler can state which
models need MSL instead of leaving it as a note.

---

## 5. Construction

There is no parse, so there is no separate build phase over text. The
transformation constructs the tree and the references together.

### 5.1 Where resolvable references come from

Three producers, and none of them searches by name:

| Producer | Target | How it is found |
|---|---|---|
| `OperatorTransformer.transformCall` → function call or block instance | the generated operator class | `GeneratedOperator.definition()`, just generated or fetched from the registry |
| any CRML-typed declaration — variable, class member, set element, constructor instance | the nested class for a CRML class | `TypeResolver` (§5.4), via the class registry |
| `ClassTransformer` → `extends` | the nested class for the CRML super-class | the class registry |

Only the first is unconditionally in hand. The other two are registry lookups,
and both can be asked for a class that has not been generated yet: a model may
declare a class before the class it extends or uses as a member type.
`ModelTransformer` transforms classes in declaration order, so ordering alone
does not save them.

### 5.2 Removing the deferral

Two options, and the second is required — see §5.4:

1. **Link task**, as the source spec describes: queue a `Runnable` closing over
   the referring `ExtendsClause` and the CRML super-class, run it after all
   classes exist. This reproduces `DOMVisitor`'s `crossrefTasks`/`linker()`
   idiom, which `:language` already uses for exactly this case
   (`ClassBuilder` populates `superClasses` via a deferred task).

2. **Two-pass class creation.** Create every nested `ClassDefinition` as a shell
   — name and kind only — register each against its CRML class, then populate
   bodies. Every reference is then in hand and **no link phase exists at all**.
   `ModelTransformer` already does half of this, reserving all class names
   before generating anything.

Option 2 is the one to take. It removes a phase rather than adding one, it makes
the "resolved references are built from objects, never from names" rule
structural rather than procedural, and §5.4 needs it: `TypeResolver` is called
while a class body is being populated, so every nested class has to exist by
then. Option 1 should be revisited only if a Modelica *parser* is ever added, at
which point the full source-spec machinery — symbol table, link tasks,
`PARSE_ERROR` — becomes appropriate wholesale.

### 5.3 Raw is chosen at construction, not after a failed lookup

This is the source spec's §5.3 short-circuit, generalised: because the
transformation writes library names as literals, it always knows a name is
external at the moment it writes it. `Modelica.call("CRMLtoModelica.Functions.and4", …)`
becomes `Modelica.rawRef("CRMLtoModelica.Functions.and4", EXTERNAL_LIBRARY)`.

No known-symbol list and no import scanning are needed — the source spec left
that as an implementation detail, and here the detail is that the information is
already at the call site. Around 104 string literals in the transformation name
a library or builtin target — 75 `CRMLtoModelica.*`, 25 `Modelica.Math.*` and
the four Modelica builtin functions (`String`, `Integer`, `integer`, `mod`) —
concentrated in `TypeResolver`, the two operator transformers and
`RecordBuild`.

### 5.4 `TypeResolver` decides raw against resolved, for types

Rather than each transformer choosing, the one function that already turns a
CRML type into a Modelica type name makes the call, and its return type carries
it:

```java
// today
public static String resolve(TypeReference type)

// becomes
public static Reference resolve(TransformationContext ctx, TypeReference type)
```

| CRML `TypeReference` | Modelica target | Result |
|---|---|---|
| `BuiltinTypeReference` → BOOLEAN, REQUIREMENT | `CRMLtoModelica.Types.Boolean4` | raw, `EXTERNAL_LIBRARY` |
| → EVENT | `CRMLtoModelica.Types.Event` | raw, `EXTERNAL_LIBRARY` |
| → CLOCK | `CRMLtoModelica.Types.CRMLClock` | raw, `EXTERNAL_LIBRARY` |
| → PERIOD, PERIODS | `CRMLtoModelica.Types.CRMLPeriod`, `…CRMLPeriods` | raw, `EXTERNAL_LIBRARY` |
| → REAL, INTEGER, STRING | `Real`, `Integer`, `String` | raw, `MODELICA_BUILTIN` |
| `UserTypereference` whose domain is a CRML `Class` | the generated nested class | **resolved** |
| `IndirectTypeReference` | — | recurse into the referred type |
| null, or a `Domain` that is not a `Class` | — | raw, `UNRESOLVED`, plus an ERROR diagnostic |

"Complex type" and "CRML class" are the same set: `Class` is the only concrete
subclass of `Domain` in `crml.xcore`, and `PowerClass` neither extends `Domain`
nor is built by any DOM builder. So the split above is total, and the last row
is a defect case rather than a category.

This is also where the two `RawReason` values earn their separation. The CRML
builtin types are records and an enumeration inside `CRMLtoModelica.mo`, which
is never parsed; `Real`, `Integer` and `String` are part of the Modelica
language itself. Both are permanently raw, but only the first is a library
dependency.

Three consequences:

* **`TypeResolver` stops being a context-free static utility.** Resolving a
  CRML class needs the class-to-`ClassDefinition` registry, so it takes the
  `TransformationContext`.
* **This makes §5.2 option 2 mandatory, not merely preferable.** A class member
  typed by a class declared later in the same model is resolved while
  `ClassTransformer` is populating the first class, before `ModelTransformer`
  has reached the second. Creating every nested class as a shell up front is
  what makes that lookup always succeed.
* **The existing `throw`/`catch` pair disappears.** `TypeResolver` currently
  throws a `RuntimeException` on an unresolvable type, and `VariableTransformer`
  and `ClassTransformer` each wrap the call in a `try`/`catch` to convert it
  into a diagnostic. With a context in hand, `TypeResolver` reports the
  diagnostic itself and returns an `UNRESOLVED` reference.

`String resolve(BuiltinType)` stays as it is, as the single place the literal
library names are written; the reference-returning overload calls it rather than
transformers calling it directly.

The two type references that do **not** come from `TypeResolver` follow the same
rule from their own construction sites: `OperatorTransformer` resolves a
generated operator class from the object in hand, and `BlockInstantiation` emits
a raw `EXTERNAL_LIBRARY` reference for `CRMLtoModelica.Blocks.*`. Its
`String blockType` parameter becomes a `Reference` accordingly.

---

## 6. Resolution

### 6.1 There is no search

With §5.2 option 2 there is no `resolve(scope, path)` function at all. The
class registry of §5.1 is keyed by the CRML `Class` **object**, not by its name,
so it is a map lookup rather than name resolution — no scope, no path, no
ambiguity. The source spec's child-only descent algorithm has no producer here,
and is recorded as an extension point (§9) rather than written.

### 6.2 Dotted component references

`ComponentReference` is the one case the source spec's algorithm could not have
expressed anyway. A generated reference such as

```modelica
CRMLtoModelica.Types.CRMLClock c = op_becomes_false_1.out;
```

has a head (`op_becomes_false_1`, a component of the enclosing class) and a tail
(`out`, a member of that component's *type*). Resolving the tail means stepping
through a type, which is not containment descent — and for a library-typed
component such as `r1.ticks` there is no type object to step into.

**Ruling: head only.** `ComponentReference.parts[0]` may carry a
`Reference` to a `ComponentDeclaration`; every later part stays a `String`. This
covers the case that matters — a renamed component breaking its own references —
and stops where the tree stops. Whole-path resolution is an extension point
(§9), reachable only if `CRMLtoModelica.mo` is ever parsed.

This is a change to `Reference.target`'s type: it must then admit a
`ComponentDeclaration` as well as a `ClassDefinition`. Two ways, mirroring a
choice M1 already faced:

- **Generic `class Reference<T>`.** Xcore supports generics — `crml.xcore` uses
  `Set<D>` — so `contains Reference<ClassDefinition> type` is plausible and
  keeps the source spec's per-feature type checking.
- **`refers ModelicaElement target`**, one non-generic class, with the feature's
  declared intent documented rather than checked.

Generics are the unproven construct here, exactly as `refers ecore::EObject` was
in M1 §12. **Validate it before anything depends on it**, and fall back to the
non-generic form if it resists — which is precisely the trade the trace model
already took, and it has been fine.

### 6.3 Applying a result

Only relevant under §5.2 option 1. A lookup that finds nothing sets
`rawRef(path, UNRESOLVED)` and reports a `Diagnostic` — see §8.

---

## 7. Rendering

`ModelicaPrinter` gains one method and loses three verbatim string writes:

```
printRef(context, ref):
  if ref.target == null:
    return ref.rawText                      // verbatim, quoting is the source's business
  return Identifiers.quotePath(pathFrom(context, ref.target))
```

`pathFrom` walks `eContainer` from both ends to the common ancestor and joins
the names — which in this metamodel is nearly always a single segment, since the
Modelica tree is one model class with a flat layer of nested classes inside it.

Two properties follow, and both are the point of the exercise:

- The printed name is **recomputed from live containment**, so renaming a
  generated class cannot leave a dangling reference (defect 2 of §1.1).
- Every resolved name goes through `Identifiers.quote`, so a declaration and its
  references cannot disagree (defect 1 of §1.1). Raw text is still printed
  exactly as stored, which is required: `CRMLtoModelica.Functions.and4` must not
  be quoted, and `der` must not become `'der'`.

---

## 8. Diagnostics

Folded into the existing `crml.compiler.crmlcv2.Diagnostic` machinery from M2
rather than a parallel mechanism, and following its severity convention.

| Condition | `RawReason` | Diagnostic | Retryable |
|---|---|---|---|
| Library, builtin or MSL name | `EXTERNAL_LIBRARY`, `MODELICA_BUILTIN`, `STANDARD_LIBRARY` | none — working as intended | never |
| Model uses `Modelica.Math.*` | `STANDARD_LIBRARY` | one `INFO` per model: needs MSL on the load path | n/a |
| Lookup found nothing | `UNRESOLVED` | `ERROR` | only if §9 extends resolution |

`UNRESOLVED` is an **ERROR**, not a warning, and that is a departure worth
stating. Everywhere else in this compiler a gap is a WARNING, because CRML
allows something the runtime library cannot express. An unresolved reference is
different in kind: the transformation should have had the object in hand, so it
means a defect in the compiler — which is exactly what the severity convention
reserves ERROR for, and what the specification tests assert against.

---

## 9. Extension points

Deferred exactly as in the source spec, with this project's triggers named:

- **Whole-path `ComponentReference` resolution** (§6.2) — needs a type-directed
  step, and a target object for library types. Only worthwhile if
  `CRMLtoModelica.mo` is parsed.
- **A search-based `resolve(scope, path)`** (§6.1) — needed the moment a
  reference cannot be built in hand, i.e. when a Modelica parser arrives. At
  that point adopt the source spec's symbol table, link tasks and `PARSE_ERROR`
  as a set; they belong together.
- **`connect` equations and ports** — currently no metamodel classes at all
  (§3.6 of the plan). Adding them would make the source spec's
  `ConnectEquation.a`/`.b` rows apply as written.
- **Further external units** — a new `RawReason` value each, recognised at the
  construction site as in §5.3.

---

## 10. Worked example

From `libraries/ETL_test/BecomesFalse.crml`, which the compiler translates
today:

```modelica
partial model BecomesFalse
    block op_becomes_false
        input CRMLtoModelica.Types.Boolean4 b;
        output CRMLtoModelica.Types.CRMLClock out;
        CRMLtoModelica.Types.CRMLClock c1(b = (CRMLtoModelica.Functions.not4(b)));
        CRMLtoModelica.Types.CRMLClock_build c1_init(clock = c1);
    equation
        out = c1;
    end op_becomes_false;
    CRMLtoModelica.Types.Boolean4 b1;
    op_becomes_false op_becomes_false_1;
    CRMLtoModelica.Types.CRMLClock c_b1_becomes_false = op_becomes_false_1.out;
equation
    op_becomes_false_1.b = b1;
end BecomesFalse;
```

| Reference | Outcome |
|---|---|
| `op_becomes_false_1`'s type | **Resolved** to the nested `block op_becomes_false`, in hand from `GeneratedOperator.definition()`. Printed by recomputing the path, so renaming the block renames the declaration too. |
| `CRMLtoModelica.Types.Boolean4`, `…CRMLClock` | **Raw**, `EXTERNAL_LIBRARY`, from `TypeResolver` (§5.4) — the CRML types `Boolean` and `Clock`. Chosen by the rule, never by a failed lookup, so no diagnostic. |
| `CRMLtoModelica.Types.CRMLClock_build` | **Raw**, `EXTERNAL_LIBRARY`, written directly by `RecordBuild`; it has no CRML type to resolve from. |
| `CRMLtoModelica.Functions.not4` | **Raw**, `EXTERNAL_LIBRARY`, from `UnaryOperatorTransformer`. |
| `op_becomes_false_1.out` | **Head resolved** to the component declaration; `out` stays a string (§6.2). |
| `c1`, `b1`, `b` in equations and modifications | Head-only component references; single segment, resolved. |

Nothing in this example changes in the printed output. That is the intended
result: the work buys structural guarantees, not different text.
