package com.dracave.title.api.event;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;
public class CustomTitleDeletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID ownerId;
    private final String titleId;
    public CustomTitleDeletedEvent(UUID ownerId, String titleId) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.ownerId = ownerId;
        this.titleId = titleId;
    }
    public UUID getOwnerId() {
        return ownerId;
    }
    public String getTitleId() {
        return titleId;
    }
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
