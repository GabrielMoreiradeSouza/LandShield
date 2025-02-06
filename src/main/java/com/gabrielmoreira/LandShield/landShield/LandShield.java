package com.gabrielmoreira.LandShield.landShield;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LandShield extends JavaPlugin implements Listener {

    private Connection connection;
    private final Map<UUID, ProtectedArea> protectedAreas = new HashMap<>();
    private final Map<Player, Location[]> playerSelections = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDatabase();
        loadProtectedAreas();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("LandShield ativado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Salvando áreas protegidas antes de desligar...");
        saveProtectedAreas();
        closeDatabaseConnection();
        getLogger().info("LandShield desativado!");
    }

    private void setupDatabase() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            getLogger().severe("Driver PostgreSQL não encontrado: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        FileConfiguration config = getConfig();
        String host = config.getString("database.host");
        int port = config.getInt("database.port");
        String database = config.getString("database.database");
        String username = config.getString("database.username");
        String password = config.getString("database.password");

        try {
            getLogger().info("Conectando ao banco de dados em: jdbc:postgresql://" + host + ":" + port + "/" + database);
            connection = DriverManager.getConnection(
                    "jdbc:postgresql://" + host + ":" + port + "/" + database,
                    username, password
            );
            createTableIfNotExists();
            getLogger().info("Conexão com o banco de dados estabelecida com sucesso.");
        } catch (SQLException e) {
            getLogger().severe("Erro ao conectar ao banco de dados: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS protected_areas (" +
                "id SERIAL PRIMARY KEY," +
                "owner UUID NOT NULL," +
                "x_min INT NOT NULL," +
                "x_max INT NOT NULL," +
                "z_min INT NOT NULL," +
                "z_max INT NOT NULL," +
                "y INT NOT NULL," +
                "last_logout BIGINT DEFAULT 0" +
                ")";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    private void loadProtectedAreas() {
        if (connection == null) {
            getLogger().severe("Conexão com o banco de dados não foi estabelecida. Áreas protegidas não serão carregadas.");
            return;
        }

        try {
            String sql = "SELECT * FROM protected_areas";
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    int xMin = rs.getInt("x_min");
                    int xMax = rs.getInt("x_max");
                    int zMin = rs.getInt("z_min");
                    int zMax = rs.getInt("z_max");
                    int y = rs.getInt("y");
                    long lastLogout = rs.getLong("last_logout");

                    protectedAreas.put(owner, new ProtectedArea(owner, xMin, xMax, zMin, zMax, y, lastLogout));
                }
            }
            getLogger().info("Áreas protegidas carregadas com sucesso!");
        } catch (SQLException e) {
            getLogger().severe("Erro ao carregar áreas protegidas: " + e.getMessage());
        }
    }

    private void saveProtectedAreas() {
        if (connection == null) {
            getLogger().severe("Conexão com o banco de dados não foi estabelecida. Áreas protegidas não serão salvas.");
            return;
        }

        try {
            String deleteSql = "DELETE FROM protected_areas";
            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                deleteStmt.executeUpdate();
            }

            String insertSql = "INSERT INTO protected_areas (owner, x_min, x_max, z_min, z_max, y, last_logout) VALUES (?::uuid, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                for (ProtectedArea area : protectedAreas.values()) {
                    insertStmt.setString(1, area.getOwner().toString());
                    insertStmt.setInt(2, area.getxMin());
                    insertStmt.setInt(3, area.getxMax());
                    insertStmt.setInt(4, area.getzMin());
                    insertStmt.setInt(5, area.getzMax());
                    insertStmt.setInt(6, area.getY());
                    insertStmt.setLong(7, area.getLastLogout());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                getLogger().info("Áreas protegidas salvas com sucesso!");
            }
        } catch (SQLException e) {
            getLogger().severe("Erro ao salvar áreas protegidas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void closeDatabaseConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                getLogger().severe("Erro ao fechar a conexão com o banco de dados: " + e.getMessage());
            }
        }
    }

    private void removeProtectedArea(Player player) {
        UUID owner = player.getUniqueId();

        if (!protectedAreas.containsKey(owner)) {
            player.sendMessage("§cVocê não possui nenhuma proteção para remover.");
            return;
        }

        protectedAreas.remove(owner);

        try {
            String deleteSql = "DELETE FROM protected_areas WHERE owner = ?::uuid";
            try (PreparedStatement stmt = connection.prepareStatement(deleteSql)) {
                stmt.setString(1, owner.toString());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    player.sendMessage("§aProteção removida com sucesso!");
                } else {
                    player.sendMessage("§cErro ao remover a proteção.");
                }
            }
        } catch (SQLException e) {
            player.sendMessage("§cErro ao remover proteção no banco de dados.");
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("removerclaim")) {
            removeProtectedArea(player);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Verificar se o jogador está segurando um stick
        if (player.getInventory().getItemInMainHand().getType() == Material.STICK) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                Location loc = event.getClickedBlock().getLocation();
                ProtectedArea area = findProtectedArea(loc);
                if (area != null) {
                    String ownerName = Bukkit.getOfflinePlayer(area.getOwner()).getName();
                    long lastLogout = area.getLastLogout();

                    long currentTime = System.currentTimeMillis();
                    long daysSinceLastLogin = (currentTime - lastLogout) / (1000 * 60 * 60 * 24);

                    player.sendMessage("§aEssa proteção pertence a: §e" + ownerName + " §a(e seu último login foi há §e" + daysSinceLastLogin + " dias§a).");
                } else {
                    player.sendMessage("§cNinguém protegeu aqui!");
                }
                event.setCancelled(true);
            }
            return;
        }

        // Verificar se o jogador está segurando o machado de ouro
        if (player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_AXE) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                Location pos1 = event.getClickedBlock().getLocation();
                playerSelections.putIfAbsent(player, new Location[2]);
                playerSelections.get(player)[0] = pos1;
                player.sendMessage("§aPosição 1 definida em: " + formatLocation(pos1));
                event.setCancelled(true);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Location pos2 = event.getClickedBlock().getLocation();
                playerSelections.putIfAbsent(player, new Location[2]);
                playerSelections.get(player)[1] = pos2;

                if (playerSelections.get(player)[0] == null) {
                    player.sendMessage("§cDefina a posição 1 antes de criar uma proteção.");
                    return;
                }

                createProtectedArea(player, playerSelections.get(player)[0], pos2);
                event.setCancelled(true);
            }
        }
    }

    private ProtectedArea findProtectedArea(Location loc) {
        for (ProtectedArea area : protectedAreas.values()) {
            if (area.isInside(loc)) {
                return area;
            }
        }
        return null;
    }

    private void createProtectedArea(Player player, Location pos1, Location pos2) {
        int xMin = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int xMax = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int zMin = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int zMax = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int y = pos1.getBlockY();

        ProtectedArea area = new ProtectedArea(player.getUniqueId(), xMin, xMax, zMin, zMax, y, System.currentTimeMillis());
        protectedAreas.put(player.getUniqueId(), area);

        addFences(pos1.getWorld(), xMin, xMax, zMin, zMax, y + 1);
        player.sendMessage("§aProteção criada com sucesso!");
    }

    private void addFences(org.bukkit.World world, int xMin, int xMax, int zMin, int zMax, int y) {
        for (int x = xMin; x <= xMax; x++) {
            world.getBlockAt(x, y, zMin).setType(Material.OAK_FENCE);
            world.getBlockAt(x, y, zMax).setType(Material.OAK_FENCE);
        }

        for (int z = zMin; z <= zMax; z++) {
            world.getBlockAt(xMin, y, z).setType(Material.OAK_FENCE);
            world.getBlockAt(xMax, y, z).setType(Material.OAK_FENCE);
        }
    }

    private String formatLocation(Location loc) {
        return String.format("X: %d, Y: %d, Z: %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
