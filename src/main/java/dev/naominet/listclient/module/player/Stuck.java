package dev.naominet.listclient.module.player;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPostUpdate;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.eventBus.events.EventPreTick;
import dev.naominet.listclient.eventBus.events.EventWorldChange;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.TimerUtils;
import dev.naominet.listclient.value.Mode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Stuck（卡住）— 抓取"使用物品/松手"包延迟一拍重放，同时取消全部移动包。
 * <p>
 * <b>Delay</b>：取消移动包直到服务器回正包（{@link ClientboundPlayerPositionPacket}）
 * 或超时；期间玩家视角变化时补发一次 Rot，保证延迟动作打在最新朝向上。
 * <b>Packet</b>：取消移动包 + 每个移动包带 ±0.5° 的抖动朝向；每 10 tick 刷新
 * 与服务器的位置同步，关闭时发 {@code START_FALL_FLYING} 恢复服务器状态。
 * <p>
 * 两个模式关闭时都把"使用物品"动作延迟一拍重放：按住使用（如蓄力弓）时模块
 * 抓取后续的 {@code RELEASE_USE_ITEM} 并原样重放，松手时机由客户端控制。
 */
public class Stuck extends Module {
    private final Mode modeSetting = new Mode("Mode", new String[]{"Delay", "Packet"}, "Delay");

    private int stuckState = 0;
    private Packet<?> capturedPacket;
    private float savedYaw;
    private float savedPitch;
    private boolean pendingDisable = false;

    /** Delay 模式：等服务器回正包的超时，避免永久卡住。 */
    private static final long DISABLE_TIMEOUT_MS = 2000;
    private final TimerUtils disableTimer = new TimerUtils();

    public Stuck() {
        super("Stuck", Category.Player);
        addValues(modeSetting);
    }

    @Override
    public void onEnable() {
        stuckState = 0;
        capturedPacket = null;
        savedYaw = mc.player != null ? mc.player.getYRot() : 0.0F;
        savedPitch = mc.player != null ? mc.player.getXRot() : 0.0F;
        pendingDisable = false;
        disableTimer.reset();
    }

    @Override
    public void onDisable() {
        stuckState = 0;
        capturedPacket = null;
        pendingDisable = false;
    }

    @Override
    public void setEnable(boolean enable) {
        if (mc.player == null) {
            return;
        }
        if (enable) {
            super.setEnable(true);
            return;
        }
        if (modeSetting.isCurrentMode("Delay") && stuckState == 3) {
            super.setEnable(false);
        } else if (modeSetting.isCurrentMode("Delay")) {
            // 等待下一个 onPost 完成收尾（发送 1337 位移强制服务器回正）再真正关闭。
            pendingDisable = true;
        } else {
            super.setEnable(false);
        }
    }

    @EventTarget
    public void onPre(EventPlayerMotionPreUpdate event) {
        if (mc.player == null) {
            return;
        }
        if (modeSetting.isCurrentMode("Packet") && stuckState != 1) {
            // 每个移动包加一个随机的微小朝向抖动，防止服务器用重复角度检测。
            float jitterYaw = mc.player.getYRot() + (float) (Math.random() - 0.5);
            event.setYaw(jitterYaw);
        }
    }

    @EventTarget
    public void onPost(EventPlayerMotionPostUpdate event) {
        if (mc.player == null) {
            return;
        }
        // 卡住期间彻底静止。
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);

        if (stuckState == 1) {
            stuckState = 2;
            float currentYaw = mc.player.getYRot();
            float currentPitch = mc.player.getXRot();
            if (shouldSendCapturedPacket() && (savedYaw != currentYaw || savedPitch != currentPitch)) {
                mc.getConnection().send(
                        new ServerboundMovePlayerPacket.Rot(currentYaw, currentPitch, mc.player.onGround(), mc.player.horizontalCollision));
                savedYaw = currentYaw;
                savedPitch = currentPitch;
            }
            // 重放被抓取的"使用物品/松手"包。
            if (capturedPacket != null) {
                mc.getConnection().send(capturedPacket);
                capturedPacket = null;
            }
        } else if (modeSetting.isCurrentMode("Packet") && mc.player.tickCount % 10 == 0) {
            // 周期性刷新位置，避免服务器因长时间无移动包而超时。
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.position(), mc.player.onGround(), mc.player.horizontalCollision));
        }

        if (pendingDisable) {
            if (modeSetting.isCurrentMode("Delay")) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                        mc.player.getX() + 1337.0, mc.player.getY(), mc.player.getZ() + 1337.0,
                        mc.player.onGround(), mc.player.horizontalCollision));
            } else {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(
                        mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
            stuckState = 3;
            pendingDisable = false;
            super.setEnable(false);
        }
    }

    /** Delay 模式等待回正包的超时兜底：太久没收到就自己关掉。 */
    @EventTarget
    public void onTick(EventPreTick event) {
        if (!modeSetting.isCurrentMode("Delay") || stuckState == 0 || stuckState == 3) {
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (!disableTimer.hasReached(DISABLE_TIMEOUT_MS)) {
            return;
        }
        stuckState = 3;
        super.setEnable(false);
    }

    private boolean shouldSendCapturedPacket() {
        if (capturedPacket instanceof ServerboundUseItemPacket useItemPacket) {
            ItemStack heldStack = mc.player.getItemInHand(useItemPacket.getHand());
            return !heldStack.is(Items.MUSHROOM_STEW) && !heldStack.is(Items.BEETROOT_SOUP)
                    && !heldStack.is(Items.SUSPICIOUS_STEW) && !(heldStack.getItem() instanceof BowItem);
        }
        if (capturedPacket instanceof ServerboundPlayerActionPacket actionPacket) {
            return actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                    && mc.player.getUseItem().getItem() instanceof BowItem;
        }
        return false;
    }

    @EventTarget
    public void onWorldChange(EventWorldChange event) {
        stuckState = 3;
        capturedPacket = null;
        setEnable(false);
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        if (mc.player == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket) {
            // 所有移动包一律吃掉，服务器看到的玩家位置停留在启用那一刻。
            event.setCancelled(true);
        } else if (packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundPlayerActionPacket) {
            capturedPacket = packet;
            stuckState = 1;
            event.setCancelled(true);
        } else if (packet instanceof ClientboundPlayerPositionPacket && modeSetting.isCurrentMode("Delay")) {
            // 服务器回正包到达：卡住流程完成，正常关闭。
            stuckState = 3;
            setEnable(false);
        }
    }
}
