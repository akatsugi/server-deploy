package com.akatsugi.serverdeploy;

import com.akatsugi.serverdeploy.model.DirectoryMapping;
import com.akatsugi.serverdeploy.model.ServerConfig;
import com.akatsugi.serverdeploy.model.WorkspaceConfig;
import com.akatsugi.serverdeploy.service.DatabaseService;
import com.akatsugi.serverdeploy.service.RemoteOpsService;
import com.akatsugi.serverdeploy.ui.FileTreeItem;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Launcher extends Application {

    private static final String TEXT_TITLE = "服务器文件上传与脚本执行";
    private static final String TEXT_SETTINGS = "设置";
    private static final String TEXT_SERVER_MANAGER = "服务器信息管理";
    private static final String TEXT_MAPPING_MANAGER = "目录映射管理";
    private static final String TEXT_WORKSPACE = "工作区";
    private static final String TEXT_CHOOSE_WORKSPACE = "选择工作区";
    private static final String TEXT_REFRESH_TREE = "刷新文件树";
    private static final String TEXT_SERVER = "服务器";
    private static final String TEXT_NEW_SERVER = "新建服务器";
    private static final String TEXT_WORKSPACE_TREE = "工作区文件树";
    private static final String TEXT_RUNTIME_PANEL = "执行参数";
    private static final String TEXT_SERVER_CONFIG = "服务器配置";
    private static final String TEXT_MAPPING_CONFIG = "目录映射配置";
    private static final String TEXT_NAME = "名称";
    private static final String TEXT_HOST = "地址";
    private static final String TEXT_PORT = "端口";
    private static final String TEXT_USERNAME = "用户名";
    private static final String TEXT_PASSWORD = "密码";
    private static final String TEXT_REMOTE_DIR = "远端目录";
    private static final String TEXT_BASE_MAPPING_DIR = "基础映射目录";
    private static final String TEXT_SAVE_SERVER = "保存服务器";
    private static final String TEXT_LOAD_SELECTED = "载入选中配置";
    private static final String TEXT_CLEAR_FORM = "清空输入";
    private static final String TEXT_REMOTE_COMMAND = "远端 Linux 命令执行";
    private static final String TEXT_SET_EXEC_DIR = "设置执行目录";
    private static final String TEXT_EXECUTE_COMMAND = "执行命令";
    private static final String TEXT_EXEC_LOG = "执行日志";
    private static final String TEXT_CLEAR_LOG = "清空日志";
    private static final String TEXT_PROMPT_COMMAND = "输入 Linux 命令，例如：ls -al && sh deploy.sh";
    private static final String TEXT_SWITCHED_WORKSPACE = "工作区已切换: ";
    private static final String TEXT_SELECTED_SERVER = "已选择服务器: ";
    private static final String TEXT_TREE_REFRESHED = "文件树已刷新";
    private static final String TEXT_SERVER_SAVED = "服务器配置已保存: ";
    private static final String TEXT_SAVE_SERVER_FAILED = "保存服务器失败";
    private static final String TEXT_SET_REMOTE_DIR = "设置远端目录";
    private static final String TEXT_SET_REMOTE_DIR_HEADER = "输入上传和命令执行共用的远端目录";
    private static final String TEXT_MISSING_SERVER = "缺少服务器";
    private static final String TEXT_SELECT_SERVER_FIRST = "请先选择或保存服务器配置";
    private static final String TEXT_SELECT_SAVED_SERVER = "请先保存并选择服务器配置";
    private static final String TEXT_INCOMPLETE_PARAMS = "参数不完整";
    private static final String TEXT_COMMAND_REQUIRED = "远端目录和 Linux 命令不能为空";
    private static final String TEXT_EXECUTING = "开始执行远端命令";
    private static final String TEXT_EXEC_DIR = "执行目录: ";
    private static final String TEXT_COMMAND = "执行命令: ";
    private static final String TEXT_STDOUT = "标准输出:\n";
    private static final String TEXT_STDERR = "错误输出:\n";
    private static final String TEXT_EXIT_CODE = "命令退出码: ";
    private static final String TEXT_COMMAND_FAILED = "命令执行失败";
    private static final String TEXT_MISSING_REMOTE_DIR = "缺少远端目录";
    private static final String TEXT_INPUT_REMOTE_DIR = "请先输入远端目录";
    private static final String TEXT_DELETE_BEFORE_UPLOAD = "上传前删除远端同名文件或文件夹";
    private static final String TEXT_LOCAL_PATH = "本地路径: ";
    private static final String TEXT_REMOTE_PATH = "远端路径: ";
    private static final String TEXT_CONFIRM_UPLOAD = "确认上传";
    private static final String TEXT_UPLOAD_HEADER = "上传文件或目录到远端";
    private static final String TEXT_START_UPLOAD = "开始上传";
    private static final String TEXT_UPLOAD_STARTED = "开始上传: ";
    private static final String TEXT_UPLOAD_COMPLETED = "上传完成: ";
    private static final String TEXT_UPLOAD_FAILED = "上传失败";
    private static final String TEXT_PREVIEW_FAILED = "文件预览失败";
    private static final String TEXT_OPEN_EXPLORER_FAILED = "资源管理器打开失败";
    private static final String TEXT_UNKNOWN_ERROR = "未知错误";
    private static final String TEXT_UPLOAD_MENU = "上传到当前服务器目录";
    private static final String TEXT_UPLOAD_TO_MAPPING = "上传到映射目录";
    private static final String TEXT_CONFIG_MAPPING = "配置映射目录";
    private static final String TEXT_COPY_FILE_NAME = "复制当前文件名";
    private static final String TEXT_COPY_FILE_PATH = "复制当前文件路径";
    private static final String TEXT_PREVIEW_FILE = "预览当前文件";
    private static final String TEXT_PREVIEW_PANEL = "文件预览";
    private static final String TEXT_PREVIEW_HINT = "双击左侧文件，或右键选择“预览当前文件”";
    private static final String TEXT_PREVIEW_UNSUPPORTED = "当前文件类型不支持内嵌预览";
    private static final String TEXT_PREVIEW_TOO_LARGE = "文件过大，暂不预览（超过 512 KB）";
    private static final String TEXT_PREVIEW_EMPTY = "暂无预览内容";
    private static final String TEXT_PREVIEW_TYPE = "渲染类型: ";
    private static final String TEXT_PREVIEW_PATH = "预览路径: ";
    private static final String TEXT_PREVIEW_PLAIN = "普通文本";
    private static final String TEXT_PREVIEW_MARKDOWN = "Markdown";
    private static final String TEXT_PREVIEW_JSON = "JSON";
    private static final String TEXT_PREVIEW_XML = "XML/HTML";
    private static final String TEXT_PREVIEW_YAML = "YAML";
    private static final String TEXT_PREVIEW_PROPERTIES = "Properties";
    private static final String TEXT_PREVIEW_SHELL = "Shell";
    private static final String TEXT_PREVIEW_SQL = "SQL";
    private static final String TEXT_PREVIEW_CODE = "代码";
    private static final String TEXT_PREVIEW_ZOOM_IN = "A+";
    private static final String TEXT_PREVIEW_ZOOM_OUT = "A-";
    private static final String TEXT_JUMP_TO_MAPPING = "跳转到映射目录";
    private static final String TEXT_COPY_MAPPED_DIR = "复制映射目录";
    private static final String TEXT_HIDE_PREVIEW = "隐藏预览";
    private static final String TEXT_OPEN_IN_EXPLORER = "资源管理器打开";
    private static final String TEXT_REFRESH_NODE = "刷新当前节点";
    private static final String TEXT_COPIED_FILE_NAME = "已复制文件名: ";
    private static final String TEXT_COPIED_FILE_PATH = "已复制文件路径: ";
    private static final String TEXT_COPIED_MAPPED_DIR = "已复制映射目录: ";
    private static final String TEXT_PREVIEWED_FILE = "已预览文件: ";
    private static final String TEXT_OPENED_IN_EXPLORER = "已在资源管理器打开: ";
    private static final String TEXT_JUMPED_TO_MAPPING = "已跳转到映射目录: ";
    private static final String TEXT_NODE_REFRESHED = "已刷新: ";
    private static final String TEXT_MAPPING_LOCAL_DIR = "本地映射目录: ";
    private static final String TEXT_SERVER_BASE_DIR = "服务器基础映射目录: ";
    private static final String TEXT_MAPPING_REMOTE_DIR = "服务器相对目录";
    private static final String TEXT_MAPPING_SAVED = "目录映射已保存: ";
    private static final String TEXT_MAPPING_REMOVED = "目录映射已删除: ";
    private static final String TEXT_MAPPING_DIR_ONLY = "只能为目录配置映射";
    private static final String TEXT_MAPPING_BASE_REQUIRED = "缺少基础映射目录";
    private static final String TEXT_INPUT_MAPPING_BASE = "请先为当前服务器设置基础映射目录";
    private static final String TEXT_MAPPING_NOT_FOUND = "未找到可用的目录映射";
    private static final String TEXT_MAPPING_NOT_FOUND_DETAIL = "请先为当前目录或其上级目录配置映射目录";
    private static final String TEXT_CONFIRM_MAPPING_UPLOAD = "确认上传到映射目录";
    private static final String TEXT_MAPPING_UPLOAD_HEADER = "按目录映射上传到远端";
    private static final String TEXT_MAPPING_UPLOAD_STARTED = "开始上传到映射目录: ";
    private static final String TEXT_MAPPING_UPLOAD_COMPLETED = "映射目录上传完成: ";
    private static final String TEXT_MAPPING_MIGRATED = "已转换旧映射数量: ";
    private static final String TEXT_MAPPING_TABLE_LOCAL = "本地绝对目录";
    private static final String TEXT_MAPPING_TABLE_REMOTE = "服务器相对目录";
    private static final String TEXT_MAPPING_USE_TREE_DIR = "使用当前树目录";
    private static final String TEXT_MAPPING_SAVE = "保存映射";
    private static final String TEXT_MAPPING_DELETE = "删除映射";
    private static final String TEXT_MAPPING_CLEAR = "清空输入";
    private static final String TEXT_MAPPING_REFRESH = "刷新映射";
    private static final String TEXT_MAPPING_LOADED = "已载入映射: ";
    private static final String TEXT_MAPPING_DIR_FILLED = "已带入目录: ";
    private static final String TEXT_MAPPING_LOCAL_REQUIRED = "请输入本地目录";
    private static final String TEXT_MAPPING_LOCAL_INVALID = "本地目录必须是存在的绝对文件夹";
    private static final String TEXT_MAPPING_REMOTE_REQUIRED = "请输入服务器相对目录";
    private static final String TEXT_MAPPING_SELECT_DIR_FROM_TREE = "请先在左侧文件树选中一个目录";
    private static final String TEXT_MAPPING_TABLE_EMPTY = "当前服务器暂无目录映射";
    private static final String TEXT_MAPPING_DELETED = "已删除映射: ";

    private static final long MAX_PREVIEW_FILE_SIZE = 512 * 1024;
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of("txt", "log", "out", "csv", "tsv");
    private static final Set<String> MARKDOWN_EXTENSIONS = Set.of("md", "markdown");
    private static final Set<String> JSON_EXTENSIONS = Set.of("json");
    private static final Set<String> XML_EXTENSIONS = Set.of("xml", "html", "htm", "xhtml");
    private static final Set<String> YAML_EXTENSIONS = Set.of("yml", "yaml");
    private static final Set<String> PROPERTIES_EXTENSIONS = Set.of("properties", "conf", "cfg", "ini");
    private static final Set<String> SHELL_EXTENSIONS = Set.of("sh", "bat", "cmd", "ps1");
    private static final Set<String> SQL_EXTENSIONS = Set.of("sql");
    private static final Set<String> CODE_EXTENSIONS = Set.of("java", "kt", "kts", "groovy", "js", "ts", "css", "scss", "less", "vue", "py", "go", "c", "h", "cpp", "hpp", "cs", "php", "rb");
    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of("exe", "dll", "msi");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "svg", "tif", "tiff");
    private static final Set<String> AUDIO_VIDEO_EXTENSIONS = Set.of("mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpeg", "mpg", "3gp");
    private static final Set<String> CODE_KEYWORDS = Set.of("abstract", "async", "await", "break", "case", "catch", "class", "const", "continue", "default", "do", "else", "enum", "extends", "false", "final", "finally", "for", "function", "if", "implements", "import", "interface", "let", "new", "null", "package", "private", "protected", "public", "return", "static", "super", "switch", "this", "throw", "throws", "true", "try", "var", "void", "while");
    private static final Set<String> SHELL_KEYWORDS = Set.of("if", "then", "else", "fi", "for", "in", "do", "done", "case", "esac", "while", "function", "echo", "export", "unset", "set", "cd", "exit", "return", "source");
    private static final Set<String> SQL_KEYWORDS = Set.of("select", "from", "where", "and", "or", "insert", "into", "update", "delete", "join", "left", "right", "inner", "outer", "on", "group", "by", "order", "having", "limit", "offset", "create", "table", "drop", "alter", "values", "set", "as", "distinct", "union", "all", "null", "is", "not", "like", "in", "exists");
    private static final Set<String> JSON_LITERALS = Set.of("true", "false", "null");

    private final DatabaseService databaseService = new DatabaseService();
    private final RemoteOpsService remoteOpsService = new RemoteOpsService();

    private final ObservableList<WorkspaceConfig> workspaceItems = FXCollections.observableArrayList();
    private final ObservableList<ServerConfig> serverItems = FXCollections.observableArrayList();
    private final List<DirectoryMapping> directoryMappings = new ArrayList<DirectoryMapping>();

    private ComboBox<WorkspaceConfig> workspaceCombo;
    private ComboBox<ServerConfig> serverCombo;
    private TreeView<Path> workspaceTree;
    private TextField remoteDirectoryField;
    private TextField baseMappingDirectoryField;
    private TextArea commandArea;
    private TextArea outputArea;
    private TabPane outputTabPane;
    private Tab logTab;
    private final Map<Path, PreviewTabState> previewTabs = new LinkedHashMap<Path, PreviewTabState>();

    private boolean reloadingWorkspaces;
    private boolean reloadingServers;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        workspaceCombo = new ComboBox<WorkspaceConfig>(workspaceItems);
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

        serverCombo = new ComboBox<ServerConfig>(serverItems);
        serverCombo.setMaxWidth(240);
        serverCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (reloadingServers || newValue == null) {
                return;
            }
            databaseService.markServerLastUsed(newValue);
            reloadServers(newValue.getId());
            log(TEXT_SELECTED_SERVER + newValue.getName());
        });

        workspaceTree = new TreeView<Path>();
        workspaceTree.setShowRoot(true);
        workspaceTree.setCellFactory(tree -> new WorkspaceTreeCell());

        remoteDirectoryField = new TextField();
        baseMappingDirectoryField = new TextField();
        baseMappingDirectoryField.setEditable(false);

        commandArea = new TextArea();
        commandArea.setPromptText(TEXT_PROMPT_COMMAND);
        commandArea.setPrefRowCount(4);

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);


        logTab = new Tab(null, buildLogPanel());
        logTab.setGraphic(createTabTitleLabel(TEXT_EXEC_LOG));
        logTab.setClosable(false);
        outputTabPane = buildOutputTabPane();


        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(buildTopPane(stage));
        root.setCenter(buildMainContent());

        reloadWorkspaces(null);
        reloadServers(null);

        Scene scene = new Scene(root, 1160, 720);
        stage.setTitle(TEXT_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildTopPane(Stage stage) {
        MenuItem serverManagerItem = new MenuItem(TEXT_SERVER_MANAGER);
        serverManagerItem.setOnAction(event -> openServerManager());
        MenuItem mappingManagerItem = new MenuItem(TEXT_MAPPING_MANAGER);
        mappingManagerItem.setOnAction(event -> openMappingManager(null));
        MenuButton settingsMenu = new MenuButton(TEXT_SETTINGS, null, serverManagerItem, mappingManagerItem);

        Button chooseWorkspaceButton = new Button(TEXT_CHOOSE_WORKSPACE);
        chooseWorkspaceButton.setOnAction(event -> chooseWorkspace(stage));

        Button refreshButton = new Button(TEXT_REFRESH_TREE);
        refreshButton.setOnAction(event -> refreshCurrentTree());

        HBox box = new HBox(
                8,
                settingsMenu,
                new Label(TEXT_WORKSPACE),
                workspaceCombo,
                chooseWorkspaceButton,
                refreshButton,
                new Label(TEXT_SERVER),
                serverCombo
        );
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(workspaceCombo, Priority.ALWAYS);
        return new VBox(10, box, new Separator(Orientation.HORIZONTAL));
    }

    private SplitPane buildMainContent() {
        VBox topRightPane = new VBox(12, buildRuntimePanel(), buildCommandPanel());
        SplitPane rightPane = new SplitPane(topRightPane, outputTabPane);
        rightPane.setOrientation(Orientation.VERTICAL);
        rightPane.setDividerPositions(0.42);

        SplitPane splitPane = new SplitPane(buildWorkspacePane(), rightPane);
        splitPane.setDividerPositions(0.34);
        return splitPane;
    }

    private VBox buildWorkspacePane() {
        VBox treePane = new VBox(8, new Label(TEXT_WORKSPACE_TREE), workspaceTree);
        VBox.setVgrow(workspaceTree, Priority.ALWAYS);
        treePane.setPrefWidth(390);
        return treePane;
    }

    private TabPane buildOutputTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(logTab);
        return tabPane;
    }

    private Label createTabTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-style: normal; -fx-font-weight: normal; -fx-font-size: 13px;");
        return label;
    }

    private Label createSectionTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-style: normal; -fx-font-weight: normal; -fx-font-size: 13px;");
        return label;
    }

    private VBox buildPreviewPane(PreviewTabState state) {
        Button zoomOutButton = new Button(TEXT_PREVIEW_ZOOM_OUT);
        zoomOutButton.setOnAction(event -> changePreviewFontSize(state, -1.0));
        Button zoomInButton = new Button(TEXT_PREVIEW_ZOOM_IN);
        zoomInButton.setOnAction(event -> changePreviewFontSize(state, 1.0));
        Button hideButton = new Button(TEXT_HIDE_PREVIEW);
        hideButton.setOnAction(event -> closePreviewTab(state.path));
        HBox header = new HBox(8, new Label(TEXT_PREVIEW_PANEL), zoomOutButton, zoomInButton, hideButton);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, header, state.pathLabel, state.typeLabel, state.webView);
        VBox.setVgrow(state.webView, Priority.ALWAYS);
        return box;
    }

    private VBox buildRuntimePanel() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label(TEXT_REMOTE_DIR), 0, 0);
        grid.add(remoteDirectoryField, 1, 0);
        grid.add(new Label(TEXT_BASE_MAPPING_DIR), 0, 1);
        grid.add(baseMappingDirectoryField, 1, 1);
        GridPane.setHgrow(remoteDirectoryField, Priority.ALWAYS);
        GridPane.setHgrow(baseMappingDirectoryField, Priority.ALWAYS);
        return new VBox(8, new Label(TEXT_RUNTIME_PANEL), grid);
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
        HBox header = new HBox(8, createSectionTitleLabel(TEXT_EXEC_LOG), clearLogButton);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, header, outputArea);
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
        closeAllPreviewTabs();

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
        if (root instanceof FileTreeItem) {
            FileTreeItem fileTreeItem = (FileTreeItem) root;
            fileTreeItem.refresh();
            log(TEXT_TREE_REFRESHED);
        }
    }

    private void openServerManager() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(TEXT_SERVER_MANAGER);

        ObservableList<ServerConfig> dialogItems = FXCollections.observableArrayList(serverItems);
        TableView<ServerConfig> table = new TableView<ServerConfig>(dialogItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ServerConfig, String> nameColumn = new TableColumn<ServerConfig, String>(TEXT_NAME);
        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        TableColumn<ServerConfig, String> hostColumn = new TableColumn<ServerConfig, String>(TEXT_HOST);
        hostColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getHost()));
        TableColumn<ServerConfig, String> baseColumn = new TableColumn<ServerConfig, String>(TEXT_BASE_MAPPING_DIR);
        baseColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getBaseMappingDirectory()));
        table.getColumns().add(nameColumn);
        table.getColumns().add(hostColumn);
        table.getColumns().add(baseColumn);
        table.setPrefHeight(220);

        TextField nameField = new TextField();
        TextField hostField = new TextField();
        TextField portField = new TextField("22");
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        TextField defaultDirectoryField = new TextField();
        TextField baseMappingField = new TextField();
        final Long[] editingId = new Long[1];

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            editingId[0] = newValue.getId();
            nameField.setText(newValue.getName());
            hostField.setText(newValue.getHost());
            portField.setText(String.valueOf(newValue.getPort()));
            usernameField.setText(newValue.getUsername());
            passwordField.setText(newValue.getPassword());
            defaultDirectoryField.setText(newValue.getDefaultDirectory());
            baseMappingField.setText(newValue.getBaseMappingDirectory());
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label(TEXT_NAME), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(TEXT_HOST), 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(new Label(TEXT_PORT), 0, 2);
        grid.add(portField, 1, 2);
        grid.add(new Label(TEXT_USERNAME), 0, 3);
        grid.add(usernameField, 1, 3);
        grid.add(new Label(TEXT_PASSWORD), 0, 4);
        grid.add(passwordField, 1, 4);
        grid.add(new Label(TEXT_REMOTE_DIR), 0, 5);
        grid.add(defaultDirectoryField, 1, 5);
        grid.add(new Label(TEXT_BASE_MAPPING_DIR), 0, 6);
        grid.add(baseMappingField, 1, 6);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(hostField, Priority.ALWAYS);
        GridPane.setHgrow(portField, Priority.ALWAYS);
        GridPane.setHgrow(usernameField, Priority.ALWAYS);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);
        GridPane.setHgrow(defaultDirectoryField, Priority.ALWAYS);
        GridPane.setHgrow(baseMappingField, Priority.ALWAYS);

        Button newButton = new Button(TEXT_NEW_SERVER);
        newButton.setOnAction(event -> {
            editingId[0] = null;
            table.getSelectionModel().clearSelection();
            nameField.clear();
            hostField.clear();
            portField.setText("22");
            usernameField.clear();
            passwordField.clear();
            defaultDirectoryField.clear();
            baseMappingField.clear();
        });

        Button saveButton = new Button(TEXT_SAVE_SERVER);
        saveButton.setOnAction(event -> {
            try {
                ServerConfig config = new ServerConfig();
                config.setId(editingId[0]);
                config.setName(nameField.getText());
                config.setHost(hostField.getText());
                config.setPort(parsePort(portField.getText()));
                config.setUsername(usernameField.getText());
                config.setPassword(passwordField.getText());
                config.setDefaultDirectory(defaultDirectoryField.getText());
                config.setBaseMappingDirectory(baseMappingField.getText());

                ServerConfig saved = databaseService.saveServer(config);
                int migratedCount = databaseService.migrateDirectoryMappingsToServerRelative(
                        saved.getId(),
                        saved.getBaseMappingDirectory()
                );
                reloadServers(saved.getId());
                dialogItems.setAll(serverItems);
                table.getSelectionModel().select(findServerById(saved.getId()));
                editingId[0] = saved.getId();
                log(TEXT_SERVER_SAVED + saved.getName());
                if (migratedCount > 0) {
                    log(TEXT_MAPPING_MIGRATED + migratedCount);
                }
            } catch (Exception e) {
                showError(TEXT_SAVE_SERVER_FAILED, unwrapMessage(e));
            }
        });

        VBox root = new VBox(12, table, grid, new HBox(8, newButton, saveButton));
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 840, 620);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void openMappingManager(Path initialDirectory) {
        ServerConfig server = currentSelectedServerForMapping();
        if (server == null) {
            showError(TEXT_MISSING_SERVER, TEXT_SELECT_SAVED_SERVER);
            return;
        }
        if (isBlank(server.getBaseMappingDirectory())) {
            showError(TEXT_MAPPING_BASE_REQUIRED, TEXT_INPUT_MAPPING_BASE);
            return;
        }

        reloadDirectoryMappings(server.getId());
        ObservableList<DirectoryMapping> dialogItems = FXCollections.observableArrayList(directoryMappings);

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(TEXT_MAPPING_MANAGER);

        Label baseDirLabel = new Label(TEXT_SERVER_BASE_DIR + normalizeRemotePath(server.getBaseMappingDirectory()));

        TableView<DirectoryMapping> table = new TableView<DirectoryMapping>(dialogItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(TEXT_MAPPING_TABLE_EMPTY));
        table.setPrefHeight(260);

        TableColumn<DirectoryMapping, String> localColumn = new TableColumn<DirectoryMapping, String>(TEXT_MAPPING_TABLE_LOCAL);
        localColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getLocalDirectory()));
        TableColumn<DirectoryMapping, String> remoteColumn = new TableColumn<DirectoryMapping, String>(TEXT_MAPPING_TABLE_REMOTE);
        remoteColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getRemoteDirectory()));
        table.getColumns().add(localColumn);
        table.getColumns().add(remoteColumn);

        TextField localField = new TextField();
        TextField remoteField = new TextField();

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            localField.setText(newValue.getLocalDirectory());
            remoteField.setText(newValue.getRemoteDirectory());
            log(TEXT_MAPPING_LOADED + newValue.getLocalDirectory());
        });

        if (initialDirectory != null) {
            localField.setText(initialDirectory.toAbsolutePath().normalize().toString());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label(TEXT_MAPPING_TABLE_LOCAL), 0, 0);
        grid.add(localField, 1, 0);
        grid.add(new Label(TEXT_MAPPING_TABLE_REMOTE), 0, 1);
        grid.add(remoteField, 1, 1);
        GridPane.setHgrow(localField, Priority.ALWAYS);
        GridPane.setHgrow(remoteField, Priority.ALWAYS);

        Button useTreeButton = new Button(TEXT_MAPPING_USE_TREE_DIR);
        useTreeButton.setOnAction(event -> {
            Path selectedDirectory = currentSelectedLocalDirectory();
            if (selectedDirectory == null) {
                showError(TEXT_MAPPING_CONFIG, TEXT_MAPPING_SELECT_DIR_FROM_TREE);
                return;
            }
            localField.setText(selectedDirectory.toAbsolutePath().normalize().toString());
            log(TEXT_MAPPING_DIR_FILLED + selectedDirectory.toAbsolutePath().normalize());
        });

        Button saveButton = new Button(TEXT_MAPPING_SAVE);
        saveButton.setOnAction(event -> {
            try {
                Path localDirectory = validateMappingLocalDirectory(localField.getText());
                String remoteRelative = validateMappingRemoteDirectory(remoteField.getText());
                DirectoryMapping saved = databaseService.saveDirectoryMapping(server.getId(), localDirectory.toString(), remoteRelative);
                reloadDirectoryMappings(server.getId());
                dialogItems.setAll(directoryMappings);
                selectMappingInTable(table, saved.getLocalDirectory());
                log(TEXT_MAPPING_SAVED + saved.getLocalDirectory() + " -> " + saved.getRemoteDirectory());
            } catch (Exception e) {
                showError(TEXT_MAPPING_CONFIG, unwrapMessage(e));
            }
        });

        Button deleteButton = new Button(TEXT_MAPPING_DELETE);
        deleteButton.setOnAction(event -> {
            try {
                Path localDirectory = validateMappingLocalDirectory(localField.getText());
                databaseService.deleteDirectoryMapping(server.getId(), localDirectory.toString());
                reloadDirectoryMappings(server.getId());
                dialogItems.setAll(directoryMappings);
                table.getSelectionModel().clearSelection();
                localField.clear();
                remoteField.clear();
                log(TEXT_MAPPING_DELETED + localDirectory);
            } catch (Exception e) {
                showError(TEXT_MAPPING_CONFIG, unwrapMessage(e));
            }
        });

        Button clearButton = new Button(TEXT_MAPPING_CLEAR);
        clearButton.setOnAction(event -> {
            table.getSelectionModel().clearSelection();
            localField.clear();
            remoteField.clear();
        });

        Button refreshButton = new Button(TEXT_MAPPING_REFRESH);
        refreshButton.setOnAction(event -> {
            reloadDirectoryMappings(server.getId());
            dialogItems.setAll(directoryMappings);
        });

        HBox actions = new HBox(8, useTreeButton, saveButton, deleteButton, clearButton, refreshButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, baseDirLabel, table, grid, actions);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 920, 620);
        dialog.setScene(scene);
        dialog.showAndWait();
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

        Task<RemoteOpsService.CommandResult> task = new Task<RemoteOpsService.CommandResult>() {
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

        String remoteDirectory = remoteDirectoryField.getText().trim();
        Optional<Boolean> confirmed = showUploadConfirmation(
                TEXT_CONFIRM_UPLOAD,
                TEXT_UPLOAD_HEADER,
                localPath,
                remoteDirectory,
                null
        );
        if (confirmed.isEmpty()) {
            return;
        }

        boolean deleteExisting = confirmed.get().booleanValue();
        Task<Void> task = new Task<Void>() {
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

    private void configureDirectoryMapping(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            showError(TEXT_CONFIG_MAPPING, TEXT_MAPPING_DIR_ONLY);
            return;
        }
        openMappingManager(path);
    }
    private void uploadPathToMapping(Path localPath) {
        ServerConfig server = currentSelectedServerForMapping();
        if (server == null) {
            showError(TEXT_MISSING_SERVER, TEXT_SELECT_SAVED_SERVER);
            return;
        }
        if (isBlank(server.getBaseMappingDirectory())) {
            showError(TEXT_MAPPING_BASE_REQUIRED, TEXT_INPUT_MAPPING_BASE);
            return;
        }

        DirectoryMapping mapping = resolveNearestMapping(server, localPath);
        if (mapping == null) {
            showError(TEXT_MAPPING_NOT_FOUND, TEXT_MAPPING_NOT_FOUND_DETAIL);
            return;
        }

        String remoteTargetPath = buildMappedRemoteTarget(server, mapping, localPath);
        Optional<Boolean> confirmed = showUploadConfirmation(
                TEXT_CONFIRM_MAPPING_UPLOAD,
                TEXT_MAPPING_UPLOAD_HEADER,
                localPath,
                remoteTargetPath,
                buildMappingDisplay(server, mapping)
        );
        if (confirmed.isEmpty()) {
            return;
        }

        boolean deleteExisting = confirmed.get().booleanValue();
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                remoteOpsService.uploadToExactPath(server, localPath, remoteTargetPath, deleteExisting, Launcher.this::log);
                return null;
            }
        };

        task.setOnRunning(event -> log(TEXT_MAPPING_UPLOAD_STARTED + localPath + " -> " + remoteTargetPath));
        task.setOnSucceeded(event -> log(TEXT_MAPPING_UPLOAD_COMPLETED + remoteTargetPath));
        task.setOnFailed(event -> showError(TEXT_UPLOAD_FAILED, unwrapMessage(task.getException())));

        startBackgroundTask(task, "mapped-remote-upload-task");
    }

    private Optional<Boolean> showUploadConfirmation(String title, String header, Path localPath,
            String remotePath, String mappingDisplay) {
        CheckBox deleteExistingCheckBox = new CheckBox(TEXT_DELETE_BEFORE_UPLOAD);
        VBox content = new VBox(10);
        content.getChildren().add(new Label(TEXT_LOCAL_PATH + localPath.toAbsolutePath().normalize()));
        if (!isBlank(mappingDisplay)) {
            Label mappingLabel = new Label(mappingDisplay);
            mappingLabel.setWrapText(true);
            content.getChildren().add(mappingLabel);
        }
        content.getChildren().add(new Label(TEXT_REMOTE_PATH + normalizeRemotePath(remotePath)));
        content.getChildren().add(deleteExistingCheckBox);

        ButtonType confirmButton = new ButtonType(TEXT_START_UPLOAD, ButtonBar.ButtonData.OK_DONE);
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle(title);
        confirmDialog.setHeaderText(header);
        confirmDialog.getDialogPane().setContent(content);
        confirmDialog.getButtonTypes().setAll(confirmButton, ButtonType.CANCEL);

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isEmpty() || result.get() != confirmButton) {
            return Optional.empty();
        }
        return Optional.of(Boolean.valueOf(deleteExistingCheckBox.isSelected()));
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
                applyServerToMainView(target);
                reloadDirectoryMappings(target.getId());
            } else {
                clearMainServerView();
                reloadDirectoryMappings(null);
            }
        } else {
            clearMainServerView();
            reloadDirectoryMappings(null);
        }
        reloadingServers = false;
    }

    private void reloadDirectoryMappings(Long serverId) {
        directoryMappings.clear();
        if (serverId != null) {
            directoryMappings.addAll(databaseService.listDirectoryMappings(serverId));
        }
    }

    private void applyServerToMainView(ServerConfig config) {
        remoteDirectoryField.setText(config.getDefaultDirectory());
        baseMappingDirectoryField.setText(config.getBaseMappingDirectory());
    }

    private void clearMainServerView() {
        remoteDirectoryField.clear();
        baseMappingDirectoryField.clear();
    }

    private WorkspaceConfig findLastUsedWorkspace() {
        return workspaceItems.stream().filter(WorkspaceConfig::isLastUsed).findFirst().orElse(null);
    }

    private ServerConfig findLastUsedServer() {
        return serverItems.stream().filter(ServerConfig::isLastUsed).findFirst().orElse(null);
    }

    private WorkspaceConfig findWorkspaceByPath(String path) {
        return workspaceItems.stream().filter(item -> item.getPath().equals(path)).findFirst().orElse(null);
    }

    private ServerConfig findServerById(Long id) {
        return serverItems.stream().filter(item -> id != null && id.equals(item.getId())).findFirst().orElse(null);
    }

    private DirectoryMapping findExactDirectoryMapping(Long serverId, Path localDirectory) {
        if (serverId == null || localDirectory == null) {
            return null;
        }
        String normalizedLocalDirectory = normalizeLocalPath(localDirectory);
        return directoryMappings.stream()
                .filter(item -> serverId.equals(item.getServerId()) && normalizedLocalDirectory.equals(item.getLocalDirectory()))
                .findFirst()
                .orElse(null);
    }

    private DirectoryMapping resolveNearestMapping(ServerConfig server, Path path) {
        if (server == null || server.getId() == null || path == null) {
            return null;
        }

        Path normalizedPath = path.toAbsolutePath().normalize();
        Path current = Files.isDirectory(normalizedPath) ? normalizedPath : normalizedPath.getParent();
        while (current != null) {
            DirectoryMapping mapping = findExactDirectoryMapping(server.getId(), current);
            if (mapping != null) {
                return mapping;
            }
            current = current.getParent();
        }
        return null;
    }

    private String buildMappedRemoteTarget(ServerConfig server, DirectoryMapping mapping, Path localPath) {
        Path mappingBase = Paths.get(mapping.getLocalDirectory()).toAbsolutePath().normalize();
        Path normalizedLocalPath = localPath.toAbsolutePath().normalize();
        String remoteTarget = joinRemotePath(normalizeRemotePath(server.getBaseMappingDirectory()), mapping.getRemoteDirectory());
        if (mappingBase.equals(normalizedLocalPath)) {
            return remoteTarget;
        }
        if (!normalizedLocalPath.startsWith(mappingBase)) {
            return remoteTarget;
        }

        Path relativePath = mappingBase.relativize(normalizedLocalPath);
        for (Path part : relativePath) {
            remoteTarget = joinRemotePath(remoteTarget, part.toString());
        }
        return remoteTarget;
    }

    private String buildMappingDisplay(ServerConfig server, DirectoryMapping mapping) {
        return TEXT_MAPPING_LOCAL_DIR + mapping.getLocalDirectory()
                + System.lineSeparator()
                + TEXT_SERVER_BASE_DIR + normalizeRemotePath(server.getBaseMappingDirectory())
                + System.lineSeparator()
                + TEXT_MAPPING_REMOTE_DIR + mapping.getRemoteDirectory();
    }

    private String resolveMappedRemoteDirectory(Path path) {
        if (path == null) {
            return null;
        }
        ServerConfig server = currentSelectedServerForMapping();
        if (server == null || isBlank(server.getBaseMappingDirectory())) {
            return null;
        }
        DirectoryMapping mapping = resolveNearestMapping(server, path);
        if (mapping == null) {
            return null;
        }
        Path directoryPath = Files.isDirectory(path) ? path : path.toAbsolutePath().normalize().getParent();
        if (directoryPath == null) {
            return null;
        }
        return buildMappedRemoteTarget(server, mapping, directoryPath);
    }

    private void copyMappedDirectory(Path path) {
        String mappedRemoteDirectory = resolveMappedRemoteDirectory(path);
        if (isBlank(mappedRemoteDirectory)) {
            showError(TEXT_MAPPING_NOT_FOUND, TEXT_MAPPING_NOT_FOUND_DETAIL);
            return;
        }
        copyToClipboard(mappedRemoteDirectory, TEXT_COPIED_MAPPED_DIR);
    }

    private void jumpToMappedDirectory(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            showError(TEXT_CONFIG_MAPPING, TEXT_MAPPING_DIR_ONLY);
            return;
        }
        ServerConfig server = currentSelectedServerForMapping();
        if (server == null) {
            showError(TEXT_MISSING_SERVER, TEXT_SELECT_SAVED_SERVER);
            return;
        }
        if (isBlank(server.getBaseMappingDirectory())) {
            showError(TEXT_MAPPING_BASE_REQUIRED, TEXT_INPUT_MAPPING_BASE);
            return;
        }
        DirectoryMapping mapping = resolveNearestMapping(server, path);
        if (mapping == null) {
            showError(TEXT_MAPPING_NOT_FOUND, TEXT_MAPPING_NOT_FOUND_DETAIL);
            return;
        }
        String remoteTargetPath = buildMappedRemoteTarget(server, mapping, path);
        remoteDirectoryField.setText(remoteTargetPath);
        log(TEXT_JUMPED_TO_MAPPING + remoteTargetPath);
    }

    private ServerConfig currentServer() {
        return serverCombo.getValue();
    }

    private ServerConfig currentSelectedServerForMapping() {
        ServerConfig selected = serverCombo.getValue();
        if (selected == null || selected.getId() == null) {
            return null;
        }
        return selected;
    }

    private Path currentSelectedLocalDirectory() {
        TreeItem<Path> selectedItem = workspaceTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getValue() == null) {
            return null;
        }
        Path value = selectedItem.getValue().toAbsolutePath().normalize();
        return Files.isDirectory(value) ? value : null;
    }

    private Path validateMappingLocalDirectory(String text) {
        if (isBlank(text)) {
            throw new IllegalArgumentException(TEXT_MAPPING_LOCAL_REQUIRED);
        }
        Path path = Paths.get(text.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(TEXT_MAPPING_LOCAL_INVALID);
        }
        return path;
    }

    private String validateMappingRemoteDirectory(String text) {
        if (isBlank(text)) {
            throw new IllegalArgumentException(TEXT_MAPPING_REMOTE_REQUIRED);
        }
        return text.trim();
    }

    private void selectMappingInTable(TableView<DirectoryMapping> table, String localDirectory) {
        for (DirectoryMapping item : table.getItems()) {
            if (localDirectory.equals(item.getLocalDirectory())) {
                table.getSelectionModel().select(item);
                table.scrollTo(item);
                break;
            }
        }
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

    private void previewPath(Path path) {
        if (path == null || Files.isDirectory(path)) {
            return;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        try {
            PreviewTabState state = getOrCreatePreviewTab(normalizedPath);
            if (!Files.exists(normalizedPath)) {
                showPreviewMessage(state, TEXT_PREVIEW_PATH + normalizedPath, TEXT_PREVIEW_TYPE + TEXT_PREVIEW_PLAIN, TEXT_PREVIEW_EMPTY, "plain");
                return;
            }
            if (isPreviewBlocked(normalizedPath)) {
                showPreviewMessage(state, TEXT_PREVIEW_PATH + normalizedPath, TEXT_PREVIEW_TYPE + TEXT_PREVIEW_PLAIN, TEXT_PREVIEW_UNSUPPORTED, "plain");
                return;
            }
            String previewType = resolvePreviewType(normalizedPath);
            String previewTypeLabel = resolvePreviewTypeLabel(previewType);
            if (Files.size(normalizedPath) > MAX_PREVIEW_FILE_SIZE) {
                showPreviewMessage(state, TEXT_PREVIEW_PATH + normalizedPath, TEXT_PREVIEW_TYPE + previewTypeLabel, TEXT_PREVIEW_TOO_LARGE, "plain");
                return;
            }
            String text = decodePreviewText(Files.readAllBytes(normalizedPath));
            showPreviewMessage(state, TEXT_PREVIEW_PATH + normalizedPath, TEXT_PREVIEW_TYPE + previewTypeLabel, text, previewType);
            log(TEXT_PREVIEWED_FILE + normalizedPath);
        } catch (Exception e) {
            showError(TEXT_PREVIEW_FAILED, unwrapMessage(e));
        }
    }

    private PreviewTabState getOrCreatePreviewTab(Path normalizedPath) {
        PreviewTabState state = previewTabs.get(normalizedPath);
        if (state != null) {
            outputTabPane.getSelectionModel().select(state.tab);
            return state;
        }
        state = new PreviewTabState(normalizedPath);
        VBox pane = buildPreviewPane(state);
        Tab tab = new Tab(null, pane);
        tab.setGraphic(createTabTitleLabel(resolvePreviewTabTitle(normalizedPath)));
        tab.setClosable(true);
        tab.setTooltip(new Tooltip(normalizedPath.toString()));
        PreviewTabState finalState = state;
        tab.setOnClosed(event -> previewTabs.remove(finalState.path));
        state.tab = tab;
        previewTabs.put(normalizedPath, state);
        outputTabPane.getTabs().add(tab);
        outputTabPane.getSelectionModel().select(tab);
        return state;
    }

    private void closePreviewTab(Path normalizedPath) {
        PreviewTabState state = previewTabs.remove(normalizedPath);
        if (state == null) {
            return;
        }
        outputTabPane.getTabs().remove(state.tab);
        outputTabPane.getSelectionModel().select(logTab);
    }

    private void closeAllPreviewTabs() {
        List<Path> paths = new ArrayList<Path>(previewTabs.keySet());
        for (Path path : paths) {
            closePreviewTab(path);
        }
    }

    private String resolvePreviewTabTitle(Path normalizedPath) {
        Path fileName = normalizedPath.getFileName();
        return fileName == null ? normalizedPath.toString() : fileName.toString();
    }

    private void changePreviewFontSize(PreviewTabState state, double delta) {
        state.fontSize = Math.max(10.0, Math.min(22.0, state.fontSize + delta));
        renderPreviewContent(state);
    }

    private void showPreviewMessage(PreviewTabState state, String pathText, String typeText, String text, String previewType) {
        state.pathText = pathText;
        state.typeText = typeText;
        state.text = text == null ? "" : text;
        state.renderType = isBlank(previewType) ? "plain" : previewType;
        state.pathLabel.setText(state.pathText);
        state.typeLabel.setText(state.typeText);
        renderPreviewContent(state);
        outputTabPane.getSelectionModel().select(state.tab);
    }

    private void renderPreviewContent(PreviewTabState state) {
        state.webView.getEngine().loadContent(buildPreviewHtml(state.text, state.renderType, state.fontSize));
    }

    private String buildPreviewHtml(String text, String previewType, double previewFontSize) {
        String bodyHtml = renderPreviewBody(text, previewType);
        int lineHeight = (int) Math.round(Math.max(18.0, previewFontSize + 8.0));
        int minHeight = (int) Math.round(Math.max(20.0, previewFontSize + 10.0));
        return "<html><head><meta charset='UTF-8'/>"
                + "<style>"
                + "html,body{margin:0;padding:0;background:#fbfcfe;color:#1f2937;font-family:'Consolas','JetBrains Mono','Microsoft YaHei UI',monospace;font-size:" + previewFontSize + "px;}"
                + ".code{padding:8px 0;counter-reset:line;}"
                + ".line{white-space:pre;display:block;padding:0 12px 0 56px;position:relative;line-height:" + lineHeight + "px;min-height:" + minHeight + "px;}"
                + ".line:before{counter-increment:line;content:counter(line);position:absolute;left:0;width:44px;padding-right:8px;text-align:right;color:#94a3b8;border-right:1px solid #e2e8f0;}"
                + ".empty{padding:16px 14px;color:#64748b;font-family:'Microsoft YaHei UI',sans-serif;font-size:" + previewFontSize + "px;}"
                + ".kw{color:#7c3aed;font-weight:600;}"
                + ".str{color:#047857;}"
                + ".num{color:#b45309;}"
                + ".comment{color:#6b7280;font-style:italic;}"
                + ".key{color:#1d4ed8;font-weight:600;}"
                + ".tag{color:#be123c;font-weight:600;}"
                + ".attr{color:#7c2d12;}"
                + ".val{color:#0369a1;}"
                + ".punct{color:#475569;}"
                + ".heading{color:#0f766e;font-weight:700;}"
                + ".mark{color:#9333ea;font-weight:600;}"
                + ".code-inline{background:#e2e8f0;color:#0f172a;border-radius:4px;padding:0 4px;}"
                + "</style></head><body>"
                + bodyHtml
                + "</body></html>";
    }

    private String renderPreviewBody(String text, String previewType) {
        if (isBlank(text)) {
            return "<div class='empty'>" + escapeHtml(TEXT_PREVIEW_HINT) + "</div>";
        }
        String normalizedText = text.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", " ");
        String[] lines = normalizedText.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        builder.append("<div class='code'>");
        for (String line : lines) {
            String renderedLine = renderPreviewLine(line, previewType);
            builder.append("<div class='line'>");
            builder.append(renderedLine.isEmpty() ? "&nbsp;" : renderedLine);
            builder.append("</div>");
        }
        builder.append("</div>");
        return builder.toString();
    }

    private String renderPreviewLine(String line, String previewType) {
        String type = isBlank(previewType) ? "plain" : previewType;
        switch (type) {
            case "markdown":
                return renderMarkdownLine(line);
            case "json":
                return renderCodeLikeLine(line, JSON_LITERALS, true, true);
            case "xml":
                return renderXmlLine(line);
            case "yaml":
                return renderYamlLine(line);
            case "properties":
                return renderPropertiesLine(line);
            case "shell":
                return renderCodeLikeLine(line, SHELL_KEYWORDS, false, false, "#");
            case "sql":
                return renderCodeLikeLine(line, SQL_KEYWORDS, true, false, "--");
            case "code":
                return renderCodeLikeLine(line, CODE_KEYWORDS, false, false, "//");
            default:
                return escapeHtml(line);
        }
    }

    private boolean isPreviewBlocked(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        String extension = getFileExtension(fileName);
        return EXECUTABLE_EXTENSIONS.contains(extension)
                || IMAGE_EXTENSIONS.contains(extension)
                || AUDIO_VIDEO_EXTENSIONS.contains(extension);
    }

    private String renderMarkdownLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~") || trimmed.startsWith(">")) {
            return wrapToken("comment", escapeHtml(line));
        }
        if (trimmed.startsWith("#")) {
            return wrapToken("heading", escapeHtml(line));
        }
        int listPrefixLength = resolveMarkdownListPrefixLength(line);
        if (listPrefixLength > 0) {
            return wrapToken("mark", escapeHtml(line.substring(0, listPrefixLength)))
                    + renderInlineCode(line.substring(listPrefixLength));
        }
        return renderInlineCode(line);
    }

    private int resolveMarkdownListPrefixLength(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        if (index + 1 < line.length() && (line.charAt(index) == '-' || line.charAt(index) == '*' || line.charAt(index) == '+')
                && Character.isWhitespace(line.charAt(index + 1))) {
            return index + 2;
        }
        int numberIndex = index;
        while (numberIndex < line.length() && Character.isDigit(line.charAt(numberIndex))) {
            numberIndex++;
        }
        if (numberIndex > index && numberIndex + 1 < line.length() && line.charAt(numberIndex) == '.'
                && Character.isWhitespace(line.charAt(numberIndex + 1))) {
            return numberIndex + 2;
        }
        return 0;
    }

    private String renderInlineCode(String line) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < line.length()) {
            int start = line.indexOf('`', index);
            if (start < 0) {
                builder.append(escapeHtml(line.substring(index)));
                break;
            }
            int end = line.indexOf('`', start + 1);
            if (end < 0) {
                builder.append(escapeHtml(line.substring(index)));
                break;
            }
            builder.append(escapeHtml(line.substring(index, start)));
            builder.append(wrapToken("code-inline", escapeHtml(line.substring(start + 1, end))));
            index = end + 1;
        }
        return builder.toString();
    }

    private String renderYamlLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return wrapToken("comment", escapeHtml(line));
        }
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        StringBuilder builder = new StringBuilder();
        if (index + 1 < line.length() && line.charAt(index) == '-' && Character.isWhitespace(line.charAt(index + 1))) {
            builder.append(escapeHtml(line.substring(0, index)));
            builder.append(wrapToken("mark", escapeHtml(line.substring(index, index + 1))));
            builder.append(escapeHtml(line.substring(index + 1, index + 2)));
            line = line.substring(index + 2);
        }
        int colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
            builder.append(wrapToken("key", escapeHtml(line.substring(0, colonIndex))));
            builder.append(wrapToken("punct", ":"));
            builder.append(renderCodeLikeLine(line.substring(colonIndex + 1), JSON_LITERALS, true, false, "#"));
            return builder.toString();
        }
        builder.append(renderCodeLikeLine(line, JSON_LITERALS, true, false, "#"));
        return builder.toString();
    }
    private String renderPropertiesLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return wrapToken("comment", escapeHtml(line));
        }
        int separatorIndex = findFirstSeparator(line, ':', '=');
        if (separatorIndex < 0) {
            return renderCodeLikeLine(line, JSON_LITERALS, true, false);
        }
        return wrapToken("key", escapeHtml(line.substring(0, separatorIndex)))
                + wrapToken("punct", escapeHtml(String.valueOf(line.charAt(separatorIndex))))
                + renderCodeLikeLine(line.substring(separatorIndex + 1), JSON_LITERALS, true, false);
    }

    private int findFirstSeparator(String line, char first, char second) {
        int firstIndex = line.indexOf(first);
        int secondIndex = line.indexOf(second);
        if (firstIndex < 0) {
            return secondIndex;
        }
        if (secondIndex < 0) {
            return firstIndex;
        }
        return Math.min(firstIndex, secondIndex);
    }

    private String renderCodeLikeLine(String line, Set<String> keywords, boolean ignoreCase, boolean jsonMode,
            String... commentMarkers) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < line.length()) {
            String commentMarker = findCommentMarker(line, index, commentMarkers);
            if (commentMarker != null) {
                builder.append(wrapToken("comment", escapeHtml(line.substring(index))));
                break;
            }
            char current = line.charAt(index);
            if (current == '"' || current == '\'') {
                int end = consumeQuotedSegment(line, index, current);
                String tokenClass = jsonMode && nextNonWhitespaceChar(line, end) == ':' ? "key" : "str";
                builder.append(wrapToken(tokenClass, escapeHtml(line.substring(index, end))));
                index = end;
                continue;
            }
            if (current == '$' && index + 1 < line.length() && isIdentifierStart(line.charAt(index + 1))) {
                int end = consumeIdentifier(line, index + 1);
                builder.append(wrapToken("key", escapeHtml(line.substring(index, end))));
                index = end;
                continue;
            }
            if (Character.isDigit(current)) {
                int end = consumeNumber(line, index);
                builder.append(wrapToken("num", escapeHtml(line.substring(index, end))));
                index = end;
                continue;
            }
            if (isIdentifierStart(current)) {
                int end = consumeIdentifier(line, index);
                String word = line.substring(index, end);
                String comparable = ignoreCase ? word.toLowerCase(Locale.ROOT) : word;
                if (keywords.contains(comparable)) {
                    builder.append(wrapToken("kw", escapeHtml(word)));
                } else {
                    builder.append(escapeHtml(word));
                }
                index = end;
                continue;
            }
            if ("{}[]():,.".indexOf(current) >= 0) {
                builder.append(wrapToken("punct", escapeHtml(String.valueOf(current))));
                index++;
                continue;
            }
            builder.append(escapeHtml(String.valueOf(current)));
            index++;
        }
        return builder.toString();
    }

    private String findCommentMarker(String line, int index, String... commentMarkers) {
        for (String marker : commentMarkers) {
            if (marker != null && !marker.isEmpty() && line.startsWith(marker, index)) {
                return marker;
            }
        }
        return null;
    }

    private int consumeQuotedSegment(String line, int start, char quote) {
        int index = start + 1;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (current == '\\') {
                index += 2;
                continue;
            }
            if (current == quote) {
                return index + 1;
            }
            index++;
        }
        return line.length();
    }

    private int consumeIdentifier(String line, int start) {
        int index = start;
        while (index < line.length() && isIdentifierPart(line.charAt(index))) {
            index++;
        }
        return index;
    }

    private int consumeNumber(String line, int start) {
        int index = start;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (Character.isDigit(current) || current == '.' || current == '_' || current == 'x' || current == 'X'
                    || current == 'a' || current == 'b' || current == 'c' || current == 'd' || current == 'e'
                    || current == 'f' || current == 'A' || current == 'B' || current == 'C' || current == 'D'
                    || current == 'E' || current == 'F') {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    private char nextNonWhitespaceChar(String line, int start) {
        int index = start;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (!Character.isWhitespace(current)) {
                return current;
            }
            index++;
        }
        return 0;
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '-';
    }

    private String renderXmlLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("<!--") || trimmed.startsWith("<![CDATA")) {
            return wrapToken("comment", escapeHtml(line));
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < line.length()) {
            int tagStart = line.indexOf('<', index);
            if (tagStart < 0) {
                builder.append(escapeHtml(line.substring(index)));
                break;
            }
            if (tagStart > index) {
                builder.append(escapeHtml(line.substring(index, tagStart)));
            }
            int tagEnd = line.indexOf('>', tagStart);
            if (tagEnd < 0) {
                builder.append(escapeHtml(line.substring(tagStart)));
                break;
            }
            builder.append(renderXmlTag(line.substring(tagStart, tagEnd + 1)));
            index = tagEnd + 1;
        }
        return builder.toString();
    }
    private String renderXmlTag(String tag) {
        if (tag.startsWith("<!--") || tag.startsWith("<![CDATA")) {
            return wrapToken("comment", escapeHtml(tag));
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        builder.append(wrapToken("punct", escapeHtml(String.valueOf(tag.charAt(index++)))));
        while (index < tag.length() && Character.isWhitespace(tag.charAt(index))) {
            builder.append(escapeHtml(String.valueOf(tag.charAt(index++))));
        }
        while (index < tag.length() && (tag.charAt(index) == '/' || tag.charAt(index) == '?' || tag.charAt(index) == '!')) {
            builder.append(wrapToken("punct", escapeHtml(String.valueOf(tag.charAt(index++)))));
        }
        int nameStart = index;
        while (index < tag.length() && isXmlNameChar(tag.charAt(index))) {
            index++;
        }
        if (index > nameStart) {
            builder.append(wrapToken("tag", escapeHtml(tag.substring(nameStart, index))));
        }
        while (index < tag.length()) {
            char current = tag.charAt(index);
            if (current == '>') {
                builder.append(wrapToken("punct", "&gt;"));
                index++;
                break;
            }
            if (current == '/' || current == '?') {
                builder.append(wrapToken("punct", escapeHtml(String.valueOf(current))));
                index++;
                continue;
            }
            if (Character.isWhitespace(current)) {
                builder.append(escapeHtml(String.valueOf(current)));
                index++;
                continue;
            }
            int attrStart = index;
            while (index < tag.length() && isXmlNameChar(tag.charAt(index))) {
                index++;
            }
            if (index > attrStart) {
                builder.append(wrapToken("attr", escapeHtml(tag.substring(attrStart, index))));
            }
            while (index < tag.length() && Character.isWhitespace(tag.charAt(index))) {
                builder.append(escapeHtml(String.valueOf(tag.charAt(index++))));
            }
            if (index < tag.length() && tag.charAt(index) == '=') {
                builder.append(wrapToken("punct", "="));
                index++;
            }
            while (index < tag.length() && Character.isWhitespace(tag.charAt(index))) {
                builder.append(escapeHtml(String.valueOf(tag.charAt(index++))));
            }
            if (index < tag.length() && (tag.charAt(index) == '"' || tag.charAt(index) == '\'')) {
                char quote = tag.charAt(index);
                int end = consumeQuotedSegment(tag, index, quote);
                builder.append(wrapToken("val", escapeHtml(tag.substring(index, end))));
                index = end;
            }
        }
        return builder.toString();
    }

    private boolean isXmlNameChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-' || value == ':' || value == '.';
    }

    private String decodePreviewText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            text = new String(bytes, Charset.defaultCharset());
        }
        return text.replace("\t", "    ").replace("\u0000", " ");
    }

    private String resolvePreviewType(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        String extension = getFileExtension(fileName);
        if (PLAIN_TEXT_EXTENSIONS.contains(extension) || ".gitignore".equals(fileName) || ".env".equals(fileName)) {
            return "plain";
        }
        if (MARKDOWN_EXTENSIONS.contains(extension)) {
            return "markdown";
        }
        if (JSON_EXTENSIONS.contains(extension)) {
            return "json";
        }
        if (XML_EXTENSIONS.contains(extension)) {
            return "xml";
        }
        if (YAML_EXTENSIONS.contains(extension)) {
            return "yaml";
        }
        if (PROPERTIES_EXTENSIONS.contains(extension)) {
            return "properties";
        }
        if (SHELL_EXTENSIONS.contains(extension)) {
            return "shell";
        }
        if (SQL_EXTENSIONS.contains(extension)) {
            return "sql";
        }
        if (CODE_EXTENSIONS.contains(extension)) {
            return "code";
        }
        return "plain";
    }

    private String resolvePreviewTypeLabel(String previewType) {
        if (previewType == null) {
            return TEXT_PREVIEW_PLAIN;
        }
        switch (previewType) {
            case "markdown":
                return TEXT_PREVIEW_MARKDOWN;
            case "json":
                return TEXT_PREVIEW_JSON;
            case "xml":
                return TEXT_PREVIEW_XML;
            case "yaml":
                return TEXT_PREVIEW_YAML;
            case "properties":
                return TEXT_PREVIEW_PROPERTIES;
            case "shell":
                return TEXT_PREVIEW_SHELL;
            case "sql":
                return TEXT_PREVIEW_SQL;
            case "code":
                return TEXT_PREVIEW_CODE;
            default:
                return TEXT_PREVIEW_PLAIN;
        }
    }

    private String getFileExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1);
    }

    private String wrapToken(String cssClass, String text) {
        return "<span class='" + cssClass + "'>" + text + "</span>";
    }

    private String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void openInExplorer(Path path) {
        if (path == null) {
            return;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        try {
            ProcessBuilder builder;
            if (Files.isDirectory(normalizedPath)) {
                builder = new ProcessBuilder("explorer.exe", normalizedPath.toString());
            } else {
                builder = new ProcessBuilder("explorer.exe", "/select," + normalizedPath.toString());
            }
            builder.start();
            log(TEXT_OPENED_IN_EXPLORER + normalizedPath);
        } catch (IOException e) {
            showError(TEXT_OPEN_EXPLORER_FAILED, unwrapMessage(e));
        }
    }
    private void log(String message) {
        Runnable writer = () -> {
            outputArea.appendText(message + System.lineSeparator());
            outputArea.positionCaret(outputArea.getLength());
            scrollLogToBottom();
        };
        if (Platform.isFxApplicationThread()) {
            writer.run();
        } else {
            Platform.runLater(writer);
        }
    }

    private void scrollLogToBottom() {
        outputArea.setScrollTop(Double.MAX_VALUE);
        outputArea.setScrollLeft(0);
        Platform.runLater(() -> {
            outputArea.positionCaret(outputArea.getLength());
            outputArea.setScrollTop(Double.MAX_VALUE);
            outputArea.setScrollLeft(0);
            outputArea.lookupAll(".scroll-bar").forEach(node -> {
                if (node instanceof javafx.scene.control.ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                    bar.setValue(bar.getMax());
                }
            });
        });
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

    private String normalizeLocalPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private String normalizeRemotePath(String value) {
        if (isBlank(value)) {
            return "/";
        }
        String normalized = value.trim().replace('\\', '/');
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
        String normalizedBase = normalizeRemotePath(base);
        String normalizedChild = child == null ? "" : child.trim().replace('\\', '/');
        while (normalizedChild.startsWith("/")) {
            normalizedChild = normalizedChild.substring(1);
        }
        if (normalizedChild.isEmpty() || ".".equals(normalizedChild)) {
            return normalizedBase;
        }
        return normalizedBase + "/" + normalizedChild;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private class PreviewTabState {

        private final Path path;
        private final Label pathLabel = new Label(TEXT_PREVIEW_EMPTY);
        private final Label typeLabel = new Label(TEXT_PREVIEW_HINT);
        private final WebView webView = new WebView();
        private Tab tab;
        private String pathText = TEXT_PREVIEW_EMPTY;
        private String typeText = TEXT_PREVIEW_HINT;
        private String text = "";
        private String renderType = "plain";
        private double fontSize = 12.0;

        private PreviewTabState(Path path) {
            this.path = path;
            this.pathLabel.setWrapText(true);
            this.typeLabel.setWrapText(true);
            this.webView.setContextMenuEnabled(false);
        }
    }

    private class WorkspaceTreeCell extends TreeCell<Path> {

        private final MenuItem previewItem = new MenuItem(TEXT_PREVIEW_FILE);
        private final MenuItem jumpToMappingItem = new MenuItem(TEXT_JUMP_TO_MAPPING);
        private final MenuItem copyMappedDirectoryItem = new MenuItem(TEXT_COPY_MAPPED_DIR);
        private final MenuItem openInExplorerItem = new MenuItem(TEXT_OPEN_IN_EXPLORER);
        private final MenuItem uploadItem = new MenuItem(TEXT_UPLOAD_MENU);
        private final MenuItem uploadToMappingItem = new MenuItem(TEXT_UPLOAD_TO_MAPPING);
        private final MenuItem configureMappingItem = new MenuItem(TEXT_CONFIG_MAPPING);
        private final MenuItem copyFileNameItem = new MenuItem(TEXT_COPY_FILE_NAME);
        private final MenuItem copyFilePathItem = new MenuItem(TEXT_COPY_FILE_PATH);
        private final MenuItem refreshItem = new MenuItem(TEXT_REFRESH_NODE);
        private final ContextMenu contextMenu = new ContextMenu(
                previewItem,
                jumpToMappingItem,
                copyMappedDirectoryItem,
                openInExplorerItem,
                new SeparatorMenuItem(),
                uploadItem,
                uploadToMappingItem,
                configureMappingItem,
                new SeparatorMenuItem(),
                copyFileNameItem,
                copyFilePathItem,
                new SeparatorMenuItem(),
                refreshItem
        );

        private WorkspaceTreeCell() {
            setOnMouseClicked(event -> {
                Path path = getItem();
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && path != null
                        && !Files.isDirectory(path)
                        && !isPreviewBlocked(path)) {
                    previewPath(path);
                }
            });
            previewItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    previewPath(path);
                }
            });
            jumpToMappingItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    jumpToMappedDirectory(path);
                }
            });
            copyMappedDirectoryItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    copyMappedDirectory(path);
                }
            });
            openInExplorerItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    openInExplorer(path);
                }
            });
            uploadItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    uploadPath(path);
                }
            });
            uploadToMappingItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    uploadPathToMapping(path);
                }
            });
            configureMappingItem.setOnAction(event -> {
                Path path = getItem();
                if (path != null) {
                    configureDirectoryMapping(path);
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
                    copyToClipboard(path.toAbsolutePath().normalize().toString(), TEXT_COPIED_FILE_PATH);
                }
            });
            refreshItem.setOnAction(event -> {
                TreeItem<Path> item = getTreeItem();
                if (item instanceof FileTreeItem) {
                    FileTreeItem fileTreeItem = (FileTreeItem) item;
                    fileTreeItem.refresh();
                    log(TEXT_NODE_REFRESHED + item.getValue());
                }
            });
            contextMenu.setOnShowing(event -> updateMenuState());
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
            updateMenuState();
            setContextMenu(contextMenu);
        }

        private void updateMenuState() {
            Path path = getItem();
            ServerConfig selectedServer = currentSelectedServerForMapping();
            boolean hasPath = path != null;
            boolean isDirectory = hasPath && Files.isDirectory(path);
            boolean hasSavedServer = selectedServer != null;
            boolean hasBaseMappingDirectory = hasSavedServer && !isBlank(selectedServer.getBaseMappingDirectory());
            boolean hasMapping = hasSavedServer && resolveNearestMapping(selectedServer, path) != null;
            previewItem.setDisable(!hasPath || isDirectory || isPreviewBlocked(path));
            jumpToMappingItem.setDisable(!isDirectory || !hasBaseMappingDirectory || !hasMapping);
            copyMappedDirectoryItem.setDisable(!hasPath || !hasBaseMappingDirectory || !hasMapping);
            openInExplorerItem.setDisable(!hasPath);
            uploadItem.setDisable(!hasPath);
            uploadToMappingItem.setDisable(!hasPath || !hasBaseMappingDirectory || !hasMapping);
            configureMappingItem.setDisable(!isDirectory || !hasSavedServer || !hasBaseMappingDirectory);
            copyFileNameItem.setDisable(!hasPath || path.getFileName() == null);
            copyFilePathItem.setDisable(!hasPath);
            refreshItem.setDisable(getTreeItem() == null);
        }
    }
}















