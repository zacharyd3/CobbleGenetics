package com.cobblegenetics.client;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.block.GeneticIncubatorBlock;
import com.cobblegenetics.block.GeneticIncubatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws what is inside the Genetic Incubator's glass: each parent's Poké Ball (the one it was caught in) while it waits
 * and breeds, then the egg once it is ready.
 *
 * <p>The balls stand for the Pokémon instead of their real models: Cobblemon models reach well past their hitboxes
 * (ears, wings, tails) and vary wildly in size, so no scaling keeps every species inside the case. Balls always fit,
 * read at a glance, and are cheap to draw. Nobody can click them: the real Pokémon are stored in the block entity.</p>
 */
public class IncubatorRenderer implements BlockEntityRenderer<GeneticIncubatorBlockEntity> {

    /** Top of the pedestal, in blocks (matches the block model). */
    private static final float FLOOR = 6 / 16f;
    /** How far each ball sits from the centre, across the front of the case. */
    private static final float SIDE = 0.17f;

    private final Map<String, ItemStack> balls = new HashMap<>();

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
            GeneticIncubatorBlockEntity.Phase phase = incubator.phase();
            if (phase == GeneticIncubatorBlockEntity.Phase.WAITING || phase == GeneticIncubatorBlockEntity.Phase.BREEDING) {
                renderBalls(incubator, level, partialTick, pose, buffers, light, overlay,
                        phase == GeneticIncubatorBlockEntity.Phase.BREEDING);
            } else if (phase == GeneticIncubatorBlockEntity.Phase.EGG_READY && !incubator.egg().isEmpty()) {
                renderEgg(incubator, level, partialTick, pose, buffers, light, overlay);
            }
        } catch (RuntimeException | LinkageError e) {
            CobbleGenetics.failed("drawing the inside of the Genetic Incubator", e);
        }
    }

    private void renderBalls(GeneticIncubatorBlockEntity incubator, Level level, float partialTick, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay, boolean breeding) {
        List<GeneticIncubatorBlockEntity.Display> displays = incubator.displays();
        Direction facing = incubator.getBlockState().getValue(GeneticIncubatorBlock.FACING);
        float time = level.getGameTime() + partialTick;
        for (int i = 0; i < displays.size(); i++) {
            ItemStack ball = ball(displays.get(i).ball());
            float side = displays.size() == 1 ? 0 : (i == 0 ? -SIDE : SIDE);
            // Each ball has its own rhythm so the pair doesn't move in lockstep
            float phase = time + i * 17;

            pose.pushPose();
            pose.translate(0.5, FLOOR, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.translate(side, 0.09 + Math.sin(phase / 14) * 0.015, 0);
            // Turned a little towards each other
            pose.mulPose(Axis.YP.rotationDegrees(i == 0 ? 25 : -25));
            if (breeding) {
                // The capture wobble: three quick shakes, then a pause (about every 2.5 seconds)
                float cycle = phase % 50;
                if (cycle < 18) {
                    pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(cycle / 18 * Math.PI * 3) * 18));
                }
            }
            // About 55% of the first version: Cobblemon ball models are large, and these must clear the glass
            pose.scale(0.72f, 0.72f, 0.72f);
            Minecraft.getInstance().getItemRenderer().renderStatic(ball, ItemDisplayContext.GROUND, light, overlay,
                    pose, buffers, level, i);
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

    /** The ball item by id, cached; a plain Poké Ball if that ball's mod is gone. */
    private ItemStack ball(String id) {
        return balls.computeIfAbsent(id, key -> {
            ResourceLocation location = ResourceLocation.tryParse(key);
            Item item = location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
            if (item == Items.AIR) {
                item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball"));
            }
            return new ItemStack(item);
        });
    }

    @Override
    public AABB getRenderBoundingBox(GeneticIncubatorBlockEntity incubator) {
        return new AABB(incubator.getBlockPos()).expandTowards(0, 1, 0);
    }
}
