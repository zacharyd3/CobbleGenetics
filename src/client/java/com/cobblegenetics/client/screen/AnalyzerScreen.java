package com.cobblegenetics.client.screen;

import com.cobblegenetics.network.AnalysisPayload;
import com.cobblegenetics.network.AnalysisPayload.Row;
import com.cobblegenetics.network.AnalysisPayload.Section;
import com.cobblegenetics.network.RecordRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Genetic Analyzer report. Everything shown was decided on the server (research level filtering), this only lays it
 * out: a dark rounded panel in the spirit of Cobblemon's UI, sections with headers, label/value rows with optional
 * bars, and greyed "requires research level N" rows that tell the player what the next level unlocks.
 */
public class AnalyzerScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 12;
    private static final int BAR_HEIGHT = 3;
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 26;

    private static final int BACKGROUND = 0xF0171A21;
    private static final int BORDER = 0xFF3B8E8A;
    private static final int SECTION = 0xFF7FD6C9;

    private final AnalysisPayload payload;
    /** Laid-out lines: either a section header, a row, or a wrapped continuation. */
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private int left;
    private int top;
    private int panelHeight;

    private record Line(Component header, Row row, FormattedCharSequence wrapped, int height) {
    }

    public AnalyzerScreen(AnalysisPayload payload) {
        super(Component.translatable("gui.cobblegenetics.analyzer"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        panelHeight = Math.min(height - 20, 260);
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panelHeight) / 2;
        layout();
        int buttonY = top + panelHeight - FOOTER_HEIGHT + 4;
        if (payload.canRecord()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.cobblegenetics.record"), button -> {
                        PacketDistributor.sendToServer(new RecordRequestPayload(payload.pokemon()));
                        button.active = false;
                    }).bounds(left + 8, buttonY, 100, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("gui.cobblegenetics.record.tooltip")))
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 88, buttonY, 80, 18).build());
    }

    private void layout() {
        lines.clear();
        int valueWidth = PANEL_WIDTH - 24 - 110;
        for (Section section : payload.sections()) {
            lines.add(new Line(section.title(), null, null, 16));
            for (Row row : section.rows()) {
                List<FormattedCharSequence> wrapped = font.split(row.value(), valueWidth);
                int height = ROW_HEIGHT + (row.bar() >= 0 ? BAR_HEIGHT + 2 : 0);
                lines.add(new Line(null, row, wrapped.isEmpty() ? FormattedCharSequence.EMPTY : wrapped.get(0), height));
                for (int i = 1; i < wrapped.size(); i++) {
                    lines.add(new Line(null, null, wrapped.get(i), ROW_HEIGHT - 2));
                }
            }
            lines.add(new Line(null, null, null, 4));
        }
    }

    private int contentHeight() {
        int total = 0;
        for (Line line : lines) {
            total += line.height();
        }
        return total;
    }

    private int viewHeight() {
        return panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Panel with a 1px teal border and a slightly lighter header band
        graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + panelHeight + 1, BORDER);
        graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight, BACKGROUND);
        graphics.fill(left, top, left + PANEL_WIDTH, top + HEADER_HEIGHT - 4, 0xF0222833);

        graphics.drawString(font, payload.title(), left + 10, top + 7, 0xFFFFFFFF, false);
        graphics.drawString(font, payload.subtitle(), left + 10, top + 19, 0xFF9AA4B2, false);
        Component badge = Component.translatable("gui.cobblegenetics.research_badge", payload.researchLevel());
        int badgeWidth = font.width(badge) + 10;
        int badgeX = left + PANEL_WIDTH - badgeWidth - 8;
        graphics.fill(badgeX, top + 8, badgeX + badgeWidth, top + 20, 0xFF2F6F6B);
        graphics.drawString(font, badge, badgeX + 5, top + 10, 0xFFE6FFFB, false);

        int viewTop = top + HEADER_HEIGHT;
        int viewBottom = viewTop + viewHeight();
        graphics.enableScissor(left, viewTop, left + PANEL_WIDTH, viewBottom);
        int y = viewTop - scroll;
        for (Line line : lines) {
            if (y + line.height() >= viewTop && y <= viewBottom) {
                drawLine(graphics, line, y);
            }
            y += line.height();
        }
        graphics.disableScissor();

        int content = contentHeight();
        if (content > viewHeight()) {
            int barHeight = Math.max(12, viewHeight() * viewHeight() / content);
            int barY = viewTop + (viewHeight() - barHeight) * scroll / Math.max(1, content - viewHeight());
            graphics.fill(left + PANEL_WIDTH - 4, barY, left + PANEL_WIDTH - 2, barY + barHeight, 0x80FFFFFF);
        }
    }

    private void drawLine(GuiGraphics graphics, Line line, int y) {
        if (line.header() != null) {
            graphics.drawString(font, line.header(), left + 10, y + 5, SECTION, false);
            graphics.fill(left + 10, y + 14, left + PANEL_WIDTH - 10, y + 15, 0x403B8E8A);
            return;
        }
        int valueX = left + 12 + 110;
        if (line.row() == null) {
            if (line.wrapped() != null) {
                graphics.drawString(font, line.wrapped(), valueX, y, 0xFFCCCCCC, false);
            }
            return;
        }
        Row row = line.row();
        int labelColor = row.locked() ? 0xFF666666 : 0xFFB8C2CE;
        graphics.drawString(font, row.label(), left + 12, y + 1, labelColor, false);
        graphics.drawString(font, line.wrapped(), valueX, y + 1, row.locked() ? 0xFF777777 : row.color(), false);
        if (row.bar() >= 0) {
            int barWidth = PANEL_WIDTH - 24 - 110;
            int barY = y + ROW_HEIGHT;
            graphics.fill(valueX, barY, valueX + barWidth, barY + BAR_HEIGHT, 0xFF2A2F38);
            int filled = Math.round(barWidth * Math.max(0, Math.min(1, row.bar())));
            graphics.fill(valueX, barY, valueX + filled, barY + BAR_HEIGHT, row.color() | 0xFF000000);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight() - viewHeight());
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * 14));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
