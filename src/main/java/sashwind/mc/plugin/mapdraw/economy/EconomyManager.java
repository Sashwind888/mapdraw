package sashwind.mc.plugin.mapdraw.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import sashwind.mc.plugin.mapdraw.Mapdraw;

public class EconomyManager {

    private final Mapdraw plugin;
    private Economy economy = null;
    private boolean enabled = false;

    public EconomyManager(Mapdraw plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            plugin.getLogger().info("经济功能已在配置中禁用。");
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("未检测到 Vault 插件，经济收费功能将自动禁用。");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = rsp.getProvider();
            this.enabled = this.economy != null;
            if (this.enabled) {
                plugin.getLogger().info("成功挂钩 Vault 经济服务: " + economy.getName());
            }
        } else {
            plugin.getLogger().info("未找到经济服务提供商 (如 EssentialsX 等)，经济功能未启用。");
        }
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public double getCreateCost() {
        return plugin.getConfig().getDouble("economy.create_cost", 100.0);
    }

    public double getUploadCost() {
        return plugin.getConfig().getDouble("economy.upload_cost_per_map", plugin.getConfig().getDouble("economy.upload_cost", 100.0));
    }

    public boolean hasMoney(Player player, double amount) {
        if (!isEnabled()) {
            return true;
        }
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!isEnabled()) {
            return true;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }
}
