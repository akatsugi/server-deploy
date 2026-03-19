package com.akatsugi.serverdeploy.idea.action;

import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.service.MappingResolver;
import com.akatsugi.serverdeploy.idea.service.RemoteUploadService;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.akatsugi.serverdeploy.idea.ui.UploadTargetDialog;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class UploadToServerAction extends AnAction implements DumbAware {

    private final ServerDeploySettingsService settingsService = ServerDeploySettingsService.getInstance();
    private final MappingResolver mappingResolver = new MappingResolver();
    private final RemoteUploadService remoteUploadService = new RemoteUploadService();

    @Override
    public void update(@NotNull AnActionEvent event) {
        Context context = buildContext(event);
        boolean visible = context != null && !context.targets.isEmpty();
        event.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Context context = buildContext(event);
        if (context == null || context.targets.isEmpty()) {
            return;
        }

        UploadTargetDialog dialog = new UploadTargetDialog(context.project, context.targets);
        if (!dialog.showAndGet()) {
            return;
        }

        ResolvedUploadTarget target = dialog.getSelectedTarget();
        if (target == null) {
            return;
        }

        settingsService.markServerLastUsed(target.getServerConfig().getId());
        runUpload(context.project, context.selectedPath, target, dialog.isDeleteExisting());
    }

    private void runUpload(Project project, Path selectedPath, ResolvedUploadTarget target, boolean deleteExisting) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Upload To Server", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                try {
                    remoteUploadService.uploadToExactPath(
                            target.getServerConfig(),
                            selectedPath,
                            target.getRemoteTargetPath(),
                            deleteExisting,
                            new RemoteUploadService.ProgressListener() {
                                @Override
                                public void onStart(int totalFiles) {
                                    indicator.setFraction(0.0);
                                    indicator.setText("Connecting to " + target.getServerConfig().getName());
                                    indicator.setText2("Total files: " + totalFiles);
                                }

                                @Override
                                public void onProgress(String fileName, int completed, int totalFiles) {
                                    indicator.setText("Uploading to " + target.getServerConfig().getName());
                                    indicator.setText2(fileName + " (" + completed + "/" + totalFiles + ")");
                                    indicator.setFraction(totalFiles == 0 ? 1.0 : (double) completed / (double) totalFiles);
                                }
                            }
                    );
                    showNotification(project, NotificationType.INFORMATION, "Upload Completed",
                            target.getServerConfig().getName() + " -> " + target.getRemoteTargetPath());
                } catch (IOException | JSchException | SftpException exception) {
                    showNotification(project, NotificationType.ERROR, "Upload Failed", exception.getMessage());
                }
            }
        });
    }

    private void showNotification(Project project, NotificationType type, String title, String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Server Deploy")
                .createNotification(title, content, type)
                .notify(project);
    }

    private Context buildContext(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || files == null || files.length != 1) {
            return null;
        }

        VirtualFile file = files[0];
        if (!file.isInLocalFileSystem()) {
            return null;
        }

        Path selectedPath = Path.of(file.getPath()).toAbsolutePath().normalize();
        List<ResolvedUploadTarget> targets = mappingResolver.resolve(
                selectedPath,
                settingsService.getServers(),
                settingsService.getMappings()
        );
        return targets.isEmpty() ? null : new Context(project, selectedPath, targets);
    }

    private static class Context {
        private final Project project;
        private final Path selectedPath;
        private final List<ResolvedUploadTarget> targets;

        private Context(Project project, Path selectedPath, List<ResolvedUploadTarget> targets) {
            this.project = project;
            this.selectedPath = selectedPath;
            this.targets = targets;
        }
    }
}