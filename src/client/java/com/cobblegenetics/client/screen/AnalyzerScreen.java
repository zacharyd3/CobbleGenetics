package com.cobblegenetics.client.screen;

import com.cobblegenetics.network.AnalysisPayload;
import com.cobblegenetics.network.AnalysisPayload.Row;
import com.cobblegenetics.network.AnalysisPayload.Section;
import com.cobblegenetics.network.RecordRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genetic Analyzer report in the style of the Pokédex / Fusion Album. Everything shown was decided on the server
 * (research level filtering); this only lays it out.
 *
 * <p>Left panel: the report, scrolling (section bands, label + value rows with bars, and under each gene a note on
 * what it means for this Pokémon), with Record and Guide on its bottom bar. Right panel: the Pokémon in 3D on its
 * type's platform with a cry button, its breeding value on the dark bar, how it compares with the best of its species
 * the player has analysed, and the research level.</p>
 */
public class AnalyzerScreen extends DexScreen {

    // Inside the right panel (pokedex_screen_info_overlay), as the Fusion Album
    private static final int NAME_TAB = 23;
    private static final int PORTRAIT_Y = 12;
    private static final int PORTRAIT_HEIGHT = 82;
    private static final int TYPES_Y = 96;
    private static final int INFO_Y = 107;
    private static final int BOTTOM_Y = 152;
    private static final int PLATFORM_WIDTH = 85;
    private static final int PLATFORM_HEIGHT = 22;
    private static final int CRY_WIDTH = 22;
    private static final int CRY_HEIGHT = 10;

    private static final int PAD = 3;
    private static final int INNER = PANEL_WIDTH - PAD - 5;
    private static final int INDENT = 6;

    private final AnalysisPayload payload;
    private final PokemonPortrait portrait;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private int contentHeight;
    private boolean recorded;

    private enum Kind { HEADER, ROW, TEXT, BAR, GAP }

    /** A laid-out line of the report. */
    private record Line(Kind kind, Component label, FormattedCharSequence text, int color, Row row, int height) {
    }

    public AnalyzerScreen(AnalysisPayload payload) {
        super(Component.translatable("gui.cobblegenetics.analyzer"));
        this.payload = payload;
        this.portrait = PokemonPortrait.of(payload.species(), payload.aspects());
    }

    @Override
    protected void init() {
        super.init();
        layout();
        clampScroll();
    }

    private void layout() {
        lines.clear();
        for (Section section : payload.sections()) {
            lines.add(new Line(Kind.HEADER, section.title(), null, WHITE, null, 11));
            for (Row row : section.rows()) {
                int valueColor = row.locked() ? 0xFF8FA9B1 : onLight(row.color());
                int labelColor = row.locked() ? 0xFF8FA9B1 : TEXT_MUTED;
                int labelWidth = font.width(row.label());
                if (labelWidth + 6 + font.width(row.value()) <= INNER) {
                    lines.add(new Line(Kind.ROW, row.label(), row.value().getVisualOrderText(), valueColor, row, 10));
                } else {
                    lines.add(new Line(Kind.ROW, row.label(), null, labelColor, row, 10));
                    for (FormattedCharSequence part : font.split(row.value(), INNER - INDENT)) {
                        lines.add(new Line(Kind.TEXT, null, part, valueColor, null, 9));
                    }
                }
                if (row.bar() >= 0 && !row.locked()) {
                    lines.add(new Line(Kind.BAR, null, null, row.color() | 0xFF000000, row, 4));
                }
                if (!row.note().getString().isEmpty()) {
                    for (FormattedCharSequence part : font.split(row.note(), INNER - INDENT)) {
                        lines.add(new Line(Kind.TEXT, null, part, 0xFF6F98A5, null, 9));
                    }
                    lines.add(new Line(Kind.GAP, null, null, 0, null, 2));
                }
            }
            lines.add(new Line(Kind.GAP, null, null, 0, null, 3));
        }
        contentHeight = lines.stream().mapToInt(Line::height).sum();
    }

    private int viewHeight() {
        return PANEL_HEIGHT - 2 * BAR;
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - viewHeight()));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderFrame(graphics);
        renderReport(graphics, mouseX, mouseY);
        renderPokemon(graphics, mouseX, mouseY, partialTick);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderReport(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + LEFT_X;
        int y = top + PANEL_Y;
        plainPanel(graphics, x, y);
        graphics.drawString(font, Component.translatable("gui.cobblegenetics.report"), x + 4, y + 2, WHITE, false);

        int viewTop = y + BAR;
        int viewBottom = viewTop + viewHeight();
        graphics.enableScissor(x, viewTop, x + PANEL_WIDTH, viewBottom);
        int lineY = viewTop + 1 - scroll;
        for (Line line : lines) {
            if (lineY + line.height() >= viewTop && lineY <= viewBottom) {
                drawLine(graphics, line, x, lineY);
            }
            lineY += line.height();
        }
        graphics.disableScissor();

        if (contentHeight > viewHeight()) {
            int track = viewHeight();
            int thumb = Math.max(8, track * track / contentHeight);
            int thumbY = viewTop + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            graphics.fill(x + PANEL_WIDTH - 3, thumbY, x + PANEL_WIDTH - 1, thumbY + thumb, DARK);
        }

        // Bottom bar: Record (owned Pokémon only) and the guide
        int barY = y + PANEL_HEIGHT - BAR;
        if (payload.canRecord()) {
            barButton(graphics, recordText(), x, barY, mouseX, mouseY, !recorded);
        }
        Component guide = Component.translatable("gui.cobblegenetics.guide.short");
        barButton(graphics, guide, guideX(), barY, mouseX, mouseY, true);
    }

    private void drawLine(GuiGraphics graphics, Line line, int x, int y) {
        switch (line.kind()) {
            case HEADER -> {
                graphics.fill(x, y, x + PANEL_WIDTH, y + 10, SELECTED);
                graphics.drawString(font, line.label(), x + PAD + 1, y + 1, WHITE, false);
            }
            case ROW -> {
                Row row = line.row();
                int labelColor = row.locked() ? 0xFF8FA9B1 : TEXT_DARK;
                if (line.text() == null) {
                    graphics.drawString(font, fit(font, row.label(), INNER), x + PAD, y, labelColor, false);
                } else {
                    graphics.drawString(font, row.label(), x + PAD, y, labelColor, false);
                    graphics.drawString(font, line.text(), x + PAD + INNER - font.width(line.text()), y, line.color(),
                            false);
                }
            }
            case TEXT -> graphics.drawString(font, line.text(), x + PAD + INDENT, y, line.color(), false);
            case BAR -> {
                graphics.fill(x + PAD, y, x + PAD + INNER, y + 2, 0xFFC9E7EE);
                int filled = Math.round(INNER * Mth.clamp(line.row().bar(), 0, 1));
                graphics.fill(x + PAD, y, x + PAD + filled, y + 2, line.color());
            }
            case GAP -> {
            }
        }
    }

    private void renderPokemon(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + RIGHT_X;
        int y = top + PANEL_Y;
        graphics.blit(INFO, x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);

        // Dark tab: the level; light part: the name
        graphics.drawCenteredString(font, String.valueOf(payload.level()), x + NAME_TAB / 2 + 1, y + 2, WHITE);
        graphics.drawString(font, fit(font, payload.name(), PANEL_WIDTH - NAME_TAB - 6), x + NAME_TAB + 4, y + 2,
                TEXT_DARK, false);

        // Portrait: its type's platform and the model on it
        int portraitTop = y + PORTRAIT_Y;
        int platformY = portraitTop + PORTRAIT_HEIGHT - PLATFORM_HEIGHT - 2;
        graphics.blit(cobblemon("platform_base_" + payload.primaryType().toLowerCase(Locale.ROOT)),
                x + (PANEL_WIDTH - PLATFORM_WIDTH) / 2, platformY, PLATFORM_WIDTH, PLATFORM_HEIGHT,
                0, 0, 113, 30, 113, 30);
        if (portrait != null) {
            portrait.render(graphics, x + 1, portraitTop, PANEL_WIDTH - 2, PORTRAIT_HEIGHT - PLATFORM_HEIGHT / 2,
                    partialTick);
            // In front of the model (it writes depth)
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.blit(CRY_BUTTON, cryX(), cryY(), CRY_WIDTH, CRY_HEIGHT, 0, 0, 44, 20, 44, 20);
            if (overCry(mouseX, mouseY)) {
                graphics.fill(cryX(), cryY(), cryX() + CRY_WIDTH, cryY() + CRY_HEIGHT, 0x40FFFFFF);
            }
            graphics.pose().popPose();
        }

        // Dark bar: the breeding value
        Component value = Component.translatable("analysis.cobblegenetics.breeding_value", payload.breedingValue());
        graphics.drawCenteredString(font, fit(font, value, PANEL_WIDTH - 6), x + PANEL_WIDTH / 2, y + TYPES_Y + 2,
                WHITE);

        // Light part: species and wild/owned, then the target to beat
        int infoY = y + INFO_Y + 3;
        graphics.drawCenteredString(font, fit(font, payload.subtitle(), PANEL_WIDTH - 6), x + PANEL_WIDTH / 2, infoY,
                TEXT_MUTED);
        List<FormattedCharSequence> best = font.split(payload.best(), PANEL_WIDTH - 8);
        int bestY = infoY + 13;
        for (int i = 0; i < Math.min(3, best.size()); i++) {
            FormattedCharSequence line = best.get(i);
            graphics.drawString(font, line, x + (PANEL_WIDTH - font.width(line)) / 2, bestY + i * 10, TEXT_DARK,
                    false);
        }

        // Bottom bar: research level
        graphics.drawCenteredString(font, Component.translatable("gui.cobblegenetics.research_level",
                payload.researchLevel()), x + PANEL_WIDTH / 2, y + BOTTOM_Y + 2, WHITE);

        if (overCry(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.cobblegenetics.cry"), mouseX, mouseY);
        } else if (mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= y + TYPES_Y && mouseY < y + TYPES_Y + BAR) {
            graphics.renderTooltip(font, font.split(Component.translatable(
                    "gui.cobblegenetics.breeding_value.tooltip"), 180), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ input

    private Component recordText() {
        return Component.translatable("gui.cobblegenetics.record");
    }

    private int guideX() {
        return left + LEFT_X + PANEL_WIDTH - font.width(Component.translatable("gui.cobblegenetics.guide.short")) - 8;
    }

    private int cryX() {
        return left + RIGHT_X + PANEL_WIDTH - CRY_WIDTH - 3;
    }

    private int cryY() {
        return top + PANEL_Y + PORTRAIT_Y + 2;
    }

    private boolean overCry(double mx, double my) {
        return portrait != null && mx >= cryX() && mx < cryX() + CRY_WIDTH && my >= cryY() && my < cryY() + CRY_HEIGHT;
    }

    private boolean inReport(double mx, double my) {
        int x = left + LEFT_X;
        int y = top + PANEL_Y + BAR;
        return mx >= x && mx < x + PANEL_WIDTH && my >= y && my < y + viewHeight();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int barY = top + PANEL_Y + PANEL_HEIGHT - BAR;
        if (button == 0 && my >= barY && my < barY + BAR) {
            int x = left + LEFT_X;
            if (payload.canRecord() && !recorded && mx >= x && mx < x + font.width(recordText()) + 8) {
                PacketDistributor.sendToServer(new RecordRequestPayload(payload.pokemon()));
                recorded = true;
                return true;
            }
            if (mx >= guideX() && mx < left + LEFT_X + PANEL_WIDTH) {
                minecraft.setScreen(new GuideScreen(this, payload.researchLevel()));
                return true;
            }
        }
        if (button == 0 && overCry(mx, my)) {
            portrait.cry();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (inReport(mx, my)) {
            scroll -= (int) (scrollY * 12);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            scroll += keyCode == GLFW.GLFW_KEY_UP ? -12 : 12;
            clampScroll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && portrait != null) {
            portrait.cry();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
