package com.szkinetic

/**
 * Kinetic Plugin API v1.
 *
 * Compatibility contract: plugins target PLUGIN_API_VERSION and only use this public
 * surface. Host internals (Compose, renderer state, persistence and timeline arrays)
 * are deliberately not exposed.
 */
const val KINETIC_PLUGIN_API_VERSION = 1

enum class PluginPermission(val id: String, val label: String) {
    PROJECT_READ("project.read", "Read the active composition"),
    LAYER_CREATE("layer.create", "Create and edit layers"),
    EFFECT_APPLY("effect.apply", "Apply supported render effects"),
    UI_ACTIONS("ui.actions", "Add commands to the plugin panel")
}

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int = KINETIC_PLUGIN_API_VERSION,
    val minHostVersion: String = "0.1.0",
    val author: String = "Unknown",
    val description: String,
    val permissions: Set<PluginPermission> = emptySet()
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{2,127}"))) { "Invalid plugin id" }
        require(name.isNotBlank() && version.isNotBlank()) { "Plugin name and version are required" }
        require(apiVersion > 0) { "Invalid API version" }
    }
}

data class PluginAction(
    val id: String,
    val title: String,
    val description: String = "",
    val permission: PluginPermission = PluginPermission.UI_ACTIONS,
    val execute: PluginContext.() -> Unit
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")))
        require(title.isNotBlank())
    }
}

/**
 * An effect registration is a named, reusable recipe backed by a render effect
 * implemented by this host version. Arbitrary third-party GPU shaders are not
 * executed by API v1.
 */
data class PluginEffect(
    val id: String,
    val name: String,
    val description: String,
    val renderEffectId: Int,
    val defaultAmount: Float = 0.5f
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(renderEffectId in 1..16) { "This host does not implement that render effect" }
        require(defaultAmount.isFinite() && defaultAmount in 0f..1f)
    }
}

interface KineticPlugin {
    val manifest: PluginManifest
    fun onLoad(host: KineticPluginHost)
    fun onEnable(context: PluginContext) {}
    fun onDisable() {}
    fun onUnload() {}
}

data class CompositionInfo(
    val name: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val durationSeconds: Float,
    val layerCount: Int
)

class PluginContext internal constructor(
    private val compositionProvider: () -> Comp?,
    private val granted: Set<PluginPermission>
) {
    val activeComposition: CompositionInfo?
        get() {
            requirePermission(PluginPermission.PROJECT_READ)
            val comp = compositionProvider() ?: return null
            return CompositionInfo(comp.name, comp.w, comp.h, comp.fps, comp.dur, comp.layers.size)
        }
    fun requirePermission(permission: PluginPermission) {
        check(permission in granted) { "Plugin permission not granted: ${permission.id}" }
    }

    fun addNull(name: String = "Plugin Null") {
        requirePermission(PluginPermission.LAYER_CREATE)
        val comp = compositionProvider() ?: error("Open a composition first")
        comp.addNull()
        comp.selected().firstOrNull()?.name = name
    }

    fun addAdjustmentLayer() {
        requirePermission(PluginPermission.LAYER_CREATE)
        (compositionProvider() ?: error("Open a composition first")).addAdjustment()
    }

    fun addSolid(name: String, argb: Long) {
        requirePermission(PluginPermission.LAYER_CREATE)
        (compositionProvider() ?: error("Open a composition first")).addSolid(name, argb)
    }

    fun applyEffect(renderEffectId: Int, amount: Float = 0.5f) {
        requirePermission(PluginPermission.EFFECT_APPLY)
        require(renderEffectId in 1..16)
        val comp = compositionProvider() ?: error("Open a composition first")
        comp.applyEffect(renderEffectId)
        comp.adjustEffectAmount(amount.coerceIn(0f, 1f) - 0.5f)
    }
}

class KineticPluginHost internal constructor(
    private val pluginId: String,
    private val declaredPermissions: Set<PluginPermission>,
    private val actionSink: (String, PluginAction) -> Unit,
    private val effectSink: (String, PluginEffect) -> Unit
) {
    fun registerAction(action: PluginAction) {
        require(action.permission in declaredPermissions) {
            "Plugin manifest must declare ${action.permission.id}"
        }
        actionSink(pluginId, action)
    }

    fun registerEffect(effect: PluginEffect) {
        require(PluginPermission.EFFECT_APPLY in declaredPermissions) {
            "Plugin manifest must declare effect.apply"
        }
        effectSink(pluginId, effect)
    }
}

data class InstalledPlugin internal constructor(
    val plugin: KineticPlugin,
    var enabled: Boolean,
    val grantedPermissions: Set<PluginPermission>,
    val actions: MutableList<PluginAction> = mutableListOf(),
    val effects: MutableList<PluginEffect> = mutableListOf()
) {
    val manifest get() = plugin.manifest
}

object PluginRegistry {
    private val installed = linkedMapOf<String, InstalledPlugin>()
    private var bootstrapped = false

    @Synchronized
    fun install(plugin: KineticPlugin, grantedPermissions: Set<PluginPermission> = plugin.manifest.permissions) {
        val manifest = plugin.manifest
        require(manifest.apiVersion == KINETIC_PLUGIN_API_VERSION) {
            "Plugin requires API ${manifest.apiVersion}; host supports $KINETIC_PLUGIN_API_VERSION"
        }
        require(manifest.permissions.containsAll(grantedPermissions)) {
            "Cannot grant permissions absent from the manifest"
        }
        require(!installed.containsKey(manifest.id)) { "Plugin id already installed: ${manifest.id}" }
        val entry = InstalledPlugin(plugin, false, grantedPermissions)
        val host = KineticPluginHost(
            manifest.id, manifest.permissions,
            { id, action -> require(entry.actions.none { it.id == id }); entry.actions.add(action) },
            { id, effect -> require(entry.effects.none { it.id == id }); entry.effects.add(effect) }
        )
        plugin.onLoad(host)
        installed[manifest.id] = entry
        setEnabled(manifest.id, true)
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        val entry = installed[id] ?: return
        if (entry.enabled == enabled) return
        if (enabled) {
            entry.plugin.onEnable(PluginContext({ null }, entry.grantedPermissions))
            entry.enabled = true
        } else {
            entry.plugin.onDisable()
            entry.enabled = false
        }
    }

    @Synchronized
    fun uninstall(id: String) {
        val entry = installed.remove(id) ?: return
        if (entry.enabled) entry.plugin.onDisable()
        entry.plugin.onUnload()
    }

    @Synchronized
    fun all(): List<InstalledPlugin> = installed.values.toList()

    @Synchronized
    fun actions(): List<Pair<InstalledPlugin, PluginAction>> =
        installed.values.filter { it.enabled }.flatMap { p -> p.actions.map { p to it } }

    @Synchronized
    fun effects(): List<Pair<InstalledPlugin, PluginEffect>> =
        installed.values.filter { it.enabled }.flatMap { p -> p.effects.map { p to it } }

    fun context(comp: Comp?, permissions: Set<PluginPermission>) =
        PluginContext({ comp }, permissions)

    @Synchronized
    fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true
        install(FastLayersPlugin())
    }
}

/** Example plugin shipped with Kinetic and also used by the SDK documentation. */
class FastLayersPlugin : KineticPlugin {
    override val manifest = PluginManifest(
        id = "com.szkinetic.fastlayers",
        name = "Fast Layers",
        version = "1.0.0",
        author = "Kinetic",
        description = "Quickly create common layer types and apply a useful starter effect.",
        permissions = setOf(
            PluginPermission.LAYER_CREATE,
            PluginPermission.EFFECT_APPLY,
            PluginPermission.UI_ACTIONS
        )
    )

    override fun onLoad(host: KineticPluginHost) {
        host.registerAction(PluginAction("null", "Create Null", "Add a controller layer.") {
            addNull()
        })
        host.registerAction(PluginAction("adjustment", "Create Adjustment", "Add an adjustment layer.") {
            addAdjustmentLayer()
        })
        host.registerEffect(
            PluginEffect("quick-contrast", "Quick Contrast", "A reusable contrast treatment.", 2, 0.68f)
        )
    }
}
