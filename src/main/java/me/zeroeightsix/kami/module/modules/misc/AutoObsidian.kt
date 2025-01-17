package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.mixin.extension.rightClickDelayTimer
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.process.AutoObsidianProcess
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.BaritoneUtils
import me.zeroeightsix.kami.util.EntityUtils
import me.zeroeightsix.kami.util.EntityUtils.flooredPosition
import me.zeroeightsix.kami.util.EntityUtils.getDroppedItem
import me.zeroeightsix.kami.util.InventoryUtils
import me.zeroeightsix.kami.util.WorldUtils.isPlaceableForChest
import me.zeroeightsix.kami.util.math.RotationUtils
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendChatMessage
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraft.block.BlockShulkerBox
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.inventory.GuiShulkerBox
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.ClickType
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.commons.extension.ceilToInt
import org.kamiblue.event.listener.listener
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Module.Info(
        name = "AutoObsidian",
        category = Module.Category.MISC,
        description = "Mines ender chest automatically to fill inventory with obsidian"
)
object AutoObsidian : Module() {
    private val searchShulker = register(Settings.b("SearchShulker", false))
    private val autoRefill = register(Settings.b("AutoRefill", false))
    private val threshold = register(Settings.integerBuilder("RefillThreshold").withValue(8).withRange(1, 56).withVisibility { autoRefill.value })
    private val targetStacks = register(Settings.integerBuilder("TargetStacks").withValue(1).withRange(1, 20))
    private val delayTicks = register(Settings.integerBuilder("DelayTicks").withValue(5).withRange(0, 10))

    enum class State {
        SEARCHING, PLACING, PRE_MINING, MINING, COLLECTING, DONE
    }

    private enum class SearchingState {
        PLACING, OPENING, PRE_MINING, MINING, COLLECTING, DONE
    }

    var pathing = false
    var goal: BlockPos? = null
    var state = State.SEARCHING

    private var active = false
    private var searchingState = SearchingState.PLACING
    private var playerPos = BlockPos(0, -1, 0)
    private var placingPos = BlockPos(0, -1, 0)
    private var shulkerBoxId = 0
    private var tickCount = 0
    private var openTime = 0L

    override fun isActive(): Boolean {
        return isEnabled && active
    }

    override fun onEnable() {
        if (mc.player == null) return
        state = State.SEARCHING
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            /* Just a delay */
            if (tickCount < delayTicks.value) {
                tickCount++
                return@safeListener
            } else tickCount = 0

            updateState()
            when (state) {

                /* Searching states */
                State.SEARCHING -> {
                    if (searchShulker.value) {
                        when (searchingState) {
                            SearchingState.PLACING -> placeShulker(placingPos)
                            SearchingState.OPENING -> openShulker(placingPos)
                            SearchingState.PRE_MINING -> mineBlock(placingPos, true)
                            SearchingState.MINING -> mineBlock(placingPos, false)
                            SearchingState.COLLECTING -> collectDroppedItem(shulkerBoxId)
                            SearchingState.DONE -> {
                                /* Positions need to be updated after moving while collecting dropped shulker box */
                                val currentPos = player.flooredPosition
                                playerPos = currentPos
                                setPlacingPos()
                            }
                        }
                    } else searchingState = SearchingState.DONE
                }

                /* Main states */
                State.PLACING -> placeEnderChest(placingPos)
                State.PRE_MINING -> mineBlock(placingPos, true)
                State.MINING -> mineBlock(placingPos, false)
                State.COLLECTING -> collectDroppedItem(49)
                State.DONE -> {
                    if (!autoRefill.value) {
                        sendChatMessage("$chatName Reached target stacks, disabling.")
                        AutoObsidian.disable()
                    } else {
                        if (active) sendChatMessage("$chatName Reached target stacks, stopping.")
                        reset()
                    }
                }
            }
        }
    }

    override fun onDisable() {
        BaritoneUtils.primary?.pathingControlManager?.mostRecentInControl()?.let {
            if (it.isPresent && it.get() == AutoObsidianProcess) {
                it.get().onLostControl()
            }
        }
        reset()
    }

    private fun SafeClientEvent.updateState() {
        val currentPos = player.flooredPosition
        if (state != State.DONE && placingPos.y == -1) {
            playerPos = currentPos
            setPlacingPos()
        }

        if (!active && state != State.DONE) {
            active = true
            BaritoneUtils.primary?.pathingControlManager?.registerProcess(AutoObsidianProcess)
        }

        /* Tell baritone to get you back to position */
        if (state != State.DONE && state != State.COLLECTING && searchingState != SearchingState.COLLECTING) {
            if (currentPos.x != playerPos.x || currentPos.z != playerPos.z) {
                pathing = true
                goal = playerPos
                return
            } else {
                pathing = false
            }
        }

        /* Updates main state */
        updateMainState()

        /* Updates searching state */
        if (state == State.SEARCHING && searchingState != SearchingState.DONE) {
            updateSearchingState()
        } else if (state != State.SEARCHING) {
            searchingState = SearchingState.PLACING
        }
    }

    private fun SafeClientEvent.updateMainState() {
        val obbyCount = countObby()

        state = when {
            state == State.DONE && autoRefill.value && InventoryUtils.countItemAll(49) <= threshold.value -> {
                State.SEARCHING
            }
            state == State.COLLECTING && getDroppedItem(49, 8.0f) == null -> {
                State.DONE
            }
            state != State.DONE && world.isAirBlock(placingPos) && obbyCount >= targetStacks.value -> {
                State.COLLECTING
            }
            state == State.MINING && world.isAirBlock(placingPos) -> {
                State.PLACING
            }
            state == State.PLACING && !world.isAirBlock(placingPos) -> {
                State.PRE_MINING
            }
            state == State.SEARCHING && searchingState == SearchingState.DONE && obbyCount < targetStacks.value -> {
                State.PLACING
            }
            else -> state
        }
    }

    private fun SafeClientEvent.updateSearchingState() {
        searchingState = when {
            searchingState == SearchingState.PLACING && InventoryUtils.countItemAll(130) > 0 -> {
                SearchingState.DONE
            }
            searchingState == SearchingState.COLLECTING && getDroppedItem(shulkerBoxId, 8.0f) == null -> {
                SearchingState.DONE
            }
            searchingState == SearchingState.MINING && world.isAirBlock(placingPos) -> {
                if (InventoryUtils.countItemAll(130) > 0) {
                    SearchingState.COLLECTING
                } else { /* In case if the shulker wasn't placed due to server lag */
                    SearchingState.PLACING
                }
            }
            searchingState == SearchingState.OPENING && (InventoryUtils.countItemAll(130) >= 64 || InventoryUtils.getSlots(0, 35, 0) == null) -> {
                SearchingState.PRE_MINING
            }
            searchingState == SearchingState.PLACING && !world.isAirBlock(placingPos) -> {
                if (world.getBlockState(placingPos).block is BlockShulkerBox) {
                    SearchingState.OPENING
                } else { /* In case if the shulker wasn't placed due to server lag */
                    SearchingState.PRE_MINING
                }
            }
            else -> searchingState
        }
    }

    private fun countObby(): Int {
        val inventory = InventoryUtils.countItemAll(49)
        val dropped = EntityUtils.getDroppedItems(49, 8.0f).sumBy { it.item.count }
        return ((inventory + dropped) / 8.0f).ceilToInt() / 8
    }

    private fun setPlacingPos() {
        if (getPlacingPos().y != -1) {
            placingPos = getPlacingPos()
        } else {
            sendChatMessage("$chatName No valid position for placing shulker box / ender chest nearby, disabling.")
            mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            this.disable()
            return
        }
    }

    private fun getPlacingPos(): BlockPos {
        val pos = playerPos
        var facing = EnumFacing.NORTH
        for (i in 1..4) {
            val posOffset = pos.offset(facing)
            val posOffsetDiagonal = posOffset.offset(facing.rotateY())
            when {
                isPlaceableForChest(posOffset) -> return posOffset
                isPlaceableForChest(posOffset.up()) -> return posOffset.up()
                isPlaceableForChest(posOffsetDiagonal) -> return posOffsetDiagonal
                isPlaceableForChest(posOffsetDiagonal.up()) -> return posOffsetDiagonal.up()
                else -> facing = facing.rotateY()
            }
        }
        return BlockPos(0, -1, 0)
    }

    private fun SafeClientEvent.lookAtBlock(pos: BlockPos) {
        val vec3d = Vec3d(pos).add(0.5, 0.0, 0.5)
        val lookAt = RotationUtils.getRotationTo(vec3d)
        player.rotationYaw = lookAt.x
        player.rotationPitch = lookAt.y
    }

    /* Tasks */
    private fun SafeClientEvent.placeShulker(pos: BlockPos) {
        for (i in 219..234) {
            if (InventoryUtils.getSlotsHotbar(i) == null) {
                if (i != 234) continue else {
                    sendChatMessage("$chatName No shulker box was found in hotbar, disabling.")
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    disable()
                    return
                }
            }
            shulkerBoxId = i
            InventoryUtils.swapSlotToItem(i)
            break
        }

        if (world.getBlockState(pos).block !is BlockShulkerBox) {
            lookAtBlock(pos)
            player.isSneaking = true
            playerController.processRightClickBlock(player, world, pos.down(), EnumFacing.UP, mc.objectMouseOver.hitVec, EnumHand.MAIN_HAND)
            player.swingArm(EnumHand.MAIN_HAND)
            mc.rightClickDelayTimer = 4
        }
    }

    private fun SafeClientEvent.placeEnderChest(pos: BlockPos) {
        if (InventoryUtils.getSlotsHotbar(130) == null && InventoryUtils.getSlotsNoHotbar(130) != null) {
            InventoryUtils.moveToHotbar(130, 278)
            return
        } else if (InventoryUtils.getSlots(0, 35, 130) == null) {
            if (searchShulker.value) {
                state = State.SEARCHING
            } else {
                sendChatMessage("$chatName No ender chest was found in inventory, disabling.")
                mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                disable()
                return
            }
        }
        InventoryUtils.swapSlotToItem(130)

        lookAtBlock(pos)
        player.isSneaking = true
        playerController.processRightClickBlock(player, world, pos.down(), mc.objectMouseOver.sideHit, mc.objectMouseOver.hitVec, EnumHand.MAIN_HAND)
        player.swingArm(EnumHand.MAIN_HAND)
        mc.rightClickDelayTimer = 4
    }


    private fun SafeClientEvent.openShulker(pos: BlockPos) {
        lookAtBlock(pos)
        if (mc.currentScreen !is GuiShulkerBox) {
            /* Added a delay here so it doesn't spam right click and get you kicked */
            if (System.currentTimeMillis() >= openTime + 2000L) {
                openTime = System.currentTimeMillis()
                playerController.processRightClickBlock(player, world, pos, mc.objectMouseOver.sideHit, mc.objectMouseOver.hitVec, EnumHand.MAIN_HAND)
            }
        } else {
            /* Extra delay here to wait for the item list to be loaded */
            Executors.newSingleThreadScheduledExecutor().schedule({
                val currentContainer = player.openContainer
                var enderChestSlot = -1
                for (i in 0..26) {
                    if (currentContainer.inventory[i].item == Blocks.ENDER_CHEST) {
                        enderChestSlot = i
                    }
                }
                if (enderChestSlot != -1) {
                    playerController.windowClick(currentContainer.windowId, enderChestSlot, 0, ClickType.QUICK_MOVE, player)
                } else {
                    sendChatMessage("$chatName No ender chest was found in shulker, disabling.")
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    disable()
                }
            }, delayTicks.value * 50L, TimeUnit.MILLISECONDS)
        }
    }

    private fun SafeClientEvent.mineBlock(pos: BlockPos, pre: Boolean) {
        if (InventoryUtils.getSlotsHotbar(278) == null && InventoryUtils.getSlotsNoHotbar(278) != null) {
            InventoryUtils.moveToHotbar(278, 130)
            return
        } else if (InventoryUtils.getSlots(0, 35, 278) == null) {
            sendChatMessage("$chatName No pickaxe was found in inventory, disabling.")
            mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
            return
        }
        InventoryUtils.swapSlotToItem(278)
        lookAtBlock(pos)

        /* Packet mining lol */
        if (pre) {
            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, mc.objectMouseOver.sideHit))
            if (state != State.SEARCHING) state = State.MINING else searchingState = SearchingState.MINING
        } else {
            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, mc.objectMouseOver.sideHit))
        }

        player.swingArm(EnumHand.MAIN_HAND)
    }

    private fun collectDroppedItem(itemId: Int) {
        pathing = if (getDroppedItem(itemId, 16.0f) != null) {
            goal = getDroppedItem(itemId, 16.0f)
            true
        } else false
    }

    private fun reset() {
        active = false
        pathing = false
        searchingState = SearchingState.PLACING
        playerPos = BlockPos(0, -1, 0)
        placingPos = BlockPos(0, -1, 0)
        tickCount = 0
    }
    /* End of tasks */
}