package com.akatsugi.serverdeploy.idea.ui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class SimpleDocumentListener implements DocumentListener {

    private final Runnable task;

    private SimpleDocumentListener(Runnable task) {
        this.task = task;
    }

    public static DocumentListener of(Runnable task) {
        return new SimpleDocumentListener(task);
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
        task.run();
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
        task.run();
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
        task.run();
    }
}
