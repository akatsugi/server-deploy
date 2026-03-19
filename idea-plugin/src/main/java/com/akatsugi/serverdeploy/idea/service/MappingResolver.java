package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MappingResolver {

    public List<ResolvedUploadTarget> resolve(Path selectedPath, List<ServerConfig> servers, List<DirectoryMapping> mappings) {
        Path normalizedSelection = selectedPath.toAbsolutePath().normalize();
        Map<String, ServerConfig> serverById = servers.stream()
                .collect(Collectors.toMap(ServerConfig::getId, server -> server, (left, right) -> left, LinkedHashMap::new));

        Map<String, Candidate> bestByServer = new LinkedHashMap<>();
        for (DirectoryMapping mapping : mappings) {
            ServerConfig server = serverById.get(mapping.getServerId());
            if (server == null) {
                continue;
            }

            Path localBase = Path.of(ServerDeploySettingsService.normalizeLocalDirectory(mapping.getLocalDirectory()));
            if (!normalizedSelection.startsWith(localBase)) {
                continue;
            }

            Candidate candidate = new Candidate(
                    server,
                    mapping,
                    buildRemoteTarget(server, mapping, localBase.relativize(normalizedSelection)),
                    localBase.getNameCount()
            );

            Candidate current = bestByServer.get(server.getId());
            if (current == null || candidate.depth > current.depth) {
                bestByServer.put(server.getId(), candidate);
            }
        }

        return bestByServer.values().stream()
                .sorted(Comparator.comparing(candidate -> safe(candidate.server.getName())))
                .map(candidate -> new ResolvedUploadTarget(candidate.server, candidate.mapping, candidate.remoteTargetPath))
                .collect(Collectors.toList());
    }

    private String buildRemoteTarget(ServerConfig server, DirectoryMapping mapping, Path relativePath) {
        String remoteBase = ServerDeploySettingsService.resolveMappingBaseDirectory(
                server.getDefaultDirectory(),
                mapping.getRemoteDirectory()
        );
        if (relativePath == null || relativePath.getNameCount() == 0) {
            return remoteBase;
        }
        String relative = relativePath.toString().replace('\\', '/');
        return ServerDeploySettingsService.joinRemotePath(remoteBase, relative);
    }

    private String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private static class Candidate {
        private final ServerConfig server;
        private final DirectoryMapping mapping;
        private final String remoteTargetPath;
        private final int depth;

        private Candidate(ServerConfig server, DirectoryMapping mapping, String remoteTargetPath, int depth) {
            this.server = server;
            this.mapping = mapping;
            this.remoteTargetPath = remoteTargetPath;
            this.depth = depth;
        }
    }
}