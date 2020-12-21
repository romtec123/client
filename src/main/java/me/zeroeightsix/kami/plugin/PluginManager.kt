package me.zeroeightsix.kami.plugin

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.util.TimerUtils
import net.minecraftforge.fml.relauncher.FMLLaunchHandler
import org.kamiblue.commons.utils.ClassUtils
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Paths

object PluginManager {
    private val pluginFiles = Paths.get(KamiMod.PLUGINS_DIRECTORY).toFile().listFiles {
        _: File, name: String -> name.endsWith(".jar")
    }

    val plugins = mutableListOf<Plugin>()

    @JvmStatic
    fun initPlugins() {
        val stopTimer = TimerUtils.StopTimer()

        if (!FMLLaunchHandler.isDeobfuscatedEnvironment() && !pluginFiles.isNullOrEmpty()) {
            val pluginUrls = arrayListOf<URL>()

            for (jarFile in pluginFiles) {
                pluginUrls.add(jarFile.toURI().toURL())
            }

            val classLoader = URLClassLoader(pluginUrls.toTypedArray(), this.javaClass.classLoader)

            ClassUtils.findClassesByClassLoader(classLoader, Plugin::class.java).forEach {
                registerPlugin(it)
            }
        } else {
            ClassUtils.findClasses("", Plugin::class.java).forEach {
                registerPlugin(it)
            }
        }

        val time = stopTimer.stop()

        KamiMod.LOG.info("${plugins.size} plugins loaded, took ${time}ms")
    }

    private fun registerPlugin(clazz: Class<out Plugin>) {
        plugins.add(clazz.newInstance())

        KamiMod.LOG.info("Registered plugin ${plugins.last().id} with plugin class name ${clazz.simpleName}.")
    }
}