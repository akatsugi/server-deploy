package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

public class RemoteUploadService {

    private static final int CONNECT_TIMEOUT = 10000;

    public interface ProgressListener {
        void onStart(int totalFiles);

        void onProgress(String fileName, int completed, int totalFiles);
    }

    public void uploadToExactPath(ServerConfig serverConfig,
            Path localPath,
            String remoteTargetPath,
            boolean deleteExisting,
            ProgressListener progressListener) throws IOException, JSchException, SftpException {
        UploadPlan plan = buildUploadPlan(localPath, remoteTargetPath);
        progressListener.onStart(plan.files.size());

        Session session = openSession(serverConfig);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);

            if (deleteExisting && exists(sftp, plan.remoteTargetPath)) {
                deleteRecursively(sftp, plan.remoteTargetPath);
            }

            if (plan.directory) {
                for (String directory : plan.remoteDirectories) {
                    ensureDirectory(sftp, directory);
                }
            } else {
                ensureParentDirectory(sftp, plan.remoteTargetPath);
            }

            int completed = 0;
            for (UploadEntry entry : plan.files) {
                ensureParentDirectory(sftp, entry.remotePath);
                sftp.put(entry.localPath.toString(), entry.remotePath);
                completed++;
                progressListener.onProgress(entry.localPath.getFileName().toString(), completed, plan.files.size());
            }
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            session.disconnect();
        }
    }

    private UploadPlan buildUploadPlan(Path localPath, String remoteTargetPath) throws IOException {
        Path normalizedLocalPath = localPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedLocalPath)) {
            throw new IOException("本地路径不存在：" + normalizedLocalPath);
        }

        String normalizedRemoteTarget = ServerDeploySettingsService.normalizeRemoteDirectory(remoteTargetPath);
        UploadPlan plan = new UploadPlan(normalizedRemoteTarget, Files.isDirectory(normalizedLocalPath));
        if (!plan.directory) {
            plan.files.add(new UploadEntry(normalizedLocalPath, normalizedRemoteTarget));
            return plan;
        }

        Files.walkFileTree(normalizedLocalPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path relative = normalizedLocalPath.relativize(dir);
                String remoteDirectory = relative.getNameCount() == 0
                        ? normalizedRemoteTarget
                        : ServerDeploySettingsService.normalizeRemoteDirectory(normalizedRemoteTarget + "/" + toRemoteRelative(relative));
                plan.remoteDirectories.add(remoteDirectory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = normalizedLocalPath.relativize(file);
                String remoteFile = ServerDeploySettingsService.normalizeRemoteDirectory(normalizedRemoteTarget + "/" + toRemoteRelative(relative));
                plan.files.add(new UploadEntry(file, remoteFile));
                return FileVisitResult.CONTINUE;
            }
        });
        return plan;
    }

    private Session openSession(ServerConfig serverConfig) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(serverConfig.getUsername(), serverConfig.getHost(), serverConfig.getPort());
        session.setPassword(serverConfig.getPassword());
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(CONNECT_TIMEOUT);
        return session;
    }

    private void ensureParentDirectory(ChannelSftp sftp, String remotePath) throws SftpException {
        int lastSlash = remotePath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return;
        }
        ensureDirectory(sftp, remotePath.substring(0, lastSlash));
    }

    private void ensureDirectory(ChannelSftp sftp, String remoteDirectory) throws SftpException {
        String normalized = ServerDeploySettingsService.normalizeRemoteDirectory(remoteDirectory);
        if ("/".equals(normalized)) {
            return;
        }
        String[] parts = normalized.substring(1).split("/");
        String current = "";
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            current = current + "/" + part;
            if (!exists(sftp, current)) {
                sftp.mkdir(current);
            }
        }
    }

    private boolean exists(ChannelSftp sftp, String remotePath) {
        try {
            sftp.lstat(remotePath);
            return true;
        } catch (SftpException ignored) {
            return false;
        }
    }

    private void deleteRecursively(ChannelSftp sftp, String remotePath) throws SftpException {
        SftpATTRS attrs = sftp.lstat(remotePath);
        if (!attrs.isDir()) {
            sftp.rm(remotePath);
            return;
        }
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(remotePath);
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            deleteRecursively(sftp, remotePath + "/" + name);
        }
        sftp.rmdir(remotePath);
    }

    private String toRemoteRelative(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    private static class UploadPlan {
        private final String remoteTargetPath;
        private final boolean directory;
        private final List<String> remoteDirectories = new ArrayList<>();
        private final List<UploadEntry> files = new ArrayList<>();

        private UploadPlan(String remoteTargetPath, boolean directory) {
            this.remoteTargetPath = remoteTargetPath;
            this.directory = directory;
        }
    }

    private static class UploadEntry {
        private final Path localPath;
        private final String remotePath;

        private UploadEntry(Path localPath, String remotePath) {
            this.localPath = localPath;
            this.remotePath = remotePath;
        }
    }
}
