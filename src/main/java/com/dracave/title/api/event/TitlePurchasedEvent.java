package com.dracave.title.api.event;
import com.dracave.title.model.CurrencyType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
public class TitlePurchasedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String titleId;
    private final CurrencyType currency;
    private final BigDecimal price;
    private final UUID operationId;
    public TitlePurchasedEvent(Player player, String titleId, CurrencyType currency, BigDecimal price, UUID operationId) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.titleId = titleId;
        this.currency = currency;
        this.price = price;
        this.operationId = operationId;
    }
    public Player getPlayer() {
        return player;
    }
    public String getTitleId() {
        return titleId;
    }
    public CurrencyType getCurrency() {
        return currency;
    }
    public BigDecimal getPrice() {
        return price;
    }
    public UUID getOperationId() {
        return operationId;
    }
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
