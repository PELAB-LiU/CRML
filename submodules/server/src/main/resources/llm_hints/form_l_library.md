## FORM-L Library

The **FORM-L Library** (Formal Requirements Modelling Language) provides a vocabulary of high-level, human-readable operators for expressing time-bounded requirements in CRML.

---

### Time Period Operators

These operators construct `Period` or `Periods` values from Boolean signals and durations.

| Operator | Return type | Meaning |
|---|---|---|
| `from Clock e` | `Periods` | From `e` (inclusive) until the end of time |
| `after Clock e` | `Periods` | After `e` (exclusive) until the end of time |
| `before Clock e` | `Periods` | From the beginning of time, but before `e` (exclusive) |
| `until Clock e` | `Periods` | From the beginning of time until `e` (inclusive) |
| `during Boolean b` | `Periods` | Each interval while `b` is continuously `true` |
| `after Clock e1 before Clock e2` | `Periods` | A combination of `after` and `before`, end is exclusive
| `after Clock e1 until Clock e2` | `Periods` | A combination of `after` and `until`, end is inclusive
| `after Clock e for Real d` | `Periods` | After `e` for the duration of `d`, end is inclusive
| `after Clock e within Real d` | `Periods` | After `e` for the duration of `d`, end is exclusive
| `from Clock e1 before Clock e2` | `Periods` | A combination of `from` and `before`, end is exclusive
| `from Clock e1 until Clock e2` | `Periods` | A combination of `from` and `until`, end is inclusive
| `from Clock e for Real d` | `Periods` | From `e` for the duration of `d`, end is inclusive
| `from Clock e within Real d` | `Periods` | From `e` for the duration of `d`, end is exclusive
| `when Clock e` | `Periods` | Alias for `during b`; emphasises point-in-time conditions |

```crml
model FORML_OperatorDefinitions is {

    Boolean b  is external;
    Boolean b1 is external;
    Boolean b2 is external;
    Integer d  is external;  // duration in seconds

    // during b: each interval while b is continuously true
    Periods during_b is [ new Clock b, new Clock not b ];

    // after b for d: interval of length d starting at each rising edge of b
    Clock   b_rises       is new Clock b;
    Periods after_b_for_d is ] b_rises, b_rises + d ];

    // from b for d: interval of length d ending at each rising edge of b
    Periods from_b_for_d is [ b_rises + (- d), b_rises ];

    // from b1 until b2: each interval from b1 rising to b2 rising
    Periods from_b1_until_b2 is [ new Clock b1, new Clock b2 ];

    // when b: alias for during b
    Periods when_b is [ new Clock b, new Clock not b ];
};
```

---

### Requirement Operators

These operators combine a `Periods` value with a condition to produce a `Boolean` requirement result.

| Operator | Meaning |
|---|---|
| `Periods P 'check count' (Clock C) op n` | True iff the count of ticks of `C` inside each period satisfies the comparison `op n` |
| `Periods P 'ensure' Boolean phi` | True iff `phi` holds throughout every period (universally quantified) |
| `Periods P 'check duration' Boolean phi op d` | True iff the cumulated duration of `phi` inside each period satisfies `op d` |
| `Periods P 'check at end' Boolean phi` | True iff `phi` holds at the end of every period |
| `Periods P 'check anytime' Boolean phi` | True iff `phi` holds at some point inside every period |

All operators desugar to `check (evaluate ... over P)` from the ETL library, with the appropriate category applied for early decision-making.

---

### Example Model

The following model illustrates all major FORM-L operators applied to a district-heating circuit scenario.

**Scenario:** A district-heating pump transfers hot water during *heating seasons*. The requirements cover count limits, temperature bounds, cumulated over-temperature duration, and a post-startup recovery check.

```crml
model HeatingCircuit is {

    // ---- Units expressed as Integer constants (seconds) ----
    Integer mn is 60;
    Integer h  is 3600;

    // ---- External signals ----
    Boolean season_heating   is external;
    Boolean pump_running     is external;
    Real    pump_temperature is external;
    Real    outlet_temp      is external;
    Boolean fault_detected   is external;

    // ---- Derived clocks for rising/falling edges ----
    Clock pump_starts   is new Clock pump_running;
    Clock pump_stops    is new Clock not pump_running;
    Clock season_starts is new Clock season_heating;
    Clock season_ends   is new Clock not season_heating;

    // ---- Time period collections (FORM-L desugared) ----

    // during season_heating
    Periods during_season is [ season_starts, season_ends ];

    // during pump_running
    Periods during_pump is [ pump_starts, pump_stops ];

    // after pump_running for 30 mn: 30-minute window after each startup
    Periods after_pump_30mn is ] pump_starts, pump_starts + (30 * mn) ];

    // after pump_running for 10 mn: 10-minute warm-up window after each startup
    Periods after_pump_10mn is ] pump_starts, pump_starts + (10 * mn) ];

    // after (outlet_temp > 70 °C) for 1 h, restricted to the heating season:
    // the window opens only when the threshold is crossed during the season
    Clock   outlet_exceeds_70 is (new Clock outlet_temp > 70.0)
                                    filter (time >= during_season start)
                                       and (time <= during_season end);
    Periods R4_periods is ] outlet_exceeds_70, outlet_exceeds_70 + (1 * h) ];

    // ---- R1: During the heating season, pump started at most 5 times ----
    Clock   pump_starts_in_season is pump_starts
                                      filter (time >= during_season start)
                                         and (time <= during_season end);
    Integer pump_start_count      is card pump_starts_in_season;
    Boolean R1 is integrate (pump_start_count <= 5) on during_season;

    // ---- R2: In the 30-min window after each startup, no new startup occurs ----
    Clock   starts_in_30mn_window is pump_starts
                                      filter (time >= after_pump_30mn start)
                                         and (time <= after_pump_30mn end);
    Integer starts_in_30mn_count  is card starts_in_30mn_window;
    Boolean R2 is integrate (starts_in_30mn_count == 0) on after_pump_30mn;

    // ---- R3: While pump running, body temperature must stay below 80 °C ----
    Boolean R3 is integrate (pump_temperature < 80.0) on during_pump;

    // ---- R4: Within each 1-h post-threshold window, outlet must not exceed 70 °C ----
    // Note: the original "check duration < 5 mn" requires the 'duration' operator,
    // which is not yet supported by the checker. This uses Boolean accumulation
    // (phi + false) as an approximation: true iff the condition never occurs.
    Boolean outlet_over_70      is outlet_temp > 70.0;
    Boolean outlet_over_70_ever is outlet_over_70 + false;
    Boolean R4 is integrate (not outlet_over_70_ever) on R4_periods;

    // ---- R5: At the end of each pump run, outlet temp must be >= 55 °C ----
    Boolean outlet_ge_55        is outlet_temp >= 55.0;
    Boolean outlet_ge_55_at_end is outlet_ge_55 at new Clock during_pump end;
    Boolean R5 is integrate outlet_ge_55_at_end on during_pump;

    // ---- R6: Within 10-min startup window, outlet must reach 55 °C at least once ----
    Boolean outlet_ge_55_ever is outlet_ge_55 + false;
    Boolean R6 is integrate outlet_ge_55_ever on after_pump_10mn;

    // ---- R7: No fault must be detected during the heating season ----
    Boolean R7 is integrate (not fault_detected) on during_season;

};
```

**Key points illustrated:**

| Requirement | Operator | Notes |
|---|---|---|
| R1 | `during … check count` | Counts rising edges of `pump.running` inside each heating-season period |
| R2 | `after … for … check count` | Creates a 30-minute window after each startup; count must be zero |
| R3 | `during … ensure` | Universal check: `pump.temperature < 80°C` must hold at every instant |
| R4 | `during … after … for … check duration` | Nested period: season window → 1-hour post-threshold window; cumulated duration of violation must be < 5 min |
| R5 | `during … check at end` | Checks the condition only at the closing event of each period |
| R6 | `after … for … check anytime` | Checks that the condition is satisfied at least once within the window |
| R7 | `during … ensure` | Boolean guard; `not fault.detected` must hold throughout the season |

**FORM-L to ETL correspondence (R3 as example):**

```crml-snippet
// FORM-L (authored form):
'during' pump.running 'ensure' pump.temperature < 80*degC

// ETL expansion:
Periods P is [pump.running becomes true, pump.running becomes false];
Boolean R3_etl is check (count ((pump.temperature >= 80*degC) becomes true inside P) == 0) over P;
```

The FORM-L form is directly equivalent but far more readable and requires no manual construction of `Periods` or `Clock` values.
