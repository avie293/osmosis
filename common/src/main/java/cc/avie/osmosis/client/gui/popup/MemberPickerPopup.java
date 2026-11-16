package cc.avie.osmosis.client.gui.popup;

import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.gui.PanelButton;
import cc.avie.osmosis.client.gui.PanelList;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * Picks friends for a group, either while creating it, with a name field, or to add them later.
 */
public final class MemberPickerPopup extends OsmosisPopup {
    private static final int WIDTH = 220;
    private static final int LIST_HEIGHT = 120;
    private static final int ROW_HEIGHT = 20;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int BOX_COLOR = 0xFF000000;
    private static final int CHECK_COLOR = 0xFF55FF55;

    private final List<FriendProfile> candidates;
    private final int maxSelected;
    private final boolean askForName;
    private final BiConsumer<String, Set<UUID>> onDone;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private @Nullable PanelButton doneButton;
    private String name = "";

    /**
     * @param askForName whether to show a name field, for creating a new group
     * @param onDone receives the entered name, empty without a name field, and the picked players
     */
    public MemberPickerPopup(Screen parent, Component title, List<FriendProfile> candidates, int maxSelected,
                             boolean askForName, BiConsumer<String, Set<UUID>> onDone) {
        super(parent, title);
        this.candidates = candidates;
        this.maxSelected = maxSelected;
        this.askForName = askForName;
        this.onDone = onDone;
    }

    /**
     * @return the friends that are not excluded, sorted by name
     */
    public static List<FriendProfile> candidates(Iterable<FriendProfile> friends, Set<UUID> excluded) {
        List<FriendProfile> candidates = new ArrayList<>();
        for (FriendProfile friend : friends) {
            if (!excluded.contains(friend.id())) {
                candidates.add(friend);
            }
        }
        candidates.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return candidates;
    }

    @Override
    protected void init() {
        super.init();
        int nameHeight = this.askForName ? 26 : 0;
        int y = this.layoutPopup(WIDTH, PADDING + 14 + nameHeight + LIST_HEIGHT + 6 + 20 + PADDING);
        if (this.askForName) {
            Component hint = Component.translatable("osmosis.group.name_hint");
            EditBox nameBox = new EditBox(this.font, this.left + PADDING, y, WIDTH - 2 * PADDING, 20, hint);
            nameBox.setHint(hint);
            nameBox.setMaxLength(24);
            nameBox.setValue(this.name);
            nameBox.setResponder(value -> {
                this.name = value;
                this.updateDoneButton();
            });
            this.addRenderableWidget(nameBox);
            y += nameHeight;
        }

        PickerList list = new PickerList(this.minecraft, this.left + 2, y, WIDTH - 4, LIST_HEIGHT);
        if (this.candidates.isEmpty()) {
            list.addRow(new HintRow());
        }
        for (FriendProfile candidate : this.candidates) {
            list.addRow(new CandidateRow(candidate));
        }
        this.addRenderableWidget(list);

        int buttonY = y + LIST_HEIGHT + 6;
        int buttonWidth = (WIDTH - 2 * PADDING - 4) / 2;
        this.addRenderableWidget(PanelButton.text(this.left + PADDING, buttonY, buttonWidth, 20,
                CommonComponents.GUI_CANCEL, this::onClose));
        this.doneButton = this.addRenderableWidget(PanelButton.text(this.left + PADDING + buttonWidth + 4, buttonY,
                buttonWidth, 20, CommonComponents.GUI_DONE, () -> {
                    this.onClose();
                    this.onDone.accept(this.name.strip(), Set.copyOf(this.selected));
                }));
        this.updateDoneButton();
    }

    private void updateDoneButton() {
        if (this.doneButton != null) {
            boolean named = !this.askForName || !this.name.isBlank();
            this.doneButton.active = named && (this.askForName || !this.selected.isEmpty());
        }
    }

    private void toggle(UUID id) {
        if (!this.selected.remove(id) && this.selected.size() < this.maxSelected) {
            this.selected.add(id);
        }
        this.updateDoneButton();
    }

    private static final class PickerList extends PanelList<Row> {
        private PickerList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, x, y, width, height, ROW_HEIGHT);
        }

        private void addRow(Row row) {
            this.addEntry(row);
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private final class HintRow extends Row {
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            graphics.centeredText(MemberPickerPopup.this.font, Component.translatable("osmosis.group.no_candidates"),
                    this.getContentX() + this.getContentWidth() / 2, this.getContentY() + 6, MUTED_COLOR);
        }
    }

    private final class CandidateRow extends Row {
        private final FriendProfile profile;
        private final ResolvableProfile face;

        private CandidateRow(FriendProfile profile) {
            this.profile = profile;
            this.face = ResolvableProfile.createUnresolved(profile.id());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getContentX() + 4;
            int y = this.getContentY() + (this.getContentHeight() - 16) / 2;
            graphics.fill(x, y + 3, x + 10, y + 13, hovered ? TEXT_COLOR : MUTED_COLOR);
            graphics.fill(x + 1, y + 4, x + 9, y + 12, BOX_COLOR);
            if (MemberPickerPopup.this.selected.contains(this.profile.id())) {
                graphics.fill(x + 2, y + 5, x + 8, y + 11, CHECK_COLOR);
            }
            PlayerFaceExtractor.extractRenderState(graphics, this.face, x + 16, y, 16);
            graphics.text(MemberPickerPopup.this.font, Component.literal(this.profile.name()), x + 38, y + 4,
                    TEXT_COLOR);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            MemberPickerPopup.this.toggle(this.profile.id());
            return true;
        }
    }
}
