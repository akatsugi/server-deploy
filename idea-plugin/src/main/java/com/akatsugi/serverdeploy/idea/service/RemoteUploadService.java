package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

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

        void onProgress(String fileName, String mappingDirectory, long uploadedBytes, long totalBytes, int completedFiles, int totalFiles);
    }

    public void uploadToExactPath(
            ServerConfig serverConfig,
            Path localPath,
            String remoteTargetPath,
            String mappingDirectory,
            boolean deleteExisting,
            ProgressListener progressListener
    ) throws IOException, JSchException, SftpException {
        uploadToExactPaths(
                serverConfig,
                List.of(new UploadRequest(localPath, remoteTargetPath, mappingDirectory)),
                deleteExisting,
                progressListener
        );
    }

    public void uploadToExactPaths(
            ServerConfig serverConfig,
            List<UploadRequest> requests,
            boolean deleteExisting,
            ProgressListener progressListener
    ) throws IOException, JSchException, SftpException {
        List<UploadPlan> plans = new ArrayList<>();
        int totalFiles = 0;
        for (UploadRequest request : requests) {
            UploadPlan plan = buildUploadPlan(request.localPath(), request.remoteTargetPath(), request.mappingDirectory());
            plans.add(plan);
            totalFiles += plan.files.size();
        }
        final int totalFileCount = totalFiles;
        progressListener.onStart(totalFileCount);

        Session session = openSession(serverConfig);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);

            for (UploadPlan plan : plans) {
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
            }

            int completed = 0;
            for (UploadPlan plan : plans) {
                for (UploadEntry entry : plan.files) {
                    ensureParentDirectory(sftp, entry.remotePath);
                    int completedFiles = completed;
                    sftp.put(entry.localPath.toString(), entry.remotePath, new SftpProgressMonitor() {
                        private long transferred;

                        @Override
                        public void init(int op, String src, String dest, long max) {
                            transferred = 0L;
                            progressListener.onProgress(
                                    entry.localPath.getFileName().toString(),
                                    entry.mappingDirectory,
                                    0L,
                                    entry.size,
                                    completedFiles,
                                    totalFileCount
                            );
                        }

                        @Override
                        public boolean count(long count) {
                            transferred += count;
                            progressListener.onProgress(
                                    entry.localPath.getFileName().toString(),
                                    entry.mappingDirectory,
                                    Math.min(transferred, entry.size),
                                    entry.size,
                                    completedFiles,
                                    totalFileCount
                            );
                            return true;
                        }

                        @Override
                        public void end() {
                            progressListener.onProgress(
                                    entry.localPath.getFileName().toString(),
                                    entry.mappingDirectory,
                                    entry.size,
                                    entry.size,
                                    completedFiles + 1,
                                    totalFileCount
                            );
                        }
                    });
                    completed++;
                }
            }
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            session.disconnect();
        }
    }

    public void testConnection(ServerConfig serverConfig) throws JSchException, SftpException {
        Session session = openSession(serverConfig);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);

            String defaultDirectory = ServerDeploySettingsService.normalizeRemoteDirectory(serverConfig.getDefaultDirectory());
            if (!"/".equals(defaultDirectory)) {
                sftp.cd(defaultDirectory);
            }
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            session.disconnect();
        }
    }

    private UploadPlan buildUploadPlan(Path localPath, String remoteTargetPath, String mappingDirectory) throws IOException {
        Path normalizedLocalPath = localPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedLocalPath)) {
            throw new IOException("本地路径不存在：" + normalizedLocalPath);
        }

        String normalizedRemoteTarget = ServerDeploySettingsService.normalizeRemoteDirectory(remoteTargetPath);
        String normalizedMappingDirectory = ServerDeploySettingsService.normalizeRemoteDirectory(mappingDirectory);
        UploadPlan plan = new UploadPlan(normalizedRemoteTarget, normalizedMappingDirectory, Files.isDirectory(normalizedLocalPath));
        if (!plan.directory) {
            plan.files.add(new UploadEntry(normalizedLocalPath, normalizedRemoteTarget, normalizedMappingDirectory, Files.size(normalizedLocalPath)));
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
                plan.files.add(new UploadEntry(file, remoteFile, normalizedMappingDirectory, attrs.size()));
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

    public record UploadRequest(Path localPath, String remoteTargetPath, String mappingDirectory) {
    }

    private static class UploadPlan {
        private final String remoteTargetPath;
        private final String mappingDirectory;
        private final boolean directory;
        private final List<String> remoteDirectories = new ArrayList<>();
        private final List<UploadEntry> files = new ArrayList<>();

        private UploadPlan(String remoteTargetPath, String mappingDirectory, boolean directory) {
            this.remoteTargetPath = remoteTargetPath;
            this.mappingDirectory = mappingDirectory;
            this.directory = directory;
        }
    }

    private static class UploadEntry {
        private final Path localPath;
        private final String remotePath;
        private final String mappingDirectory;
        private final long size;

        private UploadEntry(Path localPath, String remotePath, String mappingDirectory, long size) {
            this.localPath = localPath;
            this.remotePath = remotePath;
            this.mappingDirectory = mappingDirectory;
            this.size = size;
        }
    }
}
