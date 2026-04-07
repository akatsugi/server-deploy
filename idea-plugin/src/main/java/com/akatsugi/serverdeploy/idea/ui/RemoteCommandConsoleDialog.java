package com.akatsugi.serverdeploy.idea.ui;

import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.service.RemoteCommandService;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RemoteCommandConsoleDialog extends DialogWrapper {

    private final Project project;
    private final JPanel panel = new JPanel(new BorderLayout(0, 8));
    private final JBLabel statusLabel = new JBLabel("状态：运行中");
    private final JTextArea commandArea = new JTextArea();
    private final JTextPane outputPane = new JTextPane();
    private final StopAction stopAction = new StopAction();
    private final SimpleAttributeSet normalStyle = createStyle(new Color(210, 210, 210), false);
    private final SimpleAttributeSet infoStyle = createStyle(new Color(120, 190, 255), false);
    private final SimpleAttributeSet warnStyle = createStyle(new Color(255, 190, 90), false);
    private final SimpleAttributeSet errorStyle = createStyle(new Color(255, 120, 120), true);
    private final StringBuilder pendingStdout = new StringBuilder();
    private final StringBuilder pendingStderr = new StringBuilder();
    private RemoteCommandService.RunningCommand runningCommand;
    private boolean commandFinished;

    public RemoteCommandConsoleDialog(@Nullable Project project, ResolvedUploadTarget target, String renderedCommand) {
        super(project);
        this.project = project;
        setTitle("远程命令输出");

        commandArea.setEditable(false);
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        commandArea.setText(renderedCommand);
        commandArea.setCaretPosition(0);

        outputPane.setEditable(false);
        outputPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, outputPane.getFont().getSize()));
        installOutputPopupMenu();

        panel.setPreferredSize(new Dimension(980, 620));
        panel.add(createHeader(target), BorderLayout.NORTH);
        panel.add(createCenterPanelContent(), BorderLayout.CENTER);

        init();
        setOKButtonText("关闭");
        setResizable(true);
    }

    public RemoteCommandService.OutputListener createOutputListener() {
        return new RemoteCommandService.OutputListener() {
            @Override
            public void onStdout(String text) {
                appendOutput(text, OutputKind.STDOUT);
            }

            @Override
            public void onStderr(String text) {
                appendOutput(text, OutputKind.STDERR);
            }

            @Override
            public void onCompleted(RemoteCommandService.CommandResult result) {
                SwingUtilities.invokeLater(() -> {
                    flushPendingOutput();
                    commandFinished = true;
                    stopAction.setEnabled(false);
                    if (result.isStoppedByUser()) {
                        statusLabel.setText("状态：已停止");
                    } else if (result.isSuccess()) {
                        statusLabel.setText("状态：执行完成，退出码 0");
                    } else {
                        statusLabel.setText("状态：执行结束，退出码 " + result.getExitCode());
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                SwingUtilities.invokeLater(() -> {
                    flushPendingOutput();
                    appendRenderedText("[ERROR] " + safe(error.getMessage()) + System.lineSeparator(), errorStyle);
                    commandFinished = true;
                    stopAction.setEnabled(false);
                    statusLabel.setText("状态：执行失败 - " + safe(error.getMessage()));
                });
            }
        };
    }

    public void attachRunningCommand(RemoteCommandService.RunningCommand runningCommand) {
        this.runningCommand = runningCommand;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{stopAction, getOKAction()};
    }

    @Override
    public void doOKAction() {
        stopIfRunning();
        super.doOKAction();
    }

    @Override
    public void doCancelAction() {
        stopIfRunning();
        super.doCancelAction();
    }

    private JPanel createHeader(ResolvedUploadTarget target) {
        JPanel header = new JPanel(new GridBagLayout());
        addHeaderRow(header, 0, "目标服务器", target.getServerConfig().getName());
        addHeaderRow(header, 1, "映射目录", target.getRemoteMappingDirectory());
        addHeaderRow(header, 2, "远程目标", target.getRemoteTargetPath());
        addHeaderRow(header, 3, "当前状态", statusLabel);
        return header;
    }

    private JPanel createCenterPanelContent() {
        JPanel content = new JPanel(new BorderLayout(0, 8));

        JPanel commandPanel = new JPanel(new BorderLayout(0, 4));
        commandPanel.add(new JBLabel("执行命令"), BorderLayout.NORTH);
        commandPanel.add(new JScrollPane(commandArea), BorderLayout.CENTER);
        commandPanel.setPreferredSize(new Dimension(920, 120));

        JPanel outputPanel = new JPanel(new BorderLayout(0, 4));
        outputPanel.add(new JBLabel("实时输出"), BorderLayout.NORTH);
        outputPanel.add(new JScrollPane(outputPane), BorderLayout.CENTER);

        content.add(commandPanel, BorderLayout.NORTH);
        content.add(outputPanel, BorderLayout.CENTER);
        return content;
    }

    private void addHeaderRow(JPanel panel, int row, String label, String value) {
        addHeaderRow(panel, row, label, new JBLabel(value));
    }

    private void addHeaderRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(2, 4, 2, 12);
        panel.add(new JBLabel(label + "："), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(2, 0, 2, 4);
        panel.add(component, right);
    }

    private void appendOutput(String text, OutputKind outputKind) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder pendingBuffer = outputKind == OutputKind.STDERR ? pendingStderr : pendingStdout;
            pendingBuffer.append(text);
            renderCompletedLines(pendingBuffer, outputKind);
        });
    }

    private void installOutputPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem copyItem = new JMenuItem("复制全部输出");
        copyItem.addActionListener(event ->
                CopyPasteManager.getInstance().setContents(new StringSelection(getPlainOutputText())));

        JMenuItem saveItem = new JMenuItem("保存输出");
        saveItem.addActionListener(event -> saveOutput());

        JMenuItem clearItem = new JMenuItem("清空当前结果记录");
        clearItem.addActionListener(event -> outputPane.setText(""));

        popupMenu.add(copyItem);
        popupMenu.add(saveItem);
        popupMenu.addSeparator();
        popupMenu.add(clearItem);
        outputPane.setComponentPopupMenu(popupMenu);
    }

    private void saveOutput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("保存命令输出");
        chooser.setSelectedFile(new java.io.File("remote-command-output.log"));
        if (project != null && project.getBasePath() != null && !project.getBasePath().isBlank()) {
            chooser.setCurrentDirectory(new java.io.File(project.getBasePath()));
        }
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        try {
            Path outputFile = chooser.getSelectedFile().toPath();
            Files.writeString(outputFile, getPlainOutputText(), StandardCharsets.UTF_8);
            Messages.showInfoMessage(panel, "输出已保存到：\n" + outputFile, "远程命令输出");
        } catch (IOException exception) {
            Messages.showErrorDialog(panel, safe(exception.getMessage()), "保存输出失败");
        }
    }

    private String getPlainOutputText() {
        return outputPane.getText() == null ? "" : outputPane.getText();
    }

    private void renderCompletedLines(StringBuilder pendingBuffer, OutputKind outputKind) {
        int newlineIndex;
        while ((newlineIndex = pendingBuffer.indexOf("\n")) >= 0) {
            String line = pendingBuffer.substring(0, newlineIndex + 1);
            pendingBuffer.delete(0, newlineIndex + 1);
            appendRenderedText(line, selectStyle(line, outputKind));
        }
    }

    private void flushPendingOutput() {
        flushPendingBuffer(pendingStdout, OutputKind.STDOUT);
        flushPendingBuffer(pendingStderr, OutputKind.STDERR);
    }

    private void flushPendingBuffer(StringBuilder pendingBuffer, OutputKind outputKind) {
        if (pendingBuffer.isEmpty()) {
            return;
        }
        String remaining = pendingBuffer.toString();
        pendingBuffer.setLength(0);
        appendRenderedText(remaining, selectStyle(remaining, outputKind));
    }

    private void appendRenderedText(String text, SimpleAttributeSet style) {
        StyledDocument document = outputPane.getStyledDocument();
        try {
            document.insertString(document.getLength(), text, style);
            outputPane.setCaretPosition(document.getLength());
        } catch (BadLocationException exception) {
            throw new IllegalStateException("追加实时输出失败", exception);
        }
    }

    private SimpleAttributeSet selectStyle(String text, OutputKind outputKind) {
        if (outputKind == OutputKind.STDERR) {
            return errorStyle;
        }
        String normalized = text == null ? "" : text.toLowerCase();
        if (normalized.contains("error") || normalized.contains("exception") || normalized.contains("failed")) {
            return errorStyle;
        }
        if (normalized.contains("warn")) {
            return warnStyle;
        }
        if (normalized.contains("info")) {
            return infoStyle;
        }
        return normalStyle;
    }

    private SimpleAttributeSet createStyle(Color color, boolean bold) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        StyleConstants.setBold(attributes, bold);
        StyleConstants.setFontFamily(attributes, Font.MONOSPACED);
        return attributes;
    }

    private void stopIfRunning() {
        if (!commandFinished && runningCommand != null && runningCommand.isRunning()) {
            runningCommand.stop();
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未知错误" : value;
    }

    private enum OutputKind {
        STDOUT,
        STDERR
    }

    private final class StopAction extends DialogWrapperAction {
        private StopAction() {
            super("停止");
            setEnabled(true);
        }

        @Override
        protected void doAction(ActionEvent event) {
            stopIfRunning();
            statusLabel.setText("状态：正在停止");
            setEnabled(false);
        }
    }
}
