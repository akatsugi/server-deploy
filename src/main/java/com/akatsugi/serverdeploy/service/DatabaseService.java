package com.akatsugi.serverdeploy.service;

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
            throw new IllegalStateException("无法创建应用数据目录: " + dataDir, e);
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
                    + "last_used INTEGER NOT NULL DEFAULT 0"
                    + ")");
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 SQLite 失败", e);
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
            throw new IllegalStateException("读取工作区失败", e);
        }
        return items;
    }

    public WorkspaceConfig saveWorkspace(String workspacePath) {
        if (workspacePath == null || workspacePath.trim().isEmpty()) {
            throw new IllegalArgumentException("工作区路径不能为空");
        }

        String normalized = workspacePath.trim();
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
            throw new IllegalStateException("保存工作区失败", e);
        }
    }

    public List<ServerConfig> listServers() {
        List<ServerConfig> items = new ArrayList<ServerConfig>();
        String sql = "SELECT id, name, host, port, username, password, default_directory, last_used "
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
                        resultSet.getInt("last_used") == 1
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取服务器配置失败", e);
        }
        return items;
    }

    public ServerConfig saveServer(ServerConfig config) {
        validateServer(config);

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            resetServerLastUsed(connection);

            if (config.getId() == null) {
                String insert = "INSERT INTO servers(name, host, port, username, password, default_directory, last_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1)";
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
                        + "default_directory = ?, last_used = 1 WHERE id = ?";
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    fillServerStatement(statement, config);
                    statement.setLong(7, config.getId());
                    statement.executeUpdate();
                }
            }

            connection.commit();
            config.setLastUsed(true);
            return config;
        } catch (SQLException e) {
            throw new IllegalStateException("保存服务器配置失败", e);
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
            throw new IllegalStateException("更新最近使用服务器失败", e);
        }
    }

    private void validateServer(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }
        if (isBlank(config.getName()) || isBlank(config.getHost()) || isBlank(config.getUsername())
                || isBlank(config.getPassword()) || isBlank(config.getDefaultDirectory())) {
            throw new IllegalArgumentException("服务器名称、地址、用户名、密码和目录不能为空");
        }
    }

    private void fillServerStatement(PreparedStatement statement, ServerConfig config) throws SQLException {
        statement.setString(1, config.getName().trim());
        statement.setString(2, config.getHost().trim());
        statement.setInt(3, config.getPort() <= 0 ? 22 : config.getPort());
        statement.setString(4, config.getUsername().trim());
        statement.setString(5, config.getPassword());
        statement.setString(6, config.getDefaultDirectory().trim());
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
