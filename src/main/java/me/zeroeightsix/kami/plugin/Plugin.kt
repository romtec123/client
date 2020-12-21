package me.zeroeightsix.kami.plugin

import me.zeroeightsix.kami.command.ClientCommand
import me.zeroeightsix.kami.module.Module
import net.minecraft.client.Minecraft
import org.kamiblue.commons.utils.ClassUtils

/**
 * A plugin that exists.
 *
 * @param id The plugin's ID. Required.
 * @param displayName The plugin's display name. Required.
 * @param version The plugin's version. Required.
 * @param kamiVersion The oldest version of KAMI Blue that the plugin can run on. Required.
 * @param description A short description of the plugin.
 * @param category The plugin's category. Defaults to General.
 * @param authors A list of the authors of the plugin.
 * @param dependencies Other plugins that this plugin may require.
 * @param url A link to the plugin's website.
 */
open class Plugin(
    id: String,
    displayName: String,
    version: String,
    kamiVersion: String,
    description: String = "Descriptionless",
    category: Category = Category.GENERAL,
    authors: List<String> = emptyList(),
    dependencies: List<String> = emptyList(),
    url: String = "https://github.com/kami-blue/client"
) {

    /**
     * All possible plugin categories. "General" is the default.
     * No "Hidden" category exists for plugins; that would be a dumb idea.
     */
    enum class Category(val categoryName: String) {
        CHAT("Chat"),
        COMBAT("Combat"),
        CLIENT("Client"),
        GENERAL("General"),
        MISC("Misc"),
        MOVEMENT("Movement"),
        PLAYER("Player"),
        RENDER("Render")
    }

    val pluginModuleClasses = ClassUtils.findClasses(this.javaClass.`package`.name, Module::class.java)
    val pluginCommandClasses = ClassUtils.findClasses(this.javaClass.`package`.name, ClientCommand::class.java)

    val pluginModules = ArrayList<Module>()
    val pluginCommands = ArrayList<ClientCommand>()

    companion object {
        @JvmField val mc: Minecraft = Minecraft.getMinecraft()
    }
}