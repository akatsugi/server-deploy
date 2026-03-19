package com.akatsugi.serverdeploy.idea.model;

import java.util.Objects;
import java.util.UUID;

public class DirectoryMapping {

    private String id;
    private String serverId;
    private String localDirectory;
    private String remoteDirectory;

    public DirectoryMapping() {
    }

    public DirectoryMapping(DirectoryMapping source) {
        this.id = source.id;
        this.serverId = source.serverId;
        this.localDirectory = source.localDirectory;
        this.remoteDirectory = source.remoteDirectory;
    }

    public DirectoryMapping copy() {
        return new DirectoryMapping(this);
    }

    public void ensureIdentity() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirectoryMapping that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(serverId, that.serverId)
                && Objects.equals(localDirectory, that.localDirectory)
                && Objects.equals(remoteDirectory, that.remoteDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, serverId, localDirectory, remoteDirectory);
    }
}
