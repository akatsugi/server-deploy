package com.akatsugi.serverdeploy.model;

public class WorkspaceConfig {

    private Long id;
    private String path;
    private boolean lastUsed;

    public WorkspaceConfig() {
    }

    public WorkspaceConfig(Long id, String path, boolean lastUsed) {
        this.id = id;
        this.path = path;
        this.lastUsed = lastUsed;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(boolean lastUsed) {
        this.lastUsed = lastUsed;
    }

    @Override
    public String toString() {
        return path == null ? "" : path;
    }
}
