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
    private final JBCheckBox deleteExistingCheckBox = new JBCheckBox("Delete existing remote target before upload");

    public UploadTargetDialog(@Nullable Project project, List<ResolvedUploadTarget> targets) {
        super(project);
        targetComboBox = new JComboBox<>(new CollectionComboBoxModel<>(targets));
        setTitle("Upload To Server");
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
                .addLabeledComponent("Target Server", targetComboBox)
                .addLabeledComponent("Matched Mapping", mappingLabel)
                .addLabeledComponent("Remote Target", remoteTargetLabel)
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
