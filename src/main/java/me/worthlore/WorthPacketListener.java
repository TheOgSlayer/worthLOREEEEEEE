package me.worthlore;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds a "Worth: $x" line under the item name, client-side only (via packets).
 * The real items on the server are never modified.
 */
public class WorthPacketListener extends PacketAdapter {

    // &#00a2ff  ->  §x§0§0§a§2§f§f
    private static final String BLUE = "§x§0§0§a§2§f§f";
    private static final String GREEN = "§a";
    private static final String WHITE = "§f";

    private final WorthPlugin plugin;
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Qa", "Qi"};

    public WorthPacketListener(WorthPlugin plugin) {
        super(plugin, ListenerPriority.HIGH, packetTypes());
        this.plugin = plugin;
    }

    /**
     * SET_CURSOR_ITEM only exists in newer ProtocolLib versions, so it is looked up by name
     * at runtime. That way the plugin compiles and runs on any ProtocolLib 5.x.
     */
    private static PacketType[] packetTypes() {
        List<PacketType> types = new ArrayList<>();
        types.add(PacketType.Play.Server.SET_SLOT);
        types.add(PacketType.Play.Server.WINDOW_ITEMS);
        try {
            Object value = PacketType.Play.Server.class.getField("SET_CURSOR_ITEM").get(null);
            if (value instanceof PacketType && ((PacketType) value).isSupported()) {
                types.add((PacketType) value);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // not available in this ProtocolLib version
        }
        return types.toArray(new PacketType[0]);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        // Creative inventory is client-authoritative, so fake lore would leak into real items.
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        PacketContainer packet = event.getPacket().deepClone();

        try {
            // Single items (SET_SLOT, SET_CURSOR_ITEM, carried item of WINDOW_ITEMS)
            StructureModifier<ItemStack> items = packet.getItemModifier();
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.read(i);
                if (item != null) items.write(i, addWorth(item));
            }

            // Lists of items (WINDOW_ITEMS)
            StructureModifier<List<ItemStack>> lists = packet.getItemListModifier();
            for (int i = 0; i < lists.size(); i++) {
                List<ItemStack> original = lists.read(i);
                if (original == null) continue;
                List<ItemStack> modified = new ArrayList<>(original.size());
                for (ItemStack item : original) {
                    modified.add(item == null ? null : addWorth(item));
                }
                lists.write(i, modified);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to add worth lore: " + ex.getMessage());
            return;
        }

        event.setPacket(packet);
    }

    private ItemStack addWorth(ItemStack item) {
        if (item.getType() == Material.AIR || item.getAmount() <= 0) return item;

        Double price = plugin.getPrice(item.getType());
        if (price == null) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        double total = price * item.getAmount();
        if (plugin.isDebug() && item.getAmount() > 1) {
            plugin.getLogger().info("[debug] " + item.getType() + " x" + item.getAmount()
                    + " price=" + price + " total=" + total);
        }
        String line = BLUE + "Worth: " + GREEN + "$" + WHITE + formatMoney(total);

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        lore.add(0, line); // directly under the item name

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 2000 -> 2K, 2023 -> 2.02K, 2000000 -> 2M.
     * Max 2 decimals, trailing zeros removed, always rounded DOWN
     * (so 999999 becomes 999.99K and never "1000K").
     */
    private String formatMoney(double value) {
        BigDecimal number = BigDecimal.valueOf(value);
        BigDecimal thousand = BigDecimal.valueOf(1000);
        int index = 0;

        while (number.compareTo(thousand) >= 0 && index < SUFFIXES.length - 1) {
            number = number.movePointLeft(3);
            index++;
        }

        String text = number.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
        return text + SUFFIXES[index];
    }
}
