package app.draft.ui;

import app.draft.model.DraftSet;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SetAutocompleteComboBox extends JComboBox<DraftSet> {
    private List<DraftSet> sets = List.of();
    private boolean updating;

    SetAutocompleteComboBox() {
        setEditable(true);
        JTextComponent editor = (JTextComponent) getEditor().getEditorComponent();
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filterLater(); }
            @Override public void removeUpdate(DocumentEvent event) { filterLater(); }
            @Override public void changedUpdate(DocumentEvent event) { filterLater(); }
        });
    }

    void setSets(List<DraftSet> sets) {
        this.sets = List.copyOf(sets == null ? List.of() : sets);
        replaceModel(this.sets);
    }

    DraftSet findByCode(String code) {
        if (code == null) return null;
        return sets.stream()
                .filter(set -> set.code().equalsIgnoreCase(code))
                .findFirst().orElse(null);
    }

    boolean isUpdatingModel() {
        return updating;
    }

    private void filterLater() {
        if (!updating) SwingUtilities.invokeLater(this::filter);
    }

    private void filter() {
        if (updating) return;
        Object item = getEditor().getItem();
        String query = item == null ? "" : item.toString();
        String normalized = query.toLowerCase(Locale.ROOT).strip();
        List<DraftSet> matches = normalized.isEmpty()
                ? sets
                : sets.stream()
                .filter(set -> set.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || set.code().toLowerCase(Locale.ROOT).contains(normalized))
                .limit(40)
                .toList();
        updating = true;
        try {
            setModel(model(matches));
            getEditor().setItem(query);
        } finally {
            updating = false;
        }
        if (!matches.isEmpty() && isShowing()) showPopup();
    }

    private void replaceModel(List<DraftSet> values) {
        updating = true;
        try {
            setModel(model(values));
        } finally {
            updating = false;
        }
    }

    private DefaultComboBoxModel<DraftSet> model(List<DraftSet> values) {
        return new DefaultComboBoxModel<>(
                new ArrayList<>(values).toArray(DraftSet[]::new));
    }
}
