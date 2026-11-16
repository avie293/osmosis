package cc.avie.osmosis.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversations loaded from the server in this session. History is fetched page by page when a chat is opened, so
 * only conversations the player looked at are kept in memory.
 *
 * <p>Only touched on the render thread.
 */
public final class ChatLog {
    private final Map<UUID, Conversation> conversations = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public Conversation get(UUID friend) {
        return this.conversations.computeIfAbsent(friend, id -> new Conversation());
    }

    public void addListener(Runnable listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        this.listeners.remove(listener);
    }

    public void add(UUID friend, ChatMessage message) {
        Conversation conversation = this.conversations.get(friend);
        // Conversations that were never opened get the message with their first history page instead.
        if (conversation != null && conversation.loaded) {
            conversation.messages.add(message);
            this.changed();
        }
    }

    public void addHistory(UUID friend, List<ChatMessage> older, boolean hasMore) {
        Conversation conversation = this.get(friend);
        conversation.messages.addAll(0, older);
        conversation.hasMore = hasMore;
        conversation.loaded = true;
        conversation.loading = false;
        this.changed();
    }

    public void remove(UUID friend) {
        if (this.conversations.remove(friend) != null) {
            this.changed();
        }
    }

    /** Forgets everything, used after reconnecting since the server is the source of truth. */
    public void clear() {
        this.conversations.clear();
        this.changed();
    }

    private void changed() {
        for (int i = 0; i < this.listeners.size(); i++) {
            this.listeners.get(i).run();
        }
    }

    public static final class Conversation {
        private final List<ChatMessage> messages = new ArrayList<>();
        private boolean loaded;
        private boolean loading;
        private boolean hasMore = true;

        public List<ChatMessage> messages() {
            return Collections.unmodifiableList(this.messages);
        }

        public boolean isLoaded() {
            return this.loaded;
        }

        public boolean isLoading() {
            return this.loading;
        }

        public boolean hasMore() {
            return this.hasMore;
        }

        /**
         * Marks the next page as requested.
         *
         * @return the time to load messages before, 0 for the newest, or -1 if nothing should be requested
         */
        public long startLoading() {
            if (this.loading || (this.loaded && !this.hasMore)) {
                return -1;
            }
            this.loading = true;
            return this.messages.isEmpty() ? 0 : this.messages.getFirst().time();
        }
    }
}
