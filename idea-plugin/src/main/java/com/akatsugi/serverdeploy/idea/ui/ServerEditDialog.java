package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class ServerEditDialog extends DialogWrapper {

    private final JTextField nameField = new JTextField();
    private final JTextField hostField = new JTextField();
    private final JTextField portField = new JTextField("22");
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JTextField defaultDirectoryField = new JTextField("/");

    public ServerEditDialog(@Nullable ServerConfig source) {
        super(true);
        setTitle(source == null ? "\u65b0\u589e\u670d\u52a1\u5668" : "\u7f16\u8f91\u670d\u52a1\u5668");
        if (source != null) {
            nameField.setText(source.getName());
            hostField.setText(source.getHost());
            portField.setText(String.valueOf(source.getPort()));
            usernameField.setText(source.getUsername());
            passwordField.setText(source.getPassword());
            defaultDirectoryField.setText(source.getDefaultDirectory());
        }
        init();
    }

    public ServerConfig getServerConfig() {
        ServerConfig config = new ServerConfig();
        config.setName(nameField.getText().trim());
        config.setHost(hostField.getText().trim());
        config.setPort(Integer.parseInt(portField.getText().trim()));
        config.setUsername(usernameField.getText().trim());
        config.setPassword(new String(passwordField.getPassword()));
        config.setDefaultDirectory(ServerDeploySettingsService.normalizeRemoteDirectory(defaultDirectoryField.getText().trim()));
        return config;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (isBlank(nameField.getText()) || isBlank(hostField.getText()) || isBlank(usernameField.getText())
                || passwordField.getPassword().length == 0 || isBlank(defaultDirectoryField.getText())) {
            return new ValidationInfo("\u670d\u52a1\u5668\u914d\u7f6e\u9879\u4e0d\u80fd\u4e3a\u7a7a\u3002");
        }
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                return new ValidationInfo("\u7aef\u53e3\u8303\u56f4\u5fc5\u987b\u5728 1 \u5230 65535 \u4e4b\u95f4\u3002", portField);
            }
        } catch (NumberFormatException ex) {
            return new ValidationInfo("\u7aef\u53e3\u5fc5\u987b\u4e3a\u6570\u5b57\u3002", portField);
        }
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new java.awt.Dimension(520, 220));
        addRow(panel, 0, "\u540d\u79f0", nameField);
        addRow(panel, 1, "\u4e3b\u673a", hostField);
        addRow(panel, 2, "\u7aef\u53e3", portField);
        addRow(panel, 3, "\u7528\u6237\u540d", usernameField);
        addRow(panel, 4, "\u5bc6\u7801", passwordField);
        addRow(panel, 5, "\u9ed8\u8ba4\u8fdc\u7a0b\u76ee\u5f55", defaultDirectoryField);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}