package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraft.world.GameType
import net.minecraftforge.fml.common.gameevent.TickEvent

@Module.Info(
        name = "FakeGameMode",
        description = "Fakes your current gamemode client side",
        category = Module.Category.MISC
)
object FakeGameMode : Module() {
    private val gamemode = register(Settings.e<GameMode>("Mode", GameMode.CREATIVE))

    @Suppress("UNUSED")
    private enum class GameMode(val gameType: GameType) {
        SURVIVAL(GameType.SURVIVAL),
        CREATIVE(GameType.CREATIVE),
        ADVENTURE(GameType.ADVENTURE),
        SPECTATOR(GameType.SPECTATOR)
    }

    private var prevGameMode: GameType? = null

    init {
        safeListener<TickEvent.ClientTickEvent> {
            playerController.setGameType(gamemode.value.gameType)
        }
    }

    override fun onEnable() {
        if (mc.player == null) disable()
        else prevGameMode = mc.playerController.currentGameType
    }

    override fun onDisable() {
        if (mc.player != null) prevGameMode?.let { mc.playerController.setGameType(it) }
    }
}