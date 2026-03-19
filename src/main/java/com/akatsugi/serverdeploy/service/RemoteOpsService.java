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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Vector;

public class RemoteOpsService {

    private static final int CONNECT_TIMEOUT = 10000;

    public interface LogSink {
        void log(String message);
    }

    public void upload(ServerConfig config, Path localPath, String remoteDirectory, boolean deleteExisting, LogSink logSink)
            throws JSchException, SftpException, IOException {
        Session session = openSession(config);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);

            String normalizedRemoteDirectory = normalizeRemotePath(remoteDirectory);
            ensureDirectory(sftp, normalizedRemoteDirectory);

            String remoteTarget = joinRemotePath(normalizedRemoteDirectory, localPath.getFileName().toString());
            if (deleteExisting && exists(sftp, remoteTarget)) {
                logSink.log("删除远端同名项: " + remoteTarget);
                deleteRecursively(sftp, remoteTarget);
            }

            if (Files.isDirectory(localPath)) {
                logSink.log("上传目录: " + localPath + " -> " + remoteTarget);
                uploadDirectory(sftp, localPath, remoteTarget, logSink);
            } else {
                logSink.log("上传文件: " + localPath + " -> " + remoteTarget);
                ensureParentDirectory(sftp, remoteTarget);
                sftp.put(localPath.toString(), remoteTarget);
            }
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            session.disconnect();
        }
    }

    public CommandResult execute(ServerConfig config, String remoteDirectory, String command)
            throws JSchException, IOException {
        Session session = openSession(config);
        ChannelExec exec = null;
        try {
            exec = (ChannelExec) session.openChannel("exec");
            String fullCommand = "cd " + shellQuote(normalizeRemotePath(remoteDirectory)) + " && " + command;
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
                    fullCommand
            );
        } finally {
            if (exec != null) {
                exec.disconnect();
            }
            session.disconnect();
        }
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

    private void uploadDirectory(ChannelSftp sftp, Path localDir, String remoteDir, LogSink logSink)
            throws IOException, SftpException {
        ensureDirectory(sftp, remoteDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localDir)) {
            for (Path child : stream) {
                String remoteChild = joinRemotePath(remoteDir, child.getFileName().toString());
                if (Files.isDirectory(child)) {
                    uploadDirectory(sftp, child, remoteChild, logSink);
                } else {
                    ensureParentDirectory(sftp, remoteChild);
                    logSink.log("上传文件: " + child + " -> " + remoteChild);
                    sftp.put(child.toString(), remoteChild);
                }
            }
        }
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
                sftp.mkdir(current);
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
                throw new IOException("命令执行被中断", e);
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
        return normalizeRemotePath(base) + "/" + child;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
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
