package cc.avie.osmosis.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * The game rules of the open world, in collapsible categories. Changes go to the server thread right away.
 */
final class GameRuleList extends PanelList<GameRuleList.Row> {
    private static final int ROW_HEIGHT = 24;
    private static final int CONTROL_WIDTH = 60;

    private final IntegratedServer server;
    private final Set<GameRuleCategory> expanded;
    private String filter = "";

    GameRuleList(Minecraft minecraft, int x, int y, int width, int height, IntegratedServer server,
                 Set<GameRuleCategory> expanded) {
        super(minecraft, x, y, width, height, ROW_HEIGHT);
        this.server = server;
        this.expanded = expanded;
    }

    @Override
    public int getRowWidth() {
        return this.width - 10;
    }

    void rebuild(String filter) {
        this.filter = filter;
        double scroll = this.scrollAmount();
        this.clearEntries();
        GameRules rules = this.server.getGameRules();
        Map<GameRuleCategory, List<GameRule<?>>> byCategory = new LinkedHashMap<>();
        rules.availableRules()
                .filter(rule -> filter.isEmpty() || name(rule).getString().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing(rule -> name(rule).getString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(rule -> byCategory.computeIfAbsent(rule.category(), category -> new ArrayList<>()).add(rule));
        List<GameRuleCategory> categories = new ArrayList<>(byCategory.keySet());
        categories.sort(Comparator.comparing(category -> category.label().getString(), String.CASE_INSENSITIVE_ORDER));
        for (GameRuleCategory category : categories) {
            // Searching shows every match, so categories open by themselves.
            boolean open = !filter.isEmpty() || this.expanded.contains(category);
            this.addEntry(new CategoryRow(category, open));
            if (open) {
                for (GameRule<?> rule : byCategory.get(category)) {
                    this.addEntry(new RuleRow(rule));
                }
            }
        }
        this.setScrollAmount(scroll);
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private static Component name(GameRule<?> rule) {
        return Component.translatable(rule.getDescriptionId());
    }

    private <T> void set(GameRule<T> rule, T value) {
        this.server.execute(() -> this.server.getGameRules().set(rule, value, this.server));
    }

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    private final class CategoryRow extends Row {
        private final GameRuleCategory category;
        private final boolean open;

        private CategoryRow(GameRuleCategory category, boolean open) {
            this.category = category;
            this.open = open;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GameRuleList.this.minecraft.font;
            Sprites.draw(graphics, hovered ? Sprites.BUTTON_HOVERED : Sprites.BUTTON, this.getContentX(),
                    this.getContentY(), this.getContentWidth(), this.getContentHeight());
            int textY = this.getContentY() + (this.getContentHeight() - 8) / 2;
            graphics.text(font, this.category.label(), this.getContentX() + 8, textY, SocialScreen.TEXT_COLOR);
            Sprites.draw(graphics, Sprites.sprite(this.open ? "icon/collapse" : "icon/expand"),
                    this.getContentRight() - 8 - 9, this.getContentY() + (this.getContentHeight() - 9) / 2, 9, 9);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!GameRuleList.this.expanded.remove(this.category)) {
                GameRuleList.this.expanded.add(this.category);
            }
            GameRuleList.this.rebuild(GameRuleList.this.filter);
            return true;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private final class RuleRow extends Row {
        private final Component name;
        private final @Nullable AbstractWidget control;
        // The server applies changes on its own thread, so the toggle keeps track of what it set.
        private boolean on;

        private RuleRow(GameRule<?> rule) {
            this.name = name(rule);
            this.control = this.createControl(rule);
        }

        @SuppressWarnings("unchecked")
        private @Nullable AbstractWidget createControl(GameRule<?> rule) {
            Object value = GameRuleList.this.server.getGameRules().get(rule);
            if (rule.valueClass() == Boolean.class) {
                GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                this.on = (Boolean) value;
                PanelButton[] toggle = new PanelButton[1];
                toggle[0] = PanelButton.text(0, 0, CONTROL_WIDTH, 16, onOff(this.on), () -> {
                    this.on = !this.on;
                    GameRuleList.this.set(booleanRule, this.on);
                    toggle[0].setMessage(onOff(this.on));
                    toggle[0].setStyle(this.on ? PanelButton.Style.ACCENT : PanelButton.Style.NORMAL);
                });
                return toggle[0].setStyle(this.on ? PanelButton.Style.ACCENT : PanelButton.Style.NORMAL);
            }
            if (rule.valueClass() == Integer.class) {
                GameRule<Integer> integerRule = (GameRule<Integer>) rule;
                EditBox box = new EditBox(GameRuleList.this.minecraft.font, 0, 0, CONTROL_WIDTH, 16, this.name);
                box.setValue(String.valueOf(value));
                box.setResponder(text -> integerRule.deserialize(text).result()
                        .ifPresent(number -> GameRuleList.this.set(integerRule, number)));
                return box;
            }
            return null;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GameRuleList.this.minecraft.font;
            int x = this.getContentX() + 8;
            int y = this.getContentY();
            int right = this.getContentRight() - 4 - CONTROL_WIDTH - 6;
            graphics.text(font, font.split(this.name, right - x).getFirst(), x, y + (this.getContentHeight() - 8) / 2,
                    SocialScreen.TEXT_COLOR);
            if (this.control != null) {
                this.control.setPosition(this.getContentRight() - 4 - CONTROL_WIDTH,
                        y + (this.getContentHeight() - this.control.getHeight()) / 2);
                this.control.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.control != null ? List.of(this.control) : List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.control != null ? List.of(this.control) : List.of();
        }
    }
}
