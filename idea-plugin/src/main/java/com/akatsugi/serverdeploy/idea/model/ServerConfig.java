package com.akatsugi.serverdeploy.idea.model;

import java.util.Objects;
import java.util.UUID;

public class ServerConfig {

    private String id;
    private String name;
    private String host;
    private int port = 22;
    private String username;
    private String password;
    private String defaultDirectory = "/";
    private boolean lastUsed;

    public ServerConfig() {
    }

    public ServerConfig(ServerConfig source) {
        this.id = source.id;
        this.name = source.name;
        this.host = source.host;
        this.port = source.port;
        this.username = source.username;
        this.password = source.password;
        this.defaultDirectory = source.defaultDirectory;
        this.lastUsed = source.lastUsed;
    }

    public ServerConfig copy() {
        return new ServerConfig(this);
    }

    public void ensureIdentity() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (port <= 0) {
            port = 22;
        }
        if (defaultDirectory == null || defaultDirectory.isBlank()) {
            defaultDirectory = "/";
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDefaultDirectory() {
        return defaultDirectory;
    }

    public void setDefaultDirectory(String defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    public boolean isLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(boolean lastUsed) {
        this.lastUsed = lastUsed;
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerConfig that)) {
            return false;
        }
        return port == that.port
                && lastUsed == that.lastUsed
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(host, that.host)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password)
                && Objects.equals(defaultDirectory, that.defaultDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, host, port, username, password, defaultDirectory, lastUsed);
    }
}
