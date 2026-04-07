package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.service.RemoteCommandService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
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
    private static final String COMMAND_HINT =
            "支持占位符：${remotePath}、${remoteDirectory}、${serverName}、${host}、${username}、${defaultDirectory}";

    private final JComboBox<ResolvedUploadTarget> targetComboBox;
    private final JComboBox<String> candidateCommandComboBox;
    private final JButton fillCandidateButton = new JButton("填入命令");
    private final JBLabel mappingLabel = new JBLabel();
    private final JBLabel remoteTargetLabel = new JBLabel();
    private final JTextArea commandArea = new JTextArea(7, 80);
    private final JTextArea commandPreviewArea = new JTextArea(5, 80);
    private final Path selectedPath;
    private final RemoteCommandService remoteCommandService;

    public ExecuteRemoteCommandDialog(
            @Nullable Project project,
            Path selectedPath,
            List<ResolvedUploadTarget> targets,
            String defaultCommand,
            List<String> candidateCommands,
            RemoteCommandService remoteCommandService
    ) {
        super(project);
        this.selectedPath = selectedPath;
        this.remoteCommandService = remoteCommandService;
        this.targetComboBox = new JComboBox<>(new CollectionComboBoxModel<>(targets));
        this.candidateCommandComboBox = new JComboBox<>(candidateCommands.toArray(String[]::new));

        this.targetComboBox.setMaximumRowCount(Math.min(Math.max(targets.size(), 1), 12));
        targets.stream()
                .max(Comparator.comparingInt(target -> target.toString().length()))
                .ifPresent(targetComboBox::setPrototypeDisplayValue);
        resizeCombo(targetComboBox);

        if (!candidateCommands.isEmpty()) {
            candidateCommands.stream()
                    .max(Comparator.comparingInt(String::length))
                    .ifPresent(candidateCommandComboBox::setPrototypeDisplayValue);
        }
        resizeCombo(candidateCommandComboBox);
        candidateCommandComboBox.setEnabled(!candidateCommands.isEmpty());
        fillCandidateButton.setEnabled(!candidateCommands.isEmpty());
        fillCandidateButton.addActionListener(event -> applySelectedCandidate());

        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        commandArea.setText(defaultCommand);

        commandPreviewArea.setEditable(false);
        commandPreviewArea.setLineWrap(true);
        commandPreviewArea.setWrapStyleWord(true);

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
        panel.setPreferredSize(new Dimension(980, 460));

        addRow(panel, 0, "目标服务器", targetComboBox);
        addCandidateRow(panel, 1);
        addRow(panel, 2, "匹配映射", mappingLabel);
        addRow(panel, 3, "远程目标", remoteTargetLabel);
        addRow(panel, 4, "执行命令", new JScrollPane(commandArea));
        addRow(panel, 5, "命令预览", new JScrollPane(commandPreviewArea));

        GridBagConstraints hint = new GridBagConstraints();
        hint.gridx = 1;
        hint.gridy = 6;
        hint.weightx = 1.0;
        hint.anchor = GridBagConstraints.WEST;
        hint.insets = new Insets(0, 6, 6, 6);
        panel.add(new JBLabel(COMMAND_HINT), hint);
        return panel;
    }

    private void applySelectedCandidate() {
        String candidate = (String) candidateCommandComboBox.getSelectedItem();
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        commandArea.setText(candidate);
        commandArea.requestFocusInWindow();
    }

    private void updatePreview() {
        ResolvedUploadTarget selectedTarget = getSelectedTarget();
        if (selectedTarget == null) {
            mappingLabel.setText("");
            remoteTargetLabel.setText("");
            commandPreviewArea.setText("");
            return;
        }
        mappingLabel.setText(selectedTarget.getDirectoryMapping().getLocalDirectory() + " -> "
                + selectedTarget.getRemoteMappingDirectory());
        remoteTargetLabel.setText(selectedTarget.getRemoteTargetPath());
        commandPreviewArea.setText(remoteCommandService.renderCommand(getCommandTemplate(), selectedPath, selectedTarget));
        commandPreviewArea.setCaretPosition(0);
    }

    private void resizeCombo(JComboBox<?> comboBox) {
        Dimension preferredSize = comboBox.getPreferredSize();
        comboBox.setPreferredSize(new Dimension(Math.max(preferredSize.width, TARGET_COMBO_MIN_WIDTH), preferredSize.height));
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

    private void addCandidateRow(JPanel panel, int row) {
        JPanel content = new JPanel(new GridBagLayout());

        GridBagConstraints comboConstraints = new GridBagConstraints();
        comboConstraints.gridx = 0;
        comboConstraints.gridy = 0;
        comboConstraints.weightx = 1.0;
        comboConstraints.fill = GridBagConstraints.HORIZONTAL;
        content.add(candidateCommandComboBox, comboConstraints);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 1;
        buttonConstraints.gridy = 0;
        buttonConstraints.insets = new Insets(0, 8, 0, 0);
        content.add(fillCandidateButton, buttonConstraints);

        addRow(panel, row, "候选命令", content);
    }
}
