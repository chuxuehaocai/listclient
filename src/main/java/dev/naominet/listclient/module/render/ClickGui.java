package dev.naominet.listclient.module.render;

import com.mojang.blaze3d.platform.InputConstants;
import dev.naominet.listclient.module.Category;
import dev.naominet.listclient.module.Module;
import dev.naominet.listclient.ui.ClickGuiScreen;

/**
 * Keybind entry into {@link ClickGuiScreen} – same entry-only pattern as
 * MusicPlayer: enabling opens the screen and immediately releases the enable
 * latch so the module never shows as a permanent HUD state.
 */
public class ClickGui extends Module {

    public ClickGui() {
        super("ClickGUI", Category.Render);
        setKeyCode(InputConstants.KEY_RSHIFT);
    }

    @Override
    public void onEnable() {
        // Defer to the render thread: setEnable can arrive from the netty
        // thread (.toggle command runs inside Connection.doSendPacket) and
        // opening a screen off-thread crashes the game. Deferring also keeps
        // the opening key press from reaching the new screen.
        mc.execute(() -> {
            openScreen();
            if (isEnable()) {
                setEnable(false);
            }
        });
    }

    public void openScreen() {
        if (mc == null) return;
        if (mc.gui.screen() instanceof ClickGuiScreen) return;
        mc.gui.setScreen(new ClickGuiScreen());
    }
}
