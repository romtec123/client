package me.zeroeightsix.kami.plugin

import me.zeroeightsix.kami.KamiMod
import org.kamiblue.commons.utils.ClassUtils

object PluginManager {

    val plugins = mutableListOf<Plugin>()

    @JvmStatic
    fun initPlugins() {
        ClassUtils.findClasses("", Plugin::class.java).forEach {
            plugins.add(it.newInstance())

            KamiMod.LOG.info("Registered plugin ${plugins.last().id} with plugin class name ${it.simpleName}.")
        }

        plugins.distinct()
    }
}