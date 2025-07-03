package jb.minecolab.customLoginScreen;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class CustomLoginCommands {

    private final CustomLoginScreen plugin;

    public CustomLoginCommands(CustomLoginScreen plugin) {
        this.plugin = plugin;
    }

    // Método para que el jugador cambie su contraseña
    public void changePassword(Player player, String[] args) {
        if (plugin.usarAuthMe) { // Verificar si AuthMe está habilitado
            player.sendMessage("Use authme command");
            return;
        }

        if (args.length != 3) {
            player.sendMessage("§cUso: /cls change <oldPassword> <newPassword>");
            return;
        }

        String oldPassword = args[1];
        String newPassword = args[2];

        if (checkPassword(player.getUniqueId(), oldPassword)) {
            registerPlayer(player.getUniqueId(), player.getName(), hashPassword(newPassword));
            player.sendMessage("§aTu contraseña se ha cambiado exitosamente.");
        } else {
            player.sendMessage("§cLa contraseña antigua es incorrecta.");
        }
    }

    // Método para que el administrador elimine una cuenta
    public void deletePlayer(CommandSender sender, String[] args) {
        if (plugin.usarAuthMe) { // Verificar si AuthMe está habilitado
            sender.sendMessage("Use authme command");
            return;
        }

        if (!sender.hasPermission("customloginscreen.admin.delete")) {
            sender.sendMessage("§cNo tienes permiso para usar este comando.");
            return;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUso: /cls delplayer <playerName>");
            return;
        }

        String playerName = args[1];
        String sql = "DELETE FROM players WHERE username = ?";
        try (Connection conn = plugin.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            stmt.executeUpdate();
            sender.sendMessage("§aLa cuenta del jugador " + playerName + " ha sido eliminada.");
        } catch (SQLException e) {
            sender.sendMessage("§cError al eliminar la cuenta de " + playerName);
            e.printStackTrace();
        }
    }

    // Método para que el administrador cambie la contraseña de otro jugador
    public void changePlayerPassword(CommandSender sender, String[] args) {
        if (plugin.usarAuthMe) { // Verificar si AuthMe está habilitado
            sender.sendMessage("Use authme command");
            return;
        }

        if (!sender.hasPermission("customloginscreen.admin.changeplayerpassword")) {
            sender.sendMessage("§cNo tienes permiso para usar este comando.");
            return;
        }

        if (args.length != 3) {
            sender.sendMessage("§cUso: /cls chplayer <playerName> <newPassword>");
            return;
        }

        String playerName = args[1];
        String newPassword = args[2];
        String sql = "UPDATE players SET password = ? WHERE username = ?";
        try (Connection conn = plugin.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hashPassword(newPassword));
            stmt.setString(2, playerName);
            stmt.executeUpdate();
            sender.sendMessage("§aLa contraseña del jugador " + playerName + " ha sido cambiada.");
        } catch (SQLException e) {
            sender.sendMessage("§cError al cambiar la contraseña del jugador " + playerName);
            e.printStackTrace();
        }
    }

    // Método para verificar si un jugador está registrado
    public boolean isRegistered(UUID uuid) {
        if (plugin.usarAuthMe) return false;

        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            return result.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean checkPassword(UUID uuid, String passwordAttempt) {
        if (plugin.usarAuthMe) return false;

        String sql = "SELECT password FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                String storedHash = result.getString("password");
                return checkPasswordHash(passwordAttempt, storedHash);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String hashPassword(String password) {
        return Integer.toHexString(password.hashCode());
    }

    private boolean checkPasswordHash(String passwordAttempt, String storedHash) {
        return hashPassword(passwordAttempt).equals(storedHash);
    }

    public void registerPlayer(UUID uuid, String username, String passwordHash) {
        if (plugin.usarAuthMe) return;

        String sql = "INSERT INTO players (uuid, username, password) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setString(3, passwordHash);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
