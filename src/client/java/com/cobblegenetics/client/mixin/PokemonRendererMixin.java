package com.cobblegenetics.client.mixin;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.client.GeneticsTint;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tints Shadow and colour-mutated Pokémon in the world. Cobblemon's PosableModel already has red/green/blue
 * multipliers (it uses them for its own flashes); we multiply ours in just before the model is drawn and restore the
 * previous values right after, so Cobblemon's own effects keep working. Optional (require = 0): if a future Cobblemon
 * changes this method the Pokémon simply look normal.
 */
@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class PokemonRendererMixin {

    @Unique
    private float[] cobblegenetics$saved;

    @Inject(method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/MobRenderer;render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 0)
    private void cobblegenetics$tint(PokemonEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                                     MultiBufferSource buffer, int light, CallbackInfo ci) {
        cobblegenetics$saved = null;
        try {
            float[] tint = GeneticsTint.of(entity.getEntityData().get(PokemonEntity.Companion.getASPECTS()));
            if (tint == null) {
                return;
            }
            PosableModel model = ((PokemonRenderer) (Object) this).getModel().getPosableModel();
            if (model == null) {
                return;
            }
            cobblegenetics$saved = new float[]{model.getRed(), model.getGreen(), model.getBlue()};
            model.setRed(model.getRed() * tint[0]);
            model.setGreen(model.getGreen() * tint[1]);
            model.setBlue(model.getBlue() * tint[2]);
        } catch (RuntimeException e) {
            CobbleGenetics.failed("tinting a Pokémon", e);
        }
    }

    @Inject(method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/MobRenderer;render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void cobblegenetics$restore(PokemonEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                                        MultiBufferSource buffer, int light, CallbackInfo ci) {
        float[] saved = cobblegenetics$saved;
        cobblegenetics$saved = null;
        if (saved == null) {
            return;
        }
        PosableModel model = ((PokemonRenderer) (Object) this).getModel().getPosableModel();
        if (model != null) {
            model.setRed(saved[0]);
            model.setGreen(saved[1]);
            model.setBlue(saved[2]);
        }
    }
}
