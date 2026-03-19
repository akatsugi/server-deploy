package com.akatsugi.serverdeploy.model;

public class DirectoryMapping {

    private Long id;
    private Long serverId;
    private String localDirectory;
    private String remoteDirectory;

    public DirectoryMapping() {
    }

    public DirectoryMapping(Long id, Long serverId, String localDirectory, String remoteDirectory) {
        this.id = id;
        this.serverId = serverId;
        this.localDirectory = localDirectory;
        this.remoteDirectory = remoteDirectory;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getLocalDirectory() {
        return localDirectory;
    }

    public void setLocalDirectory(String localDirectory) {
        this.localDirectory = localDirectory;
    }

    public String getRemoteDirectory() {
        return remoteDirectory;
    }

    public void setRemoteDirectory(String remoteDirectory) {
        this.remoteDirectory = remoteDirectory;
    }
}