# Kinetic Plugin SDK — API v1

Kinetic's plugin contract is defined in `app/src/main/java/com/szkinetic/PluginApi.kt`.

## Compatibility

- Target `KINETIC_PLUGIN_API_VERSION = 1`.
- Declare a stable reverse-DNS-style plugin id (for example `dev.example.timeline-tools`).
- Use only the public types in `PluginApi.kt`; do not access renderer arrays, Compose state, private model fields, or internal storage.
- A plugin with an unsupported API version is rejected.
- Plugin callbacks execute in the host app process. Treat plugin code as trusted code; API permissions are capability declarations, not an OS sandbox.

## Plugin lifecycle

Implement `KineticPlugin`:

1. `onLoad(host)`: register actions and effect recipes once.
2. `onEnable(context)`: acquire runtime resources.
3. `onDisable()`: stop work and release runtime resources.
4. `onUnload()`: final cleanup.

Declare every capability in `PluginManifest.permissions`. The host rejects registrations that request undeclared capabilities.

## Example

```kotlin
package dev.example

import com.szkinetic.*

class TimelineToolsPlugin : KineticPlugin {
    override val manifest = PluginManifest(
        id = "dev.example.timeline-tools",
        name = "Timeline Tools",
        version = "1.0.0",
        author = "Example Studio",
        description = "Adds a few workflow shortcuts.",
        permissions = setOf(
            PluginPermission.LAYER_CREATE,
            PluginPermission.UI_ACTIONS,
            PluginPermission.EFFECT_APPLY
        )
    )

    override fun onLoad(host: KineticPluginHost) {
        host.registerAction(
            PluginAction(
                id = "controller",
                title = "Create Controller",
                description = "Creates a null layer."
            ) {
                addNull("Controller")
            }
        )
        host.registerEffect(
            PluginEffect(
                id = "cinematic-contrast",
                name = "Cinematic Contrast",
                description = "A reusable contrast effect recipe.",
                renderEffectId = 2,
                defaultAmount = 0.72f
            )
        )
    }
}
```

The receiver inside `PluginAction.execute` is `PluginContext`. Use its methods rather than reaching into Kinetic internals. For example: `addNull`, `addAdjustmentLayer`, `addSolid`, and `applyEffect`.

## Effect recipes

API v1 effect recipes map to one of the host's implemented GPU effects (IDs 1–16). They provide a plugin-owned name, description, and default strength. They do **not** inject arbitrary GLSL or run custom GPU programs. A later renderer API can add versioned shader/effect processors without breaking this contract.

## Current distribution boundary

The API and bundled example plugin are implemented. The current app does not yet discover or install third-party APK/DEX packages. External package signing, isolated loading, dependency resolution, permission prompts, and a distributable standalone SDK artifact are separate work and must be completed before users can install untrusted plugins from the internet.
