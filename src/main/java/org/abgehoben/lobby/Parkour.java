package org.abgehoben.lobby;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;


import java.io.File;
import java.io.IOException;
import java.util.*;

public class Parkour implements Listener, CommandExecutor {

    private ProtocolManager protocolManager;
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    private Map<String, Map<Integer, Location>> parkourCheckpoints = new HashMap<>();
    private Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private Map<UUID, Integer> currentCheckpoint = new HashMap<>();
    public static Map<UUID, String> currentParkour = new HashMap<>();
    private Map<UUID, Set<Integer>> reachedCheckpoints = new HashMap<>();
    private static final double CHECKPOINT_RADIUS = 0.75;
    private Set<UUID> playersOnPressurePlate = new HashSet<>();
    private Map<UUID, Long> startTime = new HashMap<>();
    private final Map<UUID, Boolean> originalFlyState = new HashMap<>(); // Store original fly state

    public Parkour(JavaPlugin plugin) {
        protocolManager = ProtocolLibrary.getProtocolManager(); // Initialize ProtocolLib
        this.plugin = plugin;
        createConfig();
        loadCheckpoints();
    }

    private void createConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false); // Use saveResource
            plugin.getLogger().info("Config file created!");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }


    private void loadCheckpoints() {
        parkourCheckpoints.clear();
        if (config.isConfigurationSection("parkours")) {
            ConfigurationSection parkoursSection = config.getConfigurationSection("parkours");
            for (String parkourName : parkoursSection.getKeys(false)) {
                Map<Integer, Location> checkpoints = new HashMap<>();
                ConfigurationSection checkpointSection = parkoursSection.getConfigurationSection(parkourName);
                for (String key : checkpointSection.getKeys(false)) {
                    try {
                        int checkpointNumber = Integer.parseInt(key);
                        String checkpointString = checkpointSection.getString(key);
                        Location location = parseLocation(checkpointString);
                        if (location != null) {
                            checkpoints.put(checkpointNumber, location);
                        } else {
                            plugin.getLogger().warning("Invalid location format for " + parkourName + ", checkpoint " + key);
                        }

                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid checkpoint number for " + parkourName + ", checkpoint " + key);
                    }
                }
                parkourCheckpoints.put(parkourName, checkpoints);
            }
        } else if (config.contains("checkpoints")) { // Migrate old format
            Map<Integer, Location> defaultCheckpoints = new HashMap<>();
            for (String key : config.getConfigurationSection("checkpoints").getKeys(false)) {
                try {
                    int checkpointNumber = Integer.parseInt(key);
                    String checkpointString = config.getString("checkpoints." + key);
                    Location location = parseLocation(checkpointString);
                    if (location != null) {
                        defaultCheckpoints.put(checkpointNumber, location);
                    } else {
                        plugin.getLogger().warning("Invalid checkpoint format in config.yml: " + checkpointString);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid checkpoint data in config.yml: " + key);
                }

            }

            if (!defaultCheckpoints.isEmpty()) {
                parkourCheckpoints.put("default", defaultCheckpoints);
                saveCheckpoints(); // Save in the new format after migrating
            }

        }

    }


    private Location parseLocation(String locationString) {
        String[] parts = locationString.split(",");
        if (parts.length == 6) {  // Changed from 4 to 6 to include yaw and pitch
            try {
                World world = Bukkit.getWorld(parts[0]);
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                double yaw = Double.parseDouble(parts[4]);  // Parse yaw
                double pitch = Double.parseDouble(parts[5]); // Parse pitch
                Location location = new Location(world, x, y, z, (float) yaw, (float) pitch);  // Use yaw and pitch
                return location;
            } catch (NumberFormatException | NullPointerException e) {
                return null;
            }
        }
        return null;
    }


    private void saveCheckpoints() {
        config.set("parkours", null); // Clear old data if exists.

        for (Map.Entry<String, Map<Integer, Location>> parkourEntry : parkourCheckpoints.entrySet()) {
            String parkourName = parkourEntry.getKey();
            for (Map.Entry<Integer, Location> checkpointEntry : parkourEntry.getValue().entrySet()) {
                int checkpointNumber = checkpointEntry.getKey();
                Location location = checkpointEntry.getValue();

                // Save yaw and pitch
                double yaw = location.getYaw();
                double pitch = location.getPitch();


                String locationString = location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ() + "," + yaw + "," + pitch;
                config.set("parkours." + parkourName + "." + checkpointNumber, locationString);
            }
        }

        try {
            config.save(configFile);
            plugin.getLogger().info("Parkour checkpoints saved to config.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
        }
    }




    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;



        if (command.getName().equalsIgnoreCase("p")) {
            if (player.hasPermission("Lobby.register.parkour")) {
                if (args.length == 2) {
                    try {
                        int checkpointNumber = Integer.parseInt(args[0]);
                        String parkourName = args[1];

                        if (player.getLocation().getBlock().getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                            Map<Integer, Location> checkpoints = parkourCheckpoints.computeIfAbsent(parkourName, k -> new HashMap<>());
                            checkpoints.put(checkpointNumber, player.getLocation());
                            saveCheckpoints();
                            player.sendMessage(ChatColor.GREEN + "Checkpoint " + checkpointNumber + " added/updated for parkour " + parkourName + "!");
                        } else {
                            player.sendMessage(ChatColor.RED + "You must be standing on a golden pressure plate.");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid checkpoint number.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /p <checkpoint_number> <parkour_name>");
                }
            } else {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            }
            return true;

        } else if (command.getName().equalsIgnoreCase("pstart")) {
            if (args.length == 1) {
                String parkourName = args[0];

                Map<Integer, Location> checkpoints = parkourCheckpoints.get(parkourName);

                List<Map.Entry<Integer, Location>> sortedCheckpoints = new ArrayList<>(checkpoints.entrySet());
                Collections.sort(sortedCheckpoints, Comparator.comparingInt(Map.Entry::getKey));
                List<Location> orderedCheckpoints = new ArrayList<>();
                for (Map.Entry<Integer, Location> entry : sortedCheckpoints) {
                    orderedCheckpoints.add(entry.getValue());
                }

                if (!orderedCheckpoints.isEmpty()) { // Check if there are checkpoints
                    player.teleport(orderedCheckpoints.get(0).clone().add(0, 0, -4)); // Teleport slightly behind start
                } else {
                    player.sendMessage("No checkpoints are set for this parkour!");
                    endParkour(player); // End parkour to avoid issues
                    return true; // Stop execution
                }
            } else {
                player.sendMessage(ChatColor.RED + "Usage: /pstart <parkour_name>");
            }
            return true;
        }
        return false;
    }


    private void startParkour(Player player, String parkourName) {
        Map<Integer, Location> checkpoints = parkourCheckpoints.get(parkourName);

        if (checkpoints == null || checkpoints.size() <= 1) {
            player.sendMessage(ChatColor.RED + "Parkour '" + parkourName + "' not found or has insufficient checkpoints!");
            return;
        }

        // Save original fly state and disable flight if enabled
        originalFlyState.put(player.getUniqueId(), player.getAllowFlight());
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
        }


        savedInventories.put(player.getUniqueId(), player.getInventory().getContents());
        currentCheckpoint.put(player.getUniqueId(), 1);
        currentParkour.put(player.getUniqueId(), parkourName);  // Store the parkour name
        player.getInventory().clear();
        reachedCheckpoints.put(player.getUniqueId(), new HashSet<>());
        giveParkourItems(player);
        startTime.put(player.getUniqueId(), System.currentTimeMillis());
        startTimer(player);
        player.sendMessage(ChatColor.GREEN + "§lParkour challenge started!");
        reachedCheckpoints.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(0);
    }



    private void giveParkourItems(Player player) {
        ItemStack leaveBed = createItem(Material.RED_BED, ChatColor.YELLOW + "Leave parkour");
        ItemStack checkpointTP = createItem(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, ChatColor.YELLOW + "Back to checkpoint");
        ItemStack restartDoor = createItem(Material.OAK_DOOR, ChatColor.YELLOW + "Restart parkour");

        player.getInventory().setItem(0, leaveBed);
        player.getInventory().setItem(4, checkpointTP);
        player.getInventory().setItem(8, restartDoor);
    }


    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { // Always check if meta is not null
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        } else {
            plugin.getLogger().warning("ItemMeta is null for " + material.name()); // Log the error
        }
        return item;
    }


    private boolean isNearCheckpoint(Location playerLocation, Location checkpoint) {
        return playerLocation.getWorld().equals(checkpoint.getWorld()) && playerLocation.distance(checkpoint) <= CHECKPOINT_RADIUS;
    }


    @EventHandler
    public void onPressurePlateStep(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (player.getLocation().getBlock().getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {

            if (!currentParkour.containsKey(playerId)) { // Outside of a parkour
                for (Map.Entry<String, Map<Integer, Location>> parkourEntry : parkourCheckpoints.entrySet()) {
                    String parkourName = parkourEntry.getKey();
                    Map<Integer, Location> checkpoints = parkourEntry.getValue();

                    if (checkpoints != null && !checkpoints.isEmpty()) {
                        List<Location> orderedCheckpoints = getOrderedCheckpoints(checkpoints);
                        for (int i = 0; i < orderedCheckpoints.size(); i++) {
                            if (isNearCheckpoint(player.getLocation(), orderedCheckpoints.get(i))) {
                                if (i == 0) {
                                    startParkour(player, parkourName);


                                } else {
                                    player.sendMessage(ChatColor.YELLOW + "Go to the start to begin the parkour: " + parkourName);
                                }
                                return; // Exit after finding a match
                            }
                        }
                    }
                }
            } else { // Inside a parkour
                String parkourName = currentParkour.get(playerId);
                Map<Integer, Location> checkpoints = parkourCheckpoints.get(parkourName);

                if (checkpoints != null && !checkpoints.isEmpty()) {
                    List<Location> orderedCheckpoints = getOrderedCheckpoints(checkpoints);
                    int index = currentCheckpoint.get(playerId);

                    if (index < orderedCheckpoints.size() && isNearCheckpoint(player.getLocation(), orderedCheckpoints.get(index))) {
                        if (!reachedCheckpoints.get(playerId).contains(index)) { // Only display the message once per checkpoint

                            long currentTime = System.currentTimeMillis();
                            long elapsedTime = currentTime - startTime.get(playerId);
                            String formattedTime = formatTime(elapsedTime);

                            player.sendMessage("§a§lYou reached §eCheckpoint #" + (index) + "§a§l after §e§l" + formattedTime + "§a§l"); // Display current checkpoint number
                            reachedCheckpoints.get(playerId).add(index);

                        }
                        if (index + 1 < orderedCheckpoints.size()) {

                            currentCheckpoint.put(playerId, index + 1);
                        } else {
                            endParkour(player);
                        }
                    }
                }

            }
        } else {
            playersOnPressurePlate.remove(playerId);
        }
    }






    private List<Location> getOrderedCheckpoints(Map<Integer, Location> checkpoints) {

        List<Map.Entry<Integer, Location>> sortedCheckpoints = new ArrayList<>(checkpoints.entrySet());
        Collections.sort(sortedCheckpoints, Comparator.comparingInt(Map.Entry::getKey));

        List<Location> orderedCheckpoints = new ArrayList<>();
        for (Map.Entry<Integer, Location> entry : sortedCheckpoints) {
            orderedCheckpoints.add(entry.getValue());
        }


        return orderedCheckpoints;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!currentParkour.containsKey(player.getUniqueId())) return;
        CancelParkour(player);
    }



    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!currentParkour.containsKey(player.getUniqueId())) return; // If no parkour is running, return



        String parkourName = currentParkour.get(player.getUniqueId());
        Map<Integer, Location> checkpoints = parkourCheckpoints.get(parkourName);
        if (checkpoints == null) {
            return;
        }



        List<Location> orderedCheckpoints = getOrderedCheckpoints(checkpoints);

        if (currentCheckpoint.containsKey(player.getUniqueId())) {
            int index = currentCheckpoint.get(player.getUniqueId());
            if (index > 0 && index <= orderedCheckpoints.size()) {
                Location lastCheckpoint = orderedCheckpoints.get(index - 1);

                if (event.getTo().getY() < lastCheckpoint.getY() - 2) {
                    player.teleport(lastCheckpoint);
                    player.sendMessage(ChatColor.YELLOW + "Too far down!");
                }
            }

        }
    }


    private void startTimer(final Player player) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (startTime.containsKey(player.getUniqueId()) && currentParkour.containsKey(player.getUniqueId())) {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - startTime.get(player.getUniqueId());
                String formattedTime = formatTime(elapsedTime);
                sendActionBar(player, formattedTime);


            }


        }, 0L, 1L); // Update every second (20 ticks)

    }


    public void sendActionBar(Player player, String message) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SET_ACTION_BAR_TEXT);
        packet.getChatComponents().write(0, WrappedChatComponent.fromText(ChatColor.translateAlternateColorCodes('&', "&a" + message)));

        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatTime(long millis) {
        long milliseconds = (millis / 10) % 100;
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;


        return String.format("%02d:%02d:%02d", minutes, seconds, milliseconds);
    }



    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!currentParkour.containsKey(player.getUniqueId())) {
            return;
        }


        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }


        String parkourName = currentParkour.get(player.getUniqueId());
        Map<Integer, Location> checkpoints = parkourCheckpoints.get(parkourName);

        if (checkpoints == null) {
            return; // handle case where parkour doesn't exist
        }

        List<Location> orderedCheckpoints = getOrderedCheckpoints(checkpoints);


        if (item.getType() == Material.RED_BED && item.getItemMeta().getDisplayName().equals(ChatColor.YELLOW + "Leave parkour")) {
            CancelParkour(player);


        } else if (item.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE && item.getItemMeta().getDisplayName().equals(ChatColor.YELLOW + "Back to checkpoint")) {
            int lastCheckpointIndex = currentCheckpoint.get(player.getUniqueId()) > 0 ? currentCheckpoint.get(player.getUniqueId()) - 1 : 0;

            if (lastCheckpointIndex < orderedCheckpoints.size()) {
                player.teleport(orderedCheckpoints.get(lastCheckpointIndex));
            }


        } else if (item.getType() == Material.OAK_DOOR && item.getItemMeta().getDisplayName().equals(ChatColor.YELLOW + "Restart parkour")) {
            if (!orderedCheckpoints.isEmpty()) {
                player.teleport(orderedCheckpoints.get(0));
                RestartParkour(player);
            } else {
                player.sendMessage("This parkour has no checkpoints set!");
            }
        }


    }


    private void endParkour(Player player) {
        if (startTime.containsKey(player.getUniqueId())) {
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime.get(player.getUniqueId());
            String formattedTime = formatTime(elapsedTime);
            player.sendMessage(ChatColor.GOLD + "Parkour finished! Your time: " + formattedTime);
            startTime.remove(player.getUniqueId()); // Stop and remove the timer

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendActionBar(player,""); // Clear action bar after a delay

            }, 40L); // Clear after 2 seconds (40 ticks)
        }

        // Restore flight if originally enabled, has permission and not in creative/spectator
        if (originalFlyState.containsKey(player.getUniqueId())) {
            boolean originalFly = originalFlyState.get(player.getUniqueId());
            if (originalFly) {
                if (player.hasPermission("lobby.fly")) {
                    if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                        player.setAllowFlight(true);
                    }
                }
            }
            originalFlyState.remove(player.getUniqueId()); // Clean up
        }

        player.getInventory().setContents(savedInventories.remove(player.getUniqueId()));
        currentCheckpoint.remove(player.getUniqueId());
        currentParkour.remove(player.getUniqueId()); // Remove player from parkour
        reachedCheckpoints.remove(player.getUniqueId());
    }


    private void RestartParkour(Player player) {
        if (startTime.containsKey(player.getUniqueId())) { //cancel parkour
            startTime.remove(player.getUniqueId()); // Stop and remove the timer

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendActionBar(player,""); // Clear action bar after a delay

            }, 40L); // Clear after 2 seconds (40 ticks)
        }
        currentCheckpoint.remove(player.getUniqueId());
        reachedCheckpoints.remove(player.getUniqueId());

        currentCheckpoint.put(player.getUniqueId(), 0);
        reachedCheckpoints.put(player.getUniqueId(), new HashSet<>());

        startTime.put(player.getUniqueId(), System.currentTimeMillis()); // Start timer
        startTimer(player); // Start sending timer updates to action bar

        player.sendMessage(ChatColor.GREEN + "§lParkour challenge restarted");
    }

    private void CancelParkour(Player player) {
        if (startTime.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "§lParkour challenge cancelled");
            startTime.remove(player.getUniqueId()); // Stop and remove the timer

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendActionBar(player,""); // Clear action bar after a delay

            }, 40L); // Clear after 2 seconds (40 ticks)
        }

        player.getInventory().setContents(savedInventories.remove(player.getUniqueId()));
        currentCheckpoint.remove(player.getUniqueId());
        currentParkour.remove(player.getUniqueId());   // Remove current parkour
        reachedCheckpoints.remove(player.getUniqueId());

        // Restore flight if originally enabled, has permission and not in creative/spectator
        if (originalFlyState.containsKey(player.getUniqueId())) {
            boolean originalFly = originalFlyState.get(player.getUniqueId());
            if (originalFly) {
                if (player.hasPermission("lobby.fly")) {
                    if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                        player.setAllowFlight(true);
                    }
                }
            }
            originalFlyState.remove(player.getUniqueId()); // Clean up
        }
    }


    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (currentCheckpoint.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {  // Simplified
        Player player = (Player) event.getWhoClicked();
        if (currentCheckpoint.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

}
