package com.akatsugi.serverdeploy.idea.model;

import java.nio.file.Files;
import java.util.List;

public class BatchUploadTarget {

    private final ServerConfig serverConfig;
    private final List<BatchUploadItem> uploadItems;

    public BatchUploadTarget(ServerConfig serverConfig, List<BatchUploadItem> uploadItems) {
        this.serverConfig = serverConfig;
        this.uploadItems = List.copyOf(uploadItems);
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public List<BatchUploadItem> getUploadItems() {
        return uploadItems;
    }

    public ResolvedUploadTarget getPrimaryTarget() {
        return uploadItems.isEmpty() ? null : uploadItems.get(0).target();
    }

    public boolean supportsSingleFileRename() {
        return uploadItems.size() == 1 && Files.isRegularFile(uploadItems.get(0).localPath());
    }

    @Override
    public String toString() {
        if (uploadItems.size() <= 1) {
            ResolvedUploadTarget primary = getPrimaryTarget();
            return primary == null ? serverConfig.getName() : serverConfig.getName() + " -> " + primary.getRemoteTargetPath();
        }
        return serverConfig.getName() + " -> " + uploadItems.size() + " 个目标";
    }

    public record BatchUploadItem(java.nio.file.Path localPath, ResolvedUploadTarget target) {
    }
}
