/*
 ** This file is part of Filius, a network construction and simulation software.
 ** 
 ** Originally created at the University of Siegen, Institute "Didactics of
 ** Informatics and E-Learning" by a students' project group:
 **     members (2006-2007): 
 **         André Asschoff, Johannes Bade, Carsten Dittich, Thomas Gerding,
 **         Nadja Haßler, Ernst Johannes Klebert, Michell Weyer
 **     supervisors:
 **         Stefan Freischlad (maintainer until 2009), Peer Stechert
 ** Project is maintained since 2010 by Christian Eibl <filius@c.fameibl.de>
 **         and Stefan Freischlad
 ** Filius is free software: you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License as published by
 ** the Free Software Foundation, either version 2 of the License, or
 ** (at your option) version 3.
 ** 
 ** Filius is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied
 ** warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 ** PURPOSE. See the GNU General Public License for more details.
 ** 
 ** You should have received a copy of the GNU General Public License
 ** along with Filius.  If not, see <http://www.gnu.org/licenses/>.
 */
package filius.gui.netzwerksicht;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.hardware.knoten.Knoten;
import filius.rahmenprogramm.EingabenUeberpruefung;
import filius.rahmenprogramm.I18n;
import filius.software.system.InternetKnotenBetriebssystem;
import filius.software.vermittlungsschicht.Weiterleitungstabelle;

@SuppressWarnings("serial")
public class JWeiterleitungsTabelle extends JTable implements I18n {
    private static Logger LOG = LoggerFactory.getLogger(JWeiterleitungsTabelle.class);

    private LinkedList<Boolean> editableRows = null;
    private JVermittlungsrechnerKonfiguration konfig;
    private boolean standardEintraegeAnzeigen = true;
    boolean notPersistedRowVisible;

    public JWeiterleitungsTabelle(JVermittlungsrechnerKonfiguration konfig) {
        super(new DefaultTableModel(1, 4));
        LOG.trace("INVOKED-2 (" + this.hashCode() + ") " + getClass() + ", constr: JWeiterleitungsTabelle(" + konfig
                + ")");

        this.konfig = konfig;

        this.setRowHeight(20);
        this.setRowMargin(2);
        this.setDragEnabled(false);
        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        final JWeiterleitungsTabelle tabelle = this;
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == 3) {
                    JPopupMenu pmRechteMausTaste = new JPopupMenu();
                    final JMenuItem miLoeschen = new JMenuItem(messages.getString("jweiterleitungstabelle_msg1"));
                    miLoeschen.setActionCommand("del");

                    final JMenuItem miZeileHinzu = new JMenuItem(messages.getString("jweiterleitungstabelle_msg2"));
                    miZeileHinzu.setActionCommand("add");

                    ActionListener al = new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            if (e.getActionCommand().equals(miZeileHinzu.getActionCommand())) {
                                neuerEintrag();
                            } else if (e.getActionCommand().equals(miLoeschen.getActionCommand())) {
                                markiertenEintragLoeschen();
                            }
                        }
                    };

                    miZeileHinzu.addActionListener(al);
                    miLoeschen.addActionListener(al);
                    pmRechteMausTaste.add(miLoeschen);
                    pmRechteMausTaste.add(miZeileHinzu);
                    pmRechteMausTaste.show(tabelle, e.getX(), e.getY());
                }
            }
        });

        TableColumnModel tcm = getColumnModel();

        // Netzwerkziel, Netzwerkmaske, ZielIp, Schnittstelle

        for (int i = 0; i < getRoutingTabellenSpalten().length; i++) {
            tcm.getColumn(i).setHeaderValue(getRoutingTabellenSpalten()[i]);
        }

    }

    public void aenderungenAnnehmen() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + ", aenderungenAnnehmen()");
        Vector<Vector> tableData;

        if (getCellEditor() != null) {
            getCellEditor().stopCellEditing();
        }

        tableData = ((DefaultTableModel) getModel()).getDataVector();
        Weiterleitungstabelle tabelle = ((InternetKnotenBetriebssystem) ((Knoten) konfig.holeHardware())
                .getSystemSoftware()).getWeiterleitungstabelle();
        tabelle.reset();
        for (int i = 0; i < tableData.size(); i++) {
            if (notPersistedRowVisible && i == tableData.size() - 1) {
                String[] routingEintrag = extractAndValidateRowData(i);
                if (tabelle.validate(routingEintrag[0], routingEintrag[1], routingEintrag[2], routingEintrag[3])) {
                    notPersistedRowVisible = false;
                    tabelle.addManuellenEintrag(routingEintrag[0], routingEintrag[1], routingEintrag[2],
                            routingEintrag[3]);
                }
            } else if (isCellEditable(i, 1)) {
                String[] routingEintrag = extractAndValidateRowData(i);
                tabelle.addManuellenEintrag(routingEintrag[0], routingEintrag[1], routingEintrag[2], routingEintrag[3]);
            }
        }
        updateAttribute();
    }

    private String[] extractAndValidateRowData(int rowIdx) {
        String tmpString;
        Vector<Object> rowData = (Vector) ((DefaultTableModel) getModel()).getDataVector().elementAt(rowIdx);
        String[] routingEintrag = new String[rowData.size()];
        for (int j = 0; j < routingEintrag.length; j++) {
            tmpString = (String) rowData.elementAt(j);
            Pattern pattern = null;
            switch (j) {
            case 0:
                pattern = EingabenUeberpruefung.musterIpAdresse;
                break;
            case 1:
                pattern = EingabenUeberpruefung.musterSubNetz;
                break;
            case 2:
                pattern = EingabenUeberpruefung.musterIpAdresse;
                break;
            case 3:
                pattern = EingabenUeberpruefung.musterIpAdresse;
                break;
            }
            if (null != tmpString && EingabenUeberpruefung.isGueltig(tmpString, pattern)) {
                routingEintrag[j] = tmpString;
            } else {
                routingEintrag[j] = "";
            }
        }
        return routingEintrag;
    }

    public boolean isCellEditable(int row, int column) {
        if (editableRows == null) {
            return true;
        } else if (row >= editableRows.size()) {
            return true;
        } else {
            return editableRows.get(row).booleanValue();
        }
    }

    public TableCellEditor getCellEditor() {
        TableCellEditor editor;

        editor = super.getCellEditor();
        if (editor != null) {
            editor.addCellEditorListener(this);
        }

        return editor;
    }

    public void editingCanceled(ChangeEvent e) {
        super.editingCanceled(e);

        aenderungenAnnehmen();
    }

    public void editingStopped(ChangeEvent e) {
        super.editingStopped(e);

        aenderungenAnnehmen();
    }

    public void neuerEintrag() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + ", neuerEintrag()");
        aenderungenAnnehmen();
        if (!notPersistedRowVisible) {
            notPersistedRowVisible = true;

            Vector<String> eintrag = new Vector<String>();
            eintrag.add("");
            eintrag.add("");
            eintrag.add("");
            eintrag.add("");

            ((DefaultTableModel) getModel()).addRow(eintrag);
            editableRows.add(Boolean.TRUE);
        }

        setRowSelectionInterval(getModel().getRowCount() - 1, getModel().getRowCount() - 1);
    }

    public void markiertenEintragLoeschen() {
        if (getSelectedRow() > -1) {
            entferneEintrag(getSelectedRow());
            aenderungenAnnehmen();
        }
    }

    private void entferneEintrag(int row) {
        if (notPersistedRowVisible) {
            notPersistedRowVisible = row < getModel().getRowCount() - 1;
        }
        ((DefaultTableModel) getModel()).removeRow(row);
        editableRows.remove(row);
    }

    public TableCellRenderer getCellRenderer(int row, int column) {
        DefaultTableCellRenderer renderer;

        renderer = new DefaultTableCellRenderer();
        renderer.setEnabled(editableRows.get(row).booleanValue());

        return renderer;
    }

    private String[] getRoutingTabellenSpalten() {
        String[] cols;

        cols = new String[4];
        cols[0] = messages.getString("jweiterleitungstabelle_msg3");
        cols[1] = messages.getString("jweiterleitungstabelle_msg4");
        cols[2] = messages.getString("jweiterleitungstabelle_msg5");
        cols[3] = messages.getString("jweiterleitungstabelle_msg6");

        return cols;
    }

    public void setzeAlleEintraegeAnzeigen(boolean flag) {
        standardEintraegeAnzeigen = flag;
    }

    public void updateAttribute() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + ", updateAttribute()");
        ListIterator it, editableIt;
        String[][] data;
        Vector<String[]> tmpData;

        /* Weiterleitungstabelle aktualisieren */
        Weiterleitungstabelle tabelle = ((InternetKnotenBetriebssystem) ((Knoten) konfig.holeHardware())
                .getSystemSoftware()).getWeiterleitungstabelle();
        List<String[]> routingTabelle = tabelle.holeTabelle();
        Vector<Object> notPersistedRow = null;
        if (notPersistedRowVisible) {
            notPersistedRow = ((DefaultTableModel) getModel()).getDataVector().lastElement();
        }
        it = routingTabelle.listIterator();
        if (standardEintraegeAnzeigen) {
            editableRows = tabelle.holeManuelleEintraegeFlags();

            data = new String[routingTabelle.size()][4];
            for (int i = 0; it.hasNext(); i++) {
                data[i] = (String[]) it.next();
            }
        } else {
            editableIt = tabelle.holeManuelleEintraegeFlags().listIterator();
            tmpData = new Vector<String[]>();

            while (it.hasNext() && editableIt.hasNext()) {
                if (((Boolean) editableIt.next()).booleanValue()) {
                    tmpData.add((String[]) it.next());
                } else {
                    it.next();
                }
            }
            data = new String[tmpData.size()][4];
            editableRows = new LinkedList<Boolean>();

            for (int i = 0; i < data.length; i++) {
                data[i] = (String[]) tmpData.elementAt(i);
                editableRows.add(Boolean.TRUE);
            }
        }
        ((DefaultTableModel) getModel()).setDataVector(data, getRoutingTabellenSpalten());
        if (null != notPersistedRow) {
            editableRows.add(Boolean.TRUE);
            ((DefaultTableModel) getModel()).addRow(notPersistedRow);
        }
        ((DefaultTableModel) getModel()).fireTableDataChanged();
    }
}
