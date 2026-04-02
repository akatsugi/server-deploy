package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.model.BatchUploadTarget;
import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

            String remoteMappingDirectory = ServerDeploySettingsService.resolveMappingBaseDirectory(
                    server.getDefaultDirectory(),
                    mapping.getRemoteDirectory()
            );
            Candidate candidate = new Candidate(
                    server,
                    mapping,
                    remoteMappingDirectory,
                    buildRemoteTarget(remoteMappingDirectory, localBase.relativize(normalizedSelection)),
                    localBase.getNameCount()
            );

            Candidate current = bestByServer.get(server.getId());
            if (current == null || candidate.depth > current.depth) {
                bestByServer.put(server.getId(), candidate);
            }
        }

        return bestByServer.values().stream()
                .sorted(Comparator.comparing(candidate -> safe(candidate.server.getName())))
                .map(candidate -> new ResolvedUploadTarget(
                        candidate.server,
                        candidate.mapping,
                        candidate.remoteMappingDirectory,
                        candidate.remoteTargetPath
                ))
                .collect(Collectors.toList());
    }

    public List<BatchUploadTarget> resolveBatch(List<Path> selectedPaths, List<ServerConfig> servers, List<DirectoryMapping> mappings) {
        List<Path> normalizedSelections = selectedPaths.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .collect(Collectors.toList());
        if (normalizedSelections.isEmpty()) {
            return List.of();
        }

        List<Map<String, ResolvedUploadTarget>> resolvedBySelection = new ArrayList<>();
        for (Path selection : normalizedSelections) {
            Map<String, ResolvedUploadTarget> byServerId = resolve(selection, servers, mappings).stream()
                    .collect(Collectors.toMap(
                            target -> target.getServerConfig().getId(),
                            target -> target,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            if (byServerId.isEmpty()) {
                return List.of();
            }
            resolvedBySelection.add(byServerId);
        }

        Set<String> commonServerIds = new LinkedHashSet<>(resolvedBySelection.get(0).keySet());
        for (int i = 1; i < resolvedBySelection.size(); i++) {
            commonServerIds.retainAll(resolvedBySelection.get(i).keySet());
        }
        if (commonServerIds.isEmpty()) {
            return List.of();
        }

        return commonServerIds.stream()
                .map(serverId -> new BatchUploadTarget(
                        resolvedBySelection.get(0).get(serverId).getServerConfig(),
                        buildBatchItems(normalizedSelections, resolvedBySelection, serverId)
                ))
                .sorted(Comparator.comparing(target -> safe(target.getServerConfig().getName())))
                .collect(Collectors.toList());
    }

    private List<BatchUploadTarget.BatchUploadItem> buildBatchItems(
            List<Path> normalizedSelections,
            List<Map<String, ResolvedUploadTarget>> resolvedBySelection,
            String serverId
    ) {
        List<BatchUploadTarget.BatchUploadItem> items = new ArrayList<>();
        for (int i = 0; i < normalizedSelections.size(); i++) {
            items.add(new BatchUploadTarget.BatchUploadItem(
                    normalizedSelections.get(i),
                    resolvedBySelection.get(i).get(serverId)
            ));
        }
        return items;
    }

    private String buildRemoteTarget(String remoteMappingDirectory, Path relativePath) {
        if (relativePath == null || relativePath.getNameCount() == 0) {
            return remoteMappingDirectory;
        }
        String relative = relativePath.toString().replace('\\', '/');
        return ServerDeploySettingsService.joinRemotePath(remoteMappingDirectory, relative);
    }

    private String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private static class Candidate {
        private final ServerConfig server;
        private final DirectoryMapping mapping;
        private final String remoteMappingDirectory;
        private final String remoteTargetPath;
        private final int depth;

        private Candidate(
                ServerConfig server,
                DirectoryMapping mapping,
                String remoteMappingDirectory,
                String remoteTargetPath,
                int depth
        ) {
            this.server = server;
            this.mapping = mapping;
            this.remoteMappingDirectory = remoteMappingDirectory;
            this.remoteTargetPath = remoteTargetPath;
            this.depth = depth;
        }
    }
}
