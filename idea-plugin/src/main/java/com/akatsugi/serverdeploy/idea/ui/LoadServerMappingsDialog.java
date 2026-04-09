package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;

public class LoadServerMappingsDialog extends DialogWrapper {

    private final JComboBox<ServerConfig> targetServerComboBox;
    private final JComboBox<ServerConfig> sourceServerComboBox;
    private final JCheckBox overwriteCheckBox = new JCheckBox("覆盖目标服务器中相同本地目录的映射", true);

    public LoadServerMappingsDialog(
            List<ServerConfig> servers,
            @Nullable ServerConfig initialTargetServer,
            @Nullable ServerConfig initialSourceServer
    ) {
        super(true);
        this.targetServerComboBox = new JComboBox<>(servers.toArray(new ServerConfig[0]));
        this.sourceServerComboBox = new JComboBox<>(servers.toArray(new ServerConfig[0]));
        setTitle("加载其他服务器映射");

        if (initialTargetServer != null) {
            selectServer(targetServerComboBox, initialTargetServer.getId());
        }
        if (initialSourceServer != null) {
            selectServer(sourceServerComboBox, initialSourceServer.getId());
        } else if (servers.size() > 1) {
            ServerConfig selectedTarget = getTargetServer();
            servers.stream()
                    .filter(server -> !Objects.equals(server.getId(), selectedTarget == null ? null : selectedTarget.getId()))
                    .findFirst()
                    .ifPresent(server -> selectServer(sourceServerComboBox, server.getId()));
        }

        init();
    }

    public ServerConfig getTargetServer() {
        return (ServerConfig) targetServerComboBox.getSelectedItem();
    }

    public ServerConfig getSourceServer() {
        return (ServerConfig) sourceServerComboBox.getSelectedItem();
    }

    public boolean isOverwriteEnabled() {
        return overwriteCheckBox.isSelected();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getTargetServer() == null) {
            return new ValidationInfo("请选择目标服务器。", targetServerComboBox);
        }
        if (getSourceServer() == null) {
            return new ValidationInfo("请选择来源服务器。", sourceServerComboBox);
        }
        if (Objects.equals(getTargetServer().getId(), getSourceServer().getId())) {
            return new ValidationInfo("来源服务器和目标服务器不能相同。", sourceServerComboBox);
        }
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(520, 150));

        addRow(panel, 0, "目标服务器", targetServerComboBox);
        addRow(panel, 1, "来源服务器", sourceServerComboBox);
        addRow(panel, 2, "处理方式", overwriteCheckBox);
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

    private void selectServer(JComboBox<ServerConfig> comboBox, String serverId) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            ServerConfig server = comboBox.getItemAt(i);
            if (Objects.equals(server.getId(), serverId)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
    }
}
