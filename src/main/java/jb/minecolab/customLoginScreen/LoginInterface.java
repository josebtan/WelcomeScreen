package jb.minecolab.customLoginScreen;

import fr.xephi.authme.api.v3.AuthMeApi;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;

import static org.bukkit.Bukkit.getLogger;

public class LoginInterface implements Listener {

    private final CustomLoginScreen plugin;
    private final CustomLoginCommands loginCommands; // Añadir referencia a CustomLoginCommands
    private final Player player;
    private final boolean useAuthMe;

    public LoginInterface(CustomLoginScreen plugin, Player player) {
        this.plugin = plugin;
        this.loginCommands = plugin.getLoginCommands(); // Corrección: usar getLoginCommands()
        this.player = player;
        this.useAuthMe = plugin.usarAuthMe;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        showLoginInterface(player);
    }

    // Mostrar la interfaz de login usando AnvilGUI
    public void showLoginInterface(Player player) {
        // Crear el ítem de la cabeza del jugador para mostrar en el AnvilGUI
        getLogger().info("mostrando login ");
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            playerHead.setItemMeta(skullMeta);
        }

        // Crear el ítem de la barrera (usado para salir de la interfaz)
        ItemStack barrier = new ItemStack(Material.BARRIER);
        var barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName(plugin.getConfig().getString("messages.quit", "Quit"));
            barrier.setItemMeta(barrierMeta);
        }

        // Establecer el título dependiendo de si el resource pack está activado o no
        String title;
        if (plugin.getConfig().getBoolean("useResourcePack.enabled")) {
            title = ChatColor.WHITE + "Ⓛ";
        } else {
            title = ChatColor.GREEN + "LOGIN";
        }

        new AnvilGUI.Builder()
                .itemLeft(playerHead)
                .itemRight(barrier)
                .text("Password")
                .plugin(plugin)
                .title(title)  // Título de acuerdo a la configuración
                .onClick((slot, stateSnapshot) -> {
                    if (slot == AnvilGUI.Slot.OUTPUT) {
                        String passwordAttempt = stateSnapshot.getText();

                        if (passwordAttempt != null && !passwordAttempt.isEmpty()) {
                            if (useAuthMe) {
                                AuthMeApi authMeApi = AuthMeApi.getInstance();
                                if (authMeApi.checkPassword(player.getName(), passwordAttempt)) {
                                    player.sendMessage(plugin.getConfig().getString("messages.login-success", "Login successful. Welcome!"));
                                    authMeApi.forceLogin(player);
                                    plugin.restoreInventory(player);
                                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                } else {
                                    player.sendMessage(plugin.getConfig().getString("messages.invalid-password", "Incorrect password. Please try again."));
                                    return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(plugin.getConfig().getString("messages.invalid-password-short", "Invalid password")));
                                }
                            } else {
                                if (loginCommands.checkPassword(player.getUniqueId(), passwordAttempt)) { // Usar la referencia a loginCommands
                                    player.sendMessage(plugin.getConfig().getString("messages.login-success", "Login successful. Welcome!"));
                                    plugin.restoreInventory(player);
                                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                } else {
                                    player.sendMessage(plugin.getConfig().getString("messages.invalid-password", "Incorrect password. Please try again."));
                                    return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(plugin.getConfig().getString("messages.invalid-password-short", "Invalid password")));
                                }
                            }
                        }
                    }
                    return Collections.emptyList();
                })
                .preventClose()
                .open(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked().equals(player)) {
            if (event.getView().getTitle().contains("Ⓛ") || event.getView().getTitle().equals(ChatColor.GREEN + "LOGIN")) {

                // Solo cancelar el arrastre del ítem del Slot.OUTPUT
                if (event.getSlot() == AnvilGUI.Slot.OUTPUT) {
                    event.setCancelled(true);
                    return;
                }

                // Verificar si el jugador está interactuando con el BARRIER
                if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                    player.kickPlayer(plugin.getConfig().getString("messages.quit-message", "You have left the server."));
                    event.setCancelled(true);
                    return;
                }

                // Cancelar la interacción con cualquier otro slot
                event.setCancelled(true);
            }
        }
    }
}
