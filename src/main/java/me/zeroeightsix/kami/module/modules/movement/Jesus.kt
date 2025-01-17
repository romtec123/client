package me.zeroeightsix.kami.module.modules.movement

import me.zeroeightsix.kami.event.events.AddCollisionBoxToListEvent
import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.mixin.extension.y
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.util.BaritoneUtils
import me.zeroeightsix.kami.util.EntityUtils
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraft.block.BlockLiquid
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityBoat
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.commons.extension.ceilToInt
import org.kamiblue.commons.extension.floorToInt
import org.kamiblue.event.listener.listener

@Module.Info(
        name = "Jesus",
        description = "Allows you to walk on water",
        category = Module.Category.MOVEMENT
)
object Jesus : Module() {
    override fun onToggle() {
        BaritoneUtils.settings?.assumeWalkOnWater?.value = isEnabled
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (isInWater(player) && !player.isSneaking) {
                player.motionY = 0.1
                if (player.ridingEntity != null && player.ridingEntity !is EntityBoat) {
                    player.ridingEntity!!.motionY = 0.3
                }
            }
        }

        listener<AddCollisionBoxToListEvent> {
            if (it.block !is BlockLiquid || it.entity !is EntityBoat || mc.player == null || mc.player.isSneaking || mc.player.fallDistance > 3) return@listener
            if ((isDrivenByPlayer(it.entity) || it.entity === mc.player)
                    && !isInWater(mc.player) && (EntityUtils.isAboveWater(mc.player, false)
                            || EntityUtils.isAboveWater(mc.player.ridingEntity, false)) && isAboveBlock(mc.player, it.pos)) {
                val axisAlignedBB = WATER_WALK_AA.offset(it.pos)
                if (it.entityBox.intersects(axisAlignedBB)) it.collidingBoxes.add(axisAlignedBB)
                it.cancel()
            }
        }

        listener<PacketEvent.Send> {
            if (it.packet is CPacketPlayer
                    && EntityUtils.isAboveWater(mc.player, true)
                    && !isInWater(mc.player)
                    && !isAboveLand(mc.player)) {
                val ticks = mc.player.ticksExisted % 2
                if (ticks == 0) it.packet.y += 0.02
            }
        }
    }

    private val WATER_WALK_AA = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.99, 1.0)

    private fun isAboveLand(entity: Entity): Boolean {
        val y = entity.posY - 0.01
        for (x in MathHelper.floor(entity.posX) until MathHelper.ceil(entity.posX)) for (z in MathHelper.floor(entity.posZ) until MathHelper.ceil(entity.posZ)) {
            val pos = BlockPos(x, MathHelper.floor(y), z)
            if (mc.world.getBlockState(pos).isFullBlock) return true
        }
        return false
    }

    private fun isAboveBlock(entity: Entity, pos: BlockPos): Boolean {
        return entity.posY >= pos.y
    }

    private fun isInWater(entity: Entity?): Boolean {
        if (entity == null) return false
        val y = (entity.posY + 0.01).floorToInt()

        for (x in entity.posX.floorToInt() until entity.posX.ceilToInt()) {
            for (z in entity.posZ.floorToInt() until entity.posZ.ceilToInt()) {
                val pos = BlockPos(x, y, z)
                if (mc.world.getBlockState(pos).block is BlockLiquid) return true
            }
        }

        return false
    }

    private fun isDrivenByPlayer(entity: Entity) = mc.player != null && entity == mc.player.ridingEntity
}