package com.dracave.title.api.event;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
public class TitleUnequipEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String titleId;
    private boolean cancelled;
    public TitleUnequipEvent(Player player, String titleId) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.titleId = titleId;
    }
    public Player getPlayer() {
        return player;
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
