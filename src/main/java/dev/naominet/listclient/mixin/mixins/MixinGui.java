package dev.naominet.listclient.mixin.mixins;

import dev.naominet.listclient.ui.MainMenuScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Installs {@link MainMenuScreen} in place of the vanilla title screen.
 * <p>
 * {@code Gui#setScreen} is the single funnel for every screen change, but the
 * title screen reaches it two different ways, so both are covered here:
 * <ul>
 *   <li>callers that build one themselves ({@code buildInitialScreens},
 *       disconnect flows, "back to title" buttons) – handled by the argument swap;</li>
 *   <li>{@code setScreen(null)} while no level is loaded, which constructs
 *       {@code new TitleScreen()} internally – handled by the constructor redirect.</li>
 * </ul>
 */
@Mixin(Gui.class)
public class MixinGui {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen listclient$swapTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen && !(screen instanceof MainMenuScreen)) {
            return new MainMenuScreen();
        }
        return screen;
    }

    @Redirect(
            method = "setScreen",
            at = @At(value = "NEW", target = "()Lnet/minecraft/client/gui/screens/TitleScreen;")
    )
    private TitleScreen listclient$replaceFallbackTitleScreen() {
        return new MainMenuScreen();
    }
}
