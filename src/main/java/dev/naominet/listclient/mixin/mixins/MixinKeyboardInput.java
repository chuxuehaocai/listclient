package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.mixin.accessors.ClientInputAccessor;
import dev.naominet.listclient.module.movement.GuiMove;
import dev.naominet.listclient.utils.RotationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void fixSilentMovement(CallbackInfo ci) {
        // GuiMove 在屏幕打开时覆写输入（必须在 RotationHandler 修正之前）。
        GuiMove.applyScreenInput((ClientInput) (Object) this);

        LocalPlayer player = Minecraft.getInstance().player;
        if (!RotationHandler.hasMoveFix(player)) {
            return;
        }

        ClientInputAccessor input = (ClientInputAccessor) (Object) this;
        Vec2 moveVector = input.listclient$getMoveVector();
        float forward = moveVector.y;
        float strafe = moveVector.x;
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }

        double targetDirection = Mth.wrapDegrees(Math.toDegrees(getDirectionYaw(
                RotationHandler.getCameraYaw(), forward, strafe
        )));
        int bestForward = 0;
        int bestStrafe = 0;
        float bestDifference = Float.MAX_VALUE;

        for (int candidateForward = -1; candidateForward <= 1; candidateForward++) {
            for (int candidateStrafe = -1; candidateStrafe <= 1; candidateStrafe++) {
                if (candidateForward == 0 && candidateStrafe == 0) {
                    continue;
                }

                double candidateDirection = Mth.wrapDegrees(Math.toDegrees(getDirectionYaw(
                        RotationHandler.getMovementYaw(), candidateForward, candidateStrafe
                )));
                float difference = (float) Math.abs(targetDirection - candidateDirection);
                if (difference < bestDifference) {
                    bestDifference = difference;
                    bestForward = candidateForward;
                    bestStrafe = candidateStrafe;
                }
            }
        }

        input.listclient$setMoveVector(new Vec2(bestStrafe, bestForward).normalized());
    }

    private static double getDirectionYaw(float yaw, double forward, double strafe) {
        if (forward < 0.0) {
            yaw += 180.0F;
        }

        float strafeFactor = 1.0F;
        if (forward < 0.0) {
            strafeFactor = -0.5F;
        } else if (forward > 0.0) {
            strafeFactor = 0.5F;
        }

        if (strafe > 0.0) {
            yaw -= 90.0F * strafeFactor;
        }
        if (strafe < 0.0) {
            yaw += 90.0F * strafeFactor;
        }

        return Math.toRadians(yaw);
    }
}
