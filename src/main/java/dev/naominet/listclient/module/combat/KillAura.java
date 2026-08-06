package dev.naominet.listclient.module.combat;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPostUpdate;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.utils.RotationHandler;
import dev.naominet.listclient.utils.RotationUtil;
import dev.naominet.listclient.utils.TimerUtils;
import dev.naominet.listclient.value.Mode;
import dev.naominet.listclient.value.Numbers;
import dev.naominet.listclient.value.Option;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {
    public static LivingEntity target;
    public static boolean isBlocking;
    public static boolean isAttacking;

    private final Option blocking = new Option("Autoblock", false);
    private final Option players = new Option("Players", true);
    private final Option animals = new Option("Animals", false);
    private final Option mobs = new Option("Mobs", true);
    private final Option invis = new Option("Invisibles", false);
    // 26.2 is a modern client — default to attack-strength pacing like vanilla.
    private final Option itemCooldown = new Option("ItemCooldownDelay", true);
    private final Option rayTrace = new Option("RayTrace", true);
    private final Numbers aps = new Numbers("APS", 12.0, 1.0, 20.0, 0.5);
    private final Numbers aimRange = new Numbers("AimRange", 4.0, 1.0, 6.0, 0.1);
    private final Numbers attackRange = new Numbers("AttackRange", 3.0, 1.0, 3.0, 0.05);
    private final Numbers rotationSpeed = new Numbers("RotationSpeed", 180.0, 30.0, 180.0, 1.0);
    private final Numbers fov = new Numbers("FoV", 360.0, 10.0, 360.0, 1.0);
    private final Numbers hurtTime = new Numbers("HurtTime", 10.0, 0.0, 10.0, 1.0);
    private final Mode mode = new Mode("Mode", new String[]{"Single", "Switch"}, "Single");
    private final Mode blockMode = new Mode("BlockMode", new String[]{"Hypixel", "Vanilla"}, "Vanilla",
            blocking::getValue);

    private List<LivingEntity> candidates = new ArrayList<>();
    private final TimerUtils attackTimer = new TimerUtils();
    private int switchIndex;
    private boolean ownedUseKey;
    private boolean hasAim;
    private int lastAttackTick = -1;

    public KillAura() {
        super("KillAura", Category.Combat);
        addValues(aps, aimRange, attackRange, rotationSpeed, fov, hurtTime, blocking, players,
                animals, mobs, invis, itemCooldown, rayTrace, mode, blockMode);
    }

    @Override
    public void onEnable() {
        target = null;
        candidates.clear();
        switchIndex = 0;
        attackTimer.reset();
        isAttacking = false;
        isBlocking = false;
        ownedUseKey = false;
        hasAim = false;
        lastAttackTick = -1;
    }

    @Override
    public void onDisable() {
        candidates.clear();
        target = null;
        isAttacking = false;
        hasAim = false;
        releaseBlockKey();
        isBlocking = false;
    }

    @EventTarget
    public void onPre(EventPlayerMotionPreUpdate event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        setSuffix(mode.getValue());
        candidates = loadCandidates();

        LivingEntity chosen = chooseTarget();
        if (chosen == null) {
            target = null;
            hasAim = false;
            releaseBlockKey();
            isBlocking = false;
            // Ease silent look back toward the camera so yRotLast does not hard-snap.
            float[] back = RotationHandler.stepToward(
                    mc.player.getYRot(), mc.player.getXRot(), rotationSpeed.floatValue());
            event.setYaw(back[0]);
            event.setPitch(back[1]);
            return;
        }
        target = chosen;

        float[] desired = RotationUtil.rotationToEntity(target);
        // One GCD step from the last sent rotation. Mixin must not snap again.
        float[] stepped = RotationHandler.stepToward(
                desired[0], desired[1], rotationSpeed.floatValue());
        // Only mark aim-ready when the rotation we are about to send already
        // intersects the hitbox within attack reach — same gate Grim Reach uses.
        double maxReach = Math.min(attackRange.getValue(), entityInteractionRange());
        hasAim = !rayTrace.getValue()
                || RotationUtil.canHitEntity(target, stepped[0], stepped[1], maxReach);
        event.setYaw(stepped[0]);
        event.setPitch(stepped[1]);

        if (blocking.getValue() && holdingSword() && blockMode.isCurrentMode("Vanilla")) {
            startBlockKey();
        }
    }

    @EventTarget
    public void onPost(EventPlayerMotionPostUpdate event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (target == null || !target.isAlive() || target.isRemoved() || !hasAim) {
            releaseBlockKey();
            return;
        }

        // Flying packet already left with this look.
        float yaw = RotationHandler.getSentYaw();
        float pitch = RotationHandler.getSentPitch();
        double maxReach = Math.min(attackRange.getValue(), entityInteractionRange());

        if (rayTrace.getValue()) {
            if (!RotationUtil.canHitEntity(target, yaw, pitch, maxReach)) {
                return;
            }
        } else if (RotationUtil.eyeDistanceToEntity(target) > maxReach) {
            return;
        }

        if (!shouldAttack()) {
            if (blocking.getValue() && holdingSword() && blockMode.isCurrentMode("Vanilla")) {
                startBlockKey();
            }
            return;
        }

        // One attack per game tick max — sendPosition can fire more than once.
        int tick = mc.player.tickCount;
        if (tick == lastAttackTick) {
            return;
        }

        if (blockMode.isCurrentMode("Hypixel") && blocking.getValue()) {
            releaseBlockKey();
        }

        isAttacking = true;
        try {
            // 26.2 order: ATTACK packet then swing ANIMATION (PacketOrderB 1.9+).
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            attackTimer.advance(1000.0 / Math.max(1.0, aps.getValue()));
            lastAttackTick = tick;
            if (mode.isCurrentMode("Switch") && !candidates.isEmpty()) {
                switchIndex = (switchIndex + 1) % candidates.size();
            }
        } finally {
            isAttacking = false;
        }

        if (blocking.getValue() && holdingSword()) {
            startBlockKey();
        }
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        if (mc.player == null) {
            return;
        }
        var packet = event.getPacket();
        if (packet instanceof ServerboundPlayerActionPacket p) {
            if (p.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                isBlocking = false;
            }
        } else if (packet instanceof ServerboundUseItemPacket p) {
            ItemStack hand = mc.player.getItemInHand(p.getHand());
            if (!hand.isEmpty() && hand.is(ItemTags.SWORDS)) {
                isBlocking = true;
            }
        } else if (packet instanceof ServerboundUseItemOnPacket p) {
            ItemStack hand = mc.player.getItemInHand(p.getHand());
            if (!hand.isEmpty() && hand.is(ItemTags.SWORDS)) {
                isBlocking = true;
            }
        }
    }

    private double entityInteractionRange() {
        try {
            return Math.min(mc.player.entityInteractionRange(), RotationUtil.VANILLA_ENTITY_REACH);
        } catch (Throwable ignored) {
            return RotationUtil.VANILLA_ENTITY_REACH;
        }
    }

    private boolean shouldAttack() {
        if (target instanceof LivingEntity living && living.hurtTime > hurtTime.intValue()) {
            return false;
        }
        if (itemCooldown.getValue() && mc.player.getAttackStrengthScale(0.0F) < 0.9F) {
            return false;
        }
        double intervalMillis = 1000.0 / Math.max(1.0, aps.getValue());
        return attackTimer.hasReached(intervalMillis);
    }

    private LivingEntity chooseTarget() {
        if (candidates.isEmpty()) {
            return null;
        }
        if (mode.isCurrentMode("Single")) {
            if (target != null && candidates.contains(target)) {
                return target;
            }
            return candidates.get(0);
        }
        if (switchIndex >= candidates.size()) {
            switchIndex = 0;
        }
        return candidates.get(switchIndex);
    }

    private List<LivingEntity> loadCandidates() {
        List<LivingEntity> result = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            return result;
        }
        double aim = aimRange.getValue();
        float halfFov = fov.floatValue() / 2.0F;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !qualifies(living)) {
                continue;
            }
            if (RotationUtil.eyeDistanceToEntity(living) > aim) {
                continue;
            }
            if (halfFov < 180.0F) {
                float[] rots = RotationUtil.rotationToEntity(living);
                if (RotationUtil.angleDiff(rots[0], mc.player.getYRot()) > halfFov) {
                    continue;
                }
            }
            result.add(living);
        }
        result.sort(Comparator
                .comparingDouble(RotationUtil::eyeDistanceToEntity)
                .thenComparingDouble(e -> {
                    float[] rots = RotationUtil.rotationToEntity(e);
                    return RotationUtil.angleDiff(rots[0], mc.player.getYRot());
                }));
        return result;
    }

    private boolean qualifies(LivingEntity entity) {
        if (mc.player == null || entity == mc.player) {
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof Player p && p.isSpectator()) {
            return false;
        }
        if (entity.isInvisible() && !invis.getValue()) {
            return false;
        }
        if (entity instanceof Player) {
            return players.getValue();
        }
        if (entity instanceof Villager || entity instanceof Animal) {
            return animals.getValue();
        }
        if (entity instanceof Monster) {
            return mobs.getValue();
        }
        return false;
    }

    private boolean holdingSword() {
        if (mc.player == null) {
            return false;
        }
        var stack = mc.player.getMainHandItem();
        return !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }

    private void startBlockKey() {
        if (!ownedUseKey) {
            ownedUseKey = true;
            mc.options.keyUse.setDown(true);
        }
    }

    private void releaseBlockKey() {
        if (ownedUseKey) {
            ownedUseKey = false;
            mc.options.keyUse.setDown(false);
        }
    }
}
