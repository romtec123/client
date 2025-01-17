package me.zeroeightsix.kami.module.modules.combat

import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.manager.managers.CombatManager
import me.zeroeightsix.kami.manager.managers.PlayerPacketManager
import me.zeroeightsix.kami.mixin.extension.syncCurrentPlayItem
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.*
import me.zeroeightsix.kami.util.combat.CrystalUtils
import me.zeroeightsix.kami.util.math.RotationUtils
import me.zeroeightsix.kami.util.math.Vec2f
import me.zeroeightsix.kami.util.math.VectorUtils
import me.zeroeightsix.kami.util.math.VectorUtils.distanceTo
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.tileentity.TileEntityBed
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.event.listener.listener
import java.util.*
import kotlin.collections.HashMap

@CombatManager.CombatModule
@Module.Info(
        name = "BedAura",
        description = "Place bed and kills enemies",
        category = Module.Category.COMBAT,
        modulePriority = 70
)
object BedAura : Module() {
    private val ignoreSecondBaseBlock = register(Settings.b("IgnoreSecondBaseBlock", false))
    private val suicideMode = register(Settings.b("SuicideMode", false))
    private val hitDelay = register(Settings.integerBuilder("HitDelay").withValue(5).withRange(1, 10))
    private val refillDelay = register(Settings.integerBuilder("RefillDelay").withValue(2).withRange(1, 5))
    private val minDamage = register(Settings.floatBuilder("MinDamage").withValue(10f).withRange(1f, 20f))
    private val maxSelfDamage = register(Settings.floatBuilder("MaxSelfDamage").withValue(4f).withRange(1f, 10f).withVisibility { !suicideMode.value })
    private val range = register(Settings.floatBuilder("Range").withValue(5f).withRange(1f, 5f))
    private val wallRange = register(Settings.floatBuilder("WallRange").withValue(2.5f).withRange(1f, 5f))

    private val placeMap = TreeMap<Pair<Float, Float>, BlockPos>(compareByDescending { it.first }) // <<TargetDamage, SelfDamage>, BlockPos>
    private val bedMap = TreeMap<Float, BlockPos>(compareBy { it }) // <SquaredDistance, BlockPos>
    private val refillTimer = TickTimer(TimeUnit.TICKS)
    private var state = State.NONE
    private var clickPos = BlockPos(0, -6969, 0)
    private var lastRotation = Vec2f.ZERO
    private var hitTickCount = 0
    private var inactiveTicks = 0

    private enum class State {
        NONE, PLACE, EXPLODE
    }

    override fun isActive(): Boolean {
        return isEnabled && inactiveTicks <= 5
    }

    override fun onDisable() {
        state = State.NONE
        inactiveTicks = 6
    }


    init {
        listener<PacketEvent.PostSend> {
            if (!CombatManager.isOnTopPriority(this) || it.packet !is CPacketPlayer || state == State.NONE || CombatSetting.pause) return@listener
            val hand = getBedHand() ?: EnumHand.MAIN_HAND
            val facing = if (state == State.PLACE) EnumFacing.UP else WorldUtils.getHitSide(clickPos)
            val hitVecOffset = WorldUtils.getHitVecOffset(facing)
            val packet = CPacketPlayerTryUseItemOnBlock(clickPos, facing, hand, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat())
            mc.connection!!.sendPacket(packet)
            mc.player.swingArm(hand)
            state = State.NONE
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (player.dimension == 0 || !CombatManager.isOnTopPriority(BedAura) || CombatSetting.pause) {
                state = State.NONE
                resetRotation()
                inactiveTicks = 6
                return@safeListener
            }

            inactiveTicks++
            if (canRefill() && refillTimer.tick(refillDelay.value.toLong())) {
                InventoryUtils.getSlotsFullInvNoHotbar(355)?.let {
                    InventoryUtils.quickMoveSlot(it[0])
                    playerController.syncCurrentPlayItem()
                }
            }

            updatePlaceMap()
            updateBedMap()

            if (hitTickCount >= hitDelay.value) {
                bedMap.values.firstOrNull()?.let { preExplode(it) } ?: getPlacePos()?.let { prePlace(it) }
            } else {
                hitTickCount++
                getPlacePos()?.let { prePlace(it) }
            }
            if (inactiveTicks <= 5) sendRotation()
            else resetRotation()
        }
    }

    private fun canRefill(): Boolean {
        return InventoryUtils.getSlotsHotbar(0) != null
                && InventoryUtils.getSlotsNoHotbar(355) != null
    }

    private fun SafeClientEvent.updatePlaceMap() {
        val cacheMap = CombatManager.target?.let {
            val posList = VectorUtils.getBlockPosInSphere(player.getPositionEyes(1f), range.value)
            val damagePosMap = HashMap<Pair<Float, Float>, BlockPos>()
            for (pos in posList) {
                val dist = player.distanceTo(pos)
                if (WorldUtils.rayTraceTo(pos) == null && dist > wallRange.value) continue
                val topSideVec = Vec3d(pos).add(0.5, 1.0, 0.5)
                val rotation = RotationUtils.getRotationTo(topSideVec)
                val facing = EnumFacing.fromAngle(rotation.x.toDouble())
                if (!canPlaceBed(pos)) continue
                val targetDamage = CrystalUtils.calcDamage(pos.offset(facing), it)
                val selfDamage = CrystalUtils.calcDamage(pos.offset(facing), player)
                if (targetDamage < minDamage.value && (suicideMode.value || selfDamage > maxSelfDamage.value))
                    damagePosMap[Pair(targetDamage, selfDamage)] = pos
            }
            damagePosMap
        }
        placeMap.clear()
        if (cacheMap != null) placeMap.putAll(cacheMap)
    }

    private fun SafeClientEvent.canPlaceBed(pos: BlockPos): Boolean {
        if (!world.getBlockState(pos).isSideSolid(world, pos, EnumFacing.UP)) return false
        val bedPos1 = pos.up()
        val bedPos2 = getSecondBedPos(bedPos1)
        return (!ignoreSecondBaseBlock.value || world.getBlockState(bedPos2.down()).isSideSolid(world, bedPos2.down(), EnumFacing.UP))
                && !isFire(bedPos1)
                && !isFire(bedPos2)
                && world.getBlockState(bedPos1).material.isReplaceable
                && (!ignoreSecondBaseBlock.value || world.getBlockState(bedPos2).material.isReplaceable)
    }

    private fun SafeClientEvent.isFire(pos: BlockPos): Boolean {
        return world.getBlockState(pos).block == Blocks.FIRE
    }

    private fun SafeClientEvent.updateBedMap() {
        val cacheMap = CombatManager.target?.let {
            val damagePosMap = HashMap<Float, BlockPos>()
            for (tileEntity in world.loadedTileEntityList) {
                if (tileEntity !is TileEntityBed) continue
                if (!tileEntity.isHeadPiece) continue
                val dist = player.distanceTo(tileEntity.pos).toFloat()
                if (dist > range.value) continue
                if (WorldUtils.rayTraceTo(tileEntity.pos) == null && dist > wallRange.value) continue
                damagePosMap[dist] = tileEntity.pos
            }
            damagePosMap
        }
        bedMap.clear()
        if (cacheMap != null) bedMap.putAll(cacheMap)
    }

    private fun getPlacePos() = placeMap.values.firstOrNull()

    private fun prePlace(pos: BlockPos) {
        if (getExplodePos() != null || InventoryUtils.countItemAll(355) == 0) return
        if (getBedHand() == null) InventoryUtils.swapSlotToItem(355)
        preClick(pos, Vec3d(0.5, 1.0, 0.5))
        state = State.PLACE
    }

    private fun getExplodePos() = bedMap.values.firstOrNull()

    private fun preExplode(pos: BlockPos) {
        hitTickCount = 0
        preClick(pos, Vec3d(0.5, 0.0, 0.5))
        state = State.EXPLODE
    }

    private fun preClick(pos: BlockPos, hitOffset: Vec3d) {
        inactiveTicks = 0
        clickPos = pos
        lastRotation = RotationUtils.getRotationTo(Vec3d(pos).add(hitOffset))
    }

    private fun getSecondBedPos(pos: BlockPos): BlockPos {
        val vec3d = Vec3d(pos).add(0.5, 0.0, 0.5)
        val rotation = RotationUtils.getRotationTo(vec3d)
        val facing = EnumFacing.fromAngle(rotation.x.toDouble())
        return pos.offset(facing)
    }

    private fun getBedHand(): EnumHand? {
        return when (Items.BED) {
            mc.player.heldItemMainhand.item -> EnumHand.MAIN_HAND
            mc.player.heldItemOffhand.item -> EnumHand.OFF_HAND
            else -> null
        }
    }

    private fun sendRotation() {
        PlayerPacketManager.addPacket(this, PlayerPacketManager.PlayerPacket(rotating = true, rotation = lastRotation))
    }

    private fun SafeClientEvent.resetRotation() {
        lastRotation = Vec2f(RotationUtils.normalizeAngle(player.rotationYaw), player.rotationPitch)
    }
}