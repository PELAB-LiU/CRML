# CRML — High-Level Coding Instructions

CRML (Contract Requirements Modeling Language) is a declarative language that aims to formalize requirements and connect their evaluation to simulation models of physical systems.
It is preferred to have some isolation between the requirements and system models. For example, requirements should be formalized without direct access to system variables and instead access them via a parameter.

## Top-level structure

Every CRML file contains one or more `model` declarations:

```crml
model MyModel is {
    // attribute and operator declarations
};
```

Attributes are declared as `Type name is <expression>;` or `Type name is external;` (value supplied by binding). Semicolons terminate every declaration.
Generally, values that are part of the requirement shall be set in the CRML model, while variables that are from the simulated system (and monitored via the CRML model) shall be declared as `external`.

## Primitive types

| Type | Description |
|---|---|
| `Boolean` | 4-valued: `true`, `false`, `undecided`, `undefined` |
| `Integer` | Arbitrary-precision integer |
| `Real` | Continuous real number |
| `String` | Text value |
| `Clock` | Discrete sequence of time instants (ticks) |
| `Event` | A single time instant (rising edge of a Boolean) |
| `Period` | A single time interval between two events |
| `Periods` | A collection of `Period` values defined by two clocks |

## Attribute declarations

```crml
Boolean flag is external;          // externally supplied
Real x is 3.14;                    // fixed value
Clock c is new Clock someBoolean;  // derived from a Boolean
Event e is new Event someBoolean;  // fires on rising edge
Period P is [ e1, e2 ];            // closed interval between two events
```

## Boolean temporal operators

Boolean supports logical operators (`and`, `or`, `not`), comparison (`==`, `<>`), 4-valued arithmetic (`+` accumulation, `*` filter), conditional (`if b then b1 else b2`), clock sampling (`b at c`), period restriction (`during P ensure b`), integration (`integrate b on P`), and duration (`duration b on P` → `Real`).

## Clock temporal operators

Clocks are built from Booleans (`new Clock b`), combined with `and`/`or`, sampled (`b at c`), filtered (`c when b`), counted (`count c on P` → `Integer`), and measured (`time of c` / `rate of c` / `period of c` → `Real`).

## Event operators

Events support delay (`e + d`), projection to a clock (`e proj c`), elapsed time (`e2 - e1` → `Real`), ordering comparisons (`e1 < e2`, etc.), and combining with `and`/`or` to form a `Clock`.

## Periods operators

`Periods` restricts one interval collection to another: `P2 while P1`. Individual `Period` boundaries can be extracted with `P start` and `P end`.

## Operators and Templates

Custom operators define reusable computations. Templates are operators restricted to Boolean arguments and Boolean return type.

```crml
// Operator (typed)
Operator [ Boolean ] Integer n1 '<=' Integer n2 = (n1 < n2) or (n1 == n2);

// Template (Boolean-only, no type annotations)
Template b1 implies b2 = not b1 or b2;
```

Both support natural-language style (keywords interleaved, preferred), mathematical style (name + parenthesised args), or mixed.

## Classes

Classes group named attributes and support inheritance:

```crml
partial class Equipment is {
    String id;
    Boolean inOperation is external;
};

class Pump is {
    Boolean cav is external;
    Boolean nocav = during inOperation ensure not cav;
} extends Equipment;

Pump pump1 is new Pump (id = "Pump1");
```

Key keywords: `partial` (abstract), `extends`, `redeclare`, `new`.

## Types and domains

User-defined types extend or specialise existing types, optionally with `alias` (unit names) and `forbid` (disallowed operators):

```crml
partial type Pressure is Quantity (SIUnit = "Pa");
type PressureBar is Pressure (userUnit = "bar", rate = 1.e5, offset = 0) alias bar;
type Requirement is Boolean forbid { *, +, integrate };
```

## ETL Library

The **ETL (Evaluation and Time Logic) Library** provides the standard operators for evaluating whether a CRML requirement is satisfied over a given time period.

Key operators:
- `b1 implies b2`, `b1 xor b2` — Boolean connectives
- `Clock C inside Period P` / `count Clock C inside Period P` — filter or count clock ticks within a period
- `Boolean b becomes true` / `becomes false` — derive a `Clock` from a Boolean signal
- `decide Boolean phi over Period P` — resolves when the outcome of `phi` is known (or at period end)
- `evaluate Boolean phi over Period P` — returns `true` iff `phi` held at the decision point
- `check Boolean phi over Periods P` — top-level requirement check: `true` iff every period passed

Use `evaluate` for individual periods; use `check` to aggregate over a `Periods` collection.

For the full operator definitions, category mechanism, and a worked example read hint file `etl_library.md`.

## FORM-L Library

The **FORM-L Library** provides high-level, human-readable operators for authoring time-bounded requirements. It builds on top of ETL and is the preferred authoring interface.

A model that uses FORM-L must import both libraries: `model M is flatten {Units, FORM_L} union { … }`.

**Time period constructors** (produce `Periods`):

| Operator | Meaning |
|---|---|
| `during b` | Each interval while `b` is `true` |
| `after b for d` | Window of length `d` starting each time `b` becomes `true` |
| `before b for d` | Window of length `d` ending each time `b` becomes `true` |
| `until b` | From evaluation start until `b` first becomes `true` |
| `from b1 until b2` | Each interval from `b1` to `b2` becoming `true` |

**Requirement operators** (combine a `Periods` with a condition):

| Operator | Meaning |
|---|---|
| `P 'check count' (C) op n` | Count of clock ticks inside each period satisfies `op n` |
| `P 'ensure' phi` | `phi` holds throughout every period |
| `P 'check duration' phi op d` | Cumulated duration of `phi` inside each period satisfies `op d` |
| `P 'check at end' phi` | `phi` holds at the end of every period |
| `P 'check anytime' phi` | `phi` holds at least once inside every period |

For the full operator definitions, desugaring to ETL, and a worked example read hint file `form_l_library.md`.

## Working with hints

For detailed syntax, operator tables, and more examples call the documentation tools:
- `list_CRML_documentation_files` — list all available hint files
- `read_hint_file` with a filename — read a specific hint file (e.g. `boolean_type.md`, `clock_type.md`, `etl_library.md`, `form_l_library.md`)
- `search_hints` with a keyword — find the hint files relevant to a topic


