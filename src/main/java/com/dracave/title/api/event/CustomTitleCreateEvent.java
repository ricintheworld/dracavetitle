package com.dracave.title.api.event;
import com.dracave.title.model.CustomTitleDraft;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
public class CustomTitleCreateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final CustomTitleDraft draft;
    private boolean cancelled;
    public CustomTitleCreateEvent(Player player, CustomTitleDraft draft) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.draft = draft;
    }
    public Player getPlayer() {
        return player;
    }
    public CustomTitleDraft getDraft() {
        return draft;
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
