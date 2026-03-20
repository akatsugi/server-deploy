package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class RemoteCommandService {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int POLL_INTERVAL_MILLIS = 100;

    public CommandResult execute(ServerConfig serverConfig, String command) throws JSchException, IOException, InterruptedException {
        Session session = openSession(serverConfig);
        ChannelExec exec = null;
        try {
            exec = (ChannelExec) session.openChannel("exec");
            exec.setCommand(command);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            exec.setInputStream(null);
            exec.setOutputStream(stdout, true);
            exec.setErrStream(stderr, true);
            exec.connect(CONNECT_TIMEOUT);

            while (!exec.isClosed()) {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }

            return new CommandResult(
                    exec.getExitStatus(),
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8)
            );
        } finally {
            if (exec != null) {
                exec.disconnect();
            }
            session.disconnect();
        }
    }

    public String renderCommand(String template, Path selectedPath, ResolvedUploadTarget target) {
        String commandTemplate = ServerDeploySettingsService.normalizeShellCommand(template);
        String remotePath = target.getRemoteTargetPath();
        String remoteDirectory = resolveRemoteDirectory(selectedPath, remotePath);
        ServerConfig serverConfig = target.getServerConfig();

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("${remotePath}", remotePath);
        placeholders.put("${remoteDirectory}", remoteDirectory);
        placeholders.put("${serverName}", safe(serverConfig.getName()));
        placeholders.put("${host}", safe(serverConfig.getHost()));
        placeholders.put("${username}", safe(serverConfig.getUsername()));
        placeholders.put("${defaultDirectory}", ServerDeploySettingsService.normalizeRemoteDirectory(serverConfig.getDefaultDirectory()));

        String rendered = commandTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue());
        }
        String script = "cd " + shellQuote(remoteDirectory) + "\n" + rendered;
        return "sh -lc " + shellQuote(script);
    }

    private String resolveRemoteDirectory(Path selectedPath, String remotePath) {
        if (Files.isDirectory(selectedPath)) {
            return remotePath;
        }
        int lastSlash = remotePath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return remotePath.substring(0, lastSlash);
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
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

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
