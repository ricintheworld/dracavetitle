package com.dracave.title.util;

import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomItemProvider {
    private CustomItemProvider() {}
    private static final class Cache {
        static final ConcurrentHashMap<String, ItemStack> resolveCache = new ConcurrentHashMap<>();
        static final ConcurrentHashMap<Integer, String> idCache = new ConcurrentHashMap<>();
    }

    public static boolean isCustomMaterial(String material) {
        if (material == null || material.isBlank()) return false;
        String lower = material.toLowerCase(Locale.ROOT);
        return lower.contains(":") && !lower.startsWith("minecraft:");
    }

    public static ItemStack resolve(String value) {
        if (value == null || value.isBlank()) return null;
        return Cache.resolveCache.computeIfAbsent(value, k -> {
            String lower = k.toLowerCase(Locale.ROOT);
            ItemStack r = null;
            if (lower.startsWith("ia:")) r = resolveIA(k);
            else if (lower.startsWith("oraxen:")) r = resolveOraxen(k);
            else if (lower.startsWith("ce:") || lower.startsWith("craftengine:"))
                r = resolveCE(k.substring(k.indexOf(':') + 1));
            else if (lower.startsWith("nexo:")) r = resolveNexo(k);
            return r;
        });
    }

    public static String getCustomItemId(ItemStack item) {
        if (item == null) return null;
        int hash = System.identityHashCode(item);
        String cached = Cache.idCache.get(hash);
        if (cached != null) return "".equals(cached) ? null : cached;
        // Only check custom items: first quick-filter by checking if it has a non-vanilla PDC tag or display
        if (!likelyCustom(item)) {
            Cache.idCache.put(hash, "");
            return null;
        }
        String id = null;
        id = tryReflect("ia:", () -> idIA(item)); if (id != null) { cache(hash, id); return id; }
        id = tryReflect("oraxen:", () -> idOraxen(item)); if (id != null) { cache(hash, id); return id; }
        id = tryReflect("ce:", () -> idCE(item)); if (id != null) { cache(hash, id); return id; }
        id = tryReflect("nexo:", () -> idNexo(item)); if (id != null) { cache(hash, id); return id; }
        Cache.idCache.put(hash, "");
        return null;
    }

    public static List<String> allItemIds() {
        List<String> ids = new ArrayList<>();
        ids.addAll(idsIA());
        ids.addAll(idsOraxen());
        ids.addAll(idsCE());
        ids.addAll(idsNexo());
        return ids;
    }

    private static void cache(int hash, String id) {
        Cache.idCache.put(hash, id);
        if (Cache.idCache.size() > 10000) Cache.idCache.clear();
    }

    private static boolean likelyCustom(ItemStack item) {
        try {
            if (item.hasItemMeta()) {
                return item.getItemMeta().getPersistentDataContainer().getKeys().stream()
                        .anyMatch(k -> !k.getNamespace().equals("minecraft"));
            }
        } catch (Exception ignored) {}
        return true; // 无法判断时走反射验证
    }

    private interface IdFunc { String get() throws Exception; }
    private static String tryReflect(String prefix, IdFunc f) {
        try { return f.get(); } catch (Exception ignored) { return null; }
    }

    private static ItemStack resolveIA(String value) {
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = cs.getMethod("getInstance", String.class).invoke(null, value.substring(3));
            if (stack != null) return (ItemStack) cs.getMethod("getItemStack").invoke(stack);
        } catch (Exception ignored) {}
        return null;
    }
    private static ItemStack resolveOraxen(String value) {
        try {
            Class<?> api = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object oItem = api.getMethod("getItemById", String.class).invoke(null, value.substring(7));
            if (oItem != null) return (ItemStack) api.getMethod("build").invoke(oItem);
        } catch (Exception ignored) {}
        return null;
    }
    private static ItemStack resolveCE(String id) {
        try {
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Object def = api.getMethod("byId", String.class).invoke(null, id);
            if (def != null) return (ItemStack) def.getClass().getMethod("buildItem",
                    Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext"))
                    .invoke(def, ItemBuildContextRef.EMPTY);
        } catch (Exception ignored) {}
        return null;
    }
    private static ItemStack resolveNexo(String value) {
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object nexo = api.getMethod("itemFromId", String.class).invoke(null, value.substring(5));
            if (nexo != null) return (ItemStack) api.getMethod("build").invoke(nexo);
        } catch (Exception ignored) {}
        return null;
    }

    private static String idIA(ItemStack item) { try {
        Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
        Object stack = cs.getMethod("byItemStack", ItemStack.class).invoke(null, item);
        if (stack != null) return "ia:" + cs.getMethod("getNamespacedID").invoke(stack);
        } catch (Exception ignored) {}
        return null;
    }
    private static String idOraxen(ItemStack item) { try {
        Class<?> api = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
        Object id = api.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
        if (id != null) return "oraxen:" + id;
        } catch (Exception ignored) {}
        return null;
    }
    private static String idCE(ItemStack item) { try {
        Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
        Object def = api.getMethod("byItemStack", ItemStack.class).invoke(null, item);
        if (def != null) return "ce:" + def.getClass().getMethod("id").invoke(def);
        } catch (Exception ignored) {}
        return null;
    }
    private static String idNexo(ItemStack item) { try {
        Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
        Object nexo = api.getMethod("itemFromItemStack", ItemStack.class).invoke(null, item);
        if (nexo != null) return "nexo:" + api.getMethod("getItemId").invoke(nexo);
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> idsIA() {
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object map = cs.getMethod("getNamespacedItems").invoke(null);
            return new ArrayList<>(((java.util.Map<String,?>)map).keySet().stream().map(k->"ia:"+k).toList());
        } catch (Exception ignored) {return List.of();}
    }
    @SuppressWarnings("unchecked")
    private static List<String> idsOraxen() {
        try {
            Class<?> api = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object set = api.getMethod("getItemIDs").invoke(null);
            return new ArrayList<>(((java.util.Set<String>)set).stream().map(k->"oraxen:"+k).toList());
        } catch (Exception ignored) {return List.of();}
    }
    @SuppressWarnings("unchecked")
    private static List<String> idsCE() {
        try {
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Object map = api.getMethod("loadedItems").invoke(null);
            return new ArrayList<>(((java.util.Map<?,?>)map).keySet().stream()
                    .map(k->"ce:"+k.toString()).toList());
        } catch (Exception ignored) {return List.of();}
    }
    @SuppressWarnings("unchecked")
    private static List<String> idsNexo() {
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object entries = api.getMethod("itemEntries").invoke(null);
            return new ArrayList<>(((Collection<?>)entries).stream().map(e->{
                try {return "nexo:"+e.getClass().getMethod("getItemId").invoke(e);}
                catch(Exception x){return null;}
            }).filter(java.util.Objects::nonNull).toList());
        } catch (Exception ignored) {return List.of();}
    }

    private static class ItemBuildContextRef {
        static Object EMPTY = resolve();
        private static Object resolve() {
            try { return Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext")
                    .getField("EMPTY").get(null); } catch (Exception ignored) { return null; }
        }
    }
}
