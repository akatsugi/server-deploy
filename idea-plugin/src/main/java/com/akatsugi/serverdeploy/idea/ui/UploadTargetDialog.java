package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.BatchUploadTarget;
import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
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

    public UploadTargetDialog(@Nullable Project project, int selectedItemCount, List<BatchUploadTarget> targets) {
        super(project);
        targetComboBox = new JComboBox<>(new CollectionComboBoxModel<>(targets));
        targetComboBox.setMaximumRowCount(Math.min(Math.max(targets.size(), 1), 12));
        targets.stream()
                .max(Comparator.comparingInt(target -> target.toString().length()))
                .ifPresent(targetComboBox::setPrototypeDisplayValue);

        Dimension preferredSize = targetComboBox.getPreferredSize();
        targetComboBox.setPreferredSize(new Dimension(Math.max(preferredSize.width, TARGET_COMBO_MIN_WIDTH), preferredSize.height));

        setTitle("上传到服务器");
        selectionLabel.setText("已选择 " + selectedItemCount + " 个文件或目录");
        targetComboBox.addActionListener(event -> updatePreview());
        init();
        updatePreview();
    }

    public BatchUploadTarget getSelectedTarget() {
        return (BatchUploadTarget) targetComboBox.getSelectedItem();
    }

    public boolean isDeleteExisting() {
        return deleteExistingCheckBox.isSelected();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("目标服务器", targetComboBox)
                .addLabeledComponent("选择数量", selectionLabel)
                .addLabeledComponent("匹配映射", mappingLabel)
                .addLabeledComponent("远程目标", remoteTargetLabel)
                .addComponent(deleteExistingCheckBox)
                .getPanel();
        panel.setPreferredSize(new Dimension(960, 200));
        return panel;
    }

    private void updatePreview() {
        BatchUploadTarget selectedTarget = getSelectedTarget();
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
            remoteTargetLabel.setText(primary.getRemoteTargetPath());
            return;
        }

        mappingLabel.setText("共 " + uploadItems.size() + " 个映射目录，首个：" +
                primary.getDirectoryMapping().getLocalDirectory() + " -> " + primary.getRemoteMappingDirectory());
        remoteTargetLabel.setText("共 " + uploadItems.size() + " 个上传目标，首个：" + primary.getRemoteTargetPath());
    }
}
