package dev.naominet.listclient.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.naominet.listclient.utils.DynamicImageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class MixinCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    @Final
    @Shadow
    private EquipmentAssetManager equipmentAssets;

    @Final
    @Shadow
    private HumanoidModel<AvatarRenderState> model;

    public MixinCapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    public void submit(final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final int lightCoords, final AvatarRenderState state, final float yRot, final float xRot, CallbackInfo ci) {
        if (!state.isInvisible && state.showCape) {
            PlayerSkin skin = state.skin;
            if(Minecraft.getInstance().player != null) {
                if (state.xRot == Minecraft.getInstance().player.getXRot()) {
                    if (skin.cape() == null) {
                        //load custom cape texture
                        if (!this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) {
                            poseStack.pushPose();
                            if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
                                poseStack.translate(0.0F, -0.053125F, 0.06875F);
                            }

                            submitNodeCollector.submitModel(this.model, state, poseStack, RenderTypes.entitySolid(DynamicImageUtils.registerAsDynamicImageFromClientResources("cape/cape.png", "capetexture")), lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay) null);
                            poseStack.popPose();
                        }
                    }

                }
            }
            if (skin.cape() != null) {
                if (!this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) {
                    poseStack.pushPose();
                    if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
                        poseStack.translate(0.0F, -0.053125F, 0.06875F);
                    }

                    submitNodeCollector.submitModel(this.model, state, poseStack, RenderTypes.entitySolid(skin.cape().texturePath()), lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay)null);
                    poseStack.popPose();
                }
            }
        }
        ci.cancel();
    }


    @Unique
    private boolean hasLayer(final ItemStack itemStack, final EquipmentClientInfo.LayerType layerType) {
        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && !equippable.assetId().isEmpty()) {
            EquipmentClientInfo equipmentClientInfo = this.equipmentAssets.get(equippable.assetId().get());
            return !equipmentClientInfo.getLayers(layerType).isEmpty();
        } else {
            return false;
        }
    }
}
