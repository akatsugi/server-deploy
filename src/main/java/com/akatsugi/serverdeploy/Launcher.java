package com.akatsugi.serverdeploy;

import com.akatsugi.serverdeploy.model.ServerConfig;
import com.akatsugi.serverdeploy.model.WorkspaceConfig;
import com.akatsugi.serverdeploy.service.DatabaseService;
import com.akatsugi.serverdeploy.service.RemoteOpsService;
import com.akatsugi.serverdeploy.ui.FileTreeItem;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Launcher extends Application {

    private static final String TEXT_TITLE = "\u670d\u52a1\u5668\u6587\u4ef6\u4e0a\u4f20\u4e0e\u811a\u672c\u6267\u884c";
    private static final String TEXT_WORKSPACE = "\u5de5\u4f5c\u533a";
    private static final String TEXT_CHOOSE_WORKSPACE = "\u9009\u62e9\u5de5\u4f5c\u533a";
    private static final String TEXT_REFRESH_TREE = "\u5237\u65b0\u6587\u4ef6\u6811";
    private static final String TEXT_SERVER = "\u670d\u52a1\u5668";
    private static final String TEXT_NEW_SERVER = "\u65b0\u5efa\u670d\u52a1\u5668";
    private static final String TEXT_WORKSPACE_TREE = "\u5de5\u4f5c\u533a\u6587\u4ef6\u6811";
    private static final String TEXT_SERVER_CONFIG = "\u670d\u52a1\u5668\u914d\u7f6e";
    private static final String TEXT_NAME = "\u540d\u79f0";
    private static final String TEXT_HOST = "\u5730\u5740";
    private static final String TEXT_PORT = "\u7aef\u53e3";
    private static final String TEXT_USERNAME = "\u7528\u6237\u540d";
    private static final String TEXT_PASSWORD = "\u5bc6\u7801";
    private static final String TEXT_REMOTE_DIR = "\u8fdc\u7aef\u76ee\u5f55";
    private static final String TEXT_SAVE_SERVER = "\u4fdd\u5b58\u670d\u52a1\u5668";
    private static final String TEXT_LOAD_SELECTED = "\u8f7d\u5165\u9009\u4e2d\u914d\u7f6e";
    private static final String TEXT_REMOTE_COMMAND = "\u8fdc\u7aef\u547d\u4ee4\u6267\u884c";
    private static final String TEXT_SET_EXEC_DIR = "\u8bbe\u7f6e\u6267\u884c\u76ee\u5f55";
    private static final String TEXT_EXECUTE_COMMAND = "\u6267\u884c\u547d\u4ee4";
    private static final String TEXT_EXEC_LOG = "\u6267\u884c\u65e5\u5fd7";
    private static final String TEXT_CLEAR_LOG = "\u6e05\u7a7a\u65e5\u5fd7";
    private static final String TEXT_PROMPT_COMMAND = "\u8f93\u5165\u8fdc\u7aef shell \u547d\u4ee4\uff0c\u4f8b\u5982\uff1ash deploy.sh";
    private static final String TEXT_SWITCHED_WORKSPACE = "\u5de5\u4f5c\u533a\u5df2\u5207\u6362: ";
    private static final String TEXT_SELECTED_SERVER = "\u5df2\u9009\u62e9\u670d\u52a1\u5668: ";
    private static final String TEXT_TREE_REFRESHED = "\u6587\u4ef6\u6811\u5df2\u5237\u65b0";
    private static final String TEXT_SERVER_SAVED = "\u670d\u52a1\u5668\u914d\u7f6e\u5df2\u4fdd\u5b58: ";
    private static final String TEXT_SAVE_SERVER_FAILED = "\u4fdd\u5b58\u670d\u52a1\u5668\u5931\u8d25";
    private static final String TEXT_SET_REMOTE_DIR = "\u8bbe\u7f6e\u8fdc\u7aef\u76ee\u5f55";
    private static final String TEXT_SET_REMOTE_DIR_HEADER = "\u8f93\u5165\u4e0a\u4f20\u548c\u547d\u4ee4\u6267\u884c\u5171\u7528\u7684\u8fdc\u7aef\u76ee\u5f55";
    private static final String TEXT_MISSING_SERVER = "\u7f3a\u5c11\u670d\u52a1\u5668";
    private static final String TEXT_SELECT_SERVER_FIRST = "\u8bf7\u5148\u9009\u62e9\u6216\u4fdd\u5b58\u670d\u52a1\u5668\u914d\u7f6e";
    private static final String TEXT_INCOMPLETE_PARAMS = "\u53c2\u6570\u4e0d\u5b8c\u6574";
    private static final String TEXT_COMMAND_REQUIRED = "\u8fdc\u7aef\u76ee\u5f55\u548c shell \u547d\u4ee4\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String TEXT_EXECUTING = "\u5f00\u59cb\u6267\u884c\u8fdc\u7aef\u547d\u4ee4";
    private static final String TEXT_EXEC_DIR = "\u6267\u884c\u76ee\u5f55: ";
    private static final String TEXT_COMMAND = "\u6267\u884c\u547d\u4ee4: ";
    private static final String TEXT_STDOUT = "\u6807\u51c6\u8f93\u51fa:\n";
    private static final String TEXT_STDERR = "\u9519\u8bef\u8f93\u51fa:\n";
    private static final String TEXT_EXIT_CODE = "\u547d\u4ee4\u9000\u51fa\u7801: ";
    private static final String TEXT_COMMAND_FAILED = "\u547d\u4ee4\u6267\u884c\u5931\u8d25";
    private static final String TEXT_MISSING_REMOTE_DIR = "\u7f3a\u5c11\u8fdc\u7aef\u76ee\u5f55";
    private static final String TEXT_INPUT_REMOTE_DIR = "\u8bf7\u5148\u8f93\u5165\u8fdc\u7aef\u76ee\u5f55";
    private static final String TEXT_DELETE_BEFORE_UPLOAD = "\u4e0a\u4f20\u524d\u5220\u9664\u8fdc\u7aef\u540c\u540d\u6587\u4ef6\u6216\u6587\u4ef6\u5939";
    private static final String TEXT_LOCAL_PATH = "\u672c\u5730\u8def\u5f84: ";
    private static final String TEXT_REMOTE_PATH = "\u8fdc\u7aef\u76ee\u5f55: ";
    private static final String TEXT_CONFIRM_UPLOAD = "\u786e\u8ba4\u4e0a\u4f20";
    private static final String TEXT_UPLOAD_HEADER = "\u4e0a\u4f20\u6587\u4ef6\u6216\u76ee\u5f55\u5230\u8fdc\u7aef";
    private static final String TEXT_START_UPLOAD = "\u5f00\u59cb\u4e0a\u4f20";
    private static final String TEXT_UPLOAD_STARTED = "\u5f00\u59cb\u4e0a\u4f20: ";
    private static final String TEXT_UPLOAD_COMPLETED = "\u4e0a\u4f20\u5b8c\u6210: ";
    private static final String TEXT_UPLOAD_FAILED = "\u4e0a\u4f20\u5931\u8d25";
    private static final String TEXT_UNKNOWN_ERROR = "\u672a\u77e5\u9519\u8bef";
    private static final String TEXT_UPLOAD_MENU = "\u4e0a\u4f20\u5230\u5f53\u524d\u670d\u52a1\u5668\u76ee\u5f55";
    private static final String TEXT_COPY_FILE_NAME = "\u590d\u5236\u5f53\u524d\u6587\u4ef6\u540d";
    private static final String TEXT_COPY_FILE_PATH = "\u590d\u5236\u5f53\u524d\u6587\u4ef6\u8def\u5f84";
    private static final String TEXT_REFRESH_NODE = "\u5237\u65b0\u5f53\u524d\u8282\u70b9";
    private static final String TEXT_COPIED_FILE_NAME = "\u5df2\u590d\u5236\u6587\u4ef6\u540d: ";
    private static final String TEXT_COPIED_FILE_PATH = "\u5df2\u590d\u5236\u6587\u4ef6\u8def\u5f84: ";
    private static final String TEXT_NODE_REFRESHED = "\u5df2\u5237\u65b0: ";

    private final DatabaseService databaseService = new DatabaseService();
    private final RemoteOpsService remoteOpsService = new RemoteOpsService();

    private final ObservableList<WorkspaceConfig> workspaceItems = FXCollections.observableArrayList();
    private final ObservableList<ServerConfig> serverItems = FXCollections.observableArrayList();

    private ComboBox<WorkspaceConfig> workspaceCombo;
    private ComboBox<ServerConfig> serverCombo;
    private TreeView<Path> workspaceTree;
    private TextField serverNameField;
    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField remoteDirectoryField;
    private TextArea commandArea;
    private TextArea outputArea;

    private Long editingServerId;
    private boolean reloadingWorkspaces;
    private boolean reloadingServers;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        workspaceCombo = new ComboBox<>(workspaceItems);
        workspaceCombo.setMaxWidth(Double.MAX_VALUE);
        workspaceCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (reloadingWorkspaces || newValue == null) {
                return;
            }
            WorkspaceConfig saved = databaseService.saveWorkspace(newValue.getPath());
            loadWorkspaceTree(Paths.get(saved.getPath()));
            reloadWorkspaces(saved.getPath());
            log(TEXT_SWITCHED_WORKSPACE + saved.getPath());
        });

        serverCombo = new ComboBox<>(serverItems);
        serverCombo.setMaxWidth(Double.MAX_VALUE);
        serverCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (reloadingServers || newValue == null) {
                return;
            }
            populateServerForm(newValue);
            databaseService.markServerLastUsed(newValue);
            reloadServers(newValue.getId());
            remoteDirectoryField.setText(newValue.getDefaultDirectory());
            log(TEXT_SELECTED_SERVER + newValue.getName());
        });

        workspaceTree = new TreeView<>();
        workspaceTree.setShowRoot(true);
        workspaceTree.setCellFactory(tree -> new WorkspaceTreeCell());

        commandArea = new TextArea();
        commandArea.setPromptText(TEXT_PROMPT_COMMAND);
        commandArea.setPrefRowCount(5);

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(buildTopPane(stage));
        root.setCenter(buildMainContent());

        reloadWorkspaces(null);
        reloadServers(null);

        Scene scene = new Scene(root, 1280, 780);
        stage.setTitle(TEXT_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildTopPane(Stage stage) {
        Button chooseWorkspaceButton = new Button(TEXT_CHOOSE_WORKSPACE);
        chooseWorkspaceButton.setOnAction(event -> chooseWorkspace(stage));

        Button refreshButton = new Button(TEXT_REFRESH_TREE);
        refreshButton.setOnAction(event -> refreshCurrentTree());

        HBox workspaceBox = new HBox(8, new Label(TEXT_WORKSPACE), workspaceCombo, chooseWorkspaceButton, refreshButton);
        workspaceBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(workspaceCombo, Priority.ALWAYS);

        Button newServerButton = new Button(TEXT_NEW_SERVER);
        newServerButton.setOnAction(event -> clearServerForm());

        HBox serverBox = new HBox(8, new Label(TEXT_SERVER), serverCombo, newServerButton);
        serverBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(serverCombo, Priority.ALWAYS);

        return new VBox(10, workspaceBox, serverBox, new Separator(Orientation.HORIZONTAL));
    }

    private SplitPane buildMainContent() {
        VBox leftPane = new VBox(8, new Label(TEXT_WORKSPACE_TREE), workspaceTree);
        leftPane.setPrefWidth(430);
        VBox.setVgrow(workspaceTree, Priority.ALWAYS);

        VBox rightPane = new VBox(12, buildServerForm(), buildCommandPanel(), buildLogPanel());
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setDividerPositions(0.35);
        return splitPane;
    }

    private VBox buildServerForm() {
        serverNameField = new TextField();
        hostField = new TextField();
        portField = new TextField("22");
        usernameField = new TextField();
        passwordField = new PasswordField();
        remoteDirectoryField = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label(TEXT_NAME), 0, 0);
        grid.add(serverNameField, 1, 0);
        grid.add(new Label(TEXT_HOST), 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(new Label(TEXT_PORT), 0, 2);
        grid.add(portField, 1, 2);
        grid.add(new Label(TEXT_USERNAME), 0, 3);
        grid.add(usernameField, 1, 3);
        grid.add(new Label(TEXT_PASSWORD), 0, 4);
        grid.add(passwordField, 1, 4);
        grid.add(new Label(TEXT_REMOTE_DIR), 0, 5);
        grid.add(remoteDirectoryField, 1, 5);
        GridPane.setHgrow(serverNameField, Priority.ALWAYS);
        GridPane.setHgrow(hostField, Priority.ALWAYS);
        GridPane.setHgrow(portField, Priority.ALWAYS);
        GridPane.setHgrow(usernameField, Priority.ALWAYS);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);
        GridPane.setHgrow(remoteDirectoryField, Priority.ALWAYS);

        Button saveButton = new Button(TEXT_SAVE_SERVER);
        saveButton.setOnAction(event -> saveServer());
        Button loadSelectedButton = new Button(TEXT_LOAD_SELECTED);
        loadSelectedButton.setOnAction(event -> {
            ServerConfig selected = serverCombo.getValue();
            if (selected != null) {
                populateServerForm(selected);
            }
        });

        return new VBox(8, new Label(TEXT_SERVER_CONFIG), grid, new HBox(8, saveButton, loadSelectedButton));
    }

    private VBox buildCommandPanel() {
        Button setDirectoryButton = new Button(TEXT_SET_EXEC_DIR);
        setDirectoryButton.setOnAction(event -> promptRemoteDirectory());
        Button executeButton = new Button(TEXT_EXECUTE_COMMAND);
        executeButton.setOnAction(event -> executeCommand());

        return new VBox(8, new Label(TEXT_REMOTE_COMMAND), commandArea, new HBox(8, setDirectoryButton, executeButton));
    }

    private VBox buildLogPanel() {
        Button clearLogButton = new Button(TEXT_CLEAR_LOG);
        clearLogButton.setOnAction(event -> outputArea.clear());

        VBox box = new VBox(8, new HBox(8, new Label(TEXT_EXEC_LOG), clearLogButton), outputArea);
        VBox.setVgrow(outputArea, Priority.ALWAYS);
        return box;
    }

    private void chooseWorkspace(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(TEXT_CHOOSE_WORKSPACE);
        WorkspaceConfig selected = workspaceCombo.getValue();
        if (selected != null) {
            File initialDir = new File(selected.getPath());
            if (initialDir.exists()) {
                chooser.setInitialDirectory(initialDir);
            }
        }

        File directory = chooser.showDialog(stage);
        if (directory == null) {
            return;
        }

        WorkspaceConfig saved = databaseService.saveWorkspace(directory.getAbsolutePath());
        loadWorkspaceTree(directory.toPath());
        reloadWorkspaces(saved.getPath());
        log(TEXT_SWITCHED_WORKSPACE + saved.getPath());
    }

    private void loadWorkspaceTree(Path workspacePath) {
        if (workspacePath == null || !Files.isDirectory(workspacePath)) {
            workspaceTree.setRoot(null);
            return;
        }
        FileTreeItem rootItem = new FileTreeItem(workspacePath);
        rootItem.setExpanded(true);
        workspaceTree.setRoot(rootItem);
    }

    private void refreshCurrentTree() {
        TreeItem<Path> root = workspaceTree.getRoot();
        if (root instanceof FileTreeItem fileTreeItem) {
            fileTreeItem.refresh();
            log(TEXT_TREE_REFRESHED);
        }
    }

    private void saveServer() {
        ServerConfig config = buildServerFromForm();
        try {
            ServerConfig saved = databaseService.saveServer(config);
            reloadServers(saved.getId());
            log(TEXT_SERVER_SAVED + saved.getName());
        } catch (Exception e) {
            showError(TEXT_SAVE_SERVER_FAILED, e.getMessage());
        }
    }

    private void clearServerForm() {
        editingServerId = null;
        serverCombo.getSelectionModel().clearSelection();
        serverNameField.clear();
        hostField.clear();
        portField.setText("22");
        usernameField.clear();
        passwordField.clear();
        remoteDirectoryField.clear();
    }

    private void populateServerForm(ServerConfig config) {
        editingServerId = config.getId();
        serverNameField.setText(config.getName());
        hostField.setText(config.getHost());
        portField.setText(String.valueOf(config.getPort()));
        usernameField.setText(config.getUsername());
        passwordField.setText(config.getPassword());
        remoteDirectoryField.setText(config.getDefaultDirectory());
    }

    private void promptRemoteDirectory() {
        TextInputDialog dialog = new TextInputDialog(remoteDirectoryField.getText());
        dialog.setTitle(TEXT_SET_REMOTE_DIR);
        dialog.setHeaderText(TEXT_SET_REMOTE_DIR_HEADER);
        dialog.setContentText(TEXT_REMOTE_DIR);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(value -> remoteDirectoryField.setText(value.trim()));
    }

    private void executeCommand() {
        ServerConfig server = currentServer();
        String remoteDirectory = remoteDirectoryField.getText();
        String command = commandArea.getText();

        if (server == null) {
            showError(TEXT_MISSING_SERVER, TEXT_SELECT_SERVER_FIRST);
            return;
        }
        if (isBlank(remoteDirectory) || isBlank(command)) {
            showError(TEXT_INCOMPLETE_PARAMS, TEXT_COMMAND_REQUIRED);
            return;
        }

        Task<RemoteOpsService.CommandResult> task = new Task<>() {
            @Override
            protected RemoteOpsService.CommandResult call() throws Exception {
                return remoteOpsService.execute(server, remoteDirectory, command);
            }
        };

        task.setOnRunning(event -> log(TEXT_EXECUTING));
        task.setOnSucceeded(event -> {
            RemoteOpsService.CommandResult result = task.getValue();
            log(TEXT_EXEC_DIR + remoteDirectory);
            log(TEXT_COMMAND + result.getFullCommand());
            if (!isBlank(result.getStdout())) {
                log(TEXT_STDOUT + result.getStdout());
            }
            if (!isBlank(result.getStderr())) {
                log(TEXT_STDERR + result.getStderr());
            }
            log(TEXT_EXIT_CODE + result.getExitCode());
        });
        task.setOnFailed(event -> showError(TEXT_COMMAND_FAILED, unwrapMessage(task.getException())));

        startBackgroundTask(task, "remote-command-task");
    }

    private void uploadPath(Path localPath) {
        ServerConfig server = currentServer();
        if (server == null) {
            showError(TEXT_MISSING_SERVER, TEXT_SELECT_SERVER_FIRST);
            return;
        }
        if (isBlank(remoteDirectoryField.getText())) {
            showError(TEXT_MISSING_REMOTE_DIR, TEXT_INPUT_REMOTE_DIR);
            return;
        }

        CheckBox deleteExistingCheckBox = new CheckBox(TEXT_DELETE_BEFORE_UPLOAD);
        VBox content = new VBox(
                10,
                new Label(TEXT_LOCAL_PATH + localPath),
                new Label(TEXT_REMOTE_PATH + remoteDirectoryField.getText().trim()),
                deleteExistingCheckBox
        );

        ButtonType confirmButton = new ButtonType(TEXT_START_UPLOAD, ButtonBar.ButtonData.OK_DONE);
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle(TEXT_CONFIRM_UPLOAD);
        confirmDialog.setHeaderText(TEXT_UPLOAD_HEADER);
        confirmDialog.getDialogPane().setContent(content);
        confirmDialog.getButtonTypes().setAll(confirmButton, ButtonType.CANCEL);

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isEmpty() || result.get() != confirmButton) {
            return;
        }

        String remoteDirectory = remoteDirectoryField.getText().trim();
        boolean deleteExisting = deleteExistingCheckBox.isSelected();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                remoteOpsService.upload(server, localPath, remoteDirectory, deleteExisting, Launcher.this::log);
                return null;
            }
        };

        task.setOnRunning(event -> log(TEXT_UPLOAD_STARTED + localPath));
        task.setOnSucceeded(event -> log(TEXT_UPLOAD_COMPLETED + localPath.getFileName()));
        task.setOnFailed(event -> showError(TEXT_UPLOAD_FAILED, unwrapMessage(task.getException())));

        startBackgroundTask(task, "remote-upload-task");
    }

    private void startBackgroundTask(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void reloadWorkspaces(String selectedPath) {
        reloadingWorkspaces = true;
        workspaceItems.setAll(databaseService.listWorkspaces());
        if (!workspaceItems.isEmpty()) {
            WorkspaceConfig target = selectedPath == null ? findLastUsedWorkspace() : findWorkspaceByPath(selectedPath);
            if (target != null) {
                workspaceCombo.getSelectionModel().select(target);
                loadWorkspaceTree(Paths.get(target.getPath()));
            }
        }
        reloadingWorkspaces = false;
    }

    private void reloadServers(Long selectedId) {
        reloadingServers = true;
        serverItems.setAll(databaseService.listServers());
        if (!serverItems.isEmpty()) {
            ServerConfig target = selectedId == null ? findLastUsedServer() : findServerById(selectedId);
            if (target != null) {
                serverCombo.getSelectionModel().select(target);
                populateServerForm(target);
            }
        }
        reloadingServers = false;
    }

    private WorkspaceConfig findLastUsedWorkspace() {
        return workspaceItems.stream().filter(WorkspaceConfig::isLastUsed).findFirst().orElse(null);
    }

    private ServerConfig findLastUsedServer() {
        return serverItems.stream().filter(ServerConfig::isLastUsed).findFirst().orElse(null);
    }

    private WorkspaceConfig findWorkspaceByPath(String path) {
        return workspaceItems.stream()
                .filter(item -> item.getPath().equals(path))
                .findFirst()
                .orElse(null);
    }

    private ServerConfig findServerById(Long id) {
        return serverItems.stream()
                .filter(item -> id != null && id.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private ServerConfig buildServerFromForm() {
        ServerConfig config = new ServerConfig();
        config.setId(editingServerId);
        config.setName(serverNameField.getText());
        config.setHost(hostField.getText());
        config.setPort(parsePort(portField.getText()));
        config.setUsername(usernameField.getText());
        config.setPassword(passwordField.getText());
        config.setDefaultDirectory(remoteDirectoryField.getText());
        return config;
    }

    private ServerConfig currentServer() {
        ServerConfig selected = serverCombo.getValue();
        if (selected != null) {
            return selected;
        }
        if (isBlank(hostField.getText()) || isBlank(usernameField.getText()) || isBlank(passwordField.getText())) {
            return null;
        }
        return buildServerFromForm();
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 22;
        }
    }

    private void copyToClipboard(String value, String successMessage) {
        if (isBlank(value)) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        log(successMessage + value);
    }

    private void log(String message) {
        Runnable writer = () -> outputArea.appendText(message + System.lineSeparator());
        if (Platform.isFxApplicationThread()) {
            writer.run();
        } else {
            Platform.runLater(writer);
        }
    }

    private void showError(String title, String message) {
        log(title + ": " + message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String unwrapMessage(Throwable throwable) {
        if (throwable == null) {
            return TEXT_UNKNOWN_ERROR;
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private class WorkspaceTreeCell extends TreeCell<Path> {

        private final MenuItem uploadItem = new MenuItem(TEXT_UPLOAD_MENU);
        private final MenuItem copyFileNameItem = new MenuItem(TEXT_COPY_FILE_NAME);
        private final MenuItem copyFilePathItem = new MenuItem(TEXT_COPY_FILE_PATH);
        private final MenuItem refreshItem = new MenuItem(TEXT_REFRESH_NODE);
        private final ContextMenu contextMenu = new ContextMenu(uploadItem, copyFileNameItem, copyFilePathItem, refreshItem);

        private WorkspaceTreeCell() {
            uploadItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    uploadPath(path);
                }
            });
            copyFileNameItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null && path.getFileName() != null) {
                    copyToClipboard(path.getFileName().toString(), TEXT_COPIED_FILE_NAME);
                }
            });
            copyFilePathItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    copyToClipboard(path.toAbsolutePath().toString(), TEXT_COPIED_FILE_PATH);
                }
            });
            refreshItem.setOnAction(event -> {
                TreeItem<Path> item = getTreeItem();
                if (item instanceof FileTreeItem fileTreeItem) {
                    fileTreeItem.refresh();
                    log(TEXT_NODE_REFRESHED + item.getValue());
                }
            });
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            Path fileName = item.getFileName();
            setText(fileName == null ? item.toString() : fileName.toString());
            setContextMenu(contextMenu);
        }
    }
}