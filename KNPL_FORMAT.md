# KNPL — Kinetic Plugin Package

KNPL v1 is a UTF-8 JSON file saved with the `.knpl` extension. It is a declarative package, not executable code. Kinetic validates every field and only runs the built-in action types listed below.

## Minimal example

```json
{
  "format": "KNPL",
  "formatVersion": 1,
  "manifest": {
    "id": "community.example.quicktools",
    "name": "Quick Tools",
    "version": "1.0.0",
    "apiVersion": 1,
    "author": "Community creator",
    "description": "A small set of useful composition actions.",
    "permissions": ["layer.create", "effect.apply"]
  },
  "actions": [
    {
      "id": "add-null",
      "title": "Create Null",
      "description": "Adds a controller layer.",
      "type": "create_null",
      "layerName": "Community Null"
    },
    {
      "id": "add-solid",
      "title": "Create Blue Solid",
      "type": "create_solid",
      "layerName": "Blue Solid",
      "color": "#4A90E2"
    },
    {
      "id": "contrast",
      "title": "Apply Contrast",
      "type": "apply_effect",
      "effectId": 2,
      "amount": 0.68
    }
  ],
  "effects": [
    {
      "id": "quick-contrast",
      "name": "Quick Contrast",
      "description": "A reusable contrast effect.",
      "renderEffectId": 2,
      "defaultAmount": 0.68
    }
  ]
}
```

## Supported action types

- `create_null` — requires `layer.create`; optional `layerName`.
- `create_adjustment` — requires `layer.create`.
- `create_solid` — requires `layer.create`; optional `layerName`, `color` (#RRGGBB or #AARRGGBB).
- `apply_effect` — requires `effect.apply`; `effectId` must be 1–16 and `amount` is 0.0–1.0.

Each action needs a unique `id` and a non-empty `title`. Effects need unique IDs, a name, a supported `renderEffectId` (1–16), and an optional `defaultAmount` from 0.0–1.0.

The manifest permission list must include every permission required by its actions/effects. The current host requires `apiVersion: 1`.

## Security and compatibility

KNPL packages cannot include Kotlin, Java, DEX, APKs, native libraries, scripts, arbitrary shaders, network code, or filesystem code. Unsupported fields/action types are not executed. A future API may add new declarative capabilities while retaining explicit validation.
