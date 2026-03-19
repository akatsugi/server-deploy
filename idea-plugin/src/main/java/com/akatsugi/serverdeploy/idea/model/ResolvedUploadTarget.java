package com.akatsugi.serverdeploy.idea.model;

public class ResolvedUploadTarget {

    private final ServerConfig serverConfig;
    private final DirectoryMapping directoryMapping;
    private final String remoteTargetPath;

    public ResolvedUploadTarget(ServerConfig serverConfig, DirectoryMapping directoryMapping, String remoteTargetPath) {
        this.serverConfig = serverConfig;
        this.directoryMapping = directoryMapping;
        this.remoteTargetPath = remoteTargetPath;
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public DirectoryMapping getDirectoryMapping() {
        return directoryMapping;
    }

    public String getRemoteTargetPath() {
        return remoteTargetPath;
    }

    @Override
    public String toString() {
        return serverConfig.getName() + " -> " + remoteTargetPath;
    }
}
