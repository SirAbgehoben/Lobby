//CosmeticsBox.java             1. rename items 2. add commands for particle menu and turn on / off 3. add glow 4. add permissions

package org.abgehoben.lobby;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.CommandExecutor;

import java.sql.SQLException;
import java.util.*;
import java.util.function.BiConsumer;

public class CosmeticsBox implements Listener, CommandExecutor{

    private final MySQLManager mySQLManager;
    private boolean connectedToDatabase = false;
    private final org.abgehoben.lobby.main plugin;
    private final Map<UUID, BukkitRunnable> activeParticleTasks = new HashMap<>();

    private final Map<String, BiConsumer<Player, Particle>> particleShapes = new HashMap<>();
    private final Map<UUID, Particle> selectedParticles = new HashMap<>();
    private final Map<UUID, Integer> particleMenuPage = new HashMap<>();
    private final Map<UUID, String> selectedShapes = new HashMap<>(); // Store selected shape name
    private final Map<UUID, Boolean> particlesEnabled = new HashMap<>();

    private final Map<UUID, Integer> shapeMenuPage = new HashMap<>(); // Add this field


    private final List<Particle> displayParticles = Arrays.asList(
            Particle.VILLAGER_ANGRY,
            Particle.ASH,
            Particle.BUBBLE_COLUMN_UP,
            Particle.BUBBLE_POP,
            Particle.CAMPFIRE_COSY_SMOKE,
            Particle.CAMPFIRE_SIGNAL_SMOKE,
            Particle.CLOUD,
            Particle.COMPOSTER,
            Particle.CRIT,
            Particle.CRIT_MAGIC,
            Particle.DAMAGE_INDICATOR,
            Particle.DOLPHIN,
            Particle.DRAGON_BREATH,
            Particle.DRIPPING_DRIPSTONE_LAVA,
            Particle.DRIPPING_DRIPSTONE_WATER,
            Particle.DRIPPING_HONEY,
            Particle.DRIPPING_OBSIDIAN_TEAR,
            Particle.REDSTONE,
            Particle.DUST_COLOR_TRANSITION,
            Particle.ELECTRIC_SPARK,
            Particle.ENCHANTMENT_TABLE,
            Particle.END_ROD,
            Particle.EXPLOSION_LARGE,
            Particle.FALLING_DUST,
            Particle.FLAME,
            Particle.FLASH,
            Particle.GLOW,
            Particle.GLOW_SQUID_INK,
            Particle.VILLAGER_HAPPY,
            Particle.HEART,
            Particle.SMOKE_LARGE,
            Particle.LAVA,
            Particle.TOWN_AURA,
            Particle.NAUTILUS,
            Particle.NOTE,
            Particle.EXPLOSION_NORMAL,
            Particle.PORTAL,
            Particle.WATER_SPLASH,
            Particle.SCULK_CHARGE,
            Particle.SMOKE_NORMAL,
            Particle.SNOWFLAKE,
            Particle.SOUL,
            Particle.SOUL_FIRE_FLAME,
            Particle.SPELL,
            Particle.SQUID_INK,
            Particle.SWEEP_ATTACK,
            Particle.WARPED_SPORE,
            Particle.WHITE_ASH
    );
    private Material getMaterialForParticle(Particle particle) {
        switch (particle) {
            case VILLAGER_ANGRY: return Material.EMERALD;
            case ASH: return Material.GUNPOWDER;
            case BUBBLE_COLUMN_UP: return Material.SOUL_SAND;
            case BUBBLE_POP: return Material.WATER_BUCKET;
            case CAMPFIRE_COSY_SMOKE: return Material.CAMPFIRE;
            case CAMPFIRE_SIGNAL_SMOKE: return Material.HAY_BLOCK;
            case CLOUD: return Material.WHITE_WOOL;
            case COMPOSTER: return Material.COMPOSTER;
            case CRIT: return Material.IRON_SWORD;
            case CRIT_MAGIC: return Material.DIAMOND_SWORD;
            case DAMAGE_INDICATOR: return Material.RED_DYE;
            case DOLPHIN: return Material.HEART_OF_THE_SEA;
            case DRAGON_BREATH: return Material.DRAGON_BREATH;
            case DRIPPING_DRIPSTONE_LAVA: return Material.LAVA_BUCKET;
            case DRIPPING_DRIPSTONE_WATER: return Material.WATER_BUCKET;
            case DRIPPING_HONEY: return Material.HONEY_BOTTLE;
            case DRIPPING_OBSIDIAN_TEAR: return Material.CRYING_OBSIDIAN;
            case REDSTONE: return Material.GLOWSTONE_DUST;
            case DUST_COLOR_TRANSITION: return Material.REDSTONE; // Generic color effect
            case ELECTRIC_SPARK: return Material.REDSTONE_TORCH;
            case ENCHANTMENT_TABLE: return Material.ENCHANTING_TABLE;
            case END_ROD: return Material.END_ROD;
            case EXPLOSION_LARGE: return Material.TNT;
            case FALLING_DUST: return Material.SAND;
            case FLAME: return Material.BLAZE_POWDER;
            case FLASH: return Material.FIREWORK_STAR;
            case GLOW: return Material.GLOWSTONE_DUST;
            case GLOW_SQUID_INK: return Material.GLOW_INK_SAC;
            case VILLAGER_HAPPY: return Material.EMERALD;
            case HEART: return Material.RED_DYE;
            case SMOKE_LARGE: return Material.SMOKER;
            case LAVA: return Material.LAVA_BUCKET;
            case TOWN_AURA: return Material.MYCELIUM;
            case NAUTILUS: return Material.NAUTILUS_SHELL;
            case NOTE: return Material.NOTE_BLOCK;
            case EXPLOSION_NORMAL: return Material.FIREWORK_STAR;
            case PORTAL: return Material.ENDER_PEARL;
            case WATER_SPLASH: return Material.WATER_BUCKET;
            case SCULK_CHARGE: return Material.SCULK_SENSOR;
            case SMOKE_NORMAL: return Material.SMOKER;
            case SNOWFLAKE: return Material.SNOWBALL;
            case SOUL: return Material.SOUL_SAND;
            case SOUL_FIRE_FLAME: return Material.SOUL_TORCH;
            case SPELL: return Material.POTION;
            case SQUID_INK: return Material.INK_SAC;
            case SWEEP_ATTACK: return Material.IRON_SWORD;
            case WARPED_SPORE: return Material.WARPED_FUNGUS;
            case WHITE_ASH: return Material.BASALT;

            default: return Material.GLOWSTONE_DUST; // Fallback if no mapping found
        }
    }



    public CosmeticsBox(main plugin) {
        this.plugin = plugin;
        this.mySQLManager = new MySQLManager(plugin, "CloudCosmetics", "192.168.178.105", 3306, "Minecraft", "6778_Minecraft");

        try {
            mySQLManager.connect();
            connectedToDatabase = true;
            plugin.getLogger().info("Successfully connected to the database.");
            createTableIfNotExists(); // Create table after successful connection
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            connectedToDatabase = false;
        }

        particleShapes.put("Circle", this::circleShape);
        particleShapes.put("Star", this::starShape);
        particleShapes.put("Wings", this::wingsShape);
        particleShapes.put("PointUnder", this::pointUnder);
        particleShapes.put("PointAbove", this::pointAbove);
        // ... register other shape functions
    }


    public void disable() {
        mySQLManager.disconnect();
        for (BukkitRunnable task : activeParticleTasks.values()) {
            task.cancel();
        }
        activeParticleTasks.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadPlayerCosmetics(player);
        // ... any other join event logic ...
    }


    private void createTableIfNotExists() {
        if (connectedToDatabase) {
            try {
                String createTableSQL = "CREATE TABLE IF NOT EXISTS player_particles (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "particle VARCHAR(255)," +
                        "enabled BOOLEAN," +
                        "shape VARCHAR(255)" + // Add shape column
                        ")";
                mySQLManager.execute(createTableSQL);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error creating table: " + e.getMessage());
            }
        }
    }

    public void loadPlayerCosmetics(Player player) {  // Call this on player join
        if (connectedToDatabase) {
            try {
                mySQLManager.query("SELECT particle, enabled, shape FROM player_particles WHERE uuid = ?", rs -> {
                    try {
                        if (rs.next()) {
                            String particleName = rs.getString("particle");
                            boolean enabled = rs.getBoolean("enabled");
                            String shape = rs.getString("shape"); // Load shape

                            if (particleName != null) {
                                selectedParticles.put(player.getUniqueId(), Particle.valueOf(particleName));
                            }
                            particlesEnabled.put(player.getUniqueId(), enabled);
                            if (shape != null) {
                                selectedShapes.put(player.getUniqueId(), shape);
                            }

                            if(enabled){ // If enabled, start effect
                                reloadParticleEffect(player);
                            }


                        }
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Error loading player cosmetics: " + e.getMessage());
                    }


                    return null; // Return null is fine here as we are just setting values in maps

                }, player.getUniqueId().toString());
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading player cosmetics: " + e.getMessage());
            }
        }
    }




    public void openCosmeticsBox(Player player) {
        Inventory cosmeticsInventory = Bukkit.createInventory(null, 27, "Cosmetics Box");
        cosmeticsInventory.setItem(12, createItem(Material.NETHER_STAR, "Particle Effects", null));
        player.openInventory(cosmeticsInventory);
    }





    private void handleShapeMenuClick(Player player, String itemName, int page, InventoryClickEvent event) {
        if (itemName.equals("§eNext Page")) {
            openParticleShapeMenu(player, page + 1);
            shapeMenuPage.put(player.getUniqueId(), page + 1);
        } else if (itemName.equals("§ePrevious Page")) {
            openParticleShapeMenu(player, page - 1);
            shapeMenuPage.put(player.getUniqueId(), page - 1);
        } else {
            setSelectedShape(player, itemName); // Store the selected shape
            player.closeInventory();
            player.sendMessage("§aShape §7" + itemName + " §aselected!");
            reloadParticleEffect(player); // Reload the effect with the new shape
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cosmetics")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
                return true;
            }
            Player player = (Player) sender;

            openCosmeticsBox(player);
        }
        if (command.getName().equalsIgnoreCase("particles")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
                return true;
            }
            Player player = (Player) sender;

            toggleParticles(player);
        }
        return false;
    }

    @EventHandler
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        String itemName = clickedItem.getItemMeta().getDisplayName();
        String inventoryTitle = event.getView().getTitle();

        if (inventoryTitle.startsWith("Particle Menu - Page ")) {
            try {
                int page = Integer.parseInt(inventoryTitle.split(" - ")[1].split(" ")[1]) - 1;
                handleParticleMenuClick(player, itemName, page, event);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid inventory title format: " + inventoryTitle);
            }
        } else if (inventoryTitle.startsWith("Shape Menu - Page ")) { // Handle Shape Menu clicks
            try {
                int page = Integer.parseInt(inventoryTitle.split(" - ")[1].split(" ")[1]) - 1;
                handleShapeMenuClick(player, itemName, page, event);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid inventory title format: " + inventoryTitle);
            }
        } else { // Handle other inventory clicks
            switch (inventoryTitle) {
                case "Cosmetics Box":
                    if (itemName.equals("Particle Effects")) {
                        openParticleMainMenu(player);
                    }
                    break;
                case "Particle Effects":
                    if (itemName.startsWith("§bParticles §8§l> §")) {
                        toggleParticles(player);
                        openParticleMainMenu(player); // Reopen to update the button
                    } else if (itemName.equals("Select Shape")) {
                        openParticleShapeMenu(player);
                    } else if (itemName.equals("Select Particle Effect")) {
                        openParticleMenu(player);
                    }
                    break;
                case "Shape Menu": // This case is no longer needed because shape menu clicks are now handled in the "Shape Menu - Page " block.
                    //If you still have non-paginated shape menus, please keep this section
                    Particle currentParticle = selectedParticles.get(player.getUniqueId());
                    BiConsumer<Player, Particle> selectedShape = particleShapes.get(itemName);

                    if (currentParticle != null && selectedShape != null) {
                        player.closeInventory();
                        startParticleEffect(player, currentParticle, selectedShape);
                    } else if (currentParticle == null) {
                        player.closeInventory();
                        player.sendMessage("§cPlease select a particle type first!");
                    } else {
                        player.closeInventory();
                        player.sendMessage("§cPlease select a shape!");
                    }
                    break;
                default:
                    break;

            }
        }
    }



    private void handleParticleMenuClick(Player player, String itemName, int page, InventoryClickEvent event) {
        if (itemName.equals("§eNext Page")) {
            int nextPage = (page + 1) % ((displayParticles.size() + 26) / 27); // Correct modulo calculation
            particleMenuPage.put(player.getUniqueId(), nextPage);
            openParticleMenu(player, nextPage);
        } else if (itemName.equals("§ePrevious Page")) {
            int prevPage = (page - 1 + ((displayParticles.size() + 26) / 27)) % ((displayParticles.size() + 26) / 27); // Corrected §ePrevious Page calculation
            particleMenuPage.put(player.getUniqueId(), prevPage);
            openParticleMenu(player, prevPage);

        } else if (displayParticles.stream().anyMatch(p -> p.name().equals(itemName.toUpperCase()))) {


            try {
                Particle particle = Particle.valueOf(itemName.toUpperCase());
                setSelectedParticle(player, particle);
                player.closeInventory();
                player.sendMessage("§aParticle §7" + particle.name() + " §aselected!");


            } catch (IllegalArgumentException e) {

                player.sendMessage("§cInvalid particle selected.");
            }

        }
    }


    private void setSelectedParticle(Player player, Particle particle) {
        selectedParticles.put(player.getUniqueId(), particle);

        if (connectedToDatabase) {
            try {
                mySQLManager.update("INSERT INTO player_particles (uuid, particle) VALUES (?, ?) ON DUPLICATE KEY UPDATE particle = ?",
                        player.getUniqueId().toString(), particle.name(), particle.name());
            } catch (SQLException e) {
                plugin.getLogger().severe("Error setting player particle: " + e.getMessage());
                player.sendMessage("§cError saving particle selection to the database.");
            }
        } else {
            player.sendMessage("§cNot connected to the database. Particle selection will not be saved permanently.");
        }



        String selectedShapeName = selectedShapes.get(player.getUniqueId());
        // Check if shape is already set (possibly from db load)
        String shapeName = selectedShapes.get(player.getUniqueId());
        if (shapeName != null){
            reloadParticleEffect(player);
        } else if (selectedShapeName != null) {
            BiConsumer<Player, Particle> selectedShape = particleShapes.get(selectedShapeName);
            if (selectedShape != null) {
                startParticleEffect(player, particle, selectedShape);
                player.closeInventory(); // Close the inventory *here*
            } else {
                player.sendMessage("§cPreviously selected shape not found. Please choose a new shape.");
                openParticleShapeMenu(player); // Open shape menu only if the stored shape is invalid
            }
        } else {
            player.sendMessage("§aParticle §7" + particle.name() + " §aselected! Please choose a shape.");
            openParticleShapeMenu(player); // Open shape menu if no shape has been previously selected
        }
    }

    private void setSelectedShape(Player player, String shapeName) {
        selectedShapes.put(player.getUniqueId(), shapeName);

        if (connectedToDatabase) {
            try {
                mySQLManager.update("UPDATE player_particles SET shape = ? WHERE uuid = ?", shapeName, player.getUniqueId().toString());
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving selected shape: " + e.getMessage());
            }
        }
        reloadParticleEffect(player); // Reload the effect immediately
    }


    private Particle getCurrentParticle(Player player) {
        if (!connectedToDatabase) {

            player.sendMessage("§cNot connected to the database. Cannot retrieve particle selection.");
            return null;

        }
        try {
            return mySQLManager.query("select Particle Effect FROM player_particles WHERE uuid = ?",
                    resultSet -> {
                        try {
                            if (resultSet.next()) {
                                return Particle.valueOf(resultSet.getString("particle"));

                            }
                        } catch (SQLException e) {

                            plugin.getLogger().severe("Error getting player particle: " + e.getMessage());
                        }
                        return null;

                    }, player.getUniqueId().toString());

        } catch (SQLException e) {

            plugin.getLogger().severe("Error getting player particle: " + e.getMessage());

            return null;
        }
    }


    private void toggleParticles(Player player) {
        UUID uuid = player.getUniqueId();
        boolean enabled = !particlesEnabled.getOrDefault(uuid, false);
        particlesEnabled.put(uuid, enabled);

        if (enabled) {
            reloadParticleEffect(player);
        } else {
            stopParticleEffect(player);
        }

        if (connectedToDatabase) {
            try {
                mySQLManager.update("UPDATE player_particles SET enabled = ? WHERE uuid = ?", enabled, uuid.toString());
            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating particle status: " + e.getMessage());
            }
        }
    }



    private void openParticleMenu(Player player) {
        int currentPage = particleMenuPage.getOrDefault(player.getUniqueId(), 0);
        openParticleMenu(player, currentPage);
    }



    private void openParticleMenu(Player player, int page) {
        Inventory particleMenu = Bukkit.createInventory(null, 36, "Particle Menu - Page " + (page + 1));
        int startIndex = page * 27;
        int endIndex = Math.min(startIndex + 27, displayParticles.size());

        for (int i = startIndex; i < endIndex; i++) {
            Particle particle = displayParticles.get(i);
            particleMenu.setItem(i - startIndex, createItem(getMaterialForParticle(particle), particle.name(), null));
        }

        if (page > 0) {
            particleMenu.setItem(28, createItem(Material.ARROW, "§ePrevious Page", null));
        }
        if (endIndex < displayParticles.size()) { // Check against displayParticles.size() for "§eNext Page" visibility
            particleMenu.setItem(34, createItem(Material.ARROW, "§eNext Page", null));
        }

        player.openInventory(particleMenu);
    }



    private void openParticleMainMenu(Player player) {
        Inventory particleEffectsMenu = Bukkit.createInventory(null, 27, "Particle Effects");

        particleEffectsMenu.setItem(11, createItem(Material.GLOWSTONE_DUST, "Select Shape", null));

        // Toggle Particles button
        boolean enabled = particlesEnabled.getOrDefault(player.getUniqueId(), false);
        Material mat = enabled ? Material.LIME_DYE : Material.RED_DYE;
        String name = enabled ? "§bParticles §8§l> §aOn" : "§bParticles §8§l> §cOff";

        particleEffectsMenu.setItem(13, createItem(mat, name, null)); // Added at slot 13


        particleEffectsMenu.setItem(15, createItem(Material.FIREWORK_STAR, "Select Particle Effect", null));


        player.openInventory(particleEffectsMenu);
    }



    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();


        if (meta != null) {
            meta.setDisplayName(name);

            meta.setLore(lore);
            meta.setUnbreakable(true);

            item.setItemMeta(meta);



        }


        return item;

    }



    private void startParticleEffect(Player player, Particle particle, BiConsumer<Player, Particle> shapeFunction) {
        if (!particlesEnabled.getOrDefault(player.getUniqueId(), false)) return;

        stopParticleEffect(player);  // Stop any existing effect

        BukkitRunnable particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                shapeFunction.accept(player, particle);
            }
        };

        particleTask.runTaskTimer(plugin, 0L, 1L);
        activeParticleTasks.put(player.getUniqueId(), particleTask);
    }


    public void stopParticleEffect(Player player) {

        BukkitRunnable task = activeParticleTasks.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();


        }

    }

    private void reloadParticleEffect(Player player) {
        Particle particle = selectedParticles.get(player.getUniqueId());
        String shapeName = selectedShapes.get(player.getUniqueId());

        if (particle != null && shapeName != null) {
            BiConsumer<Player, Particle> shapeFunction = particleShapes.get(shapeName);
            if (shapeFunction != null) {
                startParticleEffect(player, particle, shapeFunction);
            } else {
                player.sendMessage("§cSelected shape not found!");
                openParticleShapeMenu(player); // Let player choose a valid shape.
            }
        } else if (particle == null) {
            player.sendMessage("§cPlease select a particle first!");
            // Consider opening particle menu here if appropriate.
        }  else if (shapeName == null){ // Handle missing Shape
            player.sendMessage("§cPlease select a shape!");
            openParticleShapeMenu(player); // Open shape menu if no shape is set.
        }
    }


    private void openParticleShapeMenu(Player player) {
        int currentPage = shapeMenuPage.getOrDefault(player.getUniqueId(), 0);
        openParticleShapeMenu(player, currentPage); // Use paginated method
    }

    private void openParticleShapeMenu(Player player, int page) {
        List<String> particleShapesList = new ArrayList<>(particleShapes.keySet()); // Create the list here
        int totalPages = (particleShapesList.size() + 8) / 9; // Calculating pages

        if (page < 0 || page >= totalPages) { // Handle Invalid page number
            page = 0;
            shapeMenuPage.put(player.getUniqueId(), 0);
        }

        Inventory shapeMenu = Bukkit.createInventory(null, 27, "Shape Menu - Page " + (page + 1)); // Increased Size and Page Title
        int startIndex = page * 27;
        int endIndex = Math.min(startIndex + 27, particleShapesList.size()); // Calculate end index

        for (int i = startIndex; i < endIndex; i++) {
            String shapeName = particleShapesList.get(i); // Get shape name from the list
            shapeMenu.setItem(i - startIndex, createItem(Material.GLOWSTONE_DUST, shapeName, null));
        }

        if (page > 0) {
            shapeMenu.setItem(18, createItem(Material.ARROW, "§ePrevious Page", null));
        }

        if (endIndex < particleShapesList.size()) { // Check end index against total shapes
            shapeMenu.setItem(26, createItem(Material.ARROW, "§eNext Page", null));
        }

        player.openInventory(shapeMenu);
    }



    private double getGroundLevel(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        int x = playerLoc.getBlockX();
        int z = playerLoc.getBlockZ();

        int y = playerLoc.getBlockY();
        while (y > 0 && !world.getBlockAt(x, y - 1, z).getType().isSolid()) {
            y--;
        }
        return y;
    }

    private void circleShape(Player player, Particle particle) {
        double radius = 2;
        int points = 60;

        double groundY = getGroundLevel(player);
        Location playerLoc = player.getLocation();
        double particleY = playerLoc.getY() > groundY + 1.5 ? playerLoc.getY() : groundY + 0.07;
        Location particleLoc = new Location(playerLoc.getWorld(), playerLoc.getX(), particleY, playerLoc.getZ());

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location spawnLoc = particleLoc.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
            player.getWorld().spawnParticle(particle, spawnLoc, 1, 0, 0, 0, 0);
        }
    }

    private void starShape(Player player, Particle particle) {
        double outerRadius = 2;
        double innerRadius = outerRadius / 2;
        int points = 5;
        int particlesPerSegment = 10;

        double groundY = getGroundLevel(player);
        Location playerLoc = player.getLocation();
        double particleY = playerLoc.getY() > groundY + 1.5 ? playerLoc.getY() : groundY + 0.07;
        Location particleLoc = new Location(playerLoc.getWorld(), playerLoc.getX(), particleY, playerLoc.getZ());

        for (int i = 0; i < points * 2; i++) {
            double angle1 = 2 * Math.PI * i / (points * 2);
            double angle2 = 2 * Math.PI * (i + 1) / (points * 2);
            double radius1 = (i % 2 == 0) ? outerRadius : innerRadius;
            double radius2 = ((i + 1) % 2 == 0) ? outerRadius : innerRadius;

            Location point1 = particleLoc.clone().add(radius1 * Math.cos(angle1), 0, radius1 * Math.sin(angle1));
            Location point2 = particleLoc.clone().add(radius2 * Math.cos(angle2), 0, radius2 * Math.sin(angle2));

            for (int j = 0; j < particlesPerSegment; j++) {
                double t = (double) j / particlesPerSegment;
                double x = point1.getX() + t * (point2.getX() - point1.getX());
                double z = point1.getZ() + t * (point2.getZ() - point1.getZ());
                Location spawnLoc = new Location(player.getWorld(), x, particleLoc.getY(), z);
                player.getWorld().spawnParticle(particle, spawnLoc, 1, 0, 0, 0, 0);
            }
        }
    }

    private void wingsShape(Player player, Particle particle) {
        // Get player's location
        Location playerLoc = player.getLocation();

        // Ellipse parameters
        double radiusX = 1.5; // X-axis radius
        double radiusZ = 0.5; // Z-axis radius
        int points = 50;      // Number of points per ellipse
        double angleOffset1 = Math.toRadians(3);  // Offset for the first ellipse
        double angleOffset2 = Math.toRadians(33); // Offset for the second ellipse

        // Rotation around the player
        double rotationAroundPlayer = Math.toRadians(playerLoc.getYaw() - 180); // Rotate the entire shape around the player

        // Additional final rotation (90° upward rotation)
        double finalRotation = Math.toRadians(90);

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;

            // --- First Ellipse ---
            // Calculate point on the ellipse
            double x1 = radiusX * Math.cos(angle);
            double z1 = radiusZ * Math.sin(angle);

            // Apply angle offset for the first ellipse
            double rotatedX1 = x1 * Math.cos(angleOffset1) - z1 * Math.sin(angleOffset1);
            double rotatedZ1 = x1 * Math.sin(angleOffset1) + z1 * Math.cos(angleOffset1);

            // Rotate the entire shape around the player
            double finalX1 = rotatedX1 * Math.cos(rotationAroundPlayer) - rotatedZ1 * Math.sin(rotationAroundPlayer);
            double finalZ1 = rotatedX1 * Math.sin(rotationAroundPlayer) + rotatedZ1 * Math.cos(rotationAroundPlayer);

            // Apply the 90° final rotation (rotate around X-axis)
            double rotatedY1 = finalZ1 * Math.sin(finalRotation);
            double rotatedZ1Final = finalZ1 * Math.cos(finalRotation);

            // Spawn particle for the first ellipse
            Location particleLoc1 = new Location(
                    playerLoc.getWorld(),
                    playerLoc.getX() + finalX1,
                    playerLoc.getY() + rotatedY1 + 1.5, // Base height
                    playerLoc.getZ() + rotatedZ1Final
            );
            player.getWorld().spawnParticle(particle, particleLoc1, 1, 0, 0, 0, 0);

            // --- Second Ellipse ---
            // Calculate point on the ellipse
            double x2 = radiusX * Math.cos(angle);
            double z2 = radiusZ * Math.sin(angle);

            // Apply angle offset for the second ellipse
            double rotatedX2 = x2 * Math.cos(angleOffset2) - z2 * Math.sin(angleOffset2);
            double rotatedZ2 = x2 * Math.sin(angleOffset2) + z2 * Math.cos(angleOffset2);

            // Rotate the entire shape around the player
            double finalX2 = rotatedX2 * Math.cos(rotationAroundPlayer) - rotatedZ2 * Math.sin(rotationAroundPlayer);
            double finalZ2 = rotatedX2 * Math.sin(rotationAroundPlayer) + rotatedZ2 * Math.cos(rotationAroundPlayer);

            // Apply the 90° final rotation (rotate around X-axis)
            double rotatedY2 = finalZ2 * Math.sin(finalRotation);
            double rotatedZ2Final = finalZ2 * Math.cos(finalRotation);

            // Spawn particle for the second ellipse
            Location particleLoc2 = new Location(
                    playerLoc.getWorld(),
                    playerLoc.getX() + finalX2,
                    playerLoc.getY() + rotatedY2 + 1.5, // Base height
                    playerLoc.getZ() + rotatedZ2Final
            );
            player.getWorld().spawnParticle(particle, particleLoc2, 1, 0, 0, 0, 0);
        }
    }




    private void pointUnder(Player player, Particle particle) {
        double groundY = getGroundLevel(player);
        Location playerLoc = player.getLocation();
        double particleY = playerLoc.getY() > groundY + 1.5 ? playerLoc.getY() : groundY + 0.07;
        Location particleLoc = new Location(playerLoc.getWorld(), playerLoc.getX(), particleY, playerLoc.getZ());
        player.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
    }

    private void pointAbove(Player player, Particle particle) {
        Location playerLoc = player.getLocation();
        Location particleLoc = new Location(playerLoc.getWorld(), playerLoc.getX(), playerLoc.getY() + 2.25, playerLoc.getZ());
        player.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
    }


}