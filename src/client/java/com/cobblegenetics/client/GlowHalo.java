package com.cobblegenetics.client;

import com.cobblegenetics.CobbleGenetics;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Set;

/**
 * The glow of bioluminescent Pokémon (aspect "cg-glow"), on top of the particles:
 * <ul>
 *   <li>the body is drawn with a raised block light, so it stays softly visible in the dark instead of going black;</li>
 *   <li>a faint halo: one camera-facing quad with a soft round texture, drawn additively and full bright (the same
 *   render type as spider eyes), set a little behind the Pokémon so it reads as light around it, pulsing slowly.</li>
 * </ul>
 * Additive light barely shows against a bright day and stands out at night, which is what bioluminescence should do.
 */
public final class GlowHalo {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CobbleGenetics.MOD_ID, "textures/effect/glow_halo.png");
    /** Halo colour when the mutation gives neither a glow colour nor a tint: a pale sea green. */
    private static final int DEFAULT_COLOR = 0x9effe0;
    /** Minimum block light (0–15) of a glowing body: lit, but dimmer than a torch-lit Pokémon. */
    private static final int BODY_LIGHT = 10;
    /** Halo brightness (0–1) before the pulse. Kept low: the halo should be a hint, not a lamp. */
    private static final float INTENSITY = 0.32f;

    private GlowHalo() {
    }

    public static boolean glows(Set<String> aspects) {
        return aspects != null && aspects.contains("cg-glow");
    }

    /** Packed light with the block part raised to at least BODY_LIGHT. */
    public static int bodyLight(int packedLight) {
        int block = LightTexture.block(packedLight);
        int sky = LightTexture.sky(packedLight);
        return LightTexture.pack(Math.max(block, BODY_LIGHT), sky);
    }

    /** Halo colour: the glow colour, else the first tint, else the default. Package-private for tests. */
    static int color(Set<String> aspects) {
        int tint = -1;
        for (String aspect : aspects) {
            if (aspect.length() == 14 && (aspect.startsWith("cg-glow-") || aspect.startsWith("cg-tint-"))) {
                try {
                    int rgb = Integer.parseInt(aspect.substring(8), 16);
                    if (aspect.startsWith("cg-glow-")) {
                        return rgb;
                    }
                    if (tint < 0) {
                        tint = rgb;
                    }
                } catch (NumberFormatException ignored) {
                    // a malformed aspect from a datapack: ignore it
                }
            }
        }
        return tint >= 0 ? tint : DEFAULT_COLOR;
    }

    /** Draws the halo. The pose stack is at the entity's feet (as in EntityRenderer.render). */
    public static void render(PokemonEntity entity, Set<String> aspects, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer) {
        if (entity.isInvisible()) {
            return;
        }
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        // Big enough to show around the body, whatever its shape (the bounding box already includes the scale)
        float size = Math.max(width * 2.2f, height * 1.7f);
        float time = entity.tickCount + partialTicks;
        // Slow breathing pulse, with a per-entity phase so a group does not pulse in step
        float pulse = 0.8f + 0.2f * Mth.sin(time * 0.08f + (entity.getId() % 64) * 0.7f);
        float strength = INTENSITY * pulse;

        int rgb = color(aspects);
        // Additive blending ignores alpha: the colour itself is the amount of light added
        int r = Math.round(((rgb >> 16) & 0xFF) * strength);
        int g = Math.round(((rgb >> 8) & 0xFF) * strength);
        int b = Math.round((rgb & 0xFF) * strength);

        poseStack.pushPose();
        poseStack.translate(0, height * 0.5f, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        // The camera looks along +Z in this frame: push the quad behind the Pokémon so its body sits in the light
        poseStack.translate(0, 0, width * 0.45f);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffer.getBuffer(RenderType.eyes(TEXTURE));
        float h = size * 0.5f;
        // Both windings: the eyes render type culls back faces
        vertex(consumer, pose, -h, -h, 0, 1, r, g, b);
        vertex(consumer, pose, h, -h, 1, 1, r, g, b);
        vertex(consumer, pose, h, h, 1, 0, r, g, b);
        vertex(consumer, pose, -h, h, 0, 0, r, g, b);
        vertex(consumer, pose, -h, h, 0, 0, r, g, b);
        vertex(consumer, pose, h, h, 1, 0, r, g, b);
        vertex(consumer, pose, h, -h, 1, 1, r, g, b);
        vertex(consumer, pose, -h, -h, 0, 1, r, g, b);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v,
                               int r, int g, int b) {
        consumer.addVertex(pose, x, y, 0)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }
}
