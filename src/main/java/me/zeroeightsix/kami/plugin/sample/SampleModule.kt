package me.zeroeightsix.kami.plugin.sample

import me.zeroeightsix.kami.module.Module

@Module.Info(
        name = "Sample",
        category = Module.Category.MISC,
        description = "I'm a module! :D"
)
object SampleModule : Module()
