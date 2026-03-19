package com.akatsugi.serverdeploy.idea.settings;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.service.SettingsJsonService;
import com.akatsugi.serverdeploy.idea.ui.MappingEditDialog;
import com.akatsugi.serverdeploy.idea.ui.ServerEditDialog;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ServerDeploySettingsPanel {

    private final JPanel rootPanel = new JPanel(new BorderLayout(0, 12));
    private final List<ServerConfig> servers = new ArrayList<>();
    private final List<DirectoryMapping> mappings = new ArrayList<>();
    private final ServerTableModel serverTableModel = new ServerTableModel();
    private final MappingTableModel mappingTableModel = new MappingTableModel();
    private final JTable serverTable = new JTable(serverTableModel);
    private final JTable mappingTable = new JTable(mappingTableModel);
    private final ServerDeploySettingsService settingsService = ServerDeploySettingsService.getInstance();
    private final SettingsJsonService settingsJsonService = new SettingsJsonService();

    public ServerDeploySettingsPanel() {
        configureTable(serverTable, 5);
        configureTable(mappingTable, 8);

        JScrollPane serverScrollPane = new JScrollPane(serverTable);
        JScrollPane mappingScrollPane = new JScrollPane(mappingTable);
        serverScrollPane.setPreferredSize(new Dimension(920, 210));
        mappingScrollPane.setPreferredSize(new Dimension(920, 290));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createServerPanel(serverScrollPane),
                createMappingPanel(mappingScrollPane));
        splitPane.setResizeWeight(0.42);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(8);
        splitPane.setOneTouchExpandable(true);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.42));

        rootPanel.add(createToolbar(), BorderLayout.NORTH);
        rootPanel.add(splitPane, BorderLayout.CENTER);

        configureServerColumns();
        configureMappingColumns();
    }

    public JComponent getComponent() {
        return rootPanel;
    }

    public void setData(List<ServerConfig> serverValues, List<DirectoryMapping> mappingValues) {
        servers.clear();
        mappings.clear();
        servers.addAll(copyServers(serverValues));
        mappings.addAll(copyMappings(mappingValues));
        sortData();
        serverTableModel.fireTableDataChanged();
        mappingTableModel.fireTableDataChanged();
        configureServerColumns();
        configureMappingColumns();
    }

    public List<ServerConfig> getServers() {
        return copyServers(servers);
    }

    public List<DirectoryMapping> getMappings() {
        return copyMappings(mappings);
    }

    public boolean isModified(List<ServerConfig> originalServers, List<DirectoryMapping> originalMappings) {
        return !copyServers(originalServers).equals(copyServers(servers))
                || !copyMappings(originalMappings).equals(copyMappings(mappings));
    }

    public void validateState(List<ServerConfig> serverValues, List<DirectoryMapping> mappingValues) throws ConfigurationException {
        for (ServerConfig server : serverValues) {
            if (isBlank(server.getName()) || isBlank(server.getHost()) || isBlank(server.getUsername())
                    || isBlank(server.getPassword()) || isBlank(server.getDefaultDirectory())) {
                throw new ConfigurationException("\u670d\u52a1\u5668\u914d\u7f6e\u9879\u4e0d\u80fd\u4e3a\u7a7a\u3002");
            }
        }
        for (DirectoryMapping mapping : mappingValues) {
            if (isBlank(mapping.getServerId()) || isBlank(mapping.getLocalDirectory()) || isBlank(mapping.getRemoteDirectory())) {
                throw new ConfigurationException("\u6620\u5c04\u914d\u7f6e\u9879\u4e0d\u80fd\u4e3a\u7a7a\u3002");
            }
        }
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton importButton = new JButton("\u5bfc\u5165 JSON");
        JButton exportButton = new JButton("\u5bfc\u51fa JSON");
        importButton.addActionListener(event -> importFromJson());
        exportButton.addActionListener(event -> exportToJson());
        panel.add(importButton);
        panel.add(exportButton);
        return panel;
    }

    private JPanel createServerPanel(JScrollPane scrollPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("\u670d\u52a1\u5668\u5217\u8868"));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(createServerButtons(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createMappingPanel(JScrollPane scrollPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("\u76ee\u5f55\u6620\u5c04"));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(createMappingButtons(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createServerButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("\u65b0\u589e");
        JButton editButton = new JButton("\u7f16\u8f91");
        JButton removeButton = new JButton("\u5220\u9664");
        addButton.addActionListener(event -> addServer());
        editButton.addActionListener(event -> editServer());
        removeButton.addActionListener(event -> removeServer());
        panel.add(addButton);
        panel.add(editButton);
        panel.add(removeButton);
        return panel;
    }

    private JPanel createMappingButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("\u65b0\u589e");
        JButton editButton = new JButton("\u7f16\u8f91");
        JButton removeButton = new JButton("\u5220\u9664");
        addButton.addActionListener(event -> addMapping());
        editButton.addActionListener(event -> editMapping());
        removeButton.addActionListener(event -> removeMapping());
        panel.add(addButton);
        panel.add(editButton);
        panel.add(removeButton);
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

    private void configureMappingColumns() {
        TableColumnModel columns = mappingTable.getColumnModel();
        if (columns.getColumnCount() < 3) {
            return;
        }
        mappingTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        columns.getColumn(0).setPreferredWidth(180);
        columns.getColumn(1).setPreferredWidth(420);
        columns.getColumn(2).setPreferredWidth(320);
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
    }

    private void editServer() {
        int row = serverTable.getSelectedRow();
        if (row < 0) {
            return;
        }
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
        mappingTableModel.fireTableDataChanged();
    }

    private void removeServer() {
        int row = serverTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = serverTable.convertRowIndexToModel(row);
        ServerConfig removed = servers.remove(modelRow);
        mappings.removeIf(mapping -> Objects.equals(mapping.getServerId(), removed.getId()));
        if (!servers.isEmpty() && servers.stream().noneMatch(ServerConfig::isLastUsed)) {
            servers.get(0).setLastUsed(true);
        }
        serverTableModel.fireTableDataChanged();
        mappingTableModel.fireTableDataChanged();
    }

    private void addMapping() {
        if (servers.isEmpty()) {
            Messages.showInfoMessage(rootPanel, "\u8bf7\u5148\u65b0\u589e\u670d\u52a1\u5668\uff0c\u518d\u521b\u5efa\u76ee\u5f55\u6620\u5c04\u3002", "\u670d\u52a1\u5668\u90e8\u7f72");
            return;
        }
        String projectDirectory = determineDefaultProjectDirectory();
        MappingEditDialog dialog = new MappingEditDialog(servers, null, projectDirectory, projectDirectory);
        if (!dialog.showAndGet()) {
            return;
        }
        upsertMapping(dialog.getDirectoryMapping(), null);
    }

    private void editMapping() {
        int row = mappingTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = mappingTable.convertRowIndexToModel(row);
        DirectoryMapping existing = mappings.get(modelRow).copy();
        MappingEditDialog dialog = new MappingEditDialog(servers, existing, existing.getLocalDirectory(), determineDefaultProjectDirectory());
        if (!dialog.showAndGet()) {
            return;
        }
        upsertMapping(dialog.getDirectoryMapping(), existing.getId());
    }

    private void removeMapping() {
        int row = mappingTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = mappingTable.convertRowIndexToModel(row);
        mappings.remove(modelRow);
        mappingTableModel.fireTableDataChanged();
    }

    private void upsertMapping(DirectoryMapping mapping, String existingId) {
        mapping.ensureIdentity();
        if (existingId != null) {
            mapping.setId(existingId);
        }

        for (int i = 0; i < mappings.size(); i++) {
            DirectoryMapping current = mappings.get(i);
            boolean sameTarget = Objects.equals(current.getServerId(), mapping.getServerId())
                    && Objects.equals(ServerDeploySettingsService.normalizeLocalDirectory(current.getLocalDirectory()), mapping.getLocalDirectory());
            boolean sameId = Objects.equals(current.getId(), mapping.getId());
            if (sameTarget || sameId) {
                mapping.setId(current.getId());
                mappings.set(i, mapping);
                sortData();
                mappingTableModel.fireTableDataChanged();
                return;
            }
        }

        mappings.add(mapping);
        sortData();
        mappingTableModel.fireTableDataChanged();
    }

    private void exportToJson() {
        JFileChooser chooser = createJsonChooser();
        chooser.setDialogTitle("\u5bfc\u51fa\u670d\u52a1\u5668\u90e8\u7f72\u914d\u7f6e");
        chooser.setSelectedFile(new java.io.File("server-deploy-config.json"));
        if (chooser.showSaveDialog(rootPanel) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        ServerDeploySettingsState state = new ServerDeploySettingsState();
        state.setServers(getServers());
        state.setMappings(getMappings());

        try {
            Path file = ensureJsonExtension(chooser.getSelectedFile().toPath());
            Files.writeString(file, settingsJsonService.toJson(state), StandardCharsets.UTF_8);
            Messages.showInfoMessage(rootPanel, "\u914d\u7f6e\u5df2\u5bfc\u51fa\u5230\uff1a\n" + file, "\u670d\u52a1\u5668\u90e8\u7f72");
        } catch (IOException exception) {
            Messages.showErrorDialog(rootPanel, exception.getMessage(), "\u5bfc\u51fa\u5931\u8d25");
        }
    }

    private void importFromJson() {
        JFileChooser chooser = createJsonChooser();
        chooser.setDialogTitle("\u5bfc\u5165\u670d\u52a1\u5668\u90e8\u7f72\u914d\u7f6e");
        if (chooser.showOpenDialog(rootPanel) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        try {
            String json = Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);
            ServerDeploySettingsState imported = settingsService.sanitize(settingsJsonService.fromJson(json));
            setData(imported.getServers(), imported.getMappings());
            Messages.showInfoMessage(rootPanel, "\u914d\u7f6e\u5df2\u5bfc\u5165\uff0c\u8bf7\u70b9\u51fb\u201c\u5e94\u7528\u201d\u6216\u201c\u786e\u5b9a\u201d\u4fdd\u5b58\u3002", "\u670d\u52a1\u5668\u90e8\u7f72");
        } catch (Exception exception) {
            Messages.showErrorDialog(rootPanel, exception.getMessage(), "\u5bfc\u5165\u5931\u8d25");
        }
    }

    private JFileChooser createJsonChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON \u6587\u4ef6", "json"));
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
        mappings.sort(Comparator.comparing((DirectoryMapping mapping) -> serverName(mapping.getServerId()))
                .thenComparing(mapping -> safe(mapping.getLocalDirectory())));
    }

    private String serverName(String serverId) {
        return servers.stream()
                .filter(server -> Objects.equals(server.getId(), serverId))
                .map(ServerConfig::getName)
                .findFirst()
                .orElse("");
    }

    private List<ServerConfig> copyServers(List<ServerConfig> values) {
        return values.stream().map(ServerConfig::copy).collect(Collectors.toList());
    }

    private List<DirectoryMapping> copyMappings(List<DirectoryMapping> values) {
        return values.stream().map(DirectoryMapping::copy).collect(Collectors.toList());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private class ServerTableModel extends AbstractTableModel {

        private final String[] columns = {"\u540d\u79f0", "\u4e3b\u673a", "\u7aef\u53e3", "\u7528\u6237\u540d", "\u9ed8\u8ba4\u8fdc\u7a0b\u76ee\u5f55"};

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

    private class MappingTableModel extends AbstractTableModel {

        private final String[] columns = {"\u670d\u52a1\u5668", "\u672c\u5730\u76ee\u5f55", "\u8fdc\u7a0b\u76ee\u5f55"};

        @Override
        public int getRowCount() {
            return mappings.size();
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
            DirectoryMapping mapping = mappings.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> serverName(mapping.getServerId());
                case 1 -> mapping.getLocalDirectory();
                case 2 -> mapping.getRemoteDirectory();
                default -> "";
            };
        }
    }
}