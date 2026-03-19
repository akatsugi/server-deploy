package com.akatsugi.serverdeploy.idea.ui;

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
import java.util.List;

public class UploadTargetDialog extends DialogWrapper {

    private final JComboBox<ResolvedUploadTarget> targetComboBox;
    private final JBLabel mappingLabel = new JBLabel();
    private final JBLabel remoteTargetLabel = new JBLabel();
    private final JBCheckBox deleteExistingCheckBox = new JBCheckBox("\u4e0a\u4f20\u524d\u5220\u9664\u8fdc\u7a0b\u5df2\u6709\u76ee\u6807");

    public UploadTargetDialog(@Nullable Project project, List<ResolvedUploadTarget> targets) {
        super(project);
        targetComboBox = new JComboBox<>(new CollectionComboBoxModel<>(targets));
        setTitle("\u4e0a\u4f20\u5230\u670d\u52a1\u5668");
        targetComboBox.addActionListener(event -> updatePreview());
        init();
        updatePreview();
    }

    public ResolvedUploadTarget getSelectedTarget() {
        return (ResolvedUploadTarget) targetComboBox.getSelectedItem();
    }

    public boolean isDeleteExisting() {
        return deleteExistingCheckBox.isSelected();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("\u76ee\u6807\u670d\u52a1\u5668", targetComboBox)
                .addLabeledComponent("\u5339\u914d\u6620\u5c04", mappingLabel)
                .addLabeledComponent("\u8fdc\u7a0b\u76ee\u6807", remoteTargetLabel)
                .addComponent(deleteExistingCheckBox)
                .getPanel();
        panel.setPreferredSize(new java.awt.Dimension(560, 160));
        return panel;
    }

    private void updatePreview() {
        ResolvedUploadTarget selectedTarget = getSelectedTarget();
        if (selectedTarget == null) {
            mappingLabel.setText("");
            remoteTargetLabel.setText("");
            return;
        }
        mappingLabel.setText(selectedTarget.getDirectoryMapping().getLocalDirectory() + " -> "
                + selectedTarget.getDirectoryMapping().getRemoteDirectory());
        remoteTargetLabel.setText(selectedTarget.getRemoteTargetPath());
    }
}