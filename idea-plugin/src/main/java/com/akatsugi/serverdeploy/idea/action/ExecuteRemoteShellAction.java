package com.akatsugi.serverdeploy.idea.action;

import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.service.MappingResolver;
import com.akatsugi.serverdeploy.idea.service.RemoteCommandService;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.akatsugi.serverdeploy.idea.ui.ExecuteRemoteCommandDialog;
import com.akatsugi.serverdeploy.idea.ui.RemoteCommandResultDialog;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ExecuteRemoteShellAction extends AnAction implements DumbAware {

    private final ServerDeploySettingsService settingsService = ServerDeploySettingsService.getInstance();
    private final MappingResolver mappingResolver = new MappingResolver();
    private final RemoteCommandService remoteCommandService = new RemoteCommandService();

    @Override
    public void update(@NotNull AnActionEvent event) {
        VirtualFile file = resolveSingleFile(event);
        Context context = buildContext(event);
        boolean visible = file != null;
        boolean enabled = context != null && !context.targets.isEmpty();
        event.getPresentation().setVisible(visible);
        event.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Context context = buildContext(event);
        if (context == null || context.targets.isEmpty()) {
            return;
        }

        ExecuteRemoteCommandDialog dialog = new ExecuteRemoteCommandDialog(
                context.project,
                context.selectedPath,
                context.targets,
                settingsService.getDefaultShellCommand(),
                remoteCommandService
        );
        if (!dialog.showAndGet()) {
            return;
        }

        ResolvedUploadTarget target = dialog.getSelectedTarget();
        if (target == null) {
            return;
        }

        String command = remoteCommandService.renderCommand(dialog.getCommandTemplate(), context.selectedPath, target);
        settingsService.markServerLastUsed(target.getServerConfig().getId());
        runCommand(context.project, target, command);
    }

    private void runCommand(Project project, ResolvedUploadTarget target, String command) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "执行远程 Shell 命令", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("正在连接 " + target.getServerConfig().getName());
                try {
                    RemoteCommandService.CommandResult result = remoteCommandService.execute(target.getServerConfig(), command);
                    NotificationType type = result.isSuccess() ? NotificationType.INFORMATION : NotificationType.WARNING;
                    showNotification(project, type, result.isSuccess() ? "远程命令执行完成" : "远程命令执行返回非 0",
                            target.getServerConfig().getName() + "，退出码：" + result.getExitCode());
                    ApplicationManager.getApplication().invokeLater(
                            () -> new RemoteCommandResultDialog(project, target.getServerConfig().getName(), command, result).show()
                    );
                } catch (IOException | InterruptedException | RuntimeException | com.jcraft.jsch.JSchException exception) {
                    showNotification(project, NotificationType.ERROR, "远程命令执行失败", exception.getMessage());
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }

    private void showNotification(Project project, NotificationType type, String title, String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Server Deploy")
                .createNotification(title, content == null ? "" : content, type)
                .notify(project);
    }

    private Context buildContext(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile file = resolveSingleFile(event);
        if (project == null || file == null || !file.isInLocalFileSystem()) {
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

    private VirtualFile resolveSingleFile(AnActionEvent event) {
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null) {
            return files.length == 1 ? files[0] : null;
        }

        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            return file;
        }

        PsiElement[] elements = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        if (elements != null) {
            return elements.length == 1 ? PsiUtilCore.getVirtualFile(elements[0]) : null;
        }

        PsiElement element = event.getData(CommonDataKeys.PSI_ELEMENT);
        if (element != null) {
            return PsiUtilCore.getVirtualFile(element);
        }

        VirtualFile projectFileDirectory = event.getData(PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY);
        if (projectFileDirectory != null) {
            return projectFileDirectory;
        }

        Module module = event.getData(LangDataKeys.MODULE_CONTEXT);
        if (module == null) {
            module = event.getData(LangDataKeys.MODULE);
        }
        if (module != null) {
            VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
            if (contentRoots.length == 1) {
                return contentRoots[0];
            }
        }

        Project project = event.getProject();
        if (project != null) {
            ProjectView projectView = ProjectView.getInstance(project);
            AbstractProjectViewPane pane = projectView == null ? null : projectView.getCurrentProjectViewPane();
            if (pane != null) {
                PsiElement[] psiElements = pane.getSelectedPSIElements();
                if (psiElements != null && psiElements.length == 1) {
                    VirtualFile psiFile = PsiUtilCore.getVirtualFile(psiElements[0]);
                    if (psiFile != null) {
                        return psiFile;
                    }
                }

                PsiDirectory[] directories = pane.getSelectedDirectories();
                if (directories != null && directories.length == 1 && directories[0] != null) {
                    VirtualFile directoryFile = directories[0].getVirtualFile();
                    if (directoryFile != null) {
                        return directoryFile;
                    }
                }
            }
        }

        return null;
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
