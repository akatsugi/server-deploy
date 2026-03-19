package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import java.util.Objects;

public class MappingEditDialog extends DialogWrapper {

    private static final String REMOTE_DIRECTORY_HINT = "Relative values such as '.' or 'service-a' will be appended to the server default directory. Values starting with '/' are treated as absolute paths only.";

    private final JComboBox<ServerConfig> serverComboBox;
    private final JTextField localDirectoryField = new JTextField();
    private final JTextField remoteDirectoryField = new JTextField();
    private final String defaultBrowseDirectory;
    private String lastSuggestedRemoteDirectory = ".";

    public MappingEditDialog(List<ServerConfig> servers,
            @Nullable DirectoryMapping source,
            @Nullable String initialLocalDirectory,
            @Nullable String defaultBrowseDirectory) {
        super(true);
        serverComboBox = new JComboBox<>(servers.toArray(new ServerConfig[0]));
        this.defaultBrowseDirectory = defaultBrowseDirectory;
        setTitle(source == null ? "Add Mapping" : "Edit Mapping");
        remoteDirectoryField.setToolTipText(REMOTE_DIRECTORY_HINT);

        if (source != null) {
            for (ServerConfig server : servers) {
                if (Objects.equals(server.getId(), source.getServerId())) {
                    serverComboBox.setSelectedItem(server);
                    break;
                }
            }
            localDirectoryField.setText(source.getLocalDirectory());
            remoteDirectoryField.setText(source.getRemoteDirectory());
            lastSuggestedRemoteDirectory = source.getRemoteDirectory();
        } else {
            ServerConfig lastUsed = servers.stream().filter(ServerConfig::isLastUsed).findFirst().orElse(servers.isEmpty() ? null : servers.get(0));
            if (lastUsed != null) {
                serverComboBox.setSelectedItem(lastUsed);
            }
            if (initialLocalDirectory != null && !initialLocalDirectory.isBlank()) {
                localDirectoryField.setText(initialLocalDirectory);
            }
            applySuggestedRemoteDirectory();
        }

        serverComboBox.addActionListener(event -> applySuggestedRemoteDirectory());
        init();
    }

    public DirectoryMapping getDirectoryMapping() {
        DirectoryMapping mapping = new DirectoryMapping();
        ServerConfig server = (ServerConfig) serverComboBox.getSelectedItem();
        mapping.setServerId(server == null ? null : server.getId());
        mapping.setLocalDirectory(ServerDeploySettingsService.normalizeLocalDirectory(localDirectoryField.getText().trim()));
        mapping.setRemoteDirectory(ServerDeploySettingsService.normalizeMappingRemoteDirectory(remoteDirectoryField.getText().trim()));
        return mapping;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (serverComboBox.getSelectedItem() == null) {
            return new ValidationInfo("Select a target server.", serverComboBox);
        }
        if (isBlank(localDirectoryField.getText()) || isBlank(remoteDirectoryField.getText())) {
            return new ValidationInfo("All mapping fields are required.");
        }
        File localDirectory = new File(localDirectoryField.getText().trim());
        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            return new ValidationInfo("Local directory must exist.", localDirectoryField);
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        String remoteDirectory = remoteDirectoryField.getText() == null ? "" : remoteDirectoryField.getText().trim();
        if (ServerDeploySettingsService.isAbsoluteRemotePath(remoteDirectory)) {
            int result = Messages.showYesNoDialog(
                    "The remote directory starts with '/'. It will be treated as an absolute path and will not be combined with the server default directory. Continue?",
                    "Absolute Remote Path",
                    "Use Absolute Path",
                    "Cancel",
                    Messages.getWarningIcon()
            );
            if (result != Messages.YES) {
                return;
            }
        }
        super.doOKAction();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new java.awt.Dimension(720, 220));
        addRow(panel, 0, "Server", serverComboBox);

        JPanel localPanel = new JPanel(new GridBagLayout());
        GridBagConstraints text = new GridBagConstraints();
        text.gridx = 0;
        text.gridy = 0;
        text.weightx = 1.0;
        text.fill = GridBagConstraints.HORIZONTAL;
        localPanel.add(localDirectoryField, text);

        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(event -> chooseDirectory());
        GridBagConstraints button = new GridBagConstraints();
        button.gridx = 1;
        button.gridy = 0;
        button.insets = new Insets(0, 6, 0, 0);
        localPanel.add(browseButton, button);

        addRow(panel, 1, "Local Directory", localPanel);
        addRow(panel, 2, "Remote Directory", remoteDirectoryField);

        GridBagConstraints hint = new GridBagConstraints();
        hint.gridx = 1;
        hint.gridy = 3;
        hint.weightx = 1.0;
        hint.anchor = GridBagConstraints.WEST;
        hint.insets = new Insets(0, 6, 6, 6);
        panel.add(new JLabel(REMOTE_DIRECTORY_HINT), hint);
        return panel;
    }

    private void addRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(6, 6, 6, 6);
        panel.add(new JLabel(label), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(6, 6, 6, 6);
        panel.add(component, right);
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Local Directory");

        if (!isBlank(localDirectoryField.getText())) {
            chooser.setCurrentDirectory(new File(localDirectoryField.getText().trim()));
        } else if (!isBlank(defaultBrowseDirectory)) {
            chooser.setCurrentDirectory(new File(defaultBrowseDirectory));
        }

        int result = chooser.showOpenDialog(getContentPanel());
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            localDirectoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void applySuggestedRemoteDirectory() {
        String currentValue = remoteDirectoryField.getText() == null ? "" : remoteDirectoryField.getText().trim();
        if (currentValue.isEmpty() || currentValue.equals(lastSuggestedRemoteDirectory)) {
            lastSuggestedRemoteDirectory = ".";
            remoteDirectoryField.setText(lastSuggestedRemoteDirectory);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}