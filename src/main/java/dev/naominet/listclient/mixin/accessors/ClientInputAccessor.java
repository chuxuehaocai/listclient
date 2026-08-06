package dev.naominet.listclient.mixin.accessors;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector")
    Vec2 listclient$getMoveVector();

    @Accessor("moveVector")
    void listclient$setMoveVector(Vec2 moveVector);
}
