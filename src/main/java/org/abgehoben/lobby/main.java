package org.abgehoben.lobby;

import org.bukkit.plugin.java.JavaPlugin;
import org.abgehoben.lobby.LobbySelector;
import org.abgehoben.lobby.GameMenu;

public final class main extends JavaPlugin {

    private DailyRewards DailyRewards;
    private Parkour Parkour;
    private Lobby Lobby;  // Field for Lobby class
    private CosmeticsBox CosmeticsBox;

    @Override
    public void onEnable() {
        getLogger().info("Lobby plugin is enabling...");
        Parkour = new Parkour(this); // instantiate Parkour
        Lobby = new Lobby(this);     // Instantiate Lobby
        DailyRewards = new DailyRewards(this); // Instantiate DailyRewards
        CosmeticsBox = new CosmeticsBox(this); // Instantiate CosmeticsBox

        getCommand("p").setExecutor(Parkour);
        getCommand("pstart").setExecutor(Parkour);
        getCommand("fly").setExecutor(Lobby);
        getCommand("cosmetics").setExecutor(CosmeticsBox);
        getCommand("particles").setExecutor(CosmeticsBox);

        getServer().getPluginManager().registerEvents(Lobby, this);    // Register Lobby events
        getServer().getPluginManager().registerEvents(Parkour, this); // Register events
        getServer().getPluginManager().registerEvents(DailyRewards, this);
        getServer().getPluginManager().registerEvents(CosmeticsBox, this);
        getLogger().info("Lobby plugin enabled!");
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
}