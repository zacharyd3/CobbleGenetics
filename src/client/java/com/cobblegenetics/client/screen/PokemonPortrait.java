package com.cobblegenetics.client.screen;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;

/**
 * A Pokémon model in a GUI rectangle, slowly turning, framed by its real size (measured once from its cubes) so small
 * and huge species both fill the portrait. Same approach as Fusionmon's Fusion Album; draws with Cobblemon 1.7.3's
 * drawProfilePokemon without the per-species profile offsets (they are tuned for the summary's little box).
 * With Fusionmon installed, a fusion's aspects make its graft draw as in the world.
 */
final class PokemonPortrait {

    private static final float FIT = 0.85F;
    private static final float PROFILE_SCALE = 20F;
    private static final long TURN_MILLIS = 12000;

    private final RenderablePokemon model;
    private final FloatingState state = new FloatingState();
    private Vector3f center;
    private float radius = 1;
    /** Something failed (a broken model from another mod): stop trying, the screen still works. */
    private boolean broken;

    private PokemonPortrait(RenderablePokemon model) {
        this.model = model;
        state.setCurrentAspects(model.getAspects());
    }

    /** null when the species is unknown on this client. */
    static PokemonPortrait of(String speciesId, Set<String> aspects) {
        ResourceLocation id = ResourceLocation.tryParse(speciesId);
        Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
        return species == null ? null : new PokemonPortrait(new RenderablePokemon(species, aspects, ItemStack.EMPTY));
    }

    void render(GuiGraphics graphics, int x, int y, int w, int h, float partialTick) {
        if (broken) {
            return;
        }
        try {
            if (center == null) {
                measure();
            }
            float shownYaw = 30 + (System.currentTimeMillis() % TURN_MILLIS) * 360F / TURN_MILLIS;
            Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(),
                    new Vector3f(13F, shownYaw, 0F));
            float pixels = Math.min(w, h) * FIT / (2 * radius);
            Vector3f turned = rotation.transform(new Vector3f(center));
            graphics.enableScissor(x, y, x + w, y + h);
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(x + w / 2.0, y + h / 2.0, 100);
                float scale = pixels / PROFILE_SCALE;
                pose.scale(scale, scale, scale);
                pose.translate(-turned.x * PROFILE_SCALE, -turned.y * PROFILE_SCALE, -turned.z * PROFILE_SCALE);
                PokemonGuiUtilsKt.drawProfilePokemon(model, pose, rotation, PoseType.PROFILE, state, partialTick,
                        PROFILE_SCALE, false, false, 1F, 1F, 1F, 1F, 0F, 0F);
            } finally {
                pose.popPose();
                graphics.disableScissor();
            }
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            com.cobblegenetics.CobbleGenetics.failed("drawing a Pokémon in the Analyzer", e);
        }
    }

    /** The cry, as the Pokédex button: the model's "cry" animation, whose sound keyframes play it. */
    void cry() {
        try {
            state.getActiveAnimations().clear();
            state.addFirstAnimation(Set.of("cry"));
        } catch (RuntimeException e) {
            com.cobblegenetics.CobbleGenetics.failed("the cry button in the Analyzer", e);
        }
    }

    /** Box of every cube of the model in its default pose, in blocks. */
    private void measure() {
        center = new Vector3f();
        PosableModel poser = VaryingModelRepository.INSTANCE.getPoser(model.getSpecies().getResourceIdentifier(),
                state);
        if (!((Object) poser.getRootPart() instanceof ModelPart root)) {
            return;
        }
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        root.visit(new PoseStack(), (cubePose, path, index, cube) -> {
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = cubePose.pose().transformPosition(new Vector3f(
                        ((corner & 1) == 0 ? cube.minX : cube.maxX) / 16F,
                        ((corner & 2) == 0 ? cube.minY : cube.maxY) / 16F,
                        ((corner & 4) == 0 ? cube.minZ : cube.maxZ) / 16F));
                for (int axis = 0; axis < 3; axis++) {
                    box[axis] = Math.min(box[axis], point.get(axis));
                    box[axis + 3] = Math.max(box[axis + 3], point.get(axis));
                }
            }
        });
        if (box[0] > box[3]) {
            return;
        }
        center.set((box[0] + box[3]) / 2F, (box[1] + box[4]) / 2F, (box[2] + box[5]) / 2F);
        float halfX = (box[3] - box[0]) / 2F;
        float halfY = (box[4] - box[1]) / 2F;
        float halfZ = (box[5] - box[2]) / 2F;
        radius = Math.max(0.05F, Math.max(halfY, (float) Math.sqrt(halfX * halfX + halfZ * halfZ)));
    }
}
