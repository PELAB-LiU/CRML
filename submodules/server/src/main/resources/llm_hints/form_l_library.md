## FORM-L Library

The **FORM-L Library** (Formal Requirements Modelling Language) provides a vocabulary of high-level, human-readable operators for expressing time-bounded requirements in CRML.

---

### Time Period Operators

These operators construct `Period` or `Periods` values from Boolean signals and durations.

| Operator | Return type | Meaning |
|---|---|---|
| `'from' Clock e` | `Periods` | From `e` (inclusive) until the end of time |
| `'after' Clock e` | `Periods` | After `e` (exclusive) until the end of time |
| `'before' Clock e` | `Periods` | From the beginning of time, but before `e` (exclusive) |
| `'until' Clock e` | `Periods` | From the beginning of time until `e` (inclusive) |
| `'during' Boolean b` | `Periods` | Each interval while `b` is continuously `true` |
| `'after' Clock e1 'before' Clock e2` | `Periods` | A combination of `after` and `before`, end is exclusive
| `'after' Clock e1 'until' Clock e2` | `Periods` | A combination of `after` and `until`, end is inclusive
| `'after' Clock e 'for' Real d` | `Periods` | After `e` for the duration of `d`, end is inclusive
| `'after' Clock e 'within' Real d` | `Periods` | After `e` for the duration of `d`, end is exclusive
| `'from' Clock e1 'before' Clock e2` | `Periods` | A combination of `from` and `before`, end is exclusive
| `'from' Clock e1 'until' Clock e2` | `Periods` | A combination of `from` and `until`, end is inclusive
| `'from' Clock e 'for' Real d` | `Periods` | From `e` for the duration of `d`, end is inclusive
| `'from' Clock e 'within' Real d` | `Periods` | From `e` for the duration of `d`, end is exclusive
| `'when' Clock e` | `Periods` | Alias for `'during' b`; emphasises point-in-time conditions |

```crml
model FORML_OperatorDefinitions is {

    Boolean b  is external;
    Boolean b1 is external;
    Boolean b2 is external;
    Integer d  is external;  // duration in seconds

    // during b: each interval while b is continuously true
    Periods during_b is [ new Clock b, new Clock (not b) ];

    // after b for d: interval of length d starting at each rising edge of b
    Clock   b_rises       is new Clock b;
    Periods after_b_for_d is ] b_rises, b_rises + d ];

    // from b for d: interval of length d ending at each rising edge of b
    Periods from_b_for_d is [ b_rises + (- d), b_rises ];

    // from b1 until b2: each interval from b1 rising to b2 rising
    Periods from_b1_until_b2 is [ new Clock b1, new Clock b2 ];

    // when b: alias for during b
    Periods when_b is [ new Clock b, new Clock b ];
};
```

---

### Requirement Operators

These operators combine a `Periods` value with a condition to produce a `Boolean` requirement result.

| Operator | Meaning |
|---|---|
| `Periods P 'check count' Clock C 'op' n` | True iff the count of ticks of `C` inside each period satisfies the comparison `'op' n`, where `'op'` is a comparison operator |
| `Periods P 'ensure' Boolean phi` | True iff `phi` holds throughout every period (universally quantified) |
| `Periods P 'check duration' Boolean phi op d` | True iff the cumulated duration of `phi` inside each period satisfies `op d` |
| `Periods P 'check at end' Boolean phi` | True iff `phi` holds at the end of every period |
| `Periods P 'check anytime' Boolean phi` | True iff `phi` holds at some point inside every period |

All operators desugar to `check (evaluate ... over P)` from the ETL library, with the appropriate category applied for early decision-making.

---

### Example Model

The following model illustrates all major FORM-L operators applied to a battery management system scenario.

**Scenario:** A battery management system (BMS) monitors cells during *charging cycles*. The requirements cover charge-start count limits, temperature bounds, cumulated over-voltage duration, and a post-startup voltage recovery check.

```crml
model BatteryManagement is {

    // ---- Units expressed as Integer constants (seconds) ----
    Integer mn is 60;
    Integer h  is 3600;

    // ---- External signals ----
    Boolean cycle_active     is external;
    Boolean cell_charging    is external;
    Real    cell_temperature is external;
    Real    terminal_voltage is external;
    Boolean fault_detected   is external;

    // ---- Derived clocks for rising/falling edges ----
    Clock charge_starts is new Clock cell_charging;
    Clock charge_stops  is new Clock (not cell_charging);
    Clock cycle_starts  is new Clock cycle_active;
    Clock cycle_ends    is new Clock (not cycle_active);

    // ---- Time period collections (FORM-L desugared) ----

    // during cycle_active
    Periods during_cycle is [ cycle_starts, cycle_ends ];

    // during cell_charging
    Periods during_charge is [ charge_starts, charge_stops ];

    // after cell_charging for 30 mn: 30-minute window after each charge start
    Periods after_charge_30mn is ] charge_starts, charge_starts + (30) ];

    // after cell_charging for 10 mn: 10-minute window after each charge start
    Periods after_charge_10mn is ] charge_starts, charge_starts + (10) ];

    // after (terminal_voltage > 4.2 V) for 1 h, restricted to the active cycle:
    // the window opens only when the threshold is crossed during the cycle
    Clock   voltage_exceeds_42 is new Clock (terminal_voltage > 4.2)
                                    filter ((time >= during_cycle start)
                                       and (time <= during_cycle end));
    Periods R4_periods is ] voltage_exceeds_42, voltage_exceeds_42 + (1 * h) ];

    // ---- R1: During the charging cycle, charging started at most 5 times ----
    Clock   charge_starts_in_cycle is charge_starts
                                      filter ((time >= during_cycle start)
                                         and (time <= during_cycle end));
    Integer charge_start_count     is card charge_starts_in_cycle;
    Boolean R1 is integrate (charge_start_count <= 5) on during_cycle;

    // ---- R2: In the 30-min window after each charge start, no new charge start occurs ----
    Clock   starts_in_30mn_window is charge_starts
                                      filter ((time >= after_charge_30mn start)
                                         and (time <= after_charge_30mn end));
    Integer starts_in_30mn_count  is card starts_in_30mn_window;
    Boolean R2 is integrate (starts_in_30mn_count == 0) on after_charge_30mn;

    // ---- R3: While charging, cell temperature must stay below 45 °C ----
    Boolean R3 is integrate (cell_temperature < 45.0) on during_charge;

    // ---- R4: Within each 1-h post-threshold window, terminal voltage must not exceed 4.2 V ----
    // Note: the original "check duration < 5 mn" requires the 'duration' operator,
    // which is not yet supported by the checker. This uses Boolean accumulation
    // (phi + false) as an approximation: true iff the condition never occurs.
    Boolean voltage_over_42      is terminal_voltage > 4.2;
    Boolean voltage_over_42_ever is voltage_over_42 + false;
    Boolean R4 is integrate (not voltage_over_42_ever) on R4_periods;

    // ---- R5: At the end of each charge run, terminal voltage must be >= 3.8 V ----
    Boolean voltage_ge_38        is terminal_voltage >= 3.8;
    Boolean voltage_ge_38_at_end is voltage_ge_38 at new Clock during_charge end;
    Boolean R5 is integrate voltage_ge_38_at_end on during_charge;

    // ---- R6: Within 10-min charge window, terminal voltage must reach 3.8 V at least once ----
    Boolean voltage_ge_38_ever is voltage_ge_38 + false;
    Boolean R6 is integrate voltage_ge_38_ever on after_charge_10mn;

    // ---- R7: No fault must be detected during the charging cycle ----
    Boolean R7 is integrate (not fault_detected) on during_cycle;

};
```

**Key points illustrated:**

| Requirement | Operator | Notes |
|---|---|---|
| R1 | `'during' … 'check count'` | Counts rising edges of `cell_charging` inside each charging-cycle period |
| R2 | `'after' … 'for' … 'check count'` | Creates a 30-minute window after each charge start; count must be zero |
| R3 | `'during' … 'ensure'` | Universal check: `cell_temperature < 45°C` must hold at every instant |
| R4 | `'during' … 'after' … 'for' … 'check duration'` | Nested period: cycle window → 1-hour post-threshold window; cumulated duration of violation must be < 5 min |
| R5 | `'during' … 'check at end'` | Checks the condition only at the closing event of each period |
| R6 | `'after' … 'for' … 'check anytime'` | Checks that the condition is satisfied at least once within the window |
| R7 | `'during' … 'ensure'` | Boolean guard; `not fault_detected` must hold throughout the cycle |

**FORM-L to ETL correspondence (R3 as example):**

```crml-snippet
// FORM-L (authored form):
'during' cell_charging 'ensure' (cell_temperature < (45 * degC))

// ETL expansion:
Periods P is [cell_charging 'becomes true', cell_charging 'becomes false'];
Boolean R3_etl is P 'check count' ((cell_temperature >= (45 * degC)) 'becomes true' 'inside' P) '==' 0;
```

The FORM-L form is directly equivalent but far more readable and requires no manual construction of `Periods` or `Clock` values.
