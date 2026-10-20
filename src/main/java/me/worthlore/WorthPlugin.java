package me.worthlore;

import com.comphenix.protocol.ProtocolLibrary;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public class WorthPlugin extends JavaPlugin {

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private boolean debug = false;

    public boolean isDebug() {
        return debug;
    }

    @Override
    public void onEnable() {
        if (!new File(getDataFolder(), "prices.yml").exists()) {
            saveResource("prices.yml", false);
        }
        loadPrices();
        ProtocolLibrary.getProtocolManager().addPacketListener(new WorthPacketListener(this));
    }

    @Override
    public void onDisable() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
    }

    public void loadPrices() {
        prices.clear();
        File file = new File(getDataFolder(), "prices.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Flat format (DIRT: 1) is used; a "prices:" section is also accepted.
        ConfigurationSection section = yaml.getConfigurationSection("prices");
        if (section == null) section = yaml;

        for (String key : section.getKeys(false)) {
            if (!section.isInt(key) && !section.isDouble(key) && !section.isLong(key)) continue;
            Material material = Material.matchMaterial(key);
            if (material == null) {
                getLogger().warning("Unknown material in prices.yml: " + key);
                continue;
            }
            prices.put(material, section.getDouble(key));
        }
        getLogger().info("Loaded " + prices.size() + " prices from " + file.getAbsolutePath());
    }

    public Double getPrice(Material material) {
        return prices.get(material);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("worthlore.reload")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("worthdebug")) {
            debug = !debug;
            sender.sendMessage("§eWorthLore debug: " + (debug ? "ON" : "OFF"));
            return true;
        }
        loadPrices();
        for (Player p : getServer().getOnlinePlayers()) {
            p.updateInventory();
        }
        sender.sendMessage("§aprices.yml reloaded.");
        return true;
    }
}
