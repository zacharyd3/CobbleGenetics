package com.cobblegenetics.client;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.block.GeneticIncubatorBlock;
import com.cobblegenetics.block.GeneticIncubatorBlockEntity;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws what is inside the Genetic Incubator's glass: the two parents while breeding, the egg once it is ready.
 *
 * <p>The parents are client-only display copies (a PokemonEntity that is never added to the world), drawn through
 * Minecraft's normal entity renderer so they look exactly as in the world - lighting, shiny, CobbleGenetics tints.
 * Nobody can click, battle or catch them: the real Pokémon are stored in the block entity.</p>
 */
public class IncubatorRenderer implements BlockEntityRenderer<GeneticIncubatorBlockEntity> {

    /** Room inside the glass for each parent, in blocks: two side by side in the 12/16-wide case, whose glass runs
     * from the pedestal (6/16) to the cap (1 + 8/16). */
    private static final float SLOT_WIDTH = 0.36f;
    private static final float SLOT_HEIGHT = 1.05f;
    private static final float FLOOR = 6 / 16f;

    private final Map<GeneticIncubatorBlockEntity, Shown> shown = new WeakHashMap<>();

    /** The display entities for one incubator, rebuilt when what it shows changes. */
    private record Shown(List<GeneticIncubatorBlockEntity.Display> displays, PokemonEntity[] entities) {
    }

    public IncubatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GeneticIncubatorBlockEntity incubator, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = incubator.getLevel();
        if (level == null) {
            return;
        }
        try {
            if (incubator.phase() == GeneticIncubatorBlockEntity.Phase.BREEDING
                    || incubator.phase() == GeneticIncubatorBlockEntity.Phase.WAITING) {
                renderParents(incubator, level, partialTick, pose, buffers, light);
            } else if (incubator.phase() == GeneticIncubatorBlockEntity.Phase.EGG_READY && !incubator.egg().isEmpty()) {
                renderEgg(incubator, level, partialTick, pose, buffers, light, overlay);
            }
        } catch (RuntimeException | LinkageError e) {
            CobbleGenetics.failed("drawing the inside of the Genetic Incubator", e);
        }
    }

    private void renderParents(GeneticIncubatorBlockEntity incubator, Level level, float partialTick, PoseStack pose,
                               MultiBufferSource buffers, int light) {
        PokemonEntity[] entities = entities(incubator, level);
        Direction facing = incubator.getBlockState().getValue(GeneticIncubatorBlock.FACING);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        float time = level.getGameTime() + partialTick;
        for (int i = 0; i < entities.length; i++) {
            PokemonEntity entity = entities[i];
            if (entity == null) {
                continue;
            }
            float width = Math.max(0.1f, entity.getBbWidth());
            float height = Math.max(0.1f, entity.getBbHeight());
            float scale = Math.min(1, Math.min(SLOT_WIDTH / width, SLOT_HEIGHT / height));
            float side = entities.length == 1 ? 0 : (i == 0 ? -0.19f : 0.19f);

            pose.pushPose();
            pose.translate(0.5, FLOOR, 0.5);
            // Face out of the front of the case, turned a little towards each other, swaying gently
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.translate(side, 0, 0);
            pose.mulPose(Axis.YP.rotationDegrees((i == 0 ? 20 : -20) + (float) Math.sin(time / 30 + i) * 8));
            pose.scale(scale, scale, scale);
            entity.tickCount = (int) time;
            dispatcher.render(entity, 0, 0, 0, 0, partialTick, pose, buffers, light);
            pose.popPose();
        }
    }

    private void renderEgg(GeneticIncubatorBlockEntity incubator, Level level, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light, int overlay) {
        float time = level.getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0.78 + Math.sin(time / 12) * 0.04, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2));
        // A gentle wobble, as eggs do in the games
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(time / 5) * 6));
        pose.scale(0.8f, 0.8f, 0.8f);
        Minecraft.getInstance().getItemRenderer().renderStatic(incubator.egg(), ItemDisplayContext.GROUND, light,
                overlay, pose, buffers, level, 0);
        pose.popPose();
    }

    private PokemonEntity[] entities(GeneticIncubatorBlockEntity incubator, Level level) {
        List<GeneticIncubatorBlockEntity.Display> displays = incubator.displays();
        Shown current = shown.get(incubator);
        if (current != null && current.displays().equals(displays)) {
            return current.entities();
        }
        PokemonEntity[] entities = new PokemonEntity[displays.size()];
        for (int i = 0; i < displays.size(); i++) {
            entities[i] = displayEntity(level, displays.get(i), incubator);
        }
        shown.put(incubator, new Shown(List.copyOf(displays), entities));
        return entities;
    }

    private static PokemonEntity displayEntity(Level level, GeneticIncubatorBlockEntity.Display display,
                                               GeneticIncubatorBlockEntity incubator) {
        Species species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(display.species()));
        if (species == null) {
            return null;
        }
        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(species);
        pokemon.setForcedAspects(display.aspects());
        PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
        entity.getEntityData().set(PokemonEntity.Companion.getSPECIES(), species.getResourceIdentifier().toString());
        entity.getEntityData().set(PokemonEntity.Companion.getASPECTS(), display.aspects());
        // Position only matters for lighting lookups and particles: put it where it is drawn
        var pos = incubator.getBlockPos();
        entity.setPos(pos.getX() + 0.5, pos.getY() + FLOOR, pos.getZ() + 0.5);
        entity.setNoGravity(true);
        return entity;
    }

    @Override
    public AABB getRenderBoundingBox(GeneticIncubatorBlockEntity incubator) {
        // Two blocks tall: without this the parents would vanish when the lower block leaves the screen
        return new AABB(incubator.getBlockPos()).expandTowards(0, 1, 0);
    }
}
