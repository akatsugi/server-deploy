package com.akatsugi.serverdeploy.idea.action;

import com.akatsugi.serverdeploy.idea.model.BatchUploadTarget;
import com.akatsugi.serverdeploy.idea.service.MappingResolver;
import com.akatsugi.serverdeploy.idea.service.RemoteUploadService;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.akatsugi.serverdeploy.idea.ui.UploadTargetDialog;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
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
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UploadToServerAction extends AnAction implements DumbAware {

    private final ServerDeploySettingsService settingsService = ServerDeploySettingsService.getInstance();
    private final MappingResolver mappingResolver = new MappingResolver();
    private final RemoteUploadService remoteUploadService = new RemoteUploadService();

    @Override
    public void update(@NotNull AnActionEvent event) {
        List<VirtualFile> files = resolveSelectedFiles(event);
        Context context = buildContext(event);
        boolean visible = !files.isEmpty();
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

        UploadTargetDialog dialog = new UploadTargetDialog(context.project, context.selectedPaths.size(), context.targets);
        if (!dialog.showAndGet()) {
            return;
        }

        BatchUploadTarget target = dialog.getSelectedTarget();
        if (target == null) {
            return;
        }

        settingsService.markServerLastUsed(target.getServerConfig().getId());
        runUpload(context.project, target, dialog.isDeleteExisting());
    }

    private void runUpload(Project project, BatchUploadTarget target, boolean deleteExisting) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "上传到服务器", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                try {
                    List<RemoteUploadService.UploadRequest> requests = target.getUploadItems().stream()
                            .map(item -> new RemoteUploadService.UploadRequest(
                                    item.localPath(),
                                    item.target().getRemoteTargetPath(),
                                    item.target().getRemoteMappingDirectory()
                            ))
                            .toList();

                    remoteUploadService.uploadToExactPaths(
                            target.getServerConfig(),
                            requests,
                            deleteExisting,
                            new RemoteUploadService.ProgressListener() {
                                @Override
                                public void onStart(int totalFiles) {
                                    indicator.setFraction(0.0);
                                    indicator.setText("正在连接 " + target.getServerConfig().getName());
                                    indicator.setText2("文件总数：" + totalFiles);
                                }

                                @Override
                                public void onProgress(
                                        String fileName,
                                        String mappingDirectory,
                                        long uploadedBytes,
                                        long totalBytes,
                                        int completedFiles,
                                        int totalFiles
                                ) {
                                    indicator.setText("正在上传到 " + target.getServerConfig().getName() + " | 映射目录：" + mappingDirectory);
                                    indicator.setText2(fileName + " (" + formatBytes(uploadedBytes) + " / " + formatBytes(totalBytes)
                                            + ", " + completedFiles + "/" + totalFiles + ")");
                                    double fileFraction = totalBytes <= 0 ? 0.0 : Math.min(1.0, (double) uploadedBytes / (double) totalBytes);
                                    double overallProgress = totalFiles == 0 ? 1.0 : ((double) completedFiles + fileFraction) / (double) totalFiles;
                                    indicator.setFraction(Math.min(1.0, overallProgress));
                                }
                            }
                    );
                    showNotification(project, NotificationType.INFORMATION, "上传完成",
                            target.getServerConfig().getName() + "，共上传 " + target.getUploadItems().size() + " 个选择项");
                } catch (IOException | JSchException | SftpException exception) {
                    showNotification(project, NotificationType.ERROR, "上传失败", exception.getMessage());
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
        List<VirtualFile> files = resolveSelectedFiles(event);
        if (project == null || files.isEmpty()) {
            return null;
        }

        List<Path> selectedPaths = new ArrayList<>();
        for (VirtualFile file : files) {
            if (file == null || !file.isInLocalFileSystem()) {
                return null;
            }
            selectedPaths.add(Path.of(file.getPath()).toAbsolutePath().normalize());
        }

        List<BatchUploadTarget> targets = mappingResolver.resolveBatch(
                selectedPaths,
                settingsService.getServers(),
                settingsService.getMappings()
        );
        return targets.isEmpty() ? null : new Context(project, selectedPaths, targets);
    }

    private List<VirtualFile> resolveSelectedFiles(AnActionEvent event) {
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null && files.length > 0) {
            return deduplicate(List.of(files));
        }

        List<VirtualFile> resolved = new ArrayList<>();
        VirtualFile single = resolveSingleFile(event);
        if (single != null) {
            resolved.add(single);
        }

        Project project = event.getProject();
        if (project != null) {
            ProjectView projectView = ProjectView.getInstance(project);
            AbstractProjectViewPane pane = projectView == null ? null : projectView.getCurrentProjectViewPane();
            if (pane != null) {
                PsiElement[] psiElements = pane.getSelectedPSIElements();
                if (psiElements != null && psiElements.length > 0) {
                    for (PsiElement psiElement : psiElements) {
                        VirtualFile psiFile = PsiUtilCore.getVirtualFile(psiElement);
                        if (psiFile != null) {
                            resolved.add(psiFile);
                        }
                    }
                }

                PsiDirectory[] directories = pane.getSelectedDirectories();
                if (directories != null && directories.length > 0) {
                    for (PsiDirectory directory : directories) {
                        if (directory != null && directory.getVirtualFile() != null) {
                            resolved.add(directory.getVirtualFile());
                        }
                    }
                }
            }
        }
        return deduplicate(resolved);
    }

    private VirtualFile resolveSingleFile(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            return file;
        }

        PsiElement[] elements = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        if (elements != null && elements.length == 1) {
            return PsiUtilCore.getVirtualFile(elements[0]);
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

    private List<VirtualFile> deduplicate(List<VirtualFile> files) {
        Map<String, VirtualFile> deduplicated = new LinkedHashMap<>();
        for (VirtualFile file : files) {
            if (file != null) {
                deduplicated.putIfAbsent(file.getPath(), file);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }

    private static class Context {
        private final Project project;
        private final List<Path> selectedPaths;
        private final List<BatchUploadTarget> targets;

        private Context(Project project, List<Path> selectedPaths, List<BatchUploadTarget> targets) {
            this.project = project;
            this.selectedPaths = List.copyOf(selectedPaths);
            this.targets = List.copyOf(targets);
        }
    }
}
