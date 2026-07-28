package dev.naominet.listclient.module.combat;

import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventPacket;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPostUpdate;
import dev.naominet.listclient.eventBus.events.EventPlayerMotionPreUpdate;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.value.Mode;
import dev.naominet.listclient.value.Numbers;
import dev.naominet.listclient.value.Option;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {
    public static LivingEntity target;
    public static boolean isBlocking;
    public static boolean isAttacking;

    private final Numbers aps = new Numbers("APS", 13.0, 1.0, 20.0, 0.5);
    private final Numbers reach = new Numbers("Reach", 6.0, 1.0, 7.0, 0.1);
    private final Option blocking = new Option("Autoblock", true);
    private final Option players = new Option("Players", true);
    private final Option animals = new Option("Animals", true);
    private final Option mobs = new Option("Mobs", true);
    private final Option invis = new Option("Invisibles", false);
    private final Option itemCooldown = new Option("ItemCooldownDelay", false);
    private final Mode mode = new Mode("Mode", new String[]{"Single", "Switch"}, "Switch");
    private final Mode blockMode = new Mode("BlockMode", new String[]{"Hypixel", "Vanilla"}, "Vanilla");

    private List<LivingEntity> candidates = new ArrayList<>();
    private int switchIndex;
    private long lastAttackNanos;
    private boolean ownedUseKey;

    public KillAura() {
        super("KillAura", Category.Combat);
        addValues(aps, reach, blocking, players, animals, mobs, invis, itemCooldown, mode, blockMode);
    }

    @Override
    public void onEnable() {
        target = null;
        candidates.clear();
        switchIndex = 0;
        lastAttackNanos = System.nanoTime();
        isAttacking = false;
        isBlocking = false;
        ownedUseKey = false;
    }

    @Override
    public void onDisable() {
        candidates.clear();
        target = null;
        isAttacking = false;
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
            releaseBlockKey();
            isBlocking = false;
            return;
        }
        target = chosen;

        float[] rots = rotationsTo(target);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);

        if (blocking.getValue() && holdingSword()) {
            if (blockMode.isCurrentMode("Vanilla")) {
                startBlockKey();
            }
        }
    }

    @EventTarget
    public void onPost(EventPlayerMotionPostUpdate event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (target == null || !target.isAlive() || target.isRemoved()) {
            releaseBlockKey();
            return;
        }
        double reachSq = reach.getValue() * reach.getValue();
        if (mc.player.distanceToSqr(target) > reachSq) {
            return;
        }

        if (shouldAttack()) {
            if (blockMode.isCurrentMode("Hypixel") && blocking.getValue()) {
                releaseBlockKey();
            }
            isAttacking = true;
            try {
                mc.gameMode.attack(mc.player, target);
                mc.player.swing(InteractionHand.MAIN_HAND);
                lastAttackNanos = System.nanoTime();
                if (mode.isCurrentMode("Switch") && !candidates.isEmpty()) {
                    switchIndex = (switchIndex + 1) % candidates.size();
                }
            } finally {
                isAttacking = false;
            }
            if (blocking.getValue() && holdingSword()) {
                startBlockKey();
            }
        } else {
            if (blocking.getValue() && holdingSword() && blockMode.isCurrentMode("Vanilla")) {
                startBlockKey();
            }
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

    private boolean shouldAttack() {
        if (itemCooldown.getValue()) {
            return mc.player.getAttackStrengthScale(0.0F) >= 0.99F;
        }
        double apsVal = Math.max(1.0, aps.getValue());
        long intervalNanos = (long) (1_000_000_000.0 / apsVal);
        return System.nanoTime() - lastAttackNanos >= intervalNanos;
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
        // Switch mode
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
        double reachSq = reach.getValue() * reach.getValue();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!qualifies(living)) {
                continue;
            }
            if (mc.player.distanceToSqr(living) <= reachSq) {
                result.add(living);
            }
        }
        result.sort(Comparator.comparingDouble(this::angularDistance)
                .thenComparingDouble(e -> mc.player.distanceToSqr(e)));
        return result;
    }

    private boolean qualifies(LivingEntity entity) {
        if (mc.player == null) {
            return false;
        }
        if (entity == mc.player) {
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
        if (entity instanceof Villager) {
            return animals.getValue();
        }
        if (entity instanceof Animal) {
            return animals.getValue();
        }
        if (entity instanceof Monster) {
            return mobs.getValue();
        }
        return false;
    }

    private float[] rotationsTo(LivingEntity entity) {
        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox().inflate(0.1);
        double targetX = (box.minX + box.maxX) / 2.0;
        double targetY = Mth.clamp(eye.y, box.minY, box.maxY);
        double targetZ = (box.minZ + box.maxZ) / 2.0;
        double dx = targetX - eye.x;
        double dy = targetY - eye.y;
        double dz = targetZ - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, pitch};
    }

    private double angularDistance(LivingEntity entity) {
        float[] rots = rotationsTo(entity);
        float diff = rots[0] - mc.player.getYRot();
        while (diff > 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        return Math.abs(diff);
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
