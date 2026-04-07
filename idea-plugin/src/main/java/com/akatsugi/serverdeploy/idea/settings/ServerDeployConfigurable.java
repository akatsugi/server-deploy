package com.akatsugi.serverdeploy.idea.settings;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.List;

public class ServerDeployConfigurable implements Configurable {

    private final ServerDeploySettingsService settingsService = ServerDeploySettingsService.getInstance();
    private ServerDeploySettingsPanel panel;

    @Override
    public String getDisplayName() {
        return "服务器部署";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            panel = new ServerDeploySettingsPanel();
        }
        return panel.getComponent();
    }

    @Override
    public boolean isModified() {
        if (panel == null) {
            return false;
        }
        return panel.isModified(
                settingsService.getDefaultShellCommand(),
                settingsService.getShellCommandCandidates(),
                settingsService.getServers(),
                settingsService.getMappings()
        );
    }

    @Override
    public void apply() throws ConfigurationException {
        if (panel == null) {
            return;
        }
        String defaultShellCommand = panel.getDefaultShellCommand();
        List<String> shellCommandCandidates = panel.getShellCommandCandidates();
        List<ServerConfig> servers = panel.getServers();
        List<DirectoryMapping> mappings = panel.getMappings();
        panel.validateState(defaultShellCommand, shellCommandCandidates, servers, mappings);
        settingsService.update(servers, mappings, defaultShellCommand, shellCommandCandidates);
    }

    @Override
    public void reset() {
        if (panel == null) {
            return;
        }
        panel.setData(
                settingsService.getDefaultShellCommand(),
                settingsService.getShellCommandCandidates(),
                settingsService.getServers(),
                settingsService.getMappings()
        );
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
