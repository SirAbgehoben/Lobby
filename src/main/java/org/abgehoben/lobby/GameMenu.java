// GameMenu.java
package org.abgehoben.lobby;

import eu.cloudnetservice.driver.inject.InjectionLayer;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.driver.provider.ServiceTaskProvider;
import eu.cloudnetservice.driver.registry.ServiceRegistry;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.modules.bridge.BridgeDocProperties;
import eu.cloudnetservice.modules.bridge.player.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.util.Collections.replaceAll;

public class GameMenu implements Listener, InventoryHolder {

    private Inventory currentInventory;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();


    public GameMenu(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openGameMenu(Player player) {
        Inventory gui = Bukkit.createInventory(this, 45, ChatColor.GOLD + "§lGameMenu");
        this.currentInventory = gui;

        createGameMenuItem(gui, 20, Material.RED_WOOL, "§aTurfWars", "§7Capture the wool and bring it back to your base!"); // Added lore
        createGameMenuItem(gui, 19, Material.TNT, "§aTNTRun", "§7Be the last one standing on the shrinking TNT platform!");// Added lore
        createGameMenuItem(gui, 24, Material.GRASS_BLOCK, "§aSurvival", "§7Gather resources, build a base, and survive!");// Added lore
        createGameMenuItem(gui, 25, Material.ELYTRA, "§aElytraRace", "§7Soar through the sky and race to the finish line!");// Added lore
        createGameMenuItem(gui, 22, Material.NETHER_STAR, "§aLobby", "§7Return to the main lobby.");// Added lore
        createGameMenuItem(gui, 36, Material.SHULKER_SHELL, "§aOther", "§7Other games.");// Added lore


        player.openInventory(gui);
    }

    private void createGameMenuItem(Inventory gui, int slot, Material material, String name, String loreText) { // Updated method signature
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        lore.add(loreText); // Set the lore
        meta.setLore(lore); // Add lore to the meta

        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    public void OtherGames(Player player) {
        CloudServiceProvider cloudServiceProvider = InjectionLayer.ext().instance(CloudServiceProvider.class);
        Inventory gui = Bukkit.createInventory(this, 45, ChatColor.GOLD + "§lGameMenu");
        this.currentInventory = gui;

        Collection<ServiceInfoSnapshot> allServices = cloudServiceProvider.runningServices(); //using all services because I didn't find a funktion for tasks will just strip -[int] out

        int InventorySlotNumber = 0;
        for(ServiceInfoSnapshot service : allServices) {
            if(service.name().startsWith("Lobby") ||
               service.name().startsWith("TurfWars") ||
               service.name().startsWith("Proxy") ||
               service.name().startsWith("TNTRun") ||
               service.name().startsWith("Elytra") ||
               service.name().startsWith("Survival")) {
                continue; //exit current loop iteration
            }

            String taskName = service.name().replaceAll("-\\d+", ""); //remove -<int>

            createGameMenuItem(gui, InventorySlotNumber, Material.NOTE_BLOCK, "§a" + taskName, "§7Hi, I" + (taskName.endsWith("s") ? " have some ": " am a ") + taskName);
            InventorySlotNumber++;
        }

        player.openInventory(gui);
    }


    private void openTaskSelector(Player player, String task) {
        CloudServiceProvider cloudServiceProvider = InjectionLayer.ext().instance(CloudServiceProvider.class);
        if (cloudServiceProvider == null) {
            player.sendMessage("§cError: Could not access CloudNet API.");
            return;
        }


        Collection<ServiceInfoSnapshot> services = cloudServiceProvider.servicesByTask(task);


        Inventory gui = Bukkit.createInventory(this, 9, ChatColor.GOLD + "§l" + task);
        this.currentInventory = gui; // Update currentInventory


        for (ServiceInfoSnapshot service : services) {
            ItemStack item;
            if (service.readProperty(BridgeDocProperties.IS_ONLINE)) {

                if (service.readProperty(BridgeDocProperties.PLAYERS).size() > 0) {
                    item = new ItemStack(Material.EMERALD);
                } else {
                    item = new ItemStack(Material.SMOOTH_QUARTZ);
                }

                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GRAY + service.name());

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.DARK_GRAY +"● "+ ChatColor.YELLOW + service.serviceId().taskName());
                lore.add(ChatColor.DARK_GRAY +"● " + "§7" + service.readProperty(BridgeDocProperties.PLAYERS).size() + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + service.readProperty(BridgeDocProperties.MAX_PLAYERS));

                // Get the MOTD and add it to the lore
                String motd = service.readProperty(BridgeDocProperties.MOTD);
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

        long currentTime = System.currentTimeMillis();
        if (clickCooldowns.containsKey(player.getUniqueId())) {
            long lastClick = clickCooldowns.get(player.getUniqueId());
            if (currentTime - lastClick < 500) {
                return;
            }
        }
        clickCooldowns.put(player.getUniqueId(), Long.valueOf(currentTime));

        InventoryView view = event.getView();
        if (view.getTopInventory() == null || view.getTopInventory().getHolder() != this) {
            return;
        }


        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }


        String clickedItemName = clickedItem.getItemMeta().getDisplayName();

        switch (ChatColor.stripColor(clickedItemName)) {
            case "Other":
                OtherGames(player);

                break;
            case "TurfWars":
                openTaskSelector(player,"TurfWars");

                break;
            case "TNTRun":
                openTaskSelector(player,"TNTRun");
                break;

            case "Survival":
                openTaskSelector(player,"Survival");

                break;
            case "ElytraRace":
                openTaskSelector(player,"ElytraRace");

                break;
            case "Lobby":
                player.closeInventory();
                player.teleport(new org.bukkit.Location(player.getWorld(),-25.5, 157.2, 2.5, -135,0));
                break;
            default: //default for when in TaskSelector
                CloudServiceProvider cloudServiceProvider = InjectionLayer.ext().instance(CloudServiceProvider.class);
                ServiceInfoSnapshot service = cloudServiceProvider.serviceByName(ChatColor.stripColor(clickedItemName));

                if (!ChatColor.stripColor(clickedItemName).contains("-")) {
                    openTaskSelector(player, ChatColor.stripColor(clickedItemName));
                    break;
                }

                if (service != null && service.readProperty(BridgeDocProperties.IS_ONLINE)) {
                    player.closeInventory();
                    sendPlayerToServer(player, ChatColor.stripColor(clickedItemName));
                } else {
                    player.sendMessage("§cThis Server is no longer available or does not exist.");
                    player.closeInventory();
                    openGameMenu(player); // Refresh the inventory
                }


        }

    }




    private void sendPlayerToServer(Player player, String serverName) {
        PlayerManager playerManager = InjectionLayer.ext().instance(ServiceRegistry.class).firstProvider(PlayerManager.class);
        playerManager.playerExecutor(player.getUniqueId()).connect(serverName);
    }



    @Override
    public @NotNull Inventory getInventory() {
        return this.currentInventory;
    }

}