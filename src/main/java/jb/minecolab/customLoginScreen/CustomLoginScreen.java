package jb.minecolab.customLoginScreen;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

public class CustomLoginScreen extends JavaPlugin implements Listener {

    private Connection connection;
    public boolean usarAuthMe;
    private final HashMap<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private CustomLoginCommands commandHandler;

    @Override
    public void onEnable() {
        int pluginId = 23623; // Reemplaza con el ID de tu plugin en bStats
        new Metrics(this, pluginId);
        getLogger().info("bStats metrics initialized.");
        createPluginFolder();
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("AuthMe") != null) {
            usarAuthMe = true;
            getLogger().info("AuthMe detected, disabling internal database.");
        } else {
            usarAuthMe = false;
            getLogger().info("AuthMe not detected, enabling internal database.");
            connectToDatabase();
            createTable();
        }

        commandHandler = new CustomLoginCommands(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("customlogin").setExecutor(this);
        getLogger().info("CustomLoginScreen enabled.");
    }

    @Override
    public void onDisable() {
        if (!usarAuthMe && connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void createPluginFolder() {
        File folder = getDataFolder();
        if (!folder.exists() && folder.mkdirs()) {
            getLogger().info("Plugin folder created.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage(null);

        playerInventories.put(player.getUniqueId(), player.getInventory().getContents());
        player.getInventory().clear();
        getLogger().info("Player inventory hidden.");

        if (getConfig().getBoolean("useResourcePack.enabled")) {
            String resourcePackUrl = getConfig().getString("useResourcePack.url");
            String resourcePackHash = getConfig().getString("useResourcePack.hash");

            if (resourcePackUrl == null || resourcePackUrl.isEmpty()) {
                getLogger().warning("Resource pack URL is not set in config!");
                return;
            }

            byte[] hashBytes = null;
            if (resourcePackHash != null && resourcePackHash.length() == 40) {
                try {
                    hashBytes = hexStringToByteArray(resourcePackHash);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Error in hash format: " + resourcePackHash);
                    return;
                }
            } else {
                getLogger().warning("Invalid resource pack hash in config!");
                return;
            }

            player.setResourcePack(resourcePackUrl, hashBytes,
                    "Please accept this resource pack to enjoy the full experience.",
                    true);

        } else {
            if (usarAuthMe) {
                if (!AuthMeApi.getInstance().isRegistered(player.getName())) {
                    new RegisterInterface(this, player);
                } else {
                    new LoginInterface(this, player);
                }
            } else {
                if (!isRegistered(player.getUniqueId())) {
                    new RegisterInterface(this, player);
                } else {
                    new LoginInterface(this, player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        restoreInventory(player);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        if (event.getStatus() == Status.SUCCESSFULLY_LOADED) {
            if (usarAuthMe) {
                if (!AuthMeApi.getInstance().isRegistered(player.getName())) {
                    new RegisterInterface(this, player);
                } else {
                    new LoginInterface(this, player);
                }
            } else {
                if (!isRegistered(player.getUniqueId())) {
                    new RegisterInterface(this, player);
                } else {
                    new LoginInterface(this, player);
                }
            }
        } else if (event.getStatus() == Status.DECLINED) {
            player.kickPlayer("You must accept the resource pack to play on this server.");
        }
    }

    public void restoreInventory(Player player) {
        ItemStack[] items = playerInventories.get(player.getUniqueId());
        if (items != null) {
            player.getInventory().setContents(items);
            playerInventories.remove(player.getUniqueId());
        }
    }

    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public void connectToDatabase() {
        try {
            String url = "jdbc:sqlite:plugins/CustomLoginScreen/players.db";
            connection = DriverManager.getConnection(url);
            getLogger().info("Connected to the internal database.");
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to the internal database.");
            e.printStackTrace();
        }
    }

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid TEXT PRIMARY KEY," +
                "username TEXT," +
                "password TEXT)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isRegistered(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            return result.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("customlogin") || command.getName().equalsIgnoreCase("cls")) {
            if (args.length == 0) {
                sender.sendMessage("§cUsage: /cls <reload|change|delplayer|chplayer>");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("customloginscreen.reload")) {
                    sender.sendMessage("§cYou don't have permission to use this command.");
                    return true;
                }
                reloadConfig();
                sender.sendMessage("§aConfiguration reloaded successfully.");
            } else if (args[0].equalsIgnoreCase("change") && sender instanceof Player) {
                commandHandler.changePassword((Player) sender, args);
            } else if (args[0].equalsIgnoreCase("delplayer")) {
                commandHandler.deletePlayer(sender, args);
            } else if (args[0].equalsIgnoreCase("chplayer")) {
                commandHandler.changePlayerPassword(sender, args);
            } else {
                sender.sendMessage("§cUnknown subcommand.");
            }
            return true;
        }
        return false;
    }

    public Connection getConnection() {
        return connection;
    }

    public CustomLoginCommands getLoginCommands() {
        return commandHandler;
    }

    public String hashPassword(String password) {
        return Integer.toHexString(password.hashCode());
    }
}
