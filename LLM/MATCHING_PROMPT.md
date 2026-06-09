# Requirement Matching Prompt

Use this prompt with Claude Code to generate a mapping JSON for each generated
CRML model. The mapping file is placed next to the `.crml` file and loaded by
the analysis harness.

---

## How to use

For each generated model, run:

```
/generate-mapping <path-to-generated.crml>
```

Or paste the prompt below manually, substituting the generated CRML content.

---

## Prompt template (SRI)

> You are a CRML requirement analyst.
>
> Below is the **reference CRML model** and a **generated CRML model**.
> The reference defines the gold-standard requirement outputs listed under
> "Reference outputs". Your task is to identify which Boolean variable in
> the generated model semantically corresponds to each reference output.
>
> **Reference outputs to match:**
> - `R1_T`  — temperature must stay between 16°C and 30°C at all times
> - `R2_T`  — if temperature exceeds limits, it must return within 60 seconds
> - `R_T`   — conjunction of R1_T and R2_T
> - `R_speed_all` — fluid velocity must not exceed 6 m/s in heat exchangers
> - `R_flow_all`  — pump flow must stay ≥ 700 t/h while pump is in service
>
> **Reference CRML** (path: `submodules/experiments/src/main/resources/models/sri/sri_ref.crml`):
> [paste or read the reference CRML here]
>
> **Generated CRML** (path: `generated/<model>/<file>.crml`):
> [paste the generated CRML here]
>
> Respond with **only** a JSON object.  Keys are the reference variable names
> above. Values are either the corresponding Boolean name from the generated
> model, or `null` if the requirement is not implemented.
>
> Example:
> ```json
> {
>   "R1_T": "R1",
>   "R2_T": "R2",
>   "R_T": null,
>   "R_speed_all": null,
>   "R_flow_all": null
> }
> ```

---

## Prompt template (Pumps)

> You are a CRML requirement analyst.
>
> Below is the **reference CRML model** and a **generated CRML model**.
> The reference defines the gold-standard requirement outputs listed under
> "Reference outputs". Your task is to identify which Boolean variable in
> the generated model semantically corresponds to each reference output.
>
> **Reference outputs to match:**
> - `pump1.R1` — while system is in operation, pump1 must not be started more than twice
> - `pump2.R1` — same as pump1.R1 but for pump2
> - `pump3.R1` — same as pump1.R1 but for pump3
> - `pump1.R2` — at least 1 hour must separate two consecutive startups of pump1
> - `pump2.R2` — same as pump1.R2 but for pump2
> - `pump3.R2` — same as pump1.R2 but for pump3
> - `pump1.R3` — while pump1 is running, its temperature must always stay below 50°C
> - `pump2.R3` — same as pump1.R3 but for pump2
> - `pump3.R3` — same as pump1.R3 but for pump3
>
> **Reference CRML** (path: `submodules/experiments/src/main/resources/models/pumps/pumps.crml`):
> [paste or read the reference CRML here]
>
> **Generated CRML** (path: `generated/<model>/<file>.crml`):
> [paste the generated CRML here]
>
> Respond with **only** a JSON object.  Keys are the reference variable names
> above. Values are either the corresponding Boolean name from the generated
> model, or `null` if the requirement is not implemented.
>
> Example:
> ```json
> {
>   "pump1.R1": "R1",
>   "pump2.R1": "R1",
>   "pump3.R1": "R1",
>   "pump1.R2": null,
>   "pump2.R2": null,
>   "pump3.R2": null,
>   "pump1.R3": null,
>   "pump2.R3": null,
>   "pump3.R3": null
> }
> ```

---

## Prompt template (Traffic)

> You are a CRML requirement analyst.
>
> Below is the **reference CRML model** and a **generated CRML model**.
> The reference defines the gold-standard requirement outputs listed under
> "Reference outputs". Your task is to identify which Boolean variable in
> the generated model semantically corresponds to each reference output.
>
> **Reference outputs to match:**
> - `req1` — after green becomes true and before yellow becomes true, red must not become true
> - `req2` — after green becomes true, green must stay active for at least 30 seconds
> - `req3` — in the 0.2-second window starting 30 seconds after green becomes true, yellow must be true at the end
>
> **Reference CRML** (path: `submodules/experiments/src/main/resources/models/traffic/reference.crml`):
> [paste or read the reference CRML here]
>
> **Generated CRML** (path: `generated/<model>/<file>.crml`):
> [paste the generated CRML here]
>
> Respond with **only** a JSON object.  Keys are the reference variable names
> above. Values are either the corresponding Boolean name from the generated
> model, or `null` if the requirement is not implemented.
>
> Example:
> ```json
> {
>   "req1": "R1",
>   "req2": null,
>   "req3": null
> }
> ```

---

## Output location

Save the result as `<same-name-as-crml-file>_mapping.json` next to the `.crml`
file, e.g.:

```
generated/claude/SRI_temp_k1_mapping.json
generated/openai/SRI_temp_k2_mapping.json
generated/claude/pumpsystem_r1_k1_mapping.json
generated/claude/traffic_r2_k3_mapping.json
```

The harness loads these automatically when running the semantic analysis cells
in `harness_demo.ipynb`.
