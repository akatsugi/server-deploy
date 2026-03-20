package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.service.RemoteUploadService;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.atomic.AtomicReference;

public class ServerEditDialog extends DialogWrapper {

    private final JTextField nameField = new JTextField();
    private final JTextField hostField = new JTextField();
    private final JTextField portField = new JTextField("22");
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JTextField defaultDirectoryField = new JTextField("/");
    private final RemoteUploadService remoteUploadService = new RemoteUploadService();

    public ServerEditDialog(@Nullable ServerConfig source) {
        super(true);
        setTitle(source == null ? "新增服务器" : "编辑服务器");
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
        return buildServerConfig(true);
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (isBlank(nameField.getText()) || isBlank(hostField.getText()) || isBlank(usernameField.getText())
                || passwordField.getPassword().length == 0 || isBlank(defaultDirectoryField.getText())) {
            return new ValidationInfo("服务器配置项不能为空。");
        }
        return validatePort();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new java.awt.Dimension(560, 265));
        addRow(panel, 0, "名称", nameField);
        addRow(panel, 1, "主机", hostField);
        addRow(panel, 2, "端口", portField);
        addRow(panel, 3, "用户名", usernameField);
        addRow(panel, 4, "密码", passwordField);
        addRow(panel, 5, "默认远程目录", defaultDirectoryField);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton testConnectionButton = new JButton("测试连接");
        testConnectionButton.addActionListener(event -> testConnection());
        actionPanel.add(testConnectionButton);
        addRow(panel, 6, "连接测试", actionPanel);
        return panel;
    }

    private void testConnection() {
        String validationMessage = validateConnectionFields();
        if (validationMessage != null) {
            Messages.showErrorDialog(getContentPanel(), validationMessage, "测试连接");
            return;
        }

        ServerConfig config = buildServerConfig(false);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            try {
                remoteUploadService.testConnection(config);
            } catch (JSchException | SftpException exception) {
                errorMessage.set(buildConnectionErrorMessage(exception));
            }
        }, "测试连接", true, null);

        if (!completed) {
            return;
        }
        if (errorMessage.get() == null) {
            Messages.showInfoMessage(
                    getContentPanel(),
                    "连接成功，服务器和默认远程目录均可访问。",
                    "测试连接"
            );
            return;
        }
        Messages.showErrorDialog(getContentPanel(), errorMessage.get(), "测试连接失败");
    }

    private String validateConnectionFields() {
        if (isBlank(hostField.getText())) {
            return "主机不能为空。";
        }
        if (isBlank(usernameField.getText())) {
            return "用户名不能为空。";
        }
        if (passwordField.getPassword().length == 0) {
            return "密码不能为空。";
        }
        if (isBlank(defaultDirectoryField.getText())) {
            return "默认远程目录不能为空。";
        }
        ValidationInfo portValidation = validatePort();
        return portValidation == null ? null : portValidation.message;
    }

    private ValidationInfo validatePort() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                return new ValidationInfo("端口范围必须在 1 到 65535 之间。", portField);
            }
        } catch (NumberFormatException ex) {
            return new ValidationInfo("端口必须为数字。", portField);
        }
        return null;
    }

    private ServerConfig buildServerConfig(boolean requireName) {
        ServerConfig config = new ServerConfig();
        String name = nameField.getText().trim();
        config.setName(requireName || !name.isBlank() ? name : hostField.getText().trim());
        config.setHost(hostField.getText().trim());
        config.setPort(Integer.parseInt(portField.getText().trim()));
        config.setUsername(usernameField.getText().trim());
        config.setPassword(new String(passwordField.getPassword()));
        config.setDefaultDirectory(ServerDeploySettingsService.normalizeRemoteDirectory(defaultDirectoryField.getText().trim()));
        return config;
    }

    private String buildConnectionErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "连接失败，请检查服务器地址、端口、账号密码和默认远程目录。";
        }
        return message;
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