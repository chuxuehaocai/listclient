package dev.naominet.listclient.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.naominet.listclient.mixin.accessors.ClientInputAccessor;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.ClickGuiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec2;

public class GuiMove extends Module {
    public static GuiMove INSTANCE;

    public GuiMove() {
        super("GuiMove", Category.Movement);
        INSTANCE = this;
    }

    /**
     * 屏幕打开时覆写输入（KeyboardInput.tick TAIL 调用）。
     * 仅在"允许移动"的屏幕上生效，其他情况不动原输入。
     */
    public static void applyScreenInput(ClientInput input) {
        if (INSTANCE == null || !INSTANCE.isEnable()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.screen() == null) {
            return;
        }
        if (!isMovementAllowed(mc)) {
            return;
        }

        boolean forward = isKeyDown(mc, mc.options.keyUp);
        boolean back = isKeyDown(mc, mc.options.keyDown);
        boolean left = isKeyDown(mc, mc.options.keyLeft);
        boolean right = isKeyDown(mc, mc.options.keyRight);
        boolean jump = isKeyDown(mc, mc.options.keyJump);
        boolean shift = isKeyDown(mc, mc.options.keyShift);

        float forwardImpulse = impulse(forward, back);
        float leftImpulse = impulse(left, right);
        ((ClientInputAccessor) input).listclient$setMoveVector(
                new Vec2(leftImpulse, forwardImpulse).normalized());
        // 原版：按住跳跃时视为疾跑（跳的同时冲出去）。
        input.keyPresses = new Input(forward, back, left, right, jump, shift, jump);
    }

    /** 哪些屏幕允许移动：自己的点击 GUI，或整理背包时的背包界面。 */
    private static boolean isMovementAllowed(Minecraft mc) {
        if (mc.gui.screen() instanceof ChatScreen) {
            return false;
        }
        if (mc.gui.screen() instanceof ClickGuiScreen) {
            return true;
        }
        if (mc.player != null && mc.player.containerMenu instanceof InventoryMenu) {
            return true;
        }
        return false;
    }

    /** 物理层轮询键位（默认键，不依赖绑定状态）。 */
    private static boolean isKeyDown(Minecraft mc, KeyMapping keyMapping) {
        return InputConstants.isKeyDown(mc.getWindow(),
                keyMapping.getDefaultKey().getValue());
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0f;
        }
        return positive ? 1.0f : -1.0f;
    }
}
