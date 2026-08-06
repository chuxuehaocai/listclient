package dev.naominet.listclient.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aim + raytrace helpers aligned with how Grim re-traces flying look vectors
 * for Reach and RotationPlace.
 */
public final class RotationUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    public static final double VANILLA_ENTITY_REACH = 3.0;
    public static final double VANILLA_BLOCK_REACH = 4.5;

    private RotationUtil() {
    }

    public static float[] rotationTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F)};
    }

    /**
     * Aim at a stable body point (no per-tick random) so GCD stepping can converge.
     */
    public static float[] rotationToEntity(Entity entity) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return new float[]{0.0F, 0.0F};
        }
        Vec3 eye = player.getEyePosition(1.0F);
        AABB box = entity.getBoundingBox();
        // Prefer chest height; clamp into the box so the ray lands inside the hitbox.
        double aimY = Mth.clamp(box.minY + entity.getBbHeight() * 0.6, box.minY + 0.1, box.maxY - 0.05);
        // If the eye is already within the vertical span, aim straight (less pitch error).
        if (eye.y > box.minY && eye.y < box.maxY) {
            aimY = eye.y;
        }
        Vec3 aim = new Vec3(
                (box.minX + box.maxX) * 0.5,
                aimY,
                (box.minZ + box.maxZ) * 0.5
        );
        // Pull aim toward the closest surface point so edge-on targets still hit.
        Vec3 closest = closestPoint(eye, box);
        if (eye.distanceToSqr(closest) + 1.0E-4 < eye.distanceToSqr(aim)) {
            aim = closest;
        }
        return rotationTo(eye, aim);
    }

    public static float[] rotationToBlockFace(BlockPos pos, Direction face) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return new float[]{0.0F, 0.0F};
        }
        return rotationTo(player.getEyePosition(1.0F), faceHitVec(pos, face));
    }

    /**
     * Deterministic face centre (no random). Random hit vecs make GCD stepping
     * chase a moving target and never satisfy RotationPlace.
     */
    public static Vec3 faceHitVec(BlockPos pos, Direction face) {
        return new Vec3(
                pos.getX() + 0.5 + face.getStepX() * 0.5,
                pos.getY() + 0.5 + face.getStepY() * 0.5,
                pos.getZ() + 0.5 + face.getStepZ() * 0.5
        );
    }

    public static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    public static double eyeDistanceToEntity(Entity entity) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return Double.MAX_VALUE;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        return eye.distanceTo(closestPoint(eye, entity.getBoundingBox()));
    }

    public static boolean canHitEntity(Entity entity, float yaw, float pitch, double reach) {
        LocalPlayer player = mc.player;
        if (player == null || entity == null || mc.level == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        // Hard distance gate first — matches Grim's known-invalid short-circuit.
        if (eyeDistanceToEntity(entity) > reach + 0.2) {
            return false;
        }
        EntityHitResult hit = rayTraceEntity(entity, eye, yaw, pitch, reach + 0.5);
        if (hit == null || hit.getEntity() != entity) {
            return false;
        }
        return eye.distanceTo(hit.getLocation()) <= reach + 1.0E-3;
    }

    public static EntityHitResult rayTraceEntity(Entity target, Vec3 eye, float yaw, float pitch, double range) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        // directionFromRotation(pitch, yaw)
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = eye.add(look.scale(range));
        AABB search = target.getBoundingBox().inflate(0.3).minmax(player.getBoundingBox()).inflate(1.0);
        // Expand search along the ray.
        search = search.minmax(new AABB(eye, end)).inflate(0.5);
        return ProjectileUtil.getEntityHitResult(
                player, eye, end, search,
                entity -> entity == target && !entity.isSpectator() && entity.isPickable(),
                range * range
        );
    }

    public static boolean canRayTraceBlock(float yaw, float pitch, BlockPos pos, Direction face, double range) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return false;
        }
        BlockHitResult hit = rayTraceBlock(yaw, pitch, range);
        if (hit == null) {
            return false;
        }
        return hit.getBlockPos().equals(pos) && hit.getDirection() == face;
    }

    public static BlockHitResult rayTraceBlock(float yaw, float pitch, double range) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    public static float angleDiff(float a, float b) {
        return Math.abs(Mth.wrapDegrees(a - b));
    }
}
