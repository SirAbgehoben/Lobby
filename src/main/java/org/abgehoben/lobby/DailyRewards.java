//DailyRewards.java
package org.abgehoben.lobby;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class DailyRewards implements Listener, CommandExecutor {

    private boolean connectedToDatabase = false;
    private final int dailyRewardsNpcId; // Store the NPC ID
    private final JavaPlugin plugin;
    private final MySQLManager mySQLManager;
    private Economy econ = null;
    private final HashMap<UUID, Long> cooldown = new HashMap<>();

    public DailyRewards(JavaPlugin plugin, Map mySQLData) {
        this.plugin = plugin;
        this.mySQLManager = new MySQLManager(plugin, (String) mySQLData.get("name"), (String) mySQLData.get("host"), (int) mySQLData.get("port"), (String) mySQLData.get("user"), (String) mySQLData.get("password"));

        try {
            mySQLManager.connect();
            connectedToDatabase = true;
            plugin.getLogger().info("Successfully connected to the database." + mySQLData.get("name"));
            createTableIfNotExists(); // Create table after successful connection
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            connectedToDatabase = false;
        }
        setupEconomy();

        // NPC creation
        Location location = new Location(plugin.getServer().getWorld("world"), -16.5, 157, 13.5); // Replace "world" if needed
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "§6§lDailyRewards");
        this.dailyRewardsNpcId = npc.getId();
        npc.spawn(location);

        // Skin
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinName("_RaveiX");

        // Protection
        npc.setProtected(true);

        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        lookClose.setRange(50);
        lookClose.setRealisticLooking(true);



    }

    private void createTableIfNotExists() {
        if (connectedToDatabase) {
            try {
                String createTableSQL = "CREATE TABLE IF NOT EXISTS daily_rewards (" +
                        "player VARCHAR(255) PRIMARY KEY," +
                        "last_reward DATE" +
                        ")";
                mySQLManager.execute(createTableSQL);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error creating table: " + e.getMessage());
            }
        }
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @EventHandler
    public void onNPCLeftClick(NPCLeftClickEvent event) {
        if (event.getNPC().getId() == this.dailyRewardsNpcId) {
            handleNPCInteraction(event.getClicker());
        }
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        if (event.getNPC().getId() == this.dailyRewardsNpcId) {
            handleNPCInteraction(event.getClicker());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dailyreward")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
                return true;
            }
            Player player = (Player) sender;

            handleNPCInteraction(player);
        }
        return false;
    }

    private void handleNPCInteraction(Player player) {
        String playerUUID = player.getUniqueId().toString();
        LocalDate today = LocalDate.now();

        try {
            boolean hasReward = mySQLManager.query("SELECT last_reward FROM daily_rewards WHERE player = ?", resultSet -> {
                try {
                    if (resultSet.next()) {
                        LocalDate lastRewardDate = resultSet.getDate("last_reward").toLocalDate();
                        return lastRewardDate.isEqual(today);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error processing query result: " + e.getMessage());
                }
                return false;
            }, playerUUID);

            Inventory gui = Bukkit.createInventory(null, 27, "Daily Reward");

            ItemStack rewardItem = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta rewardMeta = rewardItem.getItemMeta();

            if (hasReward) {
                rewardMeta.setDisplayName(ChatColor.GRAY + "Daily Reward");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Not viable for daily reward.");
                lore.add(ChatColor.GRAY + "You can only receive this reward once a day.");
                rewardMeta.setLore(lore);
            } else {
                rewardMeta.setDisplayName(ChatColor.GOLD + "Daily Reward");
            }

            rewardItem.setItemMeta(rewardMeta);
            gui.setItem(13, rewardItem);

            player.openInventory(gui);

        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking daily reward: " + e.getMessage());
            player.sendMessage("Error checking daily reward.");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Daily Reward")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.GOLD_BLOCK) {
                Player player = (Player) event.getWhoClicked();
                UUID playerUUID = player.getUniqueId();

                try {
                    boolean hasReward = mySQLManager.query("SELECT last_reward FROM daily_rewards WHERE player = ?", resultSet -> {
                        try {
                            if (resultSet.next()) {
                                LocalDate lastRewardDate = resultSet.getDate("last_reward").toLocalDate();
                                return lastRewardDate.isEqual(LocalDate.now());
                            }
                        } catch (SQLException e) {
                            plugin.getLogger().severe("Error processing query result: " + e.getMessage());
                        }
                        return false;
                    }, playerUUID.toString());

                    if (!hasReward) {
                        if (cooldown.containsKey(playerUUID) && (System.currentTimeMillis() - cooldown.get(playerUUID)) < 20) {
                            player.sendMessage(ChatColor.RED + "You must wait before claiming your reward again!");
                            return;
                        }

                        EconomyResponse r = econ.depositPlayer(player, 500);
                        if (r.transactionSuccess()) {
                            player.sendMessage("You received your daily reward of 500 coins!");
                            mySQLManager.update("INSERT INTO daily_rewards (player, last_reward) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_reward = ?",
                                    playerUUID.toString(), LocalDate.now().toString(), LocalDate.now().toString());
                            player.closeInventory();

                            cooldown.put(playerUUID, System.currentTimeMillis()); // Add player to cooldown
                        } else {
                            player.sendMessage(ChatColor.RED + "Error giving reward: " + r.errorMessage);
                            plugin.getLogger().severe("Error giving daily reward to " + player.getName() + ": " + r.errorMessage);
                        }
                    } else {
                        if (cooldown.containsKey(playerUUID) && (System.currentTimeMillis() - cooldown.get(playerUUID)) < 200) {
                            return;
                        }

                        player.sendMessage("You have already claimed your daily reward today!");
                        cooldown.put(playerUUID, System.currentTimeMillis()); // Add player to cooldown
                    }

                } catch (SQLException e) {
                    plugin.getLogger().severe("Error giving daily reward: " + e.getMessage());
                    player.sendMessage("Error giving daily reward.");
                }
            }
        }
    }



    public MySQLManager getMySQLManager() {
        return mySQLManager;
    }
}