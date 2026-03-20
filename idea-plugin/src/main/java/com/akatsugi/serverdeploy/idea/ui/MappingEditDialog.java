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

    private static final String REMOTE_DIRECTORY_HINT = "相对路径如 '.' 或 'service-a' 会拼接到服务器默认远程目录后；以 '/' 开头的值会被视为绝对路径，不会再拼接默认目录。";

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
        setTitle(source == null ? "新增映射" : "编辑映射");
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
            return new ValidationInfo("请选择目标服务器。", serverComboBox);
        }
        if (isBlank(localDirectoryField.getText()) || isBlank(remoteDirectoryField.getText())) {
            return new ValidationInfo("映射配置项不能为空。");
        }
        File localDirectory = new File(localDirectoryField.getText().trim());
        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            return new ValidationInfo("本地目录必须存在且为文件夹。", localDirectoryField);
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        String remoteDirectory = remoteDirectoryField.getText() == null ? "" : remoteDirectoryField.getText().trim();
        if (ServerDeploySettingsService.isAbsoluteRemotePath(remoteDirectory)) {
            int result = Messages.showYesNoDialog(
                    "远程目录以 '/' 开头，将仅按绝对路径处理，不会与服务器默认目录拼接。是否继续？",
                    "绝对路径提示",
                    "使用绝对路径",
                    "取消",
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
        addRow(panel, 0, "服务器", serverComboBox);

        JPanel localPanel = new JPanel(new GridBagLayout());
        GridBagConstraints text = new GridBagConstraints();
        text.gridx = 0;
        text.gridy = 0;
        text.weightx = 1.0;
        text.fill = GridBagConstraints.HORIZONTAL;
        localPanel.add(localDirectoryField, text);

        JButton browseButton = new JButton("浏览");
        browseButton.addActionListener(event -> chooseDirectory());
        GridBagConstraints button = new GridBagConstraints();
        button.gridx = 1;
        button.gridy = 0;
        button.insets = new Insets(0, 6, 0, 0);
        localPanel.add(browseButton, button);

        addRow(panel, 1, "本地目录", localPanel);
        addRow(panel, 2, "远程目录", remoteDirectoryField);

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
        chooser.setDialogTitle("选择本地目录");

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