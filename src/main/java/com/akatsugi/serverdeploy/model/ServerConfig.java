package com.akatsugi.serverdeploy.model;

public class ServerConfig {

    private Long id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    private String defaultDirectory;
    private String baseMappingDirectory;
    private boolean lastUsed;

    public ServerConfig() {
        this.port = 22;
    }

    public ServerConfig(Long id, String name, String host, int port, String username, String password,
            String defaultDirectory, String baseMappingDirectory, boolean lastUsed) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.defaultDirectory = defaultDirectory;
        this.baseMappingDirectory = baseMappingDirectory;
        this.lastUsed = lastUsed;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public String getBaseMappingDirectory() {
        return baseMappingDirectory;
    }

    public void setBaseMappingDirectory(String baseMappingDirectory) {
        this.baseMappingDirectory = baseMappingDirectory;
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
}
