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
    val id: String,
    val displayName: String,
    val version: String,
    val kamiVersion: String,
    val description: String = "Descriptionless",
    val category: Category = Category.GENERAL,
    val authors: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val url: String = "https://github.com/kami-blue/client",
    val useReflections: Boolean = true
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

    val pluginModuleClasses = if (useReflections) ClassUtils.findClasses(this.javaClass.`package`.name, Module::class.java) else emptyList()
    val pluginCommandClasses = if (useReflections) ClassUtils.findClasses(this.javaClass.`package`.name, ClientCommand::class.java) else emptyList()

    val pluginModules = arrayListOf<Module>()
    val pluginCommands = arrayListOf<ClientCommand>()

    companion object {
        @JvmField val mc: Minecraft = Minecraft.getMinecraft()
    }
}