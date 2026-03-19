package com.akatsugi.serverdeploy.idea.action;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeployConfigurable;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.akatsugi.serverdeploy.idea.ui.MappingEditDialog;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ConfigurePathMappingAction extends AnAction implements DumbAware {

    private final ServerDeploySettingsService settingsService = ServerDeploySettingsService.getInstance();

    @Override
    public void update(@NotNull AnActionEvent event) {
        Context context = buildContext(event);
        event.getPresentation().setEnabledAndVisible(context != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Context context = buildContext(event);
        if (context == null) {
            return;
        }

        List<ServerConfig> servers = settingsService.getServers();
        if (servers.isEmpty()) {
            showNotification(context.project, NotificationType.WARNING,
                    "未配置服务器",
                    "请先至少配置一个服务器，再配置路径映射。");
            ShowSettingsUtil.getInstance().showSettingsDialog(context.project, ServerDeployConfigurable.class);
            return;
        }

        MappingEditDialog dialog = new MappingEditDialog(
                servers,
                null,
                context.localDirectory.toString(),
                context.project.getBasePath()
        );
        if (!dialog.showAndGet()) {
            return;
        }

        DirectoryMapping mapping = dialog.getDirectoryMapping();
        mapping.ensureIdentity();

        List<DirectoryMapping> mappings = settingsService.getMappings();
        boolean updated = false;
        for (int i = 0; i < mappings.size(); i++) {
            DirectoryMapping current = mappings.get(i);
            boolean sameTarget = Objects.equals(current.getServerId(), mapping.getServerId())
                    && Objects.equals(ServerDeploySettingsService.normalizeLocalDirectory(current.getLocalDirectory()), mapping.getLocalDirectory());
            if (sameTarget) {
                mapping.setId(current.getId());
                mappings.set(i, mapping);
                updated = true;
                break;
            }
        }
        if (!updated) {
            mappings.add(mapping);
        }

        settingsService.update(servers, mappings);
        showNotification(context.project, NotificationType.INFORMATION,
                updated ? "映射已更新" : "映射已新增",
                mapping.getLocalDirectory() + " -> " + mapping.getRemoteDirectory());
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
        Path localDirectory = file.isDirectory() ? selectedPath : selectedPath.getParent();
        if (localDirectory == null) {
            return null;
        }
        return new Context(project, localDirectory);
    }

    private static class Context {
        private final Project project;
        private final Path localDirectory;

        private Context(Project project, Path localDirectory) {
            this.project = project;
            this.localDirectory = localDirectory;
        }
    }
}