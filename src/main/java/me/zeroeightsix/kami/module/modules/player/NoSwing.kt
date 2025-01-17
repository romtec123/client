package me.zeroeightsix.kami.module.modules.player

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.threads.safeListener
import org.kamiblue.event.listener.listener
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraftforge.fml.common.gameevent.TickEvent

@Module.Info(
        name = "NoSwing",
        category = Module.Category.PLAYER,
        description = "Cancels server or client swing animation"
)
object NoSwing : Module() {
    private val mode = register(Settings.e<Mode>("Mode", Mode.CLIENT))

    private enum class Mode {
        CLIENT, SERVER
    }

    init {
        listener<PacketEvent.Send> {
            if (mode.value == Mode.SERVER && it.packet is CPacketAnimation) it.cancel()
        }

        safeListener<TickEvent.ClientTickEvent> {
            player.isSwingInProgress = false
            player.swingProgressInt = 0
            player.swingProgress = 0.0f
            player.prevSwingProgress = 0.0f
        }
    }
}