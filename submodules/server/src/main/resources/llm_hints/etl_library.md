## ETL Library

The **ETL (Extended Temporal Language) Library** provides the standard operators for deciding whether a CRML requirement is satisfied over a given time period. It complements the FORM-L library.

---

### Boolean Templates

ETL defines three Boolean connectives as templates (Boolean-to-Boolean, no type annotations):

| Template | Syntax | Meaning |
|---|---|---|
| Disjunction | `b1 or b2` | `not (not b1 and not b2)` |
| Exclusive or | `b1 xor b2` | `(b1 or b2) and not (b1 and b2)` |
| Implication | `b1 implies b2` | `not b1 or b2` |

```crml
model BooleanTemplates is {
    // or is built-in; shown here only for documentation:
    // Template b1 or b2 = not (not b1 and not b2);

    Template b1 xor b2 = (b1 or b2) and not (b1 and b2);
    Template b1 implies b2 = not b1 or b2;
};
```

---

### Clock Operators

These operators work on `Clock` values and `Period`/`Periods` values.

| Operator | Return type | Meaning |
|---|---|---|
| `Clock C inside Period P` | `Clock` | Filters `C` to ticks that fall within `P` |
| `count Clock C inside Period P` | `Integer` | Number of ticks of `C` that fall within `P` |

```crml
model EventOperatorsETL is {
    Boolean b is external;
    Period  P is external;

    // Ticks each time b transitions to true / false
    Clock becomes_true  is new Clock b;
    Clock becomes_false is new Clock not b;

    // Same, restricted to within P
    Clock becomes_true_inside  is becomes_true  filter (time >= P start) and (time <= P end);
    Clock becomes_false_inside is becomes_false filter (time >= P start) and (time <= P end);
};
```

---

### Event Operators

These operators derive `Clock` values from Boolean signals, optionally restricted to a `Period`.

| Operator | Return type | Meaning |
|---|---|---|
| `Boolean b becomes true` | `Clock` | Ticks each time `b` transitions to `true` |
| `Boolean b becomes false` | `Clock` | Ticks each time `b` transitions to `false` |
| `Boolean b becomes true inside Period P` | `Clock` | As above, restricted to `P` |
| `Boolean b becomes false inside Period P` | `Clock` | As above, restricted to `P` |

```crml
model EventOperatorsETL is {
    Boolean b is external;
    Period  P is external;

    // Ticks each time b transitions to true / false
    Clock becomes_true  is new Clock b;
    Clock becomes_false is new Clock not b;

    // Same, restricted to within P
    Clock becomes_true_inside  is becomes_true  filter (time >= P start) and (time <= P end);
    Clock becomes_false_inside is becomes_false filter (time >= P start) and (time <= P end);
};
```

---

### Requirement Evaluation Operators

These three operators form the core evaluation chain. They decide whether a Boolean condition `phi` is satisfied over a time period `P`.

#### `decide`

```crml-snippet
Operator decide is Operator [ Boolean ] decide Boolean phi over Period P = phi or (P end);
```

Returns `true` as soon as a *decision event* occurs — either `phi` becomes `true`, or the period ends. This is the lowest-level building block used by the category mechanism (see below) and by `evaluate`.

#### `evaluate`

```crml-snippet
Operator [ Boolean ] evaluate Boolean phi over Period P = integrate ((decide phi over P) * phi) on P;
```

Formally: `∫ a(φ, P) × φ` over `P`, where the filter `a(φ, P) = a'(φ) ∨ P↓` activates at the decision event or at the end of `P`. Returns `true` if `phi` holds when the decision is made.

#### `check`

```crml-snippet
Operator [ Boolean ] check Boolean phi over Periods P = and (evaluate phi over P);
```

Evaluates `phi` over every period in the `Periods` collection and returns `true` iff all individual evaluations are `true`. This is the top-level operator used to verify a requirement.

```crml
model RequirementEvaluationOperators is {
    Boolean phi is external;
    Period  P   is external;
    Periods PS  is external;

    Boolean decided   is phi or new Boolean P end;
    Boolean evaluated is integrate (decided * phi) on P;
    Boolean checked   is integrate phi on PS;
};
```

---

### Categories for Automated Decision Detection

Categories allow the `decide` operator to be specialised based on the monotonicity or variability properties of `phi`. Four standard categories are provided, each controlling *when* a decision event is raised enagbling the termination of a simulation early when the result is conclusive.

#### `increasing1` — Integer comparisons with early decision

Applies to comparisons where an integer quantity is known to be non-decreasing (e.g. a counter). The decision can be made before period end once the comparison outcome is certain.

```crml-snippet
Category c1 is Category increasing1 = { (>, >), (>=, >=), (<, >=), (<=, >), (==, >), (<>, >) };
Category {} C1 is associate increasing1 with decide;
```

#### `increasing2` — Real comparisons with early decision

Same idea as `increasing1` but for real-valued comparisons (excludes `==` and `<>` which are not robust for reals).

```crml-snippet
Category c2 is Category increasing2 = { (>, >), (>=, >=), (<, >=), (<=, >) };
Category {} C2 is associate increasing2 with decide;
```

#### `varying1` — Decide only at period end

For signals that can vary arbitrarily; the decision is deferred until the period ends. `phi` is observed as-is at the end.

```crml-snippet
Operator [ Boolean ] id       Boolean b = b;
Operator [ Boolean ] cte_false Boolean b = false;
Category c3 is Category varying1 = { (id, cte_false) };
Category {} C3 is associate varying1 with decide;
```

#### `varying2` — Decide at any change

For Boolean signals where any change is significant; a decision is raised immediately whenever `phi` changes value (as well as at period end).

```crml-snippet
Operator [ Boolean ] cte_true Boolean b = true;
Category c4 is Category varying2 = { (id, cte_true) };
Category {} C4 is associate varying2 with decide;
```

---

### Example Model

The following model illustrates the ETL library in a simple pump monitoring scenario. The requirement is: *"During every mission period, the pump must never cavitate."*

```crml
model PumpMonitoring is {

    // --- External signals ---
    Boolean pump_running   is external;
    Boolean cavitation     is external;
    Real    flow_rate      is external;
    Real    flow_threshold is external;

    // --- Mission period ---
    // A mission starts when the pump starts and ends when it stops.
    Event  mission_start is new Event pump_running;
    Event  mission_end   is new Event not pump_running;
    Period mission       is [ mission_start, mission_end ];

    // --- ETL Boolean connectives ---
    // "no cavitation AND adequate flow" must hold throughout the mission.
    Boolean no_cavitation  is not cavitation;
    Boolean adequate_flow  is flow_rate >= flow_threshold;
    Boolean healthy        is no_cavitation and adequate_flow;

    // --- Event counting inside the period ---
    // Count how many cavitation events occurred during the mission.
    Clock   cavitation_events is new Clock cavitation;
    Clock   cavitation_inside is cavitation_events filter (time >= mission start) and (time <= mission end);
    Integer cavitation_count  is card cavitation_inside;

    // --- Implication example ---
    // If cavitation occurred, then flow must eventually recover.
    Boolean flow_recovered              is flow_rate >= flow_threshold;
    Boolean cavitation_implies_recovery is not cavitation or flow_recovered;

    // --- Core ETL evaluation ---
    // decide: resolves as soon as healthy is false, or at mission end.
    Boolean health_decision is healthy or new Boolean mission end;

    // evaluate: true iff healthy held at the decision point.
    Boolean mission_ok is integrate (health_decision * healthy) on mission;

    // check: verify over all missions defined by pump start/stop clocks.
    Periods all_missions          is [ new Clock pump_running, new Clock not pump_running ];
    Boolean requirement_satisfied is integrate healthy on all_missions;

};
```

**Trace through the evaluation:**

1. `mission` opens at `pump_running↑` and closes at `pump_running↓`.
2. `healthy = not cavitation and (flow_rate >= flow_threshold)` is evaluated continuously.
3. `decide healthy over mission` fires `true` as soon as it is certain `healthy` cannot recover (here, immediately when `healthy` becomes `false`), or at mission end if `healthy` stayed `true`.
4. `evaluate` returns the value of `healthy` at the decision point — `false` if cavitation occurred, `true` otherwise.
5. `check` conjoins `evaluate` over all past missions; `true` only if every mission was healthy.
