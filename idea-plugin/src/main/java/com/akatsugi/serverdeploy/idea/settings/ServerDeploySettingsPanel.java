package com.akatsugi.serverdeploy.idea.settings;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.service.SettingsJsonService;
import com.akatsugi.serverdeploy.idea.ui.LoadServerMappingsDialog;
import com.akatsugi.serverdeploy.idea.ui.MappingEditDialog;
import com.akatsugi.serverdeploy.idea.ui.ServerEditDialog;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ServerDeploySettingsPanel {

    private static final String SHELL_COMMAND_HINT =
            "支持占位符：${remotePath}、${remoteDirectory}、${serverName}、${host}、${username}、${defaultDirectory}";
    private static final String CANDIDATE_COMMANDS_HINT =
            "每行一条候选命令。执行远程命令时可从候选中一键填入，再继续手动编辑。";
    private static final String UPLOAD_FILE_NAME_HINT =
            "上传单个文件时，默认填入“同步修改文件名”的值；留空表示不预设。";

    private final JPanel rootPanel = new JPanel(new BorderLayout(0, 12));
    private final JTextArea defaultShellCommandArea = new JTextArea(4, 80);
    private final JTextField defaultUploadFileNameField = new JTextField();
    private final JTextArea shellCommandCandidatesArea = new JTextArea(5, 80);
    private final List<ServerConfig> servers = new ArrayList<>();
    private final List<DirectoryMapping> mappings = new ArrayList<>();
    private final ServerTableModel serverTableModel = new ServerTableModel();
    private final JTable serverTable = new JTable(serverTableModel);
    private final JTabbedPane mappingTabbedPane = new JTabbedPane();
    private final Map<String, MappingTreeView> mappingTreeViewsByServerId = new LinkedHashMap<>();
    private final ServerDeploySettingsService settingsService = ServerDeploySettingsService.getInstance();
    private final SettingsJsonService settingsJsonService = new SettingsJsonService();

    public ServerDeploySettingsPanel() {
        configureTable(serverTable, 5);

        JScrollPane serverScrollPane = new JScrollPane(serverTable);
        serverScrollPane.setPreferredSize(new Dimension(920, 210));
        mappingTabbedPane.setPreferredSize(new Dimension(920, 290));

        rootPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        rootPanel.add(createNorthPanel(), BorderLayout.NORTH);
        rootPanel.add(createContentPanel(serverScrollPane), BorderLayout.CENTER);

        configureServerColumns();
        rebuildMappingTabs(null, null, null);
    }

    public JComponent getComponent() {
        return rootPanel;
    }

    public void setData(
            String defaultShellCommand,
            List<String> shellCommandCandidates,
            List<ServerConfig> serverValues,
            List<DirectoryMapping> mappingValues
    ) {
        setData(defaultShellCommand, "", shellCommandCandidates, serverValues, mappingValues);
    }

    public void setData(
            String defaultShellCommand,
            String defaultUploadFileName,
            List<String> shellCommandCandidates,
            List<ServerConfig> serverValues,
            List<DirectoryMapping> mappingValues
    ) {
        String selectedServerId = getSelectedMappingServerId();
        String selectedMappingId = getSelectedMappingId();
        String selectedLocalDirectory = getSelectedLocalDirectory();
        defaultShellCommandArea.setText(ServerDeploySettingsService.normalizeShellCommand(defaultShellCommand));
        defaultUploadFileNameField.setText(ServerDeploySettingsService.normalizeUploadFileName(defaultUploadFileName));
        shellCommandCandidatesArea.setText(String.join("\n",
                ServerDeploySettingsService.normalizeShellCommandCandidates(shellCommandCandidates, false)));

        servers.clear();
        mappings.clear();
        servers.addAll(copyServers(serverValues));
        mappings.addAll(copyMappings(mappingValues));
        sortData();
        serverTableModel.fireTableDataChanged();
        configureServerColumns();
        rebuildMappingTabs(selectedServerId, selectedMappingId, selectedLocalDirectory);
    }

    public List<ServerConfig> getServers() {
        return copyServers(servers);
    }

    public List<DirectoryMapping> getMappings() {
        return copyMappings(mappings);
    }

    public String getDefaultShellCommand() {
        return ServerDeploySettingsService.normalizeShellCommand(defaultShellCommandArea.getText());
    }

    public String getDefaultUploadFileName() {
        return ServerDeploySettingsService.normalizeUploadFileName(defaultUploadFileNameField.getText());
    }

    public List<String> getShellCommandCandidates() {
        return ServerDeploySettingsService.normalizeShellCommandCandidates(
                shellCommandCandidatesArea.getText().lines().collect(Collectors.toList()),
                false
        );
    }

    public boolean isModified(
            String originalDefaultShellCommand,
            List<String> originalShellCommandCandidates,
            List<ServerConfig> originalServers,
            List<DirectoryMapping> originalMappings
    ) {
        return isModified(originalDefaultShellCommand, "", originalShellCommandCandidates, originalServers, originalMappings);
    }

    public boolean isModified(
            String originalDefaultShellCommand,
            String originalDefaultUploadFileName,
            List<String> originalShellCommandCandidates,
            List<ServerConfig> originalServers,
            List<DirectoryMapping> originalMappings
    ) {
        return !Objects.equals(ServerDeploySettingsService.normalizeShellCommand(originalDefaultShellCommand), getDefaultShellCommand())
                || !Objects.equals(ServerDeploySettingsService.normalizeUploadFileName(originalDefaultUploadFileName), getDefaultUploadFileName())
                || !Objects.equals(
                ServerDeploySettingsService.normalizeShellCommandCandidates(originalShellCommandCandidates, false),
                getShellCommandCandidates()
        )
                || !copyServers(originalServers).equals(copyServers(servers))
                || !copyMappings(originalMappings).equals(copyMappings(mappings));
    }

    public void validateState(
            String defaultShellCommand,
            List<String> shellCommandCandidates,
            List<ServerConfig> serverValues,
            List<DirectoryMapping> mappingValues
    ) throws ConfigurationException {
        validateState(defaultShellCommand, "", shellCommandCandidates, serverValues, mappingValues);
    }

    public void validateState(
            String defaultShellCommand,
            String defaultUploadFileName,
            List<String> shellCommandCandidates,
            List<ServerConfig> serverValues,
            List<DirectoryMapping> mappingValues
    ) throws ConfigurationException {
        if (isBlank(defaultShellCommand)) {
            throw new ConfigurationException("默认 Shell 命令不能为空。");
        }
        if (defaultUploadFileName != null && (defaultUploadFileName.contains("/") || defaultUploadFileName.contains("\\"))) {
            throw new ConfigurationException("默认上传文件名不能包含路径分隔符。");
        }
        if (shellCommandCandidates.stream().anyMatch(this::isBlank)) {
            throw new ConfigurationException("候选 Shell 命令中不能包含空行。");
        }
        for (ServerConfig server : serverValues) {
            if (isBlank(server.getName()) || isBlank(server.getHost()) || isBlank(server.getUsername())
                    || isBlank(server.getPassword()) || isBlank(server.getDefaultDirectory())) {
                throw new ConfigurationException("服务器配置项不能为空。");
            }
        }
        for (DirectoryMapping mapping : mappingValues) {
            if (isBlank(mapping.getServerId()) || isBlank(mapping.getLocalDirectory()) || isBlank(mapping.getRemoteDirectory())) {
                throw new ConfigurationException("目录映射配置项不能为空。");
            }
        }
    }

    private JComponent createNorthPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(createToolbar(), BorderLayout.NORTH);
        panel.add(createCommandSettingsPanel(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton importButton = new JButton("导入 JSON");
        JButton exportButton = new JButton("导出 JSON");
        importButton.addActionListener(event -> importFromJson());
        exportButton.addActionListener(event -> exportToJson());
        panel.add(importButton);
        panel.add(exportButton);
        return panel;
    }

    private JComponent createCommandSettingsPanel() {
        defaultShellCommandArea.setLineWrap(true);
        defaultShellCommandArea.setWrapStyleWord(true);
        shellCommandCandidatesArea.setLineWrap(true);
        shellCommandCandidatesArea.setWrapStyleWord(true);

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(createTextAreaPanel("默认远程 Shell 命令", defaultShellCommandArea, SHELL_COMMAND_HINT));
        container.add(createTextFieldPanel("默认上传文件名", defaultUploadFileNameField, UPLOAD_FILE_NAME_HINT));
        container.add(createTextAreaPanel("候选 Shell 命令", shellCommandCandidatesArea, CANDIDATE_COMMANDS_HINT));
        return container;
    }

    private JComponent createTextAreaPanel(String title, JTextArea textArea, String hint) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new TitledBorder(title));
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        panel.add(new JLabel(hint), BorderLayout.SOUTH);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, title.contains("候选") ? 180 : 150));
        return panel;
    }

    private JComponent createTextFieldPanel(String title, JTextField textField, String hint) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new TitledBorder(title));
        panel.add(textField, BorderLayout.CENTER);
        panel.add(new JLabel(hint), BorderLayout.SOUTH);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        return panel;
    }

    private JComponent createContentPanel(JScrollPane serverScrollPane) {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints serverConstraints = new GridBagConstraints();
        serverConstraints.gridx = 0;
        serverConstraints.gridy = 0;
        serverConstraints.weightx = 1.0;
        serverConstraints.weighty = 0.42;
        serverConstraints.fill = GridBagConstraints.BOTH;
        serverConstraints.insets = new Insets(0, 0, 6, 0);
        panel.add(createServerPanel(serverScrollPane), serverConstraints);

        GridBagConstraints mappingConstraints = new GridBagConstraints();
        mappingConstraints.gridx = 0;
        mappingConstraints.gridy = 1;
        mappingConstraints.weightx = 1.0;
        mappingConstraints.weighty = 0.58;
        mappingConstraints.fill = GridBagConstraints.BOTH;
        panel.add(createMappingPanel(), mappingConstraints);

        return panel;
    }

    private JPanel createServerPanel(JScrollPane scrollPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("服务器列表"));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(createServerButtons(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createMappingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("目录映射"));
        panel.add(mappingTabbedPane, BorderLayout.CENTER);
        panel.add(createMappingButtons(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createServerButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("新增");
        JButton editButton = new JButton("编辑");
        JButton removeButton = new JButton("删除");
        JButton loadMappingsButton = new JButton("加载映射");
        addButton.addActionListener(event -> addServer());
        editButton.addActionListener(event -> editServer());
        removeButton.addActionListener(event -> removeServer());
        loadMappingsButton.addActionListener(event -> loadMappingsFromAnotherServer());
        panel.add(addButton);
        panel.add(editButton);
        panel.add(removeButton);
        panel.add(loadMappingsButton);
        return panel;
    }
    private JPanel createMappingButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("新增");
        JButton editButton = new JButton("编辑");
        JButton removeButton = new JButton("删除");
        JButton loadMappingsButton = new JButton("从其他服务器加载");
        addButton.addActionListener(event -> addMapping());
        editButton.addActionListener(event -> editMapping());
        removeButton.addActionListener(event -> removeMapping());
        loadMappingsButton.addActionListener(event -> loadMappingsFromAnotherServer());
        panel.add(addButton);
        panel.add(editButton);
        panel.add(removeButton);
        panel.add(loadMappingsButton);
        return panel;
    }

    private void configureTable(JTable table, int visibleRows) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        table.setPreferredScrollableViewportSize(new Dimension(920, table.getRowHeight() * visibleRows + 30));
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
    }

    private void configureServerColumns() {
        TableColumnModel columns = serverTable.getColumnModel();
        if (columns.getColumnCount() < 5) {
            return;
        }
        serverTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        columns.getColumn(0).setPreferredWidth(160);
        columns.getColumn(1).setPreferredWidth(180);
        columns.getColumn(2).setPreferredWidth(70);
        columns.getColumn(3).setPreferredWidth(140);
        columns.getColumn(4).setPreferredWidth(360);
    }

    private void addServer() {
        ServerEditDialog dialog = new ServerEditDialog(null);
        if (!dialog.showAndGet()) {
            return;
        }
        ServerConfig config = dialog.getServerConfig();
        config.ensureIdentity();
        if (servers.isEmpty()) {
            config.setLastUsed(true);
        }
        servers.add(config);
        sortData();
        serverTableModel.fireTableDataChanged();
        rebuildMappingTabs(config.getId(), getSelectedMappingId(), getSelectedLocalDirectory());
    }

    private void editServer() {
        int row = serverTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        String selectedServerId = getSelectedMappingServerId();
        String selectedMappingId = getSelectedMappingId();
        String selectedLocalDirectory = getSelectedLocalDirectory();
        int modelRow = serverTable.convertRowIndexToModel(row);
        ServerEditDialog dialog = new ServerEditDialog(servers.get(modelRow).copy());
        if (!dialog.showAndGet()) {
            return;
        }
        ServerConfig updated = dialog.getServerConfig();
        updated.setId(servers.get(modelRow).getId());
        updated.setLastUsed(servers.get(modelRow).isLastUsed());
        servers.set(modelRow, updated);
        sortData();
        serverTableModel.fireTableDataChanged();
        rebuildMappingTabs(selectedServerId, selectedMappingId, selectedLocalDirectory);
    }

    private void removeServer() {
        int row = serverTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        String selectedServerId = getSelectedMappingServerId();
        String selectedMappingId = getSelectedMappingId();
        String selectedLocalDirectory = getSelectedLocalDirectory();
        int modelRow = serverTable.convertRowIndexToModel(row);
        ServerConfig removed = servers.remove(modelRow);
        mappings.removeIf(mapping -> Objects.equals(mapping.getServerId(), removed.getId()));
        if (!servers.isEmpty() && servers.stream().noneMatch(ServerConfig::isLastUsed)) {
            servers.get(0).setLastUsed(true);
        }
        serverTableModel.fireTableDataChanged();
        String preferredServerId = Objects.equals(selectedServerId, removed.getId()) ? null : selectedServerId;
        rebuildMappingTabs(preferredServerId, selectedMappingId, selectedLocalDirectory);
    }

    private void addMapping() {
        if (servers.isEmpty()) {
            Messages.showInfoMessage(rootPanel, "请先新增服务器，再创建目录映射。", "服务器部署");
            return;
        }
        String defaultProjectDirectory = determineDefaultProjectDirectory();
        String selectedLocalDirectory = getSelectedLocalDirectory();
        String initialLocalDirectory = isBlank(selectedLocalDirectory) ? defaultProjectDirectory : selectedLocalDirectory;
        MappingEditDialog dialog = new MappingEditDialog(
                servers,
                null,
                initialLocalDirectory,
                defaultProjectDirectory,
                getSelectedMappingServerId()
        );
        if (!dialog.showAndGet()) {
            return;
        }
        upsertMapping(dialog.getDirectoryMapping(), null);
    }

    private void editMapping() {
        DirectoryMapping existing = getSelectedMapping();
        if (existing == null) {
            return;
        }
        DirectoryMapping editing = existing.copy();
        MappingEditDialog dialog = new MappingEditDialog(servers, editing, editing.getLocalDirectory(), determineDefaultProjectDirectory());
        if (!dialog.showAndGet()) {
            return;
        }
        upsertMapping(dialog.getDirectoryMapping(), editing.getId());
    }

    private void removeMapping() {
        DirectoryMapping selectedMapping = getSelectedMapping();
        if (selectedMapping == null) {
            return;
        }
        String selectedLocalDirectory = selectedMapping.getLocalDirectory();
        mappings.removeIf(mapping -> Objects.equals(mapping.getId(), selectedMapping.getId()));
        rebuildMappingTabs(getSelectedMappingServerId(), null, selectedLocalDirectory);
    }
    private void loadMappingsFromAnotherServer() {
        if (servers.size() < 2) {
            Messages.showInfoMessage(rootPanel, "至少需要两个服务器，才能加载其他服务器的映射。", "服务器部署");
            return;
        }

        ServerConfig initialTarget = resolvePreferredTargetServer();
        ServerConfig initialSource = resolveInitialSourceServer(initialTarget);
        LoadServerMappingsDialog dialog = new LoadServerMappingsDialog(servers, initialTarget, initialSource);
        if (!dialog.showAndGet()) {
            return;
        }

        ServerConfig targetServer = dialog.getTargetServer();
        ServerConfig sourceServer = dialog.getSourceServer();
        if (targetServer == null || sourceServer == null) {
            return;
        }

        List<DirectoryMapping> sourceMappings = mappings.stream()
                .filter(mapping -> Objects.equals(mapping.getServerId(), sourceServer.getId()))
                .map(DirectoryMapping::copy)
                .collect(Collectors.toList());
        if (sourceMappings.isEmpty()) {
            Messages.showInfoMessage(rootPanel, "来源服务器没有可加载的目录映射。", "服务器部署");
            return;
        }

        int importedCount = 0;
        int skippedCount = 0;
        String preferredLocalDirectory = getSelectedLocalDirectory();
        for (DirectoryMapping sourceMapping : sourceMappings) {
            DirectoryMapping copiedMapping = sourceMapping.copy();
            copiedMapping.setId(null);
            copiedMapping.setServerId(targetServer.getId());

            if (!dialog.isOverwriteEnabled() && hasSameLocalDirectoryMapping(targetServer.getId(), copiedMapping.getLocalDirectory())) {
                skippedCount++;
                continue;
            }

            upsertMapping(copiedMapping, null);
            preferredLocalDirectory = copiedMapping.getLocalDirectory();
            importedCount++;
        }

        rebuildMappingTabs(targetServer.getId(), null, preferredLocalDirectory);
        String message = "已从服务器“" + safe(sourceServer.getName()) + "”加载到“" + safe(targetServer.getName()) + "”。\n"
                + "导入/覆盖映射：" + importedCount;
        if (skippedCount > 0) {
            message += "\n跳过重复映射：" + skippedCount;
        }
        Messages.showInfoMessage(rootPanel, message, "服务器部署");
    }

    private boolean hasSameLocalDirectoryMapping(String serverId, String localDirectory) {
        String normalizedLocalDirectory = ServerDeploySettingsService.normalizeLocalDirectory(localDirectory);
        return mappings.stream().anyMatch(mapping ->
                Objects.equals(mapping.getServerId(), serverId)
                        && Objects.equals(
                        ServerDeploySettingsService.normalizeLocalDirectory(mapping.getLocalDirectory()),
                        normalizedLocalDirectory
                )
        );
    }

    private ServerConfig resolvePreferredTargetServer() {
        String selectedServerId = getSelectedMappingServerId();
        if (!isBlank(selectedServerId)) {
            ServerConfig selectedTabServer = findServerById(selectedServerId);
            if (selectedTabServer != null) {
                return selectedTabServer;
            }
        }

        DirectoryMapping selectedMapping = getSelectedMapping();
        if (selectedMapping != null) {
            ServerConfig mappingServer = findServerById(selectedMapping.getServerId());
            if (mappingServer != null) {
                return mappingServer;
            }
        }

        int selectedServerRow = serverTable.getSelectedRow();
        if (selectedServerRow >= 0) {
            return servers.get(serverTable.convertRowIndexToModel(selectedServerRow));
        }

        return servers.stream().filter(ServerConfig::isLastUsed).findFirst().orElse(servers.get(0));
    }

    private ServerConfig resolveInitialSourceServer(ServerConfig targetServer) {
        return servers.stream()
                .filter(server -> !Objects.equals(server.getId(), targetServer == null ? null : targetServer.getId()))
                .findFirst()
                .orElse(null);
    }

    private ServerConfig findServerById(String serverId) {
        return servers.stream()
                .filter(server -> Objects.equals(server.getId(), serverId))
                .findFirst()
                .orElse(null);
    }

    private void upsertMapping(DirectoryMapping mapping, String existingId) {
        mapping.ensureIdentity();
        if (existingId != null) {
            mapping.setId(existingId);
        }

        for (int i = 0; i < mappings.size(); i++) {
            DirectoryMapping current = mappings.get(i);
            boolean sameTarget = Objects.equals(current.getServerId(), mapping.getServerId())
                    && Objects.equals(
                    ServerDeploySettingsService.normalizeLocalDirectory(current.getLocalDirectory()),
                    mapping.getLocalDirectory()
            );
            boolean sameId = Objects.equals(current.getId(), mapping.getId());
            if (sameTarget || sameId) {
                mapping.setId(current.getId());
                mappings.set(i, mapping);
                sortData();
                rebuildMappingTabs(mapping.getServerId(), mapping.getId(), mapping.getLocalDirectory());
                return;
            }
        }

        mappings.add(mapping);
        sortData();
        rebuildMappingTabs(mapping.getServerId(), mapping.getId(), mapping.getLocalDirectory());
    }

    private void exportToJson() {
        List<ServerConfig> exportableServers = copyServers(servers);
        int selectedIndex = chooseExportScope(exportableServers);
        if (selectedIndex < 0) {
            return;
        }

        JFileChooser chooser = createJsonChooser();
        chooser.setDialogTitle("导出服务器部署配置");
        ServerConfig selectedServer = selectedIndex == 0 ? null : exportableServers.get(selectedIndex - 1);
        chooser.setSelectedFile(new java.io.File(buildExportFileName(selectedServer)));
        if (chooser.showSaveDialog(rootPanel) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        try {
            ServerDeploySettingsState state = buildExportState(selectedServer);
            Path file = ensureJsonExtension(chooser.getSelectedFile().toPath());
            Files.writeString(file, settingsJsonService.toJson(state), StandardCharsets.UTF_8);
            String message = selectedServer == null
                    ? "全部配置已导出到：\n" + file
                    : "服务器“" + safe(selectedServer.getName()) + "”的映射配置已导出到：\n" + file;
            Messages.showInfoMessage(rootPanel, message, "服务器部署");
        } catch (IOException exception) {
            Messages.showErrorDialog(rootPanel, exception.getMessage(), "导出失败");
        }
    }

    private void importFromJson() {
        JFileChooser chooser = createJsonChooser();
        chooser.setDialogTitle("导入服务器部署配置");
        if (chooser.showOpenDialog(rootPanel) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        try {
            String json = Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);
            ServerDeploySettingsState imported = settingsService.sanitize(settingsJsonService.fromJson(json));
            ImportResult result = importIncrementallyByServer(imported);
            if (result.isEmpty()) {
                Messages.showInfoMessage(rootPanel, "JSON 中没有可导入的服务器配置。", "服务器部署");
                return;
            }
            Messages.showInfoMessage(rootPanel, buildImportMessage(result), "服务器部署");
        } catch (Exception exception) {
            Messages.showErrorDialog(rootPanel, exception.getMessage(), "导入失败");
        }
    }

    private int chooseExportScope(List<ServerConfig> exportableServers) {
        List<String> options = new ArrayList<>();
        options.add("全部配置");
        for (ServerConfig server : exportableServers) {
            options.add("仅导出服务器：" + safe(server.getName()));
        }
        return Messages.showDialog(
                rootPanel,
                "请选择导出范围。",
                "导出 JSON",
                options.toArray(String[]::new),
                0,
                null
        );
    }

    private String buildExportFileName(ServerConfig server) {
        if (server == null) {
            return "server-deploy-config.json";
        }
        String serverName = safe(server.getName()).trim();
        if (serverName.isEmpty()) {
            return "server-deploy-server-mappings.json";
        }
        String sanitized = serverName.replaceAll("[\\\\/:*?\"<>|]+", "-").trim();
        return (sanitized.isEmpty() ? "server-deploy-server" : sanitized) + "-mappings.json";
    }

    private ServerDeploySettingsState buildExportState(ServerConfig selectedServer) {
        ServerDeploySettingsState state = new ServerDeploySettingsState();
        if (selectedServer == null) {
            state.setDefaultShellCommand(getDefaultShellCommand());
            state.setDefaultUploadFileName(getDefaultUploadFileName());
            state.setShellCommandCandidates(getShellCommandCandidates());
            state.setServers(getServers());
            state.setMappings(getMappings());
            return state;
        }

        state.setServers(List.of(selectedServer.copy()));
        state.setMappings(mappings.stream()
                .filter(mapping -> Objects.equals(mapping.getServerId(), selectedServer.getId()))
                .map(DirectoryMapping::copy)
                .collect(Collectors.toList()));
        return state;
    }

    private ImportResult importIncrementallyByServer(ServerDeploySettingsState imported) {
        List<ServerConfig> importedServers = copyServers(imported.getServers());
        List<DirectoryMapping> importedMappings = copyMappings(imported.getMappings());
        if (importedServers.isEmpty()) {
            return ImportResult.empty();
        }

        int addedServers = 0;
        int updatedServers = 0;
        int addedMappings = 0;
        int updatedMappings = 0;
        String preferredMappingId = getSelectedMappingId();
        String preferredLocalDirectory = getSelectedLocalDirectory();

        for (ServerConfig importedServer : importedServers) {
            importedServer.ensureIdentity();
            String originalServerId = importedServer.getId();
            ServerConfig existingServer = findMatchingServerForImport(importedServer);
            if (existingServer != null) {
                String existingServerId = existingServer.getId();
                boolean lastUsed = existingServer.isLastUsed();
                importedServer.setId(existingServerId);
                importedServer.setLastUsed(lastUsed);
                replaceServer(existingServerId, importedServer);
                updatedServers++;
            } else {
                importedServer.setLastUsed(false);
                servers.add(importedServer);
                addedServers++;
            }

            for (DirectoryMapping importedMapping : importedMappings) {
                if (!Objects.equals(importedMapping.getServerId(), originalServerId)) {
                    continue;
                }
                DirectoryMapping mappingToImport = importedMapping.copy();
                mappingToImport.setId(null);
                mappingToImport.setServerId(importedServer.getId());
                boolean updated = mergeImportedMapping(mappingToImport);
                if (updated) {
                    updatedMappings++;
                } else {
                    addedMappings++;
                }
                preferredLocalDirectory = mappingToImport.getLocalDirectory();
            }
        }

        normalizeLastUsedServers();
        sortData();
        serverTableModel.fireTableDataChanged();
        configureServerColumns();
        rebuildMappingTabs(getSelectedMappingServerId(), preferredMappingId, preferredLocalDirectory);
        return new ImportResult(addedServers, updatedServers, addedMappings, updatedMappings);
    }

    private ServerConfig findMatchingServerForImport(ServerConfig importedServer) {
        if (!isBlank(importedServer.getId())) {
            ServerConfig byId = findServerById(importedServer.getId());
            if (byId != null) {
                return byId;
            }
        }
        if (!isBlank(importedServer.getName())) {
            for (ServerConfig server : servers) {
                if (Objects.equals(safe(server.getName()), safe(importedServer.getName()))) {
                    return server;
                }
            }
        }
        for (ServerConfig server : servers) {
            if (Objects.equals(safe(server.getHost()), safe(importedServer.getHost()))
                    && server.getPort() == importedServer.getPort()
                    && Objects.equals(safe(server.getUsername()), safe(importedServer.getUsername()))) {
                return server;
            }
        }
        return null;
    }

    private void replaceServer(String serverId, ServerConfig replacement) {
        for (int i = 0; i < servers.size(); i++) {
            if (Objects.equals(servers.get(i).getId(), serverId)) {
                servers.set(i, replacement);
                return;
            }
        }
    }

    private boolean mergeImportedMapping(DirectoryMapping mapping) {
        mapping.ensureIdentity();
        String normalizedLocalDirectory = ServerDeploySettingsService.normalizeLocalDirectory(mapping.getLocalDirectory());
        for (int i = 0; i < mappings.size(); i++) {
            DirectoryMapping current = mappings.get(i);
            boolean sameTarget = Objects.equals(current.getServerId(), mapping.getServerId())
                    && Objects.equals(ServerDeploySettingsService.normalizeLocalDirectory(current.getLocalDirectory()), normalizedLocalDirectory);
            if (sameTarget) {
                mapping.setId(current.getId());
                mappings.set(i, mapping);
                return true;
            }
        }
        mappings.add(mapping);
        return false;
    }

    private void normalizeLastUsedServers() {
        if (servers.isEmpty()) {
            return;
        }
        boolean keepFirst = true;
        boolean hasLastUsed = false;
        for (ServerConfig server : servers) {
            if (server.isLastUsed()) {
                if (keepFirst) {
                    keepFirst = false;
                    hasLastUsed = true;
                } else {
                    server.setLastUsed(false);
                }
            }
        }
        if (!hasLastUsed) {
            servers.get(0).setLastUsed(true);
        }
    }

    private record ImportResult(int addedServers, int updatedServers, int addedMappings, int updatedMappings) {
        private static ImportResult empty() {
            return new ImportResult(0, 0, 0, 0);
        }

        private boolean isEmpty() {
            return addedServers == 0 && updatedServers == 0 && addedMappings == 0 && updatedMappings == 0;
        }
    }

    private String buildImportMessage(ImportResult result) {
        return "已按服务器增量导入配置。\n"
                + "新增服务器：" + result.addedServers() + "\n"
                + "更新服务器：" + result.updatedServers() + "\n"
                + "新增映射：" + result.addedMappings() + "\n"
                + "更新映射：" + result.updatedMappings() + "\n"
                + "全局命令与默认上传文件名未覆盖，请点击“应用”或“确定”保存。";
    }
    private JFileChooser createJsonChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件", "json"));
        String projectDirectory = determineDefaultProjectDirectory();
        if (!isBlank(projectDirectory)) {
            chooser.setCurrentDirectory(new java.io.File(projectDirectory));
        }
        return chooser;
    }

    private Path ensureJsonExtension(Path path) {
        String fileName = path.getFileName() == null ? "server-deploy-config.json" : path.getFileName().toString();
        return fileName.endsWith(".json") ? path : path.resolveSibling(fileName + ".json");
    }

    private String determineDefaultProjectDirectory() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
            if (project.getBasePath() != null && !project.getBasePath().isBlank()) {
                return project.getBasePath();
            }
        }
        return System.getProperty("user.home");
    }

    private void sortData() {
        servers.sort(Comparator.comparing(server -> safe(server.getName())));
        mappings.sort(Comparator.comparing((DirectoryMapping mapping) -> safe(mapping.getLocalDirectory()))
                .thenComparing(mapping -> serverName(mapping.getServerId())));
    }

    private void rebuildMappingTabs(String preferredServerId, String preferredMappingId, String preferredLocalDirectory) {
        mappingTabbedPane.removeAll();
        mappingTreeViewsByServerId.clear();

        for (ServerConfig server : servers) {
            MappingTreeView treeView = createMappingTreeView();
            rebuildMappingTree(treeView, server.getId());
            JScrollPane scrollPane = new JScrollPane(treeView.tree());
            scrollPane.setPreferredSize(new Dimension(920, 290));

            JPanel tabPanel = new JPanel(new BorderLayout());
            tabPanel.setName(server.getId());
            tabPanel.add(scrollPane, BorderLayout.CENTER);

            mappingTreeViewsByServerId.put(server.getId(), treeView);
            mappingTabbedPane.addTab(safe(server.getName()), tabPanel);
        }

        if (mappingTabbedPane.getTabCount() == 0) {
            return;
        }

        selectMappingTab(resolvePreferredServerId(preferredServerId));
        restoreTreeSelection(preferredMappingId, preferredLocalDirectory);
    }

    private MappingTreeView createMappingTreeView() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("目录映射");
        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(26);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new MappingTreeCellRenderer());
        return new MappingTreeView(root, model, tree);
    }

    private void rebuildMappingTree(MappingTreeView treeView, String serverId) {
        treeView.root().removeAllChildren();

        Map<Path, DefaultMutableTreeNode> folderNodes = new LinkedHashMap<>();
        for (DirectoryMapping mapping : mappings) {
            if (!Objects.equals(mapping.getServerId(), serverId)) {
                continue;
            }
            Path localPath = Path.of(mapping.getLocalDirectory());
            DefaultMutableTreeNode parent = treeView.root();
            Path currentPath = localPath.getRoot();
            if (currentPath != null) {
                parent = getOrCreateFolderNode(folderNodes, treeView.root(), currentPath, currentPath.toString());
            }
            for (int i = 0; i < localPath.getNameCount(); i++) {
                Path segment = localPath.getName(i);
                currentPath = currentPath == null ? Path.of(segment.toString()) : currentPath.resolve(segment.toString());
                parent = getOrCreateFolderNode(folderNodes, parent, currentPath, segment.toString());
            }
            parent.add(new DefaultMutableTreeNode(new MappingTreeNode(mapping.copy(), serverName(mapping.getServerId()))));
        }

        treeView.model().reload();
        expandMappingTree(treeView.tree());
    }
    private DefaultMutableTreeNode getOrCreateFolderNode(
            Map<Path, DefaultMutableTreeNode> folderNodes,
            DefaultMutableTreeNode parent,
            Path path,
            String label
    ) {
        DefaultMutableTreeNode existing = folderNodes.get(path);
        if (existing != null) {
            return existing;
        }
        DefaultMutableTreeNode created = new DefaultMutableTreeNode(new FolderTreeNode(path.toString(), label));
        parent.add(created);
        folderNodes.put(path, created);
        return created;
    }

    private void expandMappingTree(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    private void restoreTreeSelection(String preferredMappingId, String preferredLocalDirectory) {
        JTree selectedTree = getSelectedMappingTree();
        if (selectedTree == null) {
            return;
        }

        TreePath selection = null;
        if (!isBlank(preferredMappingId)) {
            selection = findTreePath(userObject -> userObject instanceof MappingTreeNode mappingNode
                    && Objects.equals(mappingNode.mapping().getId(), preferredMappingId));
        }
        if (selection == null && !isBlank(preferredLocalDirectory)) {
            selection = findTreePath(userObject -> userObject instanceof FolderTreeNode folderNode
                    && Objects.equals(folderNode.localDirectory(), preferredLocalDirectory));
        }
        if (selection == null && selectedTree.getRowCount() > 0) {
            selection = selectedTree.getPathForRow(0);
        }
        if (selection != null) {
            selectedTree.setSelectionPath(selection);
            selectedTree.scrollPathToVisible(selection);
        }
    }

    private TreePath findTreePath(Predicate<Object> predicate) {
        MappingTreeView treeView = getSelectedMappingTreeView();
        if (treeView == null) {
            return null;
        }
        Enumeration<?> enumeration = treeView.root().depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
            Object userObject = node.getUserObject();
            if (predicate.test(userObject)) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }

    private String resolvePreferredServerId(String preferredServerId) {
        if (!isBlank(preferredServerId) && mappingTreeViewsByServerId.containsKey(preferredServerId)) {
            return preferredServerId;
        }
        ServerConfig lastUsedServer = servers.stream().filter(ServerConfig::isLastUsed).findFirst().orElse(null);
        if (lastUsedServer != null && mappingTreeViewsByServerId.containsKey(lastUsedServer.getId())) {
            return lastUsedServer.getId();
        }
        return servers.isEmpty() ? null : servers.get(0).getId();
    }

    private void selectMappingTab(String serverId) {
        if (isBlank(serverId)) {
            return;
        }
        for (int i = 0; i < mappingTabbedPane.getTabCount(); i++) {
            Component component = mappingTabbedPane.getComponentAt(i);
            if (component instanceof JPanel panel && Objects.equals(panel.getName(), serverId)) {
                mappingTabbedPane.setSelectedIndex(i);
                return;
            }
        }
    }

    private MappingTreeView getSelectedMappingTreeView() {
        String selectedServerId = getSelectedMappingServerId();
        if (isBlank(selectedServerId)) {
            return null;
        }
        return mappingTreeViewsByServerId.get(selectedServerId);
    }

    private JTree getSelectedMappingTree() {
        MappingTreeView treeView = getSelectedMappingTreeView();
        return treeView == null ? null : treeView.tree();
    }

    private DefaultMutableTreeNode getSelectedTreeNode() {
        JTree selectedTree = getSelectedMappingTree();
        if (selectedTree == null) {
            return null;
        }
        TreePath selectionPath = selectedTree.getSelectionPath();
        if (selectionPath == null) {
            return null;
        }
        Object node = selectionPath.getLastPathComponent();
        return node instanceof DefaultMutableTreeNode treeNode ? treeNode : null;
    }

    private DirectoryMapping getSelectedMapping() {
        DefaultMutableTreeNode selectedNode = getSelectedTreeNode();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof MappingTreeNode mappingNode)) {
            return null;
        }
        return mappingNode.mapping();
    }

    private String getSelectedMappingId() {
        DirectoryMapping selectedMapping = getSelectedMapping();
        return selectedMapping == null ? null : selectedMapping.getId();
    }

    private String getSelectedMappingServerId() {
        Component selectedComponent = mappingTabbedPane.getSelectedComponent();
        if (selectedComponent instanceof JPanel panel) {
            return panel.getName();
        }
        return null;
    }

    private String getSelectedLocalDirectory() {
        DefaultMutableTreeNode selectedNode = getSelectedTreeNode();
        if (selectedNode == null) {
            return null;
        }
        Object userObject = selectedNode.getUserObject();
        if (userObject instanceof FolderTreeNode folderNode) {
            return folderNode.localDirectory();
        }
        if (userObject instanceof MappingTreeNode mappingNode) {
            return mappingNode.mapping().getLocalDirectory();
        }
        return null;
    }

    private String serverName(String serverId) {
        return servers.stream()
                .filter(server -> Objects.equals(server.getId(), serverId))
                .map(ServerConfig::getName)
                .findFirst()
                .orElse("");
    }

    private List<ServerConfig> copyServers(List<ServerConfig> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream().map(ServerConfig::copy).collect(Collectors.toList());
    }

    private List<DirectoryMapping> copyMappings(List<DirectoryMapping> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream().map(DirectoryMapping::copy).collect(Collectors.toList());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    private class ServerTableModel extends AbstractTableModel {

        private final String[] columns = {"名称", "主机", "端口", "用户名", "默认远程目录"};

        @Override
        public int getRowCount() {
            return servers.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ServerConfig server = servers.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> server.getName();
                case 1 -> server.getHost();
                case 2 -> server.getPort();
                case 3 -> server.getUsername();
                case 4 -> server.getDefaultDirectory();
                default -> "";
            };
        }
    }

    private class MappingTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus
        ) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (!(value instanceof DefaultMutableTreeNode node)) {
                return this;
            }

            Object userObject = node.getUserObject();
            if (userObject instanceof FolderTreeNode folderNode) {
                setText(folderNode.label());
                setToolTipText(folderNode.localDirectory());
            } else if (userObject instanceof MappingTreeNode mappingNode) {
                setText("[" + safe(mappingNode.serverName()) + "] -> " + mappingNode.mapping().getRemoteDirectory());
                setToolTipText(mappingNode.mapping().getLocalDirectory() + " -> " + mappingNode.mapping().getRemoteDirectory());
            } else {
                setToolTipText(null);
            }
            return this;
        }
    }

    private record FolderTreeNode(String localDirectory, String label) {
    }

    private record MappingTreeNode(DirectoryMapping mapping, String serverName) {
    }

    private record MappingTreeView(DefaultMutableTreeNode root, DefaultTreeModel model, JTree tree) {
    }
}
