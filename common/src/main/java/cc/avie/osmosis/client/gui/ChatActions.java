package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.GroupInfo;
import cc.avie.osmosis.client.gui.popup.ConfirmPopup;
import cc.avie.osmosis.client.gui.popup.GroupMembersPopup;
import cc.avie.osmosis.client.gui.popup.MemberPickerPopup;
import cc.avie.osmosis.client.gui.popup.TextInputPopup;
import cc.avie.osmosis.client.net.OsmosisConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The dropdown actions for a friend or group, shared by the chat header menu and right-clicking the sidebar.
 */
final class ChatActions {
    static final int MAX_GROUP_MEMBERS = 10;

    private ChatActions() {
    }

    static List<MenuItem> forTarget(OsmosisScreen screen, ChatTarget target) {
        List<MenuItem> items = new ArrayList<>();
        FriendList friends = OsmosisClient.friends();
        if (target.isGroup()) {
            GroupInfo group = friends.group(target.group());
            if (group != null) {
                addGroupActions(screen, group, items);
            }
        } else {
            FriendProfile friend = friends.friend(target.friend());
            if (friend != null) {
                addFriendActions(screen, friend, items);
            }
        }
        items.add(item("delete", "osmosis.menu.delete_chat", () -> confirm(screen, "osmosis.confirm.delete_chat",
                "osmosis.menu.delete_chat", () -> screen.deleteChat(target))));
        return items;
    }

    private static void addFriendActions(OsmosisScreen screen, FriendProfile friend, List<MenuItem> items) {
        OsmosisConnection connection = OsmosisClient.connection();
        items.add(item("remove", "osmosis.menu.remove", () -> confirm(screen,
                Component.translatable("osmosis.confirm.remove", friend.name()), "osmosis.menu.remove",
                () -> connection.removeFriend(friend.id()))));
        items.add(item("block", "osmosis.menu.block", () -> confirm(screen,
                Component.translatable("osmosis.confirm.block", friend.name()), "osmosis.menu.block",
                () -> connection.block(friend.id()))));
    }

    private static void addGroupActions(OsmosisScreen screen, GroupInfo group, List<MenuItem> items) {
        OsmosisConnection connection = OsmosisClient.connection();
        FriendProfile self = OsmosisClient.friends().self();
        boolean owner = self != null && group.isOwner(self.id());
        items.add(item("members", "osmosis.menu.members", () -> open(new GroupMembersPopup(screen, group))));
        if (owner) {
            items.add(item("rename", "osmosis.menu.rename", () -> open(new TextInputPopup(screen,
                    Component.translatable("osmosis.menu.rename"), group.name(), 24,
                    name -> connection.renameGroup(group.id(), name)))));
            items.add(item("add_member", "osmosis.menu.add_member", () -> openAddMembers(screen, group)));
        }
        items.add(item("leave", "osmosis.menu.leave", () -> confirm(screen,
                Component.translatable("osmosis.confirm.leave", group.name()), "osmosis.menu.leave",
                () -> connection.leaveGroup(group.id()))));
    }

    private static void openAddMembers(OsmosisScreen screen, GroupInfo group) {
        Set<UUID> members = new HashSet<>();
        group.members().forEach(member -> members.add(member.id()));
        List<FriendProfile> candidates = MemberPickerPopup.candidates(OsmosisClient.friends().friends(), members);
        open(new MemberPickerPopup(screen, Component.translatable("osmosis.menu.add_member"), candidates,
                MAX_GROUP_MEMBERS - group.members().size(), false,
                (name, picked) -> picked.forEach(id -> OsmosisClient.connection().addGroupMember(group.id(), id))));
    }

    static void openCreateGroup(OsmosisScreen screen) {
        List<FriendProfile> candidates = MemberPickerPopup.candidates(OsmosisClient.friends().friends(), Set.of());
        open(new MemberPickerPopup(screen, Component.translatable("osmosis.group.create"), candidates,
                MAX_GROUP_MEMBERS - 1, true, (name, picked) -> OsmosisClient.connection().createGroup(name, picked)));
    }

    private static void confirm(OsmosisScreen screen, String messageKey, String confirmKey, Runnable action) {
        confirm(screen, Component.translatable(messageKey), confirmKey, action);
    }

    private static void confirm(OsmosisScreen screen, Component message, String confirmKey, Runnable action) {
        Component label = Component.translatable(confirmKey);
        open(new ConfirmPopup(screen, label, message, label, action));
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    private static MenuItem item(String icon, String labelKey, Runnable action) {
        return new MenuItem(Sprites.sprite("menu/" + icon), Component.translatable(labelKey), action);
    }
}
