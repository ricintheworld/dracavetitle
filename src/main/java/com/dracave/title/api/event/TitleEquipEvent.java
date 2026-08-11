package com.dracave.title.api.event;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
public class TitleEquipEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final @Nullable String previousId;
    private final String titleId;
    private boolean cancelled;
    public TitleEquipEvent(Player player, @Nullable String previousId, String titleId) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.previousId = previousId;
        this.titleId = titleId;
    }
    public Player getPlayer() {
        return player;
    }
    public @Nullable String getPreviousTitleId() {
        return previousId;
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
