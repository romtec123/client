package me.zeroeightsix.kami.module.modules.combat

import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.event.events.ConnectionEvent
import me.zeroeightsix.kami.event.events.PlayerAttackEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.misc.FakePlayer
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.math.Vec2d
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.event.listener.listener
import kotlin.math.abs

@Module.Info(
        name = "AntiBot",
        description = "Avoid attacking fake players",
        category = Module.Category.COMBAT,
        alwaysListening = true
)
object AntiBot : Module() {
    private val tabList = register(Settings.b("TabList", true))
    private val ping = register(Settings.b("Ping", true))
    private val hp = register(Settings.b("HP", true))
    private val sleeping = register(Settings.b("Sleeping", false))
    private val hoverOnTop = register(Settings.b("HoverOnTop", true))
    private val ticksExists = register(Settings.integerBuilder("TicksExists").withValue(200).withRange(0, 500))

    val botSet = HashSet<EntityPlayer>()

    init {
        listener<ConnectionEvent.Disconnect> {
            botSet.clear()
        }

        listener<PlayerAttackEvent> {
            if (isEnabled && botSet.contains(it.entity)) it.cancel()
        }

        safeListener<TickEvent.ClientTickEvent> {
            val cacheSet = HashSet<EntityPlayer>()
            for (entity in world.loadedEntityList) {
                if (entity !is EntityPlayer) continue
                if (entity == player) continue
                if (!isBot(entity)) continue
                cacheSet.add(entity)
            }
            botSet.removeIf { !cacheSet.contains(it) }
            botSet.addAll(cacheSet)
        }
    }

    private fun SafeClientEvent.isBot(entity: EntityPlayer) = entity.name == player.name
            || entity.name == FakePlayer.playerName.value
            || tabList.value && connection.getPlayerInfo(entity.name) == null
            || ping.value && connection.getPlayerInfo(entity.name)?.responseTime ?: -1 <= 0
            || hp.value && entity.health !in 0f..20f
            || sleeping.value && entity.isPlayerSleeping && !entity.onGround
            || hoverOnTop.value && hoverCheck(entity)
            || entity.ticksExisted < ticksExists.value

    private fun SafeClientEvent.hoverCheck(entity: EntityPlayer): Boolean {
        val distXZ = Vec2d(entity.posX, entity.posZ).minus(player.posX, player.posZ).lengthSquared()
        return distXZ < 16 && entity.posY - player.posY > 2.0 && abs(entity.posY - entity.prevPosY) < 0.1
    }
}
