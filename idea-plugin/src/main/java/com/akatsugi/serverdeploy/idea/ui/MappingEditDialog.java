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

    private static final String REMOTE_DIRECTORY_HINT = "\u76f8\u5bf9\u8def\u5f84\u5982 '.' \u6216 'service-a' \u4f1a\u62fc\u63a5\u5230\u670d\u52a1\u5668\u9ed8\u8ba4\u8fdc\u7a0b\u76ee\u5f55\u540e\uff1b\u4ee5 '/' \u5f00\u5934\u7684\u503c\u4f1a\u88ab\u89c6\u4e3a\u7edd\u5bf9\u8def\u5f84\uff0c\u4e0d\u4f1a\u518d\u62fc\u63a5\u9ed8\u8ba4\u76ee\u5f55\u3002";

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
        setTitle(source == null ? "\u65b0\u589e\u6620\u5c04" : "\u7f16\u8f91\u6620\u5c04");
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
            return new ValidationInfo("\u8bf7\u9009\u62e9\u76ee\u6807\u670d\u52a1\u5668\u3002", serverComboBox);
        }
        if (isBlank(localDirectoryField.getText()) || isBlank(remoteDirectoryField.getText())) {
            return new ValidationInfo("\u6620\u5c04\u914d\u7f6e\u9879\u4e0d\u80fd\u4e3a\u7a7a\u3002");
        }
        File localDirectory = new File(localDirectoryField.getText().trim());
        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            return new ValidationInfo("\u672c\u5730\u76ee\u5f55\u5fc5\u987b\u5b58\u5728\u4e14\u4e3a\u6587\u4ef6\u5939\u3002", localDirectoryField);
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        String remoteDirectory = remoteDirectoryField.getText() == null ? "" : remoteDirectoryField.getText().trim();
        if (ServerDeploySettingsService.isAbsoluteRemotePath(remoteDirectory)) {
            int result = Messages.showYesNoDialog(
                    "\u8fdc\u7a0b\u76ee\u5f55\u4ee5 '/' \u5f00\u5934\uff0c\u5c06\u4ec5\u6309\u7edd\u5bf9\u8def\u5f84\u5904\u7406\uff0c\u4e0d\u4f1a\u4e0e\u670d\u52a1\u5668\u9ed8\u8ba4\u76ee\u5f55\u62fc\u63a5\u3002\u662f\u5426\u7ee7\u7eed\uff1f",
                    "\u7edd\u5bf9\u8def\u5f84\u63d0\u793a",
                    "\u4f7f\u7528\u7edd\u5bf9\u8def\u5f84",
                    "\u53d6\u6d88",
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
        addRow(panel, 0, "\u670d\u52a1\u5668", serverComboBox);

        JPanel localPanel = new JPanel(new GridBagLayout());
        GridBagConstraints text = new GridBagConstraints();
        text.gridx = 0;
        text.gridy = 0;
        text.weightx = 1.0;
        text.fill = GridBagConstraints.HORIZONTAL;
        localPanel.add(localDirectoryField, text);

        JButton browseButton = new JButton("\u6d4f\u89c8");
        browseButton.addActionListener(event -> chooseDirectory());
        GridBagConstraints button = new GridBagConstraints();
        button.gridx = 1;
        button.gridy = 0;
        button.insets = new Insets(0, 6, 0, 0);
        localPanel.add(browseButton, button);

        addRow(panel, 1, "\u672c\u5730\u76ee\u5f55", localPanel);
        addRow(panel, 2, "\u8fdc\u7a0b\u76ee\u5f55", remoteDirectoryField);

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
        chooser.setDialogTitle("\u9009\u62e9\u672c\u5730\u76ee\u5f55");

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