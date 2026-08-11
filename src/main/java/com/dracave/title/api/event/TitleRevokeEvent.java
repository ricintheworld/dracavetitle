package com.dracave.title.api.event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;
public class TitleRevokeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String titleId;
    private boolean cancelled;
    public TitleRevokeEvent(UUID playerId, String titleId) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.playerId = playerId;
        this.titleId = titleId;
    }
    public UUID getPlayerId() {
        return playerId;
    }
    public String getTitleId() {
        return titleId;
    }
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
