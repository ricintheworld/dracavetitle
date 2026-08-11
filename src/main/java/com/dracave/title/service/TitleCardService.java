package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.model.TitleDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
public final class TitleCardService {
    public static final String KEY_KIND = "card_kind";
    public static final String KEY_TYPE = "card_type";
    public static final String KEY_DAYS = "card_days";
    public static final String KEY_TITLE_ID = "card_title_id";
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTitlePlugin plugin;
    private final NamespacedKey kindKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey daysKey;
    private final NamespacedKey titleIdKey;
    public TitleCardService(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, KEY_KIND);
        this.typeKey = new NamespacedKey(plugin, KEY_TYPE);
        this.daysKey = new NamespacedKey(plugin, KEY_DAYS);
        this.titleIdKey = new NamespacedKey(plugin, KEY_TITLE_ID);
    }
    public ItemStack randomCard(CurrencyType currency, int days) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String currencyDisplay = plugin.getConfig().getString("purchase.currencies." + currency.id() + ".display", currency.id());
        meta.displayName(MINI.deserialize("<gold>随机称号卡（" + stripTags(currencyDisplay) + "）</gold>"));
        meta.lore(List.of(
                MINI.deserialize("<gray>使用后随机获得一个"),
                MINI.deserialize("<gray>" + (days > 0 ? "限时 " + days + " 天的" : "永久的") + "可购买称号"),
                MINI.deserialize("<gray>货币类型：" + currencyDisplay),
                MINI.deserialize(""),
                MINI.deserialize("<yellow>右键空气或方块使用</yellow>")
        ));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "random");
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, currency.id());
        meta.getPersistentDataContainer().set(daysKey, PersistentDataType.INTEGER, days);
        item.setItemMeta(meta);
        return item;
    }
    public ItemStack titleCard(String titleId, int days) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize("<aqua>称号卡</aqua>"));
        meta.lore(List.of(
                MINI.deserialize("<gray>使用后获得指定称号"),
                MINI.deserialize("<gray>称号：<white>" + MINI.escapeTags(titleId)),
                MINI.deserialize("<gray>期限：" + (days > 0 ? "限时 " + days + " 天" : "永久")),
                MINI.deserialize(""),
                MINI.deserialize("<yellow>右键空气或方块使用</yellow>")
        ));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "fixed");
        meta.getPersistentDataContainer().set(titleIdKey, PersistentDataType.STRING, titleId);
        meta.getPersistentDataContainer().set(daysKey, PersistentDataType.INTEGER, days);
        item.setItemMeta(meta);
        return item;
    }
    public boolean isCard(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(kindKey, PersistentDataType.STRING);
    }
    public String cardKind(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
    }
    public int cardDays(ItemStack item) {
        Integer days = item.getItemMeta().getPersistentDataContainer().get(daysKey, PersistentDataType.INTEGER);
        return days == null ? 0 : days;
    }
    public String cardTitleId(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(titleIdKey, PersistentDataType.STRING);
    }
    public CurrencyType cardCurrency(ItemStack item) {
        String id = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        try {
            return id == null ? null : CurrencyType.parse(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
    public TitleDefinition randomPurchasable(CurrencyType currency, Player player) {
        var data = plugin.service().getCached(player.getUniqueId());
        if (data == null) {
            return null;
        }
        List<TitleDefinition> candidates = plugin.registry().all().stream()
                .filter(t -> t.purchasable() && t.purchaseOffer().currency() == currency)
                .filter(t -> !data.unlocked().contains(t.id()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
    private String stripTags(String miniMessage) {
        return miniMessage.replaceAll("<[^>]+>", "");
    }
}
