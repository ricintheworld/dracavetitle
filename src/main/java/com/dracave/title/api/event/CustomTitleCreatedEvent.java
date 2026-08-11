package com.dracave.title.api.event;
import com.dracave.title.model.CustomTitle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
public class CustomTitleCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CustomTitle title;
    public CustomTitleCreatedEvent(CustomTitle title) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.title = title;
    }
    public CustomTitle getTitle() {
        return title;
    }
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
