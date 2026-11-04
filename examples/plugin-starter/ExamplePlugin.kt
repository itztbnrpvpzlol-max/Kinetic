package dev.example

import com.szkinetic.*

class ExamplePlugin : KineticPlugin {
    override val manifest = PluginManifest(
        id = "dev.example.kinetic-starter",
        name = "Kinetic Starter",
        version = "1.0.0",
        author = "Your Name",
        description = "A minimal example plugin.",
        permissions = setOf(PluginPermission.LAYER_CREATE, PluginPermission.UI_ACTIONS)
    )

    override fun onLoad(host: KineticPluginHost) {
        host.registerAction(PluginAction(
            id = "create-controller",
            title = "Create Controller",
            description = "Adds a null layer to the active composition."
        ) {
            addNull("My Controller")
        })
    }
}
