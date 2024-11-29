package org.abgehoben.lobby;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.abgehoben.lobby.GameMenu; // Import GameMenu
import org.abgehoben.lobby.LobbySelector; // Import LobbySelector
import org.abgehoben.lobby.CosmeticsBox; // import added
import org.abgehoben.lobby.Parkour;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.util.Vector;
import org.bukkit.block.Block; // Import for Block manipulation

import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

import java.util.*;

public class Lobby implements Listener, CommandExecutor{

    private final GameMenu gameMenu; // Instance of GameMenu
    private final LobbySelector lobbySelector; // Instance of LobbySelector
    private final CosmeticsBox CosmeticsBox; // Instance of CosmeticsBox

    private final main plugin;
    private final Map<UUID, Long> launchCooldowns = new HashMap<>();
    private final double launchpadRadius = 0.75; // Radius around the pressure plate

    private final Map<Location, Long> placedBlocks = new ConcurrentHashMap<>(); // To track placed blocks and their timestamps
    private final Location restrictedCenter = new Location(null, -25, 157, 2);
    private final double restrictedRadius = 7;
    private final ItemStack redSandstoneBlocks;

    private final Location[] launchpadLocations = new Location[]{
            new Location(null, -25.5, 157, -0.5),
            new Location(null, -23.5, 157, 0.5),
            new Location(null, -22.5, 157, 2.5)
    };

    public Lobby(main plugin) {
        this.plugin = plugin;
        this.gameMenu = new GameMenu(plugin); // Initialize GameMenu
        this.lobbySelector = new LobbySelector(plugin); // Initialize LobbySelector
        this.CosmeticsBox = new CosmeticsBox(plugin); // Initialize CosmeticsBox
        plugin.getCommand("fly").setExecutor(this); // Register the command
        Bukkit.getPluginManager().registerEvents(this.lobbySelector, plugin);
        this.redSandstoneBlocks = createRedSandstoneBlocks();
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getInventory().clear();  // Clear the inventory completely
        event.getPlayer().setGameMode(GameMode.SURVIVAL);
        // Set the player's spawn location and facing direction
        Location spawnLocation = new Location(player.getWorld(), -25.5, 157.2, 2.5, -135.0f, 0.0f); // Floats for yaw and pitch!
        player.teleport(spawnLocation);  // Teleport the player
        sendTitle(player, "§b§lWelcome", player.getName(), 10, 70, 20);
        // Display join message only to the joining player
        player.sendMessage("§8§m+---------------***---------------+");
        player.sendMessage("");
        player.sendMessage("§r    §7Welcome, §b§n" + player.getName() + "§r §7to AbgehobenNetwork");
        player.sendMessage("");
        player.sendMessage("§r  §2§lWEBSITE §fwww.AbgehobenNetwork.net");
        player.sendMessage("§r  §9§lDISCORD §fdiscord.abgehoben.org");
        player.sendMessage("");
        player.sendMessage("§8§m+---------------***---------------+");

        createScoreboard(player); // Create and display the scoreboard
        giveLobbyItems(player); // Give all lobby items at once
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        spawnFirework(player.getLocation());
        healPlayer(player);

        if (player.hasPermission("lobby.fly")) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR)
                player.setAllowFlight(true); // Enable flight on join if permission is present and not in creative/spectator
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return; // Prevent NullPointerException if no item clicked


        if (isItemWithName(item, ChatColor.GREEN + "Game Menu §7(Right-Click)")) {


            gameMenu.openGameMenu(player);


        } else if (isItemWithName(item, "§bServer Information §7(Right-Click)")) {
            player.sendMessage("");
            player.sendMessage("§e§lServer Information");
            player.sendMessage("");
            player.sendMessage("§bWebsite: §fwww.abgehoben.org");
            player.sendMessage("§dDiscord: §fdiscord.abgehoben.org");
            player.sendMessage("");


        } else if (isItemWithName(item, "§bPlayers §8§l> §aVisible §7(Right-Click)")) {
            player.sendMessage("§3Abgehoben §8§l| §cPlayer visibility disabled");
            // Hide other players
            for (Player otherPlayer : plugin.getServer().getOnlinePlayers()) {
                if (otherPlayer != player) { // Don't hide the player from themselves
                    player.hidePlayer(plugin, otherPlayer);
                }
            }


            ItemStack hiddenPlayersDye = createItem(Material.RED_DYE, 0, "§bPlayers §8§l> §cHidden §7(Right-Click)",
                    Arrays.asList(ChatColor.GRAY + "§7Click to show all players!"));
            player.getInventory().setItem(8, hiddenPlayersDye);
            player.updateInventory();


        } else if (isItemWithName(item, "§bPlayers §8§l> §cHidden §7(Right-Click)")) {
            player.sendMessage("§3Abgehoben §8§l| §cPlayer visibility enabled");
            // Show other players
            for (Player otherPlayer : plugin.getServer().getOnlinePlayers()) {
                player.showPlayer(plugin, otherPlayer);
            }


            ItemStack shownPlayersDye = createItem(Material.LIME_DYE, 0, "§bPlayers §8§l> §aVisible §7(Right-Click)",
                    Arrays.asList(ChatColor.GRAY + "§7Click to hide all players!"));
            player.getInventory().setItem(8, shownPlayersDye);
            player.updateInventory();


        } else if (isItemWithName(item, "§bLobby Selector §7(Right-Click)")) {


            lobbySelector.openLobbySelector(player); // Call the openLobbySelector method


        } else if (isItemWithName(item, "§bCosmetics Box §7(Right-Click)")) {


            CosmeticsBox.openCosmeticsBox(player); // Call the openCosmeticsBox method


        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Location playerLocation = event.getTo(); // Use 'To' location for accurate checks

        if (!isOnCooldown(playerUUID)) {
            for (Location launchpadLocation : launchpadLocations) {
                // Set world for launchpad locations on first use
                if (launchpadLocation.getWorld() == null) {
                    launchpadLocation.setWorld(player.getWorld());
                }
                if (isNearLaunchpad(playerLocation, launchpadLocation, launchpadRadius)) {
                    // Launch the player
                    Vector launchVector = player.getLocation().getDirection().normalize().multiply(1.5);
                    launchVector.setY(0.7);
                    player.setVelocity(launchVector);

                    player.playSound(playerLocation, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);

                    // Set cooldown
                    launchCooldowns.put(playerUUID, System.currentTimeMillis());
                    break;
                }
            }
        }

        Location center = new Location(player.getWorld(), 0, 135, 0);

        if (playerLocation.distance(center) > 170) {
            Location spawnLocation = new Location(player.getWorld(), -25.5, 157.2, 2.5, -135.0f, 0.0f);
            player.teleport(spawnLocation);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("fly")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
                return true;
            }

            Player player = (Player) sender;



            if (player.hasPermission("lobby.fly")) {
                if (Parkour.currentParkour.containsKey(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "You cannot use /fly while in a parkour.");
                    return true; // Block the command during parkour
                } else {

                    // Toggle flight mode and handle default enabled state
                    boolean canFly = !player.getAllowFlight(); // Toggle

                    if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                        player.setAllowFlight(canFly); // Apply toggle only in survival or adventure
                        player.sendMessage(canFly ? "§3Abgehoben §8§l| §cYou can now fly." : "§3Abgehoben §8§l| §cYou can no longer fly.");
                    } else {
                        player.sendMessage(ChatColor.RED + "This command can only be used in survival or adventure.");
                    }
                }
            } else {
                player.setAllowFlight(false);
                player.sendMessage("You don't have permission to use this command.");
            }

            return true;
        }
        return false;
    }


    // Helper methods (keep these)
    private void giveLobbyItems(Player player) {
        // Navigator Compass
        ItemStack navigator = createItem(Material.COMPASS, 0, ChatColor.GREEN + "Game Menu §7(Right-Click)",
                Arrays.asList(ChatColor.GRAY + "Right click to open the server selector"));
        player.getInventory().setItem(0, navigator); // Far left slot

        // Server Info Book
        ItemStack serverInfo = createItem(Material.BOOK, 0, "§bServer Information §7(Right-Click)",
                Arrays.asList(ChatColor.GRAY + "Right click to show server infos"));
        player.getInventory().setItem(1, serverInfo); // Next to Navigator

        // CosmeticsBox
        ItemStack Cosmeticsbox = createItem(Material.CHEST, 0, "§bCosmetics Box §7(Right-Click)",
                Arrays.asList(ChatColor.GRAY + "Right click to open the Cosmetics Box"));
        player.getInventory().setItem(4, Cosmeticsbox); // middle slot

        // Red Sandstone Blocks
        player.getInventory().setItem(5, redSandstoneBlocks.clone()); // Give 64 red sandstone blocks in the 4th slot

        // Lobby selector
        ItemStack LobbySelector = createItem(Material.NETHER_STAR, 0, "§bLobby Selector §7(Right-Click)",
                Arrays.asList(ChatColor.GRAY + "Right click to open the Lobby selector"));
        player.getInventory().setItem(7, LobbySelector); // Next to playerVisibility Dye

        // Player Visibility Dye (starts as "shown")
        ItemStack playerVisibility = createItem(Material.LIME_DYE, 0, "§bPlayers §8§l> §aVisible §7(Right-Click)",
                Arrays.asList(ChatColor.GRAY + "§7Click to hide all players!"));
        player.getInventory().setItem(8, playerVisibility); // Slot 8

        player.updateInventory();
    }

    private void createScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("LobbyBoard", "dummy", ChatColor.DARK_AQUA + "§b§o■ §8┃ " + "§3§lAbgehobenNetwork" + " §8┃ §b§o■");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Score score1 = objective.getScore(ChatColor.GRAY + "§m§l----------------------");
        score1.setScore(7);

        Score score2 = objective.getScore(ChatColor.YELLOW + "§lPlayer:");
        score2.setScore(6);


        Score score3 = objective.getScore(ChatColor.WHITE + "  " + player.getName()); // Player's Name
        score3.setScore(5);


        Score score4 = objective.getScore(" "); // Empty line
        score4.setScore(4);


        Score score5 = objective.getScore(ChatColor.YELLOW + "§lRank:");
        score5.setScore(3);

        // Use PlaceholderAPI to get the player's rank
        Score score6 = objective.getScore(ChatColor.WHITE + "  " + PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%")); // PlaceholderAPI placeholder!
        score6.setScore(2);


        Score score8 = objective.getScore(ChatColor.GRAY + "§m§l----------------------§l"); //§l for difference between row 1 and 8
        score8.setScore(1);



        player.setScoreboard(scoreboard);
    }

    private ItemStack createItem(Material material, int data, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore); // Set the lore
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public void spawnFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fireworkMeta = firework.getFireworkMeta();

        FireworkEffect effect = FireworkEffect.builder()
                .withColor(Color.AQUA)
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withFlicker()
                .withTrail()
                .build();

        fireworkMeta.addEffect(effect);
        fireworkMeta.setPower(1);
        firework.setFireworkMeta(fireworkMeta);
    }

    public void healPlayer(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
    }

    private boolean isItemWithName(ItemStack item, String name) {  // Improved item check
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName().equals(name);
        }
        return false;
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    private ItemStack createRedSandstoneBlocks() {
        ItemStack blocks = new ItemStack(Material.RED_SANDSTONE, 64);
        ItemMeta meta = blocks.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Golden Blocks");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "These blocks are quite heavy... mentally", ChatColor.GRAY + "Definitely not stolen from a desert temple"));
        meta.setUnbreakable(true);
        blocks.setItemMeta(meta);
        return blocks;
    }
    private boolean isOnCooldown(UUID playerUUID) {
        if (launchCooldowns.containsKey(playerUUID)) {
            long lastLaunch = launchCooldowns.get(playerUUID);
            long currentTime = System.currentTimeMillis();
            return (currentTime - lastLaunch) < 500; // 500 milliseconds = 0.5 seconds
        }
        return false; // No cooldown found for player
    }
    // Helper function to check if near launchpad (with radius and location object)
    private boolean isNearLaunchpad(Location playerLocation, Location launchpadLocation, double radius) {
        return playerLocation.distance(launchpadLocation) <= radius;
    }
    private boolean isRestrictedArea(Location location) {
        if (restrictedCenter.getWorld() == null) {
            restrictedCenter.setWorld(location.getWorld()); // Set world on first use
        }
        return location.distance(restrictedCenter) <= restrictedRadius;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (isRestrictedArea(player.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot place blocks here!");
            return;
        }

        if (isItemWithName(itemInHand, ChatColor.GOLD + "Golden Blocks") && itemInHand.getType() == Material.RED_SANDSTONE) {
            Location blockLocation = event.getBlock().getLocation();
            placedBlocks.put(blockLocation, System.currentTimeMillis());

            // Generate a unique entity ID for THIS block
            int blockEntityId = (int) (Math.random() * Integer.MAX_VALUE);

            new BukkitRunnable() {
                int damageLevel = 0;
                @Override
                public void run() {
                    if (!placedBlocks.containsKey(blockLocation)) {
                        this.cancel();
                        return;
                    }

                    if (damageLevel >= 10 ) {
                        if (placedBlocks.containsKey(blockLocation)) {
                            Block block = blockLocation.getBlock();
                            block.setType(Material.AIR);
                            placedBlocks.remove(blockLocation);
                        }
                        this.cancel();
                        return;
                    }

                    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);

                    packet.getIntegers().write(0, blockEntityId); // Use the unique block entity ID here
                    packet.getBlockPositionModifier().write(0, new BlockPosition(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ()));
                    packet.getIntegers().write(1, damageLevel);

                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    damageLevel++;

                }
            }.runTaskTimer(plugin, 10L, 10L);

            // Schedule block removal after 5 seconds
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (placedBlocks.containsKey(blockLocation)) {
                        Block block = blockLocation.getBlock();
                        block.setType(Material.AIR);
                        placedBlocks.remove(blockLocation);
                    }
                }
            }.runTaskLater(plugin, 100L);

            // Refill the inventory slot after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if(player.isOnline()){ //check if the player is still online before giving him the items again.
                    player.getInventory().setItem(5, redSandstoneBlocks.clone());
                    player.updateInventory();
                }

            }, 2L);

        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material clickedBlockType = event.getClickedBlock().getType();

            List<Material> restrictedBlocks = Arrays.asList(
                    Material.CHEST, Material.TRAPPED_CHEST,
                    Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
                    Material.BARREL, Material.SHULKER_BOX,
                    Material.HOPPER, Material.CRAFTING_TABLE // Added crafting table
            );

            if (restrictedBlocks.contains(clickedBlockType)) { //restricted block check to not block OnBlockPlace event through prevention of place
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();

        // Check if the clicked inventory is the player's main inventory
        if (clickedInventory != null && clickedInventory.getType() == InventoryType.PLAYER) {
            event.setCancelled(true);  // Only cancel if it's the main inventory
        }
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onBlockFireSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onOffHandSwap(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event)
    {
        event.setCancelled(true);
    }
}