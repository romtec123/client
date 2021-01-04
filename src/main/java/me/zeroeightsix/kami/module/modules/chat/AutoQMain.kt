package me.zeroeightsix.kami.module.modules.chat

import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.TickTimer
import me.zeroeightsix.kami.util.TimeUnit
import me.zeroeightsix.kami.util.text.MessageSendHelper
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendServerMessage
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.text.SimpleDateFormat
import java.util.*

@Module.Info(
        name = "AutoQMain",
        description = "Automatically does '/queue main'",
        category = Module.Category.CHAT,
        showOnArray = Module.ShowOnArray.OFF
)
object AutoQMain : Module() {
    private val showWarns = register(Settings.b("ShowWarnings", true))
    private val dimensionWarning = register(Settings.b("DimensionWarning", true))
    private val autoDisable = register(Settings.b("AutoDisable", true))
    private val delay = register(Settings.integerBuilder("Delay").withValue(30).withRange(5, 120).withStep(5))

    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (!timer.tick(delay.value.toLong())) return@safeListener

            if (mc.currentServerData == null) {
                sendMessage("&l&6Error: &r&6You are in singleplayer")
                return@safeListener
            }

            if (!mc.currentServerData!!.serverIP.equals("2b2t.org", ignoreCase = true)) {
                return@safeListener
            }

            if (player.dimension != 1 && dimensionWarning.value) {
                sendMessage("&l&6Warning: &r&6You are not in the end. Not running &b/queue main&7.")
                return@safeListener
            }
            
            if (player.dimension != 1 && autoDisable.value) {
                disable()
                return@safeListener
            }

            sendQueueMain()
        }
    }

    private fun sendQueueMain() {
        val formatter = SimpleDateFormat("HH:mm:ss")
        val date = Date(System.currentTimeMillis())

        MessageSendHelper.sendChatMessage("&7Ran &b/queue main&7 at " + formatter.format(date))
        sendServerMessage("/queue main")
    }

    private fun sendMessage(message: String) {
        if (showWarns.value) MessageSendHelper.sendWarningMessage("$chatName $message")
    }
}
