package com.akatsugi.serverdeploy.ui;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileTreeItem extends TreeItem<Path> {

    private boolean childrenLoaded;

    public FileTreeItem(Path value) {
        super(value);
    }

    @Override
    public ObservableList<TreeItem<Path>> getChildren() {
        if (!childrenLoaded) {
            childrenLoaded = true;
            super.getChildren().setAll(loadChildren(getValue()));
        }
        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        Path value = getValue();
        return value == null || !Files.isDirectory(value);
    }

    public void refresh() {
        childrenLoaded = false;
        super.getChildren().clear();
        getChildren();
    }

    private List<TreeItem<Path>> loadChildren(Path directory) {
        List<TreeItem<Path>> items = new ArrayList<TreeItem<Path>>();
        if (directory == null || !Files.isDirectory(directory)) {
            return items;
        }

        List<Path> children = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                children.add(child);
            }
        } catch (IOException e) {
            return items;
        }

        Collections.sort(children, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                boolean leftDir = Files.isDirectory(left);
                boolean rightDir = Files.isDirectory(right);
                if (leftDir != rightDir) {
                    return leftDir ? -1 : 1;
                }
                return left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString());
            }
        });

        for (Path child : children) {
            items.add(new FileTreeItem(child));
        }
        return items;
    }
}
