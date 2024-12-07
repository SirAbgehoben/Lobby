// LobbySelector.java
package org.abgehoben.lobby;

import eu.cloudnetservice.driver.inject.InjectionLayer;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.driver.registry.ServiceRegistry;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.modules.bridge.BridgeDocProperties;
import eu.cloudnetservice.modules.bridge.player.CloudPlayer;
import eu.cloudnetservice.modules.bridge.player.PlayerManager;
import eu.cloudnetservice.modules.bridge.player.executor.PlayerExecutor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.Listener;  // Make sure this import is present
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LobbySelector implements Listener, InventoryHolder { // Implement Listener and InventoryHolder

    private Inventory currentInventory; // Store the currently open inventory
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();

    public LobbySelector(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin); // Register the listener

    }

    public void openLobbySelector(Player player) {
        CloudServiceProvider cloudServiceProvider = InjectionLayer.ext().instance(CloudServiceProvider.class);
        if (cloudServiceProvider == null) {
            player.sendMessage("§cError: Could not access CloudNet API.");
            return;
        }


        List<ServiceInfoSnapshot> lobbies = new ArrayList<>(cloudServiceProvider.servicesByTask("Lobby"));


        Collections.sort(lobbies, Comparator.comparingInt(o -> {
            String name = o.name();
            try {
                return Integer.parseInt(name.substring(name.lastIndexOf("-") + 1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }));

        Inventory gui = Bukkit.createInventory(this, 9, ChatColor.GOLD + "§lLobbySelector");  // Use 'this' as the holder
        this.currentInventory = gui; // Store the inventory

        for (ServiceInfoSnapshot lobby : lobbies) {
            if (lobby.readProperty(BridgeDocProperties.IS_ONLINE)) {
                ItemStack item;
                if (lobby.readProperty(BridgeDocProperties.PLAYERS).size() > 0) {
                    item = new ItemStack(Material.EMERALD);
                } else {
                    item = new ItemStack(Material.SMOOTH_QUARTZ);
                }

                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GRAY + lobby.name());

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.DARK_GRAY +"● "+ ChatColor.YELLOW + lobby.serviceId().taskName());
                lore.add(ChatColor.DARK_GRAY +"● " + "§7" + lobby.readProperty(BridgeDocProperties.PLAYERS).size() + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + lobby.readProperty(BridgeDocProperties.MAX_PLAYERS));

                // Get the MOTD and add it to the lore
                String motd = lobby.readProperty(BridgeDocProperties.MOTD);
                if (motd != null && !motd.isEmpty()) {
                    lore.add(ChatColor.DARK_GRAY +"● " + ChatColor.GRAY + motd);
                }

                meta.setLore(lore);

                item.setItemMeta(meta);
                gui.addItem(item);
            }
        }

        player.openInventory(gui);
    }




    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // Cooldown check
        long currentTime = System.currentTimeMillis();
        if (clickCooldowns.containsKey(player.getUniqueId())) {
            long lastClick = clickCooldowns.get(player.getUniqueId());
            if (currentTime - lastClick < 500) { // 500 milliseconds = 0.5 seconds
                return; // Ignore click if within cooldown
            }
        }
        clickCooldowns.put(player.getUniqueId(), currentTime); // Update last click time

        InventoryView view = event.getView();
        if (view.getTopInventory() == null || view.getTopInventory().getHolder() != this) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }


        String lobbyName = clickedItem.getItemMeta().getDisplayName();

        // Remove color codes from lobbyName
        String cleanedLobbyName = ChatColor.stripColor(lobbyName);


        CloudServiceProvider cloudServiceProvider = InjectionLayer.ext().instance(CloudServiceProvider.class);
        if (cloudServiceProvider == null) {
            player.sendMessage("§cError: Could not access CloudNet API.");
            return;
        }

        ServiceInfoSnapshot lobby = cloudServiceProvider.serviceByName(cleanedLobbyName);

        if (lobby != null && lobby.readProperty(BridgeDocProperties.IS_ONLINE)) { // Check if online!
            player.closeInventory();
            sendPlayerToLobby(player, cleanedLobbyName);  // Important: Use cleaned name here as well
        } else {
            player.sendMessage("§cThis lobby is no longer available or does not exist.");
            // Refresh the inventory to show accurate lobby status (optional, but recommended)
            player.closeInventory();
            openLobbySelector(player);
        }
    }



    private void sendPlayerToLobby(Player player, String lobbyName) {
        PlayerManager playerManager = InjectionLayer.ext().instance(ServiceRegistry.class).firstProvider(PlayerManager.class);
        playerManager.playerExecutor(player.getUniqueId()).connect(lobbyName);
    }


    @Override
    public @NotNull Inventory getInventory() {
        return this.currentInventory; // Correctly return the inventory
    }
}