package com.cobblegenetics.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.locale.Language;

/**
 * The look of Cobblemon's Pokédex (and Fusionmon's Fusion Album), shared by the Analyzer and its guide: the white
 * Pokédex frame tinted (green on the left for genes, violet on the right for mutations), its screen, and two panels of
 * 139 × 163. Only Cobblemon textures that exist in 1.7.3; the left panel is drawn by hand like the album's list.
 */
abstract class DexScreen extends Screen {

    protected static final int BASE_WIDTH = 345;
    protected static final int BASE_HEIGHT = 207;
    protected static final int PANEL_WIDTH = 139;
    protected static final int PANEL_HEIGHT = 163;
    protected static final int BAR = 11;
    protected static final int LEFT_X = 26;
    protected static final int RIGHT_X = 180;
    protected static final int PANEL_Y = 28;
    private static final int SEAM_X = 172;

    // Pokédex screen colours
    protected static final int DARK = 0xFF3A96B6;
    protected static final int LIGHT = 0xFFEFFDFF;
    protected static final int SELECTED = 0xFF5BD1DA;
    protected static final int HOVERED = 0xFFD2F4F7;
    protected static final int TEXT_DARK = 0xFF1F4E5F;
    protected static final int TEXT_MUTED = 0xFF5E8B99;
    protected static final int WHITE = 0xFFFFFFFF;

    private static final float[] LEFT_TINT = {0.36F, 0.86F, 0.52F};
    private static final float[] RIGHT_TINT = {0.62F, 0.44F, 0.96F};

    protected static final ResourceLocation FRAME = cobblemon("pokedex_base_white");
    protected static final ResourceLocation SCREEN = cobblemon("pokedex_screen");
    protected static final ResourceLocation INFO = cobblemon("pokedex_screen_info_overlay");
    protected static final ResourceLocation CRY_BUTTON = cobblemon("button_sound");

    protected int left;
    protected int top;

    protected DexScreen(Component title) {
        super(title);
    }

    protected static ResourceLocation cobblemon(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/" + name + ".png");
    }

    @Override
    protected void init() {
        left = (width - BASE_WIDTH) / 2;
        top = (height - BASE_HEIGHT) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Only the darkening, no menu blur (as the Pokédex)
        renderTransparentBackground(graphics);
    }

    /**
     * Widgets only. Not super.render: in 1.21 Screen.render draws the darkened background again, which would dim
     * the whole Pokédex we just drew on top of it.
     */
    protected void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    protected void renderFrame(GuiGraphics graphics) {
        tintedFrame(graphics, left, left + SEAM_X, LEFT_TINT);
        tintedFrame(graphics, left + SEAM_X, left + BASE_WIDTH, RIGHT_TINT);
        graphics.blit(SCREEN, left, top, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        graphics.drawCenteredString(font, title, left + BASE_WIDTH / 2, top + 15, WHITE);
    }

    private void tintedFrame(GuiGraphics graphics, int fromX, int toX, float[] tint) {
        graphics.enableScissor(fromX, top, toX, top + BASE_HEIGHT);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1F);
        graphics.blit(FRAME, left, top, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        graphics.disableScissor();
    }

    /** Hand-drawn panel: dark bar, light body, dark bar (the album's list panel). */
    protected void plainPanel(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + PANEL_WIDTH, y + BAR, DARK);
        graphics.fill(x, y + BAR, x + PANEL_WIDTH, y + PANEL_HEIGHT - BAR, LIGHT);
        graphics.fill(x, y + PANEL_HEIGHT - BAR, x + PANEL_WIDTH, y + PANEL_HEIGHT, DARK);
    }

    /** Cuts a text to a width with "…". */
    protected static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, width - font.width("…"));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, Component.literal("…")));
    }

    /** Colours the server picked for a dark background, made readable on the light Pokédex screen. */
    protected static int onLight(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.62F);
        int g = (int) (((color >> 8) & 0xFF) * 0.62F);
        int b = (int) ((color & 0xFF) * 0.62F);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A text button drawn on a dark bar: hover highlight, returns its width. */
    protected int barButton(GuiGraphics graphics, Component text, int x, int y, int mouseX, int mouseY, boolean active) {
        int w = font.width(text) + 8;
        boolean hovered = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BAR;
        if (hovered) {
            graphics.fill(x, y, x + w, y + BAR, 0x40FFFFFF);
        }
        graphics.drawString(font, text, x + 4, y + 2, active ? WHITE : 0xFF9CC9D6, false);
        return w;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
