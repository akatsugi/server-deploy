package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.BatchUploadTarget;
import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.util.Comparator;
import java.util.List;

public class UploadTargetDialog extends DialogWrapper {

    private static final int TARGET_COMBO_MIN_WIDTH = 760;

    private final JComboBox<BatchUploadTarget> targetComboBox;
    private final JBLabel selectionLabel = new JBLabel();
    private final JBLabel mappingLabel = new JBLabel();
    private final JBLabel remoteTargetLabel = new JBLabel();
    private final JBCheckBox deleteExistingCheckBox = new JBCheckBox("上传前删除远程已有目标");
    private final JBCheckBox renameFileCheckBox = new JBCheckBox("同步修改文件名");
    private final JTextField renameFileField = new JTextField();
    private final JBLabel renameHintLabel = new JBLabel("仅单个文件上传时支持修改文件名。");

    public UploadTargetDialog(
            @Nullable Project project,
            int selectedItemCount,
            List<BatchUploadTarget> targets,
            String defaultUploadFileName
    ) {
        super(project);
        targetComboBox = new JComboBox<>(new CollectionComboBoxModel<>(targets));
        targetComboBox.setMaximumRowCount(Math.min(Math.max(targets.size(), 1), 12));
        targets.stream()
                .max(Comparator.comparingInt(target -> target.toString().length()))
                .ifPresent(targetComboBox::setPrototypeDisplayValue);

        Dimension preferredSize = targetComboBox.getPreferredSize();
        targetComboBox.setPreferredSize(new Dimension(Math.max(preferredSize.width, TARGET_COMBO_MIN_WIDTH), preferredSize.height));
        targetComboBox.setSelectedItem(null);

        setTitle("上传到服务器");
        selectionLabel.setText("已选择 " + selectedItemCount + " 个文件或目录");
        renameFileField.setText(ServerDeploySettingsService.normalizeUploadFileName(defaultUploadFileName));
        renameFileField.setEnabled(false);
        renameHintLabel.setEnabled(false);

        targetComboBox.addActionListener(event -> updatePreview());
        renameFileCheckBox.addActionListener(event -> updateRenameFieldState());
        renameFileField.getDocument().addDocumentListener(SimpleDocumentListener.of(this::updatePreview));
        init();
        updatePreview();
    }

    public BatchUploadTarget getSelectedTarget() {
        return (BatchUploadTarget) targetComboBox.getSelectedItem();
    }

    public boolean isDeleteExisting() {
        return deleteExistingCheckBox.isSelected();
    }

    public boolean isRenameFileEnabled() {
        return renameFileCheckBox.isSelected() && supportsRename();
    }

    public String getRenameFileName() {
        return ServerDeploySettingsService.normalizeUploadFileName(renameFileField.getText());
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getSelectedTarget() == null) {
            return new ValidationInfo("请选择目标服务器。", targetComboBox);
        }
        if (isRenameFileEnabled()) {
            String fileName = getRenameFileName();
            if (fileName.isBlank()) {
                return new ValidationInfo("请输入修改后的文件名。", renameFileField);
            }
            if (fileName.contains("/") || fileName.contains("\\")) {
                return new ValidationInfo("文件名不能包含路径分隔符。", renameFileField);
            }
        }
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("目标服务器", targetComboBox)
                .addLabeledComponent("选择数量", selectionLabel)
                .addLabeledComponent("匹配映射", mappingLabel)
                .addLabeledComponent("远程目标", remoteTargetLabel)
                .addComponent(deleteExistingCheckBox)
                .addComponent(renameFileCheckBox)
                .addLabeledComponent("修改后文件名", renameFileField)
                .addComponent(renameHintLabel)
                .getPanel();
        panel.setPreferredSize(new Dimension(980, 250));
        return panel;
    }

    private void updatePreview() {
        BatchUploadTarget selectedTarget = getSelectedTarget();
        updateRenameFieldState();
        if (selectedTarget == null) {
            mappingLabel.setText("");
            remoteTargetLabel.setText("");
            return;
        }

        List<BatchUploadTarget.BatchUploadItem> uploadItems = selectedTarget.getUploadItems();
        ResolvedUploadTarget primary = selectedTarget.getPrimaryTarget();
        if (primary == null) {
            mappingLabel.setText("");
            remoteTargetLabel.setText("");
            return;
        }

        if (uploadItems.size() == 1) {
            mappingLabel.setText(primary.getDirectoryMapping().getLocalDirectory() + " -> " + primary.getRemoteMappingDirectory());
            remoteTargetLabel.setText(buildPreviewRemoteTarget(primary.getRemoteTargetPath()));
            return;
        }

        mappingLabel.setText("共 " + uploadItems.size() + " 个映射目录，首个："
                + primary.getDirectoryMapping().getLocalDirectory() + " -> " + primary.getRemoteMappingDirectory());
        remoteTargetLabel.setText("共 " + uploadItems.size() + " 个上传目标，首个：" + primary.getRemoteTargetPath());
    }

    private String buildPreviewRemoteTarget(String remoteTargetPath) {
        if (!isRenameFileEnabled()) {
            return remoteTargetPath;
        }
        String fileName = getRenameFileName();
        if (fileName.isBlank()) {
            return remoteTargetPath;
        }
        int lastSlash = remoteTargetPath.lastIndexOf('/');
        String remoteDirectory = lastSlash <= 0 ? "/" : remoteTargetPath.substring(0, lastSlash);
        return ServerDeploySettingsService.joinRemotePath(remoteDirectory, fileName);
    }

    private void updateRenameFieldState() {
        boolean enabled = renameFileCheckBox.isSelected() && supportsRename();
        renameFileField.setEnabled(enabled);
        renameHintLabel.setEnabled(!supportsRename());
        if (!supportsRename()) {
            renameFileCheckBox.setSelected(false);
        }
    }

    private boolean supportsRename() {
        BatchUploadTarget selectedTarget = getSelectedTarget();
        return selectedTarget != null && selectedTarget.supportsSingleFileRename();
    }
}
