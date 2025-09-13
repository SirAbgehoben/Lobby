package org.abgehoben.lobby;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;


public class LobbyNPCs implements Listener {


    private final int OtherGamesNpcId;

    public LobbyNPCs(JavaPlugin plugin) {
        // NPC creation
        Location location = new Location(plugin.getServer().getWorld("world"), -23.5, 157, -18.5);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "§b§lOtherGames");
        this.OtherGamesNpcId = npc.getId();
        npc.spawn(location);

        // Skin
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinName("Lewtty");

        // Protection
        npc.setProtected(true);

        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        lookClose.setRange(50);
        lookClose.setRealisticLooking(true);



    }

    @EventHandler
    public void onNPCLeftClick(NPCLeftClickEvent event) {
        if (event.getNPC().getId() == this.OtherGamesNpcId) {
            handleNPCInteraction(event.getClicker());
        }
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        if (event.getNPC().getId() == this.OtherGamesNpcId) {
            handleNPCInteraction(event.getClicker());
        }
    }

    private void handleNPCInteraction(Player player) {
        GameMenu gameMenu = main.getGameMenu();
        gameMenu.OtherGames(player);
    }

}
