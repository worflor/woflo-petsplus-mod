# Morality aspects data guide

The malevolence emotion now rises from a data-driven interplay between **vice** and
**virtue** aspects. Designers can author both trait definitions and rule weights in
datapacks without touching code. This guide summarises the new resources and fields.

## Trait definitions

Trait definitions live under `data/<namespace>/petsplus/morality/traits/*.json` and
are loaded by `MoralityAspectDefinitionLoader`.

```json
{
  "replace": true,
  "aspects": [
    {
      "id": "petsplus:vice/cruelty",
      "kind": "vice",
      "baseline": 0.0,
      "persistence": 1.0,
      "passive_drift": 0.0,
      "impressionability": 1.0,
      "synergy": {
        "petsplus:virtue/compassion": -0.1,
        "petsplus:vice/bloodlust": 0.15
      }
    },
    {
      "id": "petsplus:virtue/compassion",
      "kind": "virtue",
      "baseline": 0.6,
      "persistence": 1.0,
      "passive_drift": 0.0,
      "impressionability": 0.7
    }
  ]
}
```

| Field | Description |
| --- | --- |
| `id` | Identifier for the aspect. Vices and virtues share a single namespace. |
| `kind` | Either `"vice"` or `"virtue"`. Defaults to vice when omitted. |
| `baseline` | Starting value for the aspect. Virtues often begin above zero to reflect innate morals. |
| `persistence` | Fraction (0–1) of the current deviation from baseline retained each Minecraft day. `1.0` means changes persist indefinitely until countered. |
| `passive_drift` | Optional amount (per Minecraft day) the value nudges back toward the baseline even without new deeds. Leave at `0` for fully persistent traits. |
| `impressionability` | Multiplier applied to deltas when deeds adjust this aspect. Values below `1` make traits harder to sway. |
| `synergy` | Optional map of aspect ids to multipliers applied when this aspect changes. Positive values spread vice, negative values bolster virtues. |

> Legacy datapacks that still provide `decay_half_life` are auto-converted into a matching `persistence` value during load, so older content keeps working.

## Malevolence rules

Rules live under `data/<namespace>/petsplus/morality/malevolence_rules/*.json` and
are parsed by `MalevolenceRulesDataLoader`. Existing fields still work; the new
ones extend how each deed steers virtues and vices.

### New tag and victim fields

* `vice_weights`: map of aspect id → weight. Each resolved tag or victim entry
  adds `base * multipliers * weight` to the given vice.
* `virtue_weights`: map of aspect id → weight that may be positive (nurture a virtue)
  or negative (erode it). Negative values make later vice surges more likely.
* `virtue_requirements`: map of aspect id → `{ "min": <float>, "max": <float> }`.
  When the virtue level falls outside the range, the vice contribution is
  suppressed and the ledger stores a "struggling" charge instead of raw score.

### Example fragment

```json
"team_betrayal": {
  "base": 3.0,
  "telemetry_bias": 0.2,
  "vice_weights": {
    "petsplus:vice/betrayal": 1.2
  },
  "virtue_requirements": {
    "petsplus:virtue/loyalty": {
      "min": 0.0,
      "max": 0.55
    }
  }
}
```

### Persistence keys

The ledger serialises per-aspect values and persona data via the following pet
state keys:

* `morality_malevolence_vices`
* `morality_malevolence_virtues`
* `morality_malevolence_persona`

These fields ensure older saves upgrade cleanly while giving datapacks full
control over vice/virtue behaviour.

