package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.service.RemoteCommandService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class ExecuteRemoteCommandDialog extends DialogWrapper {

    private static final int TARGET_COMBO_MIN_WIDTH = 760;
    private static final String COMMAND_HINT = "支持占位符：${remotePath}、${remoteDirectory}、${serverName}、${host}、${username}、${defaultDirectory}";

    private final JComboBox<ResolvedUploadTarget> targetComboBox;
    private final JBLabel mappingLabel = new JBLabel();
    private final JBLabel remoteTargetLabel = new JBLabel();
    private final JBLabel commandPreviewLabel = new JBLabel();
    private final JTextArea commandArea = new JTextArea(5, 80);
    private final Path selectedPath;
    private final RemoteCommandService remoteCommandService;

    public ExecuteRemoteCommandDialog(
            @Nullable Project project,
            Path selectedPath,
            List<ResolvedUploadTarget> targets,
            String defaultCommand,
            RemoteCommandService remoteCommandService
    ) {
        super(project);
        this.selectedPath = selectedPath;
        this.remoteCommandService = remoteCommandService;
        this.targetComboBox = new JComboBox<>(new CollectionComboBoxModel<>(targets));
        this.targetComboBox.setMaximumRowCount(Math.min(Math.max(targets.size(), 1), 12));
        targets.stream()
                .max(Comparator.comparingInt(target -> target.toString().length()))
                .ifPresent(targetComboBox::setPrototypeDisplayValue);

        Dimension preferredSize = targetComboBox.getPreferredSize();
        targetComboBox.setPreferredSize(new Dimension(Math.max(preferredSize.width, TARGET_COMBO_MIN_WIDTH), preferredSize.height));

        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        commandArea.setText(defaultCommand);

        setTitle("执行远程 Shell 命令");
        targetComboBox.addActionListener(event -> updatePreview());
        commandArea.getDocument().addDocumentListener(SimpleDocumentListener.of(this::updatePreview));
        init();
        updatePreview();
    }

    public ResolvedUploadTarget getSelectedTarget() {
        return (ResolvedUploadTarget) targetComboBox.getSelectedItem();
    }

    public String getCommandTemplate() {
        return commandArea.getText() == null ? "" : commandArea.getText().trim();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getSelectedTarget() == null) {
            return new ValidationInfo("请选择目标服务器。", targetComboBox);
        }
        if (getCommandTemplate().isBlank()) {
            return new ValidationInfo("Shell 命令不能为空。", commandArea);
        }
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(960, 320));

        addRow(panel, 0, "目标服务器", targetComboBox);
        addRow(panel, 1, "匹配映射", mappingLabel);
        addRow(panel, 2, "远程目标", remoteTargetLabel);
        addRow(panel, 3, "执行命令", new JScrollPane(commandArea));
        addRow(panel, 4, "命令预览", commandPreviewLabel);

        GridBagConstraints hint = new GridBagConstraints();
        hint.gridx = 1;
        hint.gridy = 5;
        hint.weightx = 1.0;
        hint.anchor = GridBagConstraints.WEST;
        hint.insets = new Insets(0, 6, 6, 6);
        panel.add(new JBLabel(COMMAND_HINT), hint);
        return panel;
    }

    private void updatePreview() {
        ResolvedUploadTarget selectedTarget = getSelectedTarget();
        if (selectedTarget == null) {
            mappingLabel.setText("");
            remoteTargetLabel.setText("");
            commandPreviewLabel.setText("");
            return;
        }
        mappingLabel.setText(selectedTarget.getDirectoryMapping().getLocalDirectory() + " -> "
                + selectedTarget.getDirectoryMapping().getRemoteDirectory());
        remoteTargetLabel.setText(selectedTarget.getRemoteTargetPath());
        commandPreviewLabel.setText(remoteCommandService.renderCommand(getCommandTemplate(), selectedPath, selectedTarget));
    }

    private void addRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(6, 6, 6, 6);
        panel.add(new JBLabel(label), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(6, 6, 6, 6);
        panel.add(component, right);
    }
}
