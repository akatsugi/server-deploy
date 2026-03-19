package com.akatsugi.serverdeploy.service;

import com.akatsugi.serverdeploy.model.ServerConfig;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoteOpsService {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int MAX_UPLOAD_THREADS = 4;

    public interface LogSink {
        void log(String message);
    }

    public void upload(ServerConfig config, Path localPath, String remoteDirectory, boolean deleteExisting, LogSink logSink)
            throws JSchException, SftpException, IOException {
        String normalizedRemoteDirectory = normalizeRemotePath(remoteDirectory);
        String remoteTarget = joinRemotePath(normalizedRemoteDirectory, localPath.getFileName().toString());
        uploadToExactPath(config, localPath, remoteTarget, deleteExisting, logSink);
    }

    public void uploadToExactPath(ServerConfig config, Path localPath, String remoteTargetPath, boolean deleteExisting, LogSink logSink)
            throws JSchException, SftpException, IOException {
        UploadPlan plan = buildUploadPlan(localPath, remoteTargetPath);
        logSink.log("Upload target: " + plan.getLocalRoot() + " -> " + plan.getRemoteTargetPath());
        logSink.log("Upload summary: total files = " + plan.getTotalFiles() + ", threads = " + determineThreadCount(plan.getTotalFiles()));

        prepareRemoteTarget(config, plan, deleteExisting, logSink);
        uploadFiles(config, plan, logSink);
        logUploadCompleted(plan, logSink);
    }

    public CommandResult execute(ServerConfig config, String remoteDirectory, String command)
            throws JSchException, IOException {
        Session session = openSession(config);
        ChannelExec exec = null;
        try {
            exec = (ChannelExec) session.openChannel("exec");
            String workingCommand = "cd " + shellQuote(normalizeRemotePath(remoteDirectory)) + " && " + command;
            String fullCommand = "if command -v bash >/dev/null 2>&1; then bash -lc "
                    + shellQuote(workingCommand)
                    + "; else sh -lc "
                    + shellQuote(workingCommand)
                    + "; fi";
            exec.setCommand(fullCommand);
            exec.setInputStream(null);

            InputStream stdout = exec.getInputStream();
            InputStream stderr = exec.getExtInputStream();
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();

            exec.connect(CONNECT_TIMEOUT);
            awaitCommandCompletion(exec, stdout, stderr, outputBuffer, errorBuffer);

            return new CommandResult(
                    exec.getExitStatus(),
                    outputBuffer.toString(StandardCharsets.UTF_8),
                    errorBuffer.toString(StandardCharsets.UTF_8),
                    workingCommand
            );
        } finally {
            if (exec != null) {
                exec.disconnect();
            }
            session.disconnect();
        }
    }

    private UploadPlan buildUploadPlan(Path localPath, String remoteTargetPath) throws IOException {
        Path normalizedLocalPath = localPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedLocalPath)) {
            throw new IOException("Local path does not exist: " + normalizedLocalPath);
        }

        UploadPlan plan = new UploadPlan(normalizedLocalPath, normalizeRemotePath(remoteTargetPath), Files.isDirectory(normalizedLocalPath));
        if (plan.isDirectory()) {
            Files.walkFileTree(normalizedLocalPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path relativePath = normalizedLocalPath.relativize(dir);
                    String remoteDirectory = relativePath.getNameCount() == 0
                            ? plan.getRemoteTargetPath()
                            : joinRemotePath(plan.getRemoteTargetPath(), toRemoteRelativePath(relativePath));
                    plan.addDirectory(remoteDirectory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relativePath = normalizedLocalPath.relativize(file);
                    String remoteFile = joinRemotePath(plan.getRemoteTargetPath(), toRemoteRelativePath(relativePath));
                    plan.addFile(new UploadFileEntry(file, remoteFile));
                    return FileVisitResult.CONTINUE;
                }
            });
            plan.sortDirectories();
            return plan;
        }

        plan.addFile(new UploadFileEntry(normalizedLocalPath, plan.getRemoteTargetPath()));
        return plan;
    }

    private void prepareRemoteTarget(ServerConfig config, UploadPlan plan, boolean deleteExisting, LogSink logSink)
            throws JSchException, SftpException {
        Session session = openSession(config);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);

            if (deleteExisting && exists(sftp, plan.getRemoteTargetPath())) {
                logSink.log("Delete remote target: " + plan.getRemoteTargetPath());
                deleteRecursively(sftp, plan.getRemoteTargetPath());
            }

            if (plan.isDirectory()) {
                for (String remoteDirectory : plan.getRemoteDirectories()) {
                    ensureDirectory(sftp, remoteDirectory);
                }
            } else {
                ensureParentDirectory(sftp, plan.getRemoteTargetPath());
            }
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            session.disconnect();
        }
    }

    private void uploadFiles(ServerConfig config, UploadPlan plan, LogSink logSink)
            throws JSchException, SftpException, IOException {
        int totalFiles = plan.getTotalFiles();
        if (totalFiles == 0) {
            return;
        }

        int threadCount = determineThreadCount(totalFiles);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger completedCount = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<Future<Void>>();

        try {
            for (UploadFileEntry entry : plan.getFiles()) {
                futures.add(executor.submit(() -> {
                    uploadSingleFile(config, entry);
                    int done = completedCount.incrementAndGet();
                    logSink.log("[" + done + "/" + totalFiles + "] Uploaded: " + entry.getLocalFile() + " -> " + entry.getRemoteFile());
                    return null;
                }));
            }

            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Upload interrupted", e);
        } catch (ExecutionException e) {
            executor.shutdownNow();
            rethrowUploadException(e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private void uploadSingleFile(ServerConfig config, UploadFileEntry entry)
            throws JSchException, SftpException {
        Session session = openSession(config);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);
            ensureParentDirectory(sftp, entry.getRemoteFile());
            sftp.put(entry.getLocalFile().toString(), entry.getRemoteFile());
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            session.disconnect();
        }
    }

    private void logUploadCompleted(UploadPlan plan, LogSink logSink) {
        logSink.log("========================================");
        logSink.log("UPLOAD COMPLETED");
        logSink.log("Remote target: " + plan.getRemoteTargetPath());
        logSink.log("Files uploaded: " + plan.getTotalFiles() + "/" + plan.getTotalFiles());
        logSink.log("========================================");
    }

    private void rethrowUploadException(Throwable throwable) throws JSchException, SftpException, IOException {
        if (throwable instanceof JSchException) {
            throw (JSchException) throwable;
        }
        if (throwable instanceof SftpException) {
            throw (SftpException) throwable;
        }
        if (throwable instanceof IOException) {
            throw (IOException) throwable;
        }
        throw new IOException("Upload failed", throwable);
    }

    private int determineThreadCount(int totalFiles) {
        return Math.max(1, Math.min(MAX_UPLOAD_THREADS, totalFiles));
    }

    private Session openSession(ServerConfig config) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        session.setPassword(config.getPassword());
        Properties properties = new Properties();
        properties.put("StrictHostKeyChecking", "no");
        session.setConfig(properties);
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
        String normalized = normalizeRemotePath(remoteDirectory);
        if ("/".equals(normalized)) {
            return;
        }

        String[] parts = normalized.substring(1).split("/");
        String current = "";
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            current = current + "/" + part;
            if (!exists(sftp, current)) {
                try {
                    sftp.mkdir(current);
                } catch (SftpException e) {
                    if (!exists(sftp, current)) {
                        throw e;
                    }
                }
            }
        }
    }

    private boolean exists(ChannelSftp sftp, String remotePath) {
        try {
            sftp.lstat(remotePath);
            return true;
        } catch (SftpException e) {
            return false;
        }
    }

    private void deleteRecursively(ChannelSftp sftp, String remotePath) throws SftpException {
        SftpATTRS attrs = sftp.lstat(remotePath);
        if (attrs.isDir()) {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftp.ls(remotePath);
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                deleteRecursively(sftp, joinRemotePath(remotePath, name));
            }
            sftp.rmdir(remotePath);
        } else {
            sftp.rm(remotePath);
        }
    }

    private void awaitCommandCompletion(ChannelExec exec, InputStream stdout, InputStream stderr,
            ByteArrayOutputStream outputBuffer, ByteArrayOutputStream errorBuffer) throws IOException {
        while (true) {
            drainAvailable(stdout, outputBuffer);
            drainAvailable(stderr, errorBuffer);

            if (exec.isClosed()) {
                drainAvailable(stdout, outputBuffer);
                drainAvailable(stderr, errorBuffer);
                break;
            }

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Command execution interrupted", e);
            }
        }
    }

    private void drainAvailable(InputStream inputStream, ByteArrayOutputStream buffer) throws IOException {
        if (inputStream == null) {
            return;
        }

        byte[] data = new byte[4096];
        while (inputStream.available() > 0) {
            int len = inputStream.read(data, 0, data.length);
            if (len < 0) {
                break;
            }
            buffer.write(data, 0, len);
        }
    }

    private String toRemoteRelativePath(Path relativePath) {
        String normalized = relativePath.toString().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String normalizeRemotePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }

        String normalized = path.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String joinRemotePath(String base, String child) {
        return normalizeRemotePath(base) + "/" + child.replace('\\', '/');
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static class UploadPlan {
        private final Path localRoot;
        private final String remoteTargetPath;
        private final boolean directory;
        private final List<String> remoteDirectories = new ArrayList<String>();
        private final List<UploadFileEntry> files = new ArrayList<UploadFileEntry>();

        private UploadPlan(Path localRoot, String remoteTargetPath, boolean directory) {
            this.localRoot = localRoot;
            this.remoteTargetPath = remoteTargetPath;
            this.directory = directory;
        }

        private Path getLocalRoot() {
            return localRoot;
        }

        private String getRemoteTargetPath() {
            return remoteTargetPath;
        }

        private boolean isDirectory() {
            return directory;
        }

        private List<String> getRemoteDirectories() {
            return remoteDirectories;
        }

        private List<UploadFileEntry> getFiles() {
            return files;
        }

        private int getTotalFiles() {
            return files.size();
        }

        private void addDirectory(String remoteDirectory) {
            remoteDirectories.add(remoteDirectory);
        }

        private void addFile(UploadFileEntry fileEntry) {
            files.add(fileEntry);
        }

        private void sortDirectories() {
            remoteDirectories.sort(Comparator.comparingInt(RemoteOpsService::countPathDepth));
        }
    }

    private static class UploadFileEntry {
        private final Path localFile;
        private final String remoteFile;

        private UploadFileEntry(Path localFile, String remoteFile) {
            this.localFile = localFile;
            this.remoteFile = remoteFile;
        }

        private Path getLocalFile() {
            return localFile;
        }

        private String getRemoteFile() {
            return remoteFile;
        }
    }

    private static int countPathDepth(String remotePath) {
        String normalized = remotePath == null ? "" : remotePath.trim();
        if (normalized.isEmpty() || "/".equals(normalized)) {
            return 0;
        }
        int depth = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final String fullCommand;

        public CommandResult(int exitCode, String stdout, String stderr, String fullCommand) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.fullCommand = fullCommand;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public String getFullCommand() {
            return fullCommand;
        }
    }
}

