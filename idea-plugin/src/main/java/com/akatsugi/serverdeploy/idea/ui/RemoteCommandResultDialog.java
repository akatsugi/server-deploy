package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.service.RemoteCommandService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class RemoteCommandResultDialog extends DialogWrapper {

    private final JPanel panel;

    public RemoteCommandResultDialog(
            @Nullable Project project,
            String serverName,
            String command,
            RemoteCommandService.CommandResult result
    ) {
        super(project);
        setTitle(result.isSuccess() ? "远程命令执行成功" : "远程命令执行失败");

        JTextArea textArea = new JTextArea(buildContent(serverName, command, result));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(920, 420));
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        init();
        setOKButtonText("关闭");
        setResizable(true);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel;
    }

    private String buildContent(String serverName, String command, RemoteCommandService.CommandResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("服务器：").append(serverName).append('\n');
        builder.append("退出码：").append(result.getExitCode()).append("\n\n");
        builder.append("命令：\n").append(command).append("\n\n");
        builder.append("标准输出：\n").append(emptyToPlaceholder(result.getStdout())).append("\n\n");
        builder.append("标准错误：\n").append(emptyToPlaceholder(result.getStderr()));
        return builder.toString();
    }

    private String emptyToPlaceholder(String value) {
        return value == null || value.isBlank() ? "(空)" : value;
    }
}
