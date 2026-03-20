package com.akatsugi.serverdeploy.idea.settings;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ServerDeploySettingsState {

    private String defaultShellCommand = "ls -l ${remotePath}";
    private List<ServerConfig> servers = new ArrayList<>();
    private List<DirectoryMapping> mappings = new ArrayList<>();

    public String getDefaultShellCommand() {
        return defaultShellCommand;
    }

    public void setDefaultShellCommand(String defaultShellCommand) {
        this.defaultShellCommand = defaultShellCommand;
    }

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers == null ? new ArrayList<>() : servers;
    }

    public List<DirectoryMapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<DirectoryMapping> mappings) {
        this.mappings = mappings == null ? new ArrayList<>() : mappings;
    }

    public ServerDeploySettingsState copy() {
        ServerDeploySettingsState copy = new ServerDeploySettingsState();
        copy.setDefaultShellCommand(defaultShellCommand);
        copy.setServers(servers.stream().map(ServerConfig::copy).collect(Collectors.toList()));
        copy.setMappings(mappings.stream().map(DirectoryMapping::copy).collect(Collectors.toList()));
        return copy;
    }
}
