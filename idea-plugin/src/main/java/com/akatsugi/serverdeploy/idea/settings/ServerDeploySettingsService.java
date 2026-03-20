package com.akatsugi.serverdeploy.idea.settings;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service(Service.Level.APP)
@State(name = "ServerDeploySettings", storages = @Storage("server-deploy-plugin.xml"))
public final class ServerDeploySettingsService implements PersistentStateComponent<ServerDeploySettingsState> {

    public static final String DEFAULT_SHELL_COMMAND = "ls -l ${remotePath}";

    private ServerDeploySettingsState state = new ServerDeploySettingsState();

    public static ServerDeploySettingsService getInstance() {
        return ApplicationManager.getApplication().getService(ServerDeploySettingsService.class);
    }

    @Override
    public @Nullable ServerDeploySettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull ServerDeploySettingsState state) {
        this.state = sanitize(state.copy());
    }

    public List<ServerConfig> getServers() {
        return state.getServers().stream()
                .map(ServerConfig::copy)
                .sorted(Comparator.comparing(server -> safe(server.getName())))
                .collect(Collectors.toList());
    }

    public List<DirectoryMapping> getMappings() {
        return state.getMappings().stream()
                .map(DirectoryMapping::copy)
                .sorted(Comparator.comparing(mapping -> safe(mapping.getLocalDirectory())))
                .collect(Collectors.toList());
    }

    public String getDefaultShellCommand() {
        return normalizeShellCommand(state.getDefaultShellCommand());
    }

    public void update(List<ServerConfig> servers, List<DirectoryMapping> mappings) {
        update(servers, mappings, state.getDefaultShellCommand());
    }

    public void update(List<ServerConfig> servers, List<DirectoryMapping> mappings, String defaultShellCommand) {
        ServerDeploySettingsState newState = new ServerDeploySettingsState();
        newState.setDefaultShellCommand(defaultShellCommand);
        newState.setServers(copyServers(servers));
        newState.setMappings(copyMappings(mappings));
        state = sanitize(newState);
    }

    public ServerDeploySettingsState sanitize(ServerDeploySettingsState source) {
        List<ServerConfig> normalizedServers = copyServers(source.getServers());
        normalizedServers.forEach(ServerConfig::ensureIdentity);

        Set<String> serverIds = normalizedServers.stream()
                .map(ServerConfig::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<DirectoryMapping> normalizedMappings = copyMappings(source.getMappings()).stream()
                .peek(DirectoryMapping::ensureIdentity)
                .filter(mapping -> serverIds.contains(mapping.getServerId()))
                .filter(mapping -> !safe(mapping.getLocalDirectory()).isBlank() && !safe(mapping.getRemoteDirectory()).isBlank())
                .peek(mapping -> mapping.setLocalDirectory(normalizeLocalDirectory(mapping.getLocalDirectory())))
                .peek(mapping -> mapping.setRemoteDirectory(normalizeMappingRemoteDirectory(mapping.getRemoteDirectory())))
                .collect(Collectors.toList());

        if (!normalizedServers.isEmpty() && normalizedServers.stream().noneMatch(ServerConfig::isLastUsed)) {
            normalizedServers.get(0).setLastUsed(true);
        } else if (normalizedServers.stream().filter(ServerConfig::isLastUsed).count() > 1) {
            boolean keepFirst = true;
            for (ServerConfig server : normalizedServers) {
                if (server.isLastUsed() && keepFirst) {
                    keepFirst = false;
                    continue;
                }
                server.setLastUsed(false);
            }
        }

        ServerDeploySettingsState normalized = new ServerDeploySettingsState();
        normalized.setDefaultShellCommand(normalizeShellCommand(source.getDefaultShellCommand()));
        normalized.setServers(normalizedServers);
        normalized.setMappings(normalizedMappings);
        return normalized;
    }

    public void markServerLastUsed(String serverId) {
        for (ServerConfig server : state.getServers()) {
            server.setLastUsed(Objects.equals(server.getId(), serverId));
        }
    }

    private List<ServerConfig> copyServers(List<ServerConfig> servers) {
        if (servers == null) {
            return new ArrayList<>();
        }
        return servers.stream().map(ServerConfig::copy).collect(Collectors.toList());
    }

    private List<DirectoryMapping> copyMappings(List<DirectoryMapping> mappings) {
        if (mappings == null) {
            return new ArrayList<>();
        }
        return mappings.stream().map(DirectoryMapping::copy).collect(Collectors.toList());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static String normalizeLocalDirectory(String value) {
        return Path.of(value.trim()).toAbsolutePath().normalize().toString();
    }

    public static String normalizeRemoteDirectory(String value) {
        String normalized = value.trim().replace('\\', '/');
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

    public static String normalizeMappingRemoteDirectory(String value) {
        String normalized = value.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith("/")) {
            return normalizeRemoteDirectory(normalized);
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? "." : normalized;
    }

    public static boolean isAbsoluteRemotePath(String value) {
        return value != null && value.trim().startsWith("/");
    }

    public static String resolveMappingBaseDirectory(String defaultRemoteDirectory, String mappingRemoteDirectory) {
        String normalizedDefault = normalizeRemoteDirectory(defaultRemoteDirectory);
        String normalizedMapping = normalizeMappingRemoteDirectory(mappingRemoteDirectory);

        if (isAbsoluteRemotePath(normalizedMapping)) {
            return normalizeRemoteDirectory(normalizedMapping);
        }

        if (".".equals(normalizedMapping)) {
            return normalizedDefault;
        }
        return joinRemotePath(normalizedDefault, normalizedMapping);
    }

    public static String joinRemotePath(String base, String child) {
        String normalizedBase = normalizeRemoteDirectory(base);
        String normalizedChild = child.replace('\\', '/');
        while (normalizedChild.startsWith("/")) {
            normalizedChild = normalizedChild.substring(1);
        }
        return normalizeRemoteDirectory(normalizedBase + "/" + normalizedChild);
    }

    public static String normalizeShellCommand(String value) {
        String normalized = value == null ? "" : value.strip();
        return normalized.isEmpty() ? DEFAULT_SHELL_COMMAND : normalized;
    }
}
