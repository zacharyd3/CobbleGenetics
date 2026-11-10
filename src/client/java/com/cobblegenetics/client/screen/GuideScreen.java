package com.cobblegenetics.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * "? Guide" from the Analyzer: what each gene does and why it is worth breeding for, in the same Pokédex look. Topics on
 * the left (like the Fusion Album's list), the chosen one on the right. Topics about genes the player cannot see yet
 * are listed but locked until that research level, so the guide also says what research unlocks.
 */
public class GuideScreen extends DexScreen {

    /** Topic id (lang keys guide.cobblegenetics.<id>.title/body) and the research level that unlocks it. */
    private record Topic(String id, int level) {
    }

    private static final List<Topic> TOPICS = List.of(
            new Topic("why", 1), new Topic("potential", 1), new Topic("stability", 2), new Topic("affinity", 2),
            new Topic("mutations", 2), new Topic("susceptibility", 3), new Topic("traits", 3),
            new Topic("prediction", 4), new Topic("shadow", 1), new Topic("daycare", 1));

    private static final int ROW = 11;

    private final Screen parent;
    private final int researchLevel;
    private int selected;
    private int scroll;
    private List<FormattedCharSequence> body = List.of();

    public GuideScreen(Screen parent, int researchLevel) {
        super(Component.translatable("gui.cobblegenetics.guide"));
        this.parent = parent;
        this.researchLevel = researchLevel;
    }

    @Override
    protected void init() {
        super.init();
        choose(selected);
    }

    private boolean unlocked(Topic topic) {
        return researchLevel >= topic.level();
    }

    private Component title(Topic topic) {
        return Component.translatable("guide.cobblegenetics." + topic.id() + ".title");
    }

    private void choose(int index) {
        selected = Mth.clamp(index, 0, TOPICS.size() - 1);
        scroll = 0;
        Topic topic = TOPICS.get(selected);
        Component text = unlocked(topic) ? Component.translatable("guide.cobblegenetics." + topic.id() + ".body")
                : Component.translatable("gui.cobblegenetics.guide.locked", topic.level());
        // Narrower than the panel: room for the margin and the scrollbar on the right
        body = font.split(text, PANEL_WIDTH - 14);
    }

    private int viewHeight() {
        return PANEL_HEIGHT - 2 * BAR - 4;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderFrame(graphics);

        // Left: topics
        int x = left + LEFT_X;
        int y = top + PANEL_Y;
        plainPanel(graphics, x, y);
        graphics.drawString(font, Component.translatable("gui.cobblegenetics.guide.topics"), x + 4, y + 2, WHITE,
                false);
        for (int i = 0; i < TOPICS.size(); i++) {
            Topic topic = TOPICS.get(i);
            int rowY = y + BAR + i * ROW;
            boolean hovered = mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= rowY && mouseY < rowY + ROW;
            if (i == selected) {
                graphics.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW, SELECTED);
            } else if (hovered) {
                graphics.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW, HOVERED);
            }
            int color = i == selected ? WHITE : unlocked(topic) ? TEXT_DARK : 0xFF8FA9B1;
            Component name = unlocked(topic) ? title(topic)
                    : title(topic).copy().append(" (" + topic.level() + ")");
            graphics.drawString(font, fit(font, name, PANEL_WIDTH - 8), x + 4, rowY + 2, color, false);
        }
        barButton(graphics, Component.translatable("gui.cobblegenetics.back"), x, y + PANEL_HEIGHT - BAR, mouseX,
                mouseY, true);

        // Right: the chosen topic
        int rx = left + RIGHT_X;
        plainPanel(graphics, rx, y);
        graphics.drawString(font, fit(font, title(TOPICS.get(selected)), PANEL_WIDTH - 8), rx + 4, y + 2, WHITE, false);
        int viewTop = y + BAR + 3;
        graphics.enableScissor(rx, viewTop, rx + PANEL_WIDTH, viewTop + viewHeight());
        int lineY = viewTop - scroll;
        int color = unlocked(TOPICS.get(selected)) ? TEXT_DARK : TEXT_MUTED;
        for (FormattedCharSequence line : body) {
            graphics.drawString(font, line, rx + 5, lineY, color, false);
            lineY += 10;
        }
        graphics.disableScissor();
        int content = body.size() * 10;
        if (content > viewHeight()) {
            int thumb = Math.max(8, viewHeight() * viewHeight() / content);
            int thumbY = viewTop + (viewHeight() - thumb) * scroll / Math.max(1, content - viewHeight());
            graphics.fill(rx + PANEL_WIDTH - 3, thumbY, rx + PANEL_WIDTH - 1, thumbY + thumb, DARK);
        }
        graphics.drawCenteredString(font, Component.translatable("gui.cobblegenetics.research_level", researchLevel),
                rx + PANEL_WIDTH / 2, y + PANEL_HEIGHT - BAR + 2, WHITE);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int x = left + LEFT_X;
        int y = top + PANEL_Y;
        if (button == 0 && mx >= x && mx < x + PANEL_WIDTH) {
            if (my >= y + PANEL_HEIGHT - BAR && my < y + PANEL_HEIGHT) {
                onClose();
                return true;
            }
            int index = (int) ((my - (y + BAR)) / ROW);
            if (my >= y + BAR && index >= 0 && index < TOPICS.size()) {
                choose(index);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int max = Math.max(0, body.size() * 10 - viewHeight());
        scroll = Mth.clamp(scroll - (int) (scrollY * 10), 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            choose(selected + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
