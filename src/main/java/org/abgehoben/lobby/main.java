package org.abgehoben.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.abgehoben.lobby.LobbySelector;
import org.abgehoben.lobby.GameMenu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

public final class main extends JavaPlugin {

    private Map<String, Object> config;

    private DailyRewards DailyRewards;
    private Parkour Parkour;
    private Lobby Lobby;  // Field for Lobby class
    private CosmeticsBox CosmeticsBox;
    private LobbyNPCs LobbyNPCs;
    private static GameMenu GameMenu;

    @Override
    public void onEnable() {
        getLogger().info("Lobby plugin is enabling...");

        loadMySQLData();

        Map<String, Object> cosmeticsBoxMySQLData = (Map<String, Object>) config.get("CosmeticsBox");
        Map<String, Object> dailyRewardsMySQLData = (Map<String, Object>) config.get("DailyRewards");

        Parkour = new Parkour(this);
        Lobby = new Lobby(this, cosmeticsBoxMySQLData);
        DailyRewards = new DailyRewards(this, dailyRewardsMySQLData);
        CosmeticsBox = new CosmeticsBox(this, cosmeticsBoxMySQLData);
        LobbyNPCs = new LobbyNPCs(this);
        GameMenu = new GameMenu(this);

        getCommand("p").setExecutor(Parkour);
        getCommand("pstart").setExecutor(Parkour);
        getCommand("fly").setExecutor(Lobby);
        getCommand("cosmetics").setExecutor(CosmeticsBox);
        getCommand("particles").setExecutor(CosmeticsBox);
        getCommand("glow").setExecutor(CosmeticsBox);
        getCommand("dailyreward").setExecutor(DailyRewards);

        getServer().getPluginManager().registerEvents(Lobby, this);    // Register Lobby events
        getServer().getPluginManager().registerEvents(LobbyNPCs, this);    // Register Lobby events
        getServer().getPluginManager().registerEvents(Parkour, this); // Register events
        getServer().getPluginManager().registerEvents(DailyRewards, this);
        getServer().getPluginManager().registerEvents(CosmeticsBox, this);
        getLogger().info("Lobby plugin enabled!");
    }

    private void loadMySQLData() {
        File dataFile = new File(this.getDataFolder(), "mysql.yml");

        if (dataFile.exists()) {
            try {
                // Read the existing configuration
                try (FileInputStream inputStream = new FileInputStream(dataFile)) {
                    Yaml yaml = new Yaml();
                    config = yaml.load(inputStream);
                    System.out.println("Configuration loaded");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                getLogger().info("Loading MySQL data from file: " + dataFile.getName());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error loading elevator data from file: " + dataFile.getName(), e);
            }
        } else {
            // Create the default configuration
            config = createDefaultConfig();
            saveConfig(config);
            getLogger().info("Default configuration created");
        }
    }

    private Map<String, Object> createDefaultConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> cosmeticsBox = new HashMap<>();
        cosmeticsBox.put("host", "localhost");
        cosmeticsBox.put("port", 3306);
        cosmeticsBox.put("name", "cosmetics_box_db");
        cosmeticsBox.put("user", "your_db_user");
        cosmeticsBox.put("password", "your_db_password");

        Map<String, Object> dailyRewards = new HashMap<>();
        dailyRewards.put("host", "localhost");
        dailyRewards.put("port", 3306);
        dailyRewards.put("name", "daily_rewards_db");
        dailyRewards.put("user", "your_db_user");
        dailyRewards.put("password", "your_db_password");

        config.put("CosmeticsBox", cosmeticsBox);
        config.put("DailyRewards", dailyRewards);

        return config;
    }

    private void saveConfig(Map<String, Object> config) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);

        File dataFile = new File(this.getDataFolder(), "mysql.yml");
        try (FileWriter writer = new FileWriter(dataFile)) {
            yaml.dump(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDisable() {
        getLogger().info("Lobby plugin is disabling...");
        try {
            CosmeticsBox.disable();
        } catch(Exception e) {
            getLogger().severe("Error disabling CosmeticsBox: " + e.getMessage());
        }
        getLogger().info("Lobby plugin disabled!");
    }

    public Lobby getLobby() { // Add getter for Lobby
        return Lobby;
    }
    public Parkour getParkour() { // Add getter for Parkour
        return Parkour;
    }
    public DailyRewards getDailyRewards() {
        return DailyRewards;
    }
    public CosmeticsBox getCosmeticsBox() {
        return CosmeticsBox;
    }
    public LobbyNPCs getLobbyNPCs() {
        return LobbyNPCs;
    }
    public static GameMenu getGameMenu() {
        return GameMenu;
    }
}