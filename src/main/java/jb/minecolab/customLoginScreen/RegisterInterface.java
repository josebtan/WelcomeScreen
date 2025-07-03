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

public class RegisterInterface implements Listener {

    private final CustomLoginScreen plugin;
    private final CustomLoginCommands loginCommands; // Marcar final y eliminar inicialización a null
    private final Player player;
    private final boolean useAuthMe;

    public RegisterInterface(CustomLoginScreen plugin, Player player) {
        this.plugin = plugin;
        this.loginCommands = plugin.getLoginCommands(); // Inicializar desde plugin
        this.player = player;
        this.useAuthMe = plugin.usarAuthMe;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        showRegisterInterface(player);
    }

    // Mostrar la interfaz de registro utilizando AnvilGUI
    public void showRegisterInterface(Player player) {
        // Crear el ítem de cabeza de jugador
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName(ChatColor.GRAY + plugin.getConfig().getString("messages.enter-password", "Enter your password"));
            playerHead.setItemMeta(skullMeta);
        }

        // Crear el ítem de barrera (para salir)
        ItemStack barrier = new ItemStack(Material.BARRIER);
        var barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName(ChatColor.RED + plugin.getConfig().getString("messages.quit", "Quit"));
            barrier.setItemMeta(barrierMeta);
        }

        // Configuración del título
        String title;
        if (plugin.getConfig().getBoolean("useResourcePack.enabled")) {
            title = ChatColor.WHITE + "Ⓡ";
        } else {
            title = ChatColor.GREEN + "REGISTER";
        }

        new AnvilGUI.Builder()
                .itemLeft(playerHead)
                .itemRight(barrier)
                .text("Password")
                .plugin(plugin)
                .title(title)
                .onClick((slot, stateSnapshot) -> {
                    if (slot == AnvilGUI.Slot.OUTPUT) {
                        String passwordAttempt = stateSnapshot.getText();

                        if (passwordAttempt != null && !passwordAttempt.isEmpty()) {
                            if (useAuthMe) {
                                AuthMeApi authMeApi = AuthMeApi.getInstance();
                                if (!authMeApi.isRegistered(player.getName())) {
                                    authMeApi.registerPlayer(player.getName(), passwordAttempt);
                                    player.sendMessage(plugin.getConfig().getString("messages.register-success", "Registration successful with AuthMe."));

                                    authMeApi.forceLogin(player);
                                    plugin.restoreInventory(player);
                                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                } else {
                                    player.sendMessage(plugin.getConfig().getString("messages.player-already-registered", "This player is already registered with AuthMe."));
                                    return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(plugin.getConfig().getString("messages.already-registered-short", "Already registered")));
                                }
                            } else {
                                // Usar el método isRegistered de CustomLoginCommands
                                if (!loginCommands.isRegistered(player.getUniqueId())) {
                                    String hashedPassword = plugin.hashPassword(passwordAttempt);
                                    loginCommands.registerPlayer(player.getUniqueId(), player.getName(), hashedPassword); // Llama a registerPlayer de plugin
                                    player.sendMessage(plugin.getConfig().getString("messages.register-success", "Registration successful."));
                                    plugin.restoreInventory(player);
                                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                                } else {
                                    player.sendMessage(plugin.getConfig().getString("messages.player-already-registered", "This player is already registered."));
                                    return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(plugin.getConfig().getString("messages.already-registered-short", "Already registered")));
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
            if (event.getView().getTitle().contains("Ⓡ")) {
                // Solo cancelar el arrastre del ítem del Slot.OUTPUT
                if (event.getSlot() == AnvilGUI.Slot.OUTPUT) {
                    event.setCancelled(true);  // No permitir sacar el ítem
                    return;  // Permitir que el clic sea procesado sin interferencia
                }

                // Verificar si el jugador está interactuando con el BARRIER
                if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                    // Manejo del clic en la barrera (salida)
                    player.kickPlayer(plugin.getConfig().getString("messages.quit-message", "You have left the server."));
                    event.setCancelled(true);  // Cancelar la interacción para evitar bugs en la GUI
                    return;  // Salir para que no procese el resto del código
                }

                // Cancelar la interacción con cualquier otro slot
                event.setCancelled(true);
            }
        }
    }
}
