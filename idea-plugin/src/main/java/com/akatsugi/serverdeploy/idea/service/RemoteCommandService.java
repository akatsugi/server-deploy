package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsService;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteCommandService {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int POLL_INTERVAL_MILLIS = 100;
    private static final int READER_JOIN_TIMEOUT_MILLIS = 1000;

    public CommandResult execute(ServerConfig serverConfig, String command) throws JSchException, IOException, InterruptedException {
        AtomicReference<CommandResult> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        RunningCommand runningCommand = start(serverConfig, command, new OutputListener() {
            @Override
            public void onCompleted(CommandResult result) {
                resultRef.set(result);
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
            }
        });

        CommandResult result = runningCommand.awaitCompletion();
        if (errorRef.get() instanceof IOException ioException) {
            throw ioException;
        }
        if (errorRef.get() instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return resultRef.get() != null ? resultRef.get() : result;
    }

    public RunningCommand start(ServerConfig serverConfig, String command, OutputListener listener) throws JSchException, IOException {
        Session session = openSession(serverConfig);
        ChannelExec exec = null;
        try {
            exec = (ChannelExec) session.openChannel("exec");
            exec.setCommand(command);
            exec.setInputStream(null);

            InputStream stdout = exec.getInputStream();
            InputStream stderr = exec.getErrStream();
            exec.connect(CONNECT_TIMEOUT);

            RunningCommand runningCommand = new RunningCommand(session, exec, stdout, stderr, listener);
            runningCommand.start();
            return runningCommand;
        } catch (IOException | JSchException exception) {
            if (exec != null) {
                exec.disconnect();
            }
            session.disconnect();
            throw exception;
        }
    }

    public String renderCommand(String template, Path selectedPath, ResolvedUploadTarget target) {
        String commandTemplate = ServerDeploySettingsService.normalizeShellCommand(template);
        String remotePath = target.getRemoteTargetPath();
        String remoteDirectory = target.getRemoteMappingDirectory();
        ServerConfig serverConfig = target.getServerConfig();

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("${remotePath}", remotePath);
        placeholders.put("${remoteDirectory}", remoteDirectory);
        placeholders.put("${serverName}", safe(serverConfig.getName()));
        placeholders.put("${host}", safe(serverConfig.getHost()));
        placeholders.put("${username}", safe(serverConfig.getUsername()));
        placeholders.put("${defaultDirectory}",
                ServerDeploySettingsService.normalizeRemoteDirectory(serverConfig.getDefaultDirectory()));

        String rendered = commandTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue());
        }
        String script = "cd " + shellQuote(remoteDirectory) + "\n" + rendered;
        return "sh -lc " + shellQuote(script);
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

    public interface OutputListener {
        default void onStdout(String text) {
        }

        default void onStderr(String text) {
        }

        default void onCompleted(CommandResult result) {
        }

        default void onError(Throwable error) {
        }
    }

    public static class RunningCommand {
        private final Session session;
        private final ChannelExec exec;
        private final InputStream stdout;
        private final InputStream stderr;
        private final OutputListener listener;
        private final StringBuffer stdoutBuffer = new StringBuffer();
        private final StringBuffer stderrBuffer = new StringBuffer();
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private volatile CommandResult result;

        private RunningCommand(
                Session session,
                ChannelExec exec,
                InputStream stdout,
                InputStream stderr,
                OutputListener listener
        ) {
            this.session = session;
            this.exec = exec;
            this.stdout = stdout;
            this.stderr = stderr;
            this.listener = listener == null ? new OutputListener() {
            } : listener;
        }

        private void start() {
            Thread stdoutThread = createReaderThread(stdout, false, "server-deploy-stdout");
            Thread stderrThread = createReaderThread(stderr, true, "server-deploy-stderr");
            stdoutThread.start();
            stderrThread.start();

            Thread waiter = new Thread(() -> waitForExit(stdoutThread, stderrThread), "server-deploy-command-exit");
            waiter.setDaemon(true);
            waiter.start();
        }

        public boolean isRunning() {
            return !completed.get() && exec.isConnected() && !exec.isClosed();
        }

        public void stop() {
            stopRequested.set(true);
            disconnectQuietly();
        }

        public CommandResult awaitCompletion() throws InterruptedException {
            completionLatch.await();
            return result;
        }

        private Thread createReaderThread(InputStream stream, boolean stderrStream, String threadName) {
            Thread thread = new Thread(() -> readStream(stream, stderrStream), threadName);
            thread.setDaemon(true);
            return thread;
        }

        private void readStream(InputStream stream, boolean stderrStream) {
            byte[] buffer = new byte[4096];
            try {
                while (true) {
                    int length = stream.read(buffer);
                    if (length < 0) {
                        return;
                    }
                    if (length == 0) {
                        continue;
                    }
                    String text = new String(buffer, 0, length, StandardCharsets.UTF_8);
                    if (stderrStream) {
                        stderrBuffer.append(text);
                        listener.onStderr(text);
                    } else {
                        stdoutBuffer.append(text);
                        listener.onStdout(text);
                    }
                }
            } catch (IOException exception) {
                if (!stopRequested.get() && !completed.get()) {
                    finishWithError(exception);
                }
            }
        }

        private void waitForExit(Thread stdoutThread, Thread stderrThread) {
            try {
                while (exec.isConnected() && !exec.isClosed()) {
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                }
                closeQuietly(stdout);
                closeQuietly(stderr);
                stdoutThread.join(READER_JOIN_TIMEOUT_MILLIS);
                stderrThread.join(READER_JOIN_TIMEOUT_MILLIS);
                finish(new CommandResult(
                        exec.getExitStatus(),
                        stdoutBuffer.toString(),
                        stderrBuffer.toString(),
                        stopRequested.get()
                ));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                finishWithError(exception);
            } finally {
                disconnectQuietly();
            }
        }

        private void finish(CommandResult commandResult) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            result = commandResult;
            listener.onCompleted(commandResult);
            completionLatch.countDown();
        }

        private void finishWithError(Throwable error) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            listener.onError(error);
            result = new CommandResult(-1, stdoutBuffer.toString(), stderrBuffer.toString(), stopRequested.get());
            completionLatch.countDown();
            disconnectQuietly();
        }

        private void disconnectQuietly() {
            if (exec.isConnected()) {
                exec.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }

        private void closeQuietly(InputStream stream) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final boolean stoppedByUser;

        public CommandResult(int exitCode, String stdout, String stderr, boolean stoppedByUser) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.stoppedByUser = stoppedByUser;
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

        public boolean isStoppedByUser() {
            return stoppedByUser;
        }

        public boolean isSuccess() {
            return exitCode == 0 && !stoppedByUser;
        }
    }
}
