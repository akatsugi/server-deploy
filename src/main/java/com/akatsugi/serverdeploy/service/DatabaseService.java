package com.akatsugi.serverdeploy.service;

import com.akatsugi.serverdeploy.model.DirectoryMapping;
import com.akatsugi.serverdeploy.model.ServerConfig;
import com.akatsugi.serverdeploy.model.WorkspaceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {

    private final String jdbcUrl;

    public DatabaseService() {
        Path dataDir = Paths.get(System.getProperty("user.dir"), "app-data");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create app data directory: " + dataDir, e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dataDir.resolve("server-deploy.db").toAbsolutePath();
        initSchema();
    }

    private void initSchema() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS workspaces ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "path TEXT NOT NULL UNIQUE,"
                    + "last_used INTEGER NOT NULL DEFAULT 0"
                    + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS servers ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL UNIQUE,"
                    + "host TEXT NOT NULL,"
                    + "port INTEGER NOT NULL,"
                    + "username TEXT NOT NULL,"
                    + "password TEXT NOT NULL,"
                    + "default_directory TEXT NOT NULL,"
                    + "base_mapping_directory TEXT NOT NULL DEFAULT '',"
                    + "last_used INTEGER NOT NULL DEFAULT 0"
                    + ")");
            ensureColumnExists(connection, statement, "servers", "base_mapping_directory",
                    "ALTER TABLE servers ADD COLUMN base_mapping_directory TEXT NOT NULL DEFAULT ''");
            ensureColumnExists(connection, statement, "servers", "base_local_directory",
                    "ALTER TABLE servers ADD COLUMN base_local_directory TEXT NOT NULL DEFAULT ''");
            statement.execute("CREATE TABLE IF NOT EXISTS directory_mappings ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "server_id INTEGER NOT NULL,"
                    + "local_directory TEXT NOT NULL,"
                    + "remote_directory TEXT NOT NULL,"
                    + "UNIQUE(server_id, local_directory)"
                    + ")");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public List<WorkspaceConfig> listWorkspaces() {
        List<WorkspaceConfig> items = new ArrayList<WorkspaceConfig>();
        String sql = "SELECT id, path, last_used FROM workspaces ORDER BY last_used DESC, path ASC";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                items.add(new WorkspaceConfig(
                        resultSet.getLong("id"),
                        resultSet.getString("path"),
                        resultSet.getInt("last_used") == 1
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load workspaces", e);
        }
        return items;
    }

    public WorkspaceConfig saveWorkspace(String workspacePath) {
        if (workspacePath == null || workspacePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace path must not be empty");
        }

        String normalized = normalizeAbsoluteLocalPath(workspacePath);
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            resetWorkspaceLastUsed(connection);

            WorkspaceConfig existing = findWorkspaceByPath(connection, normalized);
            if (existing != null) {
                markWorkspaceLastUsed(connection, existing.getId());
                connection.commit();
                existing.setLastUsed(true);
                return existing;
            }

            String insert = "INSERT INTO workspaces(path, last_used) VALUES (?, 1)";
            try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, normalized);
                statement.executeUpdate();
                ResultSet keys = statement.getGeneratedKeys();
                Long id = keys.next() ? keys.getLong(1) : null;
                connection.commit();
                return new WorkspaceConfig(id, normalized, true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save workspace", e);
        }
    }

    public List<ServerConfig> listServers() {
        List<ServerConfig> items = new ArrayList<ServerConfig>();
        String sql = "SELECT id, name, host, port, username, password, default_directory, base_mapping_directory, last_used "
                + "FROM servers ORDER BY last_used DESC, name ASC";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                items.add(new ServerConfig(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("host"),
                        resultSet.getInt("port"),
                        resultSet.getString("username"),
                        resultSet.getString("password"),
                        resultSet.getString("default_directory"),
                        resultSet.getString("base_mapping_directory"),
                        resultSet.getInt("last_used") == 1
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load servers", e);
        }
        return items;
    }

    public ServerConfig saveServer(ServerConfig config) {
        validateServer(config);

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            resetServerLastUsed(connection);

            if (config.getId() == null) {
                String insert = "INSERT INTO servers(name, host, port, username, password, default_directory, base_mapping_directory, last_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
                try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                    fillServerStatement(statement, config);
                    statement.executeUpdate();
                    ResultSet keys = statement.getGeneratedKeys();
                    if (keys.next()) {
                        config.setId(keys.getLong(1));
                    }
                }
            } else {
                String update = "UPDATE servers SET name = ?, host = ?, port = ?, username = ?, password = ?, "
                        + "default_directory = ?, base_mapping_directory = ?, last_used = 1 WHERE id = ?";
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    fillServerStatement(statement, config);
                    statement.setLong(8, config.getId());
                    statement.executeUpdate();
                }
            }

            connection.commit();
            config.setLastUsed(true);
            return config;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save server", e);
        }
    }

    public void markServerLastUsed(ServerConfig config) {
        if (config == null || config.getId() == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            resetServerLastUsed(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE servers SET last_used = 1 WHERE id = ?")) {
                statement.setLong(1, config.getId());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update last used server", e);
        }
    }

    public List<DirectoryMapping> listDirectoryMappings(Long serverId) {
        List<DirectoryMapping> items = new ArrayList<DirectoryMapping>();
        if (serverId == null) {
            return items;
        }

        String sql = "SELECT id, server_id, local_directory, remote_directory FROM directory_mappings "
                + "WHERE server_id = ? ORDER BY local_directory ASC";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, serverId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                items.add(new DirectoryMapping(
                        resultSet.getLong("id"),
                        resultSet.getLong("server_id"),
                        resultSet.getString("local_directory"),
                        resultSet.getString("remote_directory")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load directory mappings", e);
        }
        return items;
    }

    public DirectoryMapping saveDirectoryMapping(Long serverId, String localDirectory, String remoteDirectory) {
        if (serverId == null) {
            throw new IllegalArgumentException("Server id must not be empty");
        }
        if (localDirectory == null || localDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Local directory must not be empty");
        }
        if (remoteDirectory == null || remoteDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Remote directory must not be empty");
        }

        String normalizedLocalDirectory = normalizeAbsoluteLocalPath(localDirectory);
        String normalizedRemoteDirectory = normalizeRelativeRemotePath(remoteDirectory);

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            DirectoryMapping existing = findDirectoryMapping(connection, serverId, normalizedLocalDirectory);
            if (existing == null) {
                String insert = "INSERT INTO directory_mappings(server_id, local_directory, remote_directory) VALUES (?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, serverId);
                    statement.setString(2, normalizedLocalDirectory);
                    statement.setString(3, normalizedRemoteDirectory);
                    statement.executeUpdate();
                    ResultSet keys = statement.getGeneratedKeys();
                    Long id = keys.next() ? keys.getLong(1) : null;
                    connection.commit();
                    return new DirectoryMapping(id, serverId, normalizedLocalDirectory, normalizedRemoteDirectory);
                }
            }

            String update = "UPDATE directory_mappings SET remote_directory = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, normalizedRemoteDirectory);
                statement.setLong(2, existing.getId());
                statement.executeUpdate();
                connection.commit();
                existing.setRemoteDirectory(normalizedRemoteDirectory);
                return existing;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save directory mapping", e);
        }
    }

    public void deleteDirectoryMapping(Long serverId, String localDirectory) {
        if (serverId == null || localDirectory == null || localDirectory.trim().isEmpty()) {
            return;
        }

        String normalizedLocalDirectory = normalizeAbsoluteLocalPath(localDirectory);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM directory_mappings WHERE server_id = ? AND local_directory = ?")) {
            statement.setLong(1, serverId);
            statement.setString(2, normalizedLocalDirectory);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete directory mapping", e);
        }
    }

    public int migrateDirectoryMappingsToServerRelative(Long serverId, String baseMappingDirectory) {
        if (serverId == null || isBlank(baseMappingDirectory)) {
            return 0;
        }

        String normalizedBaseMappingDirectory = normalizeAbsoluteRemotePath(baseMappingDirectory);
        int migratedCount = 0;
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            String legacyBaseLocalDirectory = loadLegacyBaseLocalDirectory(connection, serverId);
            String sql = "SELECT id, local_directory, remote_directory FROM directory_mappings WHERE server_id = ? ORDER BY id ASC";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, serverId);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    long id = resultSet.getLong("id");
                    String storedLocalDirectory = resultSet.getString("local_directory");
                    String storedRemoteDirectory = resultSet.getString("remote_directory");

                    String newLocalDirectory = normalizeMappingLocalFromLegacy(storedLocalDirectory, legacyBaseLocalDirectory);
                    String newRemoteDirectory = normalizeMappingRemoteFromLegacy(storedRemoteDirectory, normalizedBaseMappingDirectory);
                    if (newLocalDirectory == null || newRemoteDirectory == null) {
                        continue;
                    }

                    DirectoryMapping existing = findDirectoryMapping(connection, serverId, newLocalDirectory);
                    if (existing != null && !Long.valueOf(id).equals(existing.getId())) {
                        continue;
                    }

                    if (newLocalDirectory.equals(storedLocalDirectory) && newRemoteDirectory.equals(storedRemoteDirectory)) {
                        continue;
                    }

                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE directory_mappings SET local_directory = ?, remote_directory = ? WHERE id = ?")) {
                        update.setString(1, newLocalDirectory);
                        update.setString(2, newRemoteDirectory);
                        update.setLong(3, id);
                        update.executeUpdate();
                    }
                    migratedCount++;
                }
            }
            connection.commit();
            return migratedCount;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate directory mappings", e);
        }
    }

    private void validateServer(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Server config must not be empty");
        }
        if (isBlank(config.getName()) || isBlank(config.getHost()) || isBlank(config.getUsername())
                || isBlank(config.getPassword()) || isBlank(config.getDefaultDirectory())) {
            throw new IllegalArgumentException("Server name, host, username, password and directory must not be empty");
        }
    }

    private void fillServerStatement(PreparedStatement statement, ServerConfig config) throws SQLException {
        statement.setString(1, config.getName().trim());
        statement.setString(2, config.getHost().trim());
        statement.setInt(3, config.getPort() <= 0 ? 22 : config.getPort());
        statement.setString(4, config.getUsername().trim());
        statement.setString(5, config.getPassword());
        statement.setString(6, config.getDefaultDirectory().trim());
        statement.setString(7, normalizeOptionalAbsoluteRemotePath(config.getBaseMappingDirectory()));
    }

    private WorkspaceConfig findWorkspaceByPath(Connection connection, String workspacePath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, path, last_used FROM workspaces WHERE path = ?")) {
            statement.setString(1, workspacePath);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return new WorkspaceConfig(
                        resultSet.getLong("id"),
                        resultSet.getString("path"),
                        resultSet.getInt("last_used") == 1
                );
            }
        }
        return null;
    }

    private DirectoryMapping findDirectoryMapping(Connection connection, Long serverId, String localDirectory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, server_id, local_directory, remote_directory FROM directory_mappings "
                        + "WHERE server_id = ? AND local_directory = ?")) {
            statement.setLong(1, serverId);
            statement.setString(2, localDirectory);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return new DirectoryMapping(
                        resultSet.getLong("id"),
                        resultSet.getLong("server_id"),
                        resultSet.getString("local_directory"),
                        resultSet.getString("remote_directory")
                );
            }
        }
        return null;
    }

    private String loadLegacyBaseLocalDirectory(Connection connection, Long serverId) throws SQLException {
        if (!hasColumn(connection, "servers", "base_local_directory")) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT base_local_directory FROM servers WHERE id = ?")) {
            statement.setLong(1, serverId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                String value = resultSet.getString("base_local_directory");
                return isBlank(value) ? null : normalizeAbsoluteLocalPath(value);
            }
        }
        return null;
    }

    private String normalizeMappingLocalFromLegacy(String storedLocalDirectory, String legacyBaseLocalDirectory) {
        if (isBlank(storedLocalDirectory)) {
            return null;
        }
        Path storedPath = Paths.get(storedLocalDirectory.trim());
        if (storedPath.isAbsolute()) {
            return storedPath.toAbsolutePath().normalize().toString();
        }
        if (isBlank(legacyBaseLocalDirectory)) {
            return null;
        }
        return Paths.get(legacyBaseLocalDirectory, storedLocalDirectory.trim()).toAbsolutePath().normalize().toString();
    }

    private String normalizeMappingRemoteFromLegacy(String storedRemoteDirectory, String normalizedBaseMappingDirectory) {
        if (isBlank(storedRemoteDirectory)) {
            return null;
        }
        String trimmed = storedRemoteDirectory.trim();
        if (!looksLikeAbsoluteRemotePath(trimmed)) {
            return normalizeRelativeRemotePath(trimmed);
        }
        String normalizedRemote = normalizeAbsoluteRemotePath(trimmed);
        if (normalizedRemote.equals(normalizedBaseMappingDirectory)) {
            return ".";
        }
        String prefix = normalizedBaseMappingDirectory + "/";
        if (!normalizedRemote.startsWith(prefix)) {
            return null;
        }
        return normalizeRelativeRemotePath(normalizedRemote.substring(prefix.length()));
    }

    private void ensureColumnExists(Connection connection, Statement statement, String tableName, String columnName,
            String alterSql) throws SQLException {
        if (hasColumn(connection, tableName, columnName)) {
            return;
        }
        statement.execute(alterSql);
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resetWorkspaceLastUsed(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE workspaces SET last_used = 0")) {
            statement.executeUpdate();
        }
    }

    private void markWorkspaceLastUsed(Connection connection, Long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE workspaces SET last_used = 1 WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private void resetServerLastUsed(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE servers SET last_used = 0")) {
            statement.executeUpdate();
        }
    }

    private String normalizeAbsoluteLocalPath(String path) {
        return Paths.get(path.trim()).toAbsolutePath().normalize().toString();
    }

    private String normalizeAbsoluteRemotePath(String path) {
        String normalized = path.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeOptionalAbsoluteRemotePath(String path) {
        if (isBlank(path)) {
            return "";
        }
        return normalizeAbsoluteRemotePath(path);
    }

    private String normalizeRelativeRemotePath(String path) {
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? "." : normalized;
    }

    private boolean looksLikeAbsoluteRemotePath(String path) {
        return path.startsWith("/");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
