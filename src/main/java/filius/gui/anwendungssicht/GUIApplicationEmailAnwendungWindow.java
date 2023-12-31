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
package filius.gui.anwendungssicht;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.gui.JExtendedTable;
import filius.rahmenprogramm.EingabenUeberpruefung;
import filius.rahmenprogramm.ResourceUtil;
import filius.software.email.Email;
import filius.software.email.EmailAnwendung;
import filius.software.email.EmailKonto;
import filius.software.email.EmailUtils;

/**
 * Applikationsfenster fr den Email-Client
 * 
 * @author Thomas Gerding & Johannes Bade
 * 
 */
public class GUIApplicationEmailAnwendungWindow extends GUIApplicationWindow {
    private static Logger LOG = LoggerFactory.getLogger(GUIApplicationEmailAnwendungWindow.class);

    private enum ListMode {
        INBOX, OUTBOX, UNKNOWN
    };

    private static final long serialVersionUID = 1L;

    private JTabbedPane tabbedPane;
    private JPanel gesendetPanel, eingangPanel;
    private JScrollPane gesendetScroll, eingangScroll;
    private JEditorPane emailVorschau;
    private JButton buttonMailsAbholen, buttonMailVerfassen, buttonMailAntworten, buttonKonten, buttonEmailLoeschen;
    private JProgressBar progressBar;
    private Box middleBox;
    private DefaultTableModel posteingangModell = new DefaultTableModel(0, 2);
    private DefaultTableModel gesendeteModell = new DefaultTableModel(0, 2);
    private JTable posteingangTable, gesendeteTable;
    private JTextField tfName, tfEmailAdresse, tfPOP3Server, tfPOP3Port, tfSMTPServer, tfSMTPPort, tfBenutzername;
    private JPasswordField tfPasswort;

    private Email aktuelleMail = null;
    private int zeilenNummer;
    private int auswahlfuerloeschen;
    ListMode paa = ListMode.UNKNOWN;

    private JPanel progressPanel;
    private JPanel verfassenPanel;

    public GUIApplicationEmailAnwendungWindow(GUIDesktopPanel desktop, String appName) {
        super(desktop, appName);

        ((EmailAnwendung) holeAnwendung()).holePOP3Client().hinzuBeobachter(this);

        initialisiereKomponenten();

        laden();
        posteingangAktualisieren();
        gesendeteAktualisieren();
    }

    private void initialisiereKomponenten() {
        tabbedPane = new JTabbedPane();

        gesendetPanel = new JPanel(new BorderLayout());

        eingangPanel = new JPanel(new BorderLayout());

        Box gesendetBox = Box.createHorizontalBox();
        gesendetBox.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        Box eingangBox = Box.createHorizontalBox();
        eingangBox.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        Box vorschauBox = Box.createHorizontalBox();
        vorschauBox.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        posteingangTable = new JExtendedTable(posteingangModell, false);
        TableColumnModel tcm = posteingangTable.getColumnModel();
        tcm.getColumn(0).setHeaderValue(messages.getString("emailanwendung_msg1"));
        tcm.getColumn(1).setHeaderValue(messages.getString("emailanwendung_msg2"));
        eingangScroll = new JScrollPane(posteingangTable);

        posteingangTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent lse) {
                zeilenNummer = posteingangTable.getSelectedRow();
                auswahlfuerloeschen = zeilenNummer;
                paa = ListMode.INBOX;
                if (zeilenNummer != -1) {
                    Email tmpEmail = (Email) ((EmailAnwendung) holeAnwendung()).getEmpfangeneNachrichten()
                            .get(zeilenNummer);
                    emailVorschau.setContentType("text/plain");
                    emailVorschau.setText(tmpEmail.getText());
                    aktuelleMail = tmpEmail;
                    emailVorschau.updateUI();
                }
            }
        });

        eingangBox.add(eingangScroll);
        eingangPanel.add(eingangBox, BorderLayout.CENTER);

        gesendeteTable = new JExtendedTable(gesendeteModell, false);
        TableColumnModel tcmGesendet = gesendeteTable.getColumnModel();
        tcmGesendet.getColumn(0).setHeaderValue(messages.getString("emailanwendung_msg3"));
        tcmGesendet.getColumn(1).setHeaderValue(messages.getString("emailanwendung_msg2"));

        gesendeteTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent lse) {
                zeilenNummer = gesendeteTable.getSelectedRow();
                auswahlfuerloeschen = zeilenNummer;
                paa = ListMode.OUTBOX;
                if (zeilenNummer != -1) {
                    Email tmpEmail = (Email) ((EmailAnwendung) holeAnwendung()).getGesendeteNachrichten()
                            .get(zeilenNummer);
                    emailVorschau.setContentType("text/plain");
                    emailVorschau.setText(tmpEmail.getText());
                    aktuelleMail = tmpEmail;
                    emailVorschau.updateUI();

                }
            }
        });

        gesendetScroll = new JScrollPane(gesendeteTable);
        gesendetBox.add(gesendetScroll);
        gesendetPanel.add(gesendetBox, BorderLayout.CENTER);

        emailVorschau = new JEditorPane();
        emailVorschau.setBackground(new Color(255, 255, 255));
        emailVorschau.setContentType("text/html");
        emailVorschau.setEditable(false);
        emailVorschau.setText("<html><head><base href=\"file:bilder\"></head><body>" + "<img src=\"file:"
                + ResourceUtil.getResourceUrlEncodedPath("img/email_icon.png") + "\" align=\"top\">"
                + "<font face=arial>" + messages.getString("emailanwendung_msg4") + "!<br /></font>"
                + "</body></html>");
        JScrollPane vorschauScrollPane = new JScrollPane(emailVorschau);
        vorschauScrollPane.setPreferredSize(new Dimension(300, 200));
        vorschauBox.add(vorschauScrollPane);

        eingangPanel.add(vorschauBox, BorderLayout.SOUTH);

        tabbedPane.addTab(messages.getString("emailanwendung_msg5"),
                new ImageIcon(getClass().getResource("/gfx/desktop/email_ordner_posteingang.png")), eingangPanel);
        tabbedPane.addTab(messages.getString("emailanwendung_msg6"),
                new ImageIcon(getClass().getResource("/gfx/desktop/email_ordner_gesendet.png")), gesendetPanel);

        tabbedPane.setTabPlacement(JTabbedPane.LEFT);

        Box topBox = Box.createHorizontalBox();
        ImageIcon image = new ImageIcon(getClass().getResource("/gfx/desktop/email_emails_abholen.png"));
        buttonMailsAbholen = new JButton(image);
        image = new ImageIcon(getClass().getResource("/gfx/desktop/email_emails_abholen.gif"));
        buttonMailsAbholen.setRolloverIcon(image);
        buttonMailsAbholen.setFocusPainted(false);
        buttonMailsAbholen.setActionCommand("Abholen");
        buttonMailsAbholen.setToolTipText(messages.getString("emailanwendung_msg7"));

        topBox.add(buttonMailsAbholen);
        topBox.add(Box.createHorizontalStrut(5)); // Platz zw. urlFeld und
        // senden

        image = new ImageIcon(getClass().getResource("/gfx/desktop/email_email_verfassen.png"));
        buttonMailVerfassen = new JButton(image);
        /* Gif Animation fuer Hover Effekt */
        image = new ImageIcon(getClass().getResource("/gfx/desktop/email_email_verfassen.gif"));
        buttonMailVerfassen.setRolloverIcon(image);
        buttonMailVerfassen.setFocusPainted(false);
        buttonMailVerfassen.setActionCommand("Verfassen");
        buttonMailVerfassen.setToolTipText(messages.getString("emailanwendung_msg8"));
        /* ActionListener */
        ActionListener al = new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
                if (arg0.getActionCommand() == buttonMailVerfassen.getActionCommand()) {
                    emailVerfassen(null);
                }
                if (arg0.getActionCommand() == buttonMailAntworten.getActionCommand()) {
                    emailVerfassen(aktuelleMail);
                }

                if (arg0.getActionCommand() == buttonMailsAbholen.getActionCommand()) {
                    emailsAbholen();
                }

                if (arg0.getActionCommand() == buttonKonten.getActionCommand()) {
                    kontoVerwalten();
                    kontoAktualisieren();
                }
                if (arg0.getActionCommand() == buttonEmailLoeschen.getActionCommand()) {
                    emailLoeschen(auswahlfuerloeschen, paa);
                }
            }
        };
        buttonMailVerfassen.addActionListener(al);
        buttonMailsAbholen.addActionListener(al);
        topBox.add(buttonMailVerfassen);
        topBox.add(Box.createHorizontalStrut(5));

        image = new ImageIcon(getClass().getResource("/gfx/desktop/email_email_antworten.png"));
        buttonMailAntworten = new JButton(image);
        image = new ImageIcon(getClass().getResource("/gfx/desktop/email_email_antworten.gif"));
        buttonMailAntworten.setRolloverIcon(image);
        buttonMailAntworten.setFocusPainted(false);
        buttonMailAntworten.addActionListener(al);
        buttonMailAntworten.setActionCommand("antworten");
        buttonMailAntworten.setToolTipText(messages.getString("emailanwendung_msg9"));
        topBox.add(buttonMailAntworten);
        topBox.add(Box.createHorizontalStrut(5));

        image = new ImageIcon(getClass().getResource("/gfx/desktop/icon_emailloeschen.png"));
        buttonEmailLoeschen = new JButton(messages.getString("emailanwendung_msg43"));
        buttonEmailLoeschen.addActionListener(al);
        buttonEmailLoeschen.setActionCommand("loeschen");
        buttonEmailLoeschen.setToolTipText(messages.getString("emailanwendung_msg10"));
        topBox.add(buttonEmailLoeschen);
        topBox.add(Box.createHorizontalStrut(5));

        buttonKonten = new JButton(messages.getString("emailanwendung_msg44"));
        buttonKonten.addActionListener(al);
        buttonKonten.setActionCommand("konten");
        buttonKonten.setToolTipText(messages.getString("emailanwendung_msg11"));
        topBox.add(buttonKonten);
        topBox.add(Box.createHorizontalStrut(5));

        topBox.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(tabbedPane, BorderLayout.CENTER);
        contentPane.add(vorschauBox, BorderLayout.SOUTH);
        contentPane.add(topBox, BorderLayout.NORTH);

        add(contentPane, BorderLayout.CENTER);
    }

    /**
     * loescht eine ausgewaehlte und als Parameter uebergebene Email aus dem Posteingang oder dem Postausgang des
     * Client. Dazu dient der zweite Parameter als Abfrage.
     * 
     * @return boolean
     */
    public void emailLoeschen(int rowIndex, ListMode list) {
        if (ListMode.INBOX.equals(list)) {
            // dann loeschen aus dem Posteingang
            ((EmailAnwendung) holeAnwendung()).removeReceivedMail(posteingangTable.getSelectedRow());
            zeilenNummer = zeilenNummer - 1;
            posteingangAktualisieren();
            emailVorschau.setText(" ");
            emailVorschau.updateUI();
        } else if (ListMode.OUTBOX.equals(list)) {
            // dann loeschen aus dem Postausgang
            ((EmailAnwendung) holeAnwendung()).removeSentMail(gesendeteTable.getSelectedRow());
            zeilenNummer = zeilenNummer - 1;
            gesendeteAktualisieren();
            emailVorschau.setText(" ");
            emailVorschau.updateUI();
        } else {
            LOG.debug(
                    "============================================GuiAppl. Emailloeschen: Email konnte nicht geloescht werden=======================================");
        }
    }

    // provide more sophisticated and 'real' layout for quoted text
    private String replyLayout(String text) {
        return "> " + text.replaceAll("\\n", "\n> ");
    }

    private void emailVerfassen(Email antwortAuf) {
        verfassenPanel = new JPanel(new BorderLayout());

        /* Obere Box (Sende Button usw.) */
        Box topBox = Box.createHorizontalBox();
        JButton buttonSenden = new JButton(messages.getString("emailanwendung_msg13"));
        buttonSenden.setActionCommand("senden");
        topBox.add(buttonSenden);

        topBox.add(Box.createHorizontalStrut(5));
        JButton cancelButton = new JButton(messages.getString("emailanwendung_msg37"));
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                GUIApplicationEmailAnwendungWindow.this.desktop.closeModularWindow();
            }
        });
        topBox.add(cancelButton);
        topBox.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        verfassenPanel.add(topBox, BorderLayout.NORTH);

        /*
         * Mittlere Box (enthält das Betreffs- & Nachrichten Feld sowie An,CC und BCC
         */
        middleBox = Box.createVerticalBox();

        Box absenderBox = Box.createHorizontalBox();

        Vector<String> kontenVector = new Vector<String>();
        for (EmailKonto konto : ((EmailAnwendung) holeAnwendung()).holeKontoListe().values()) {
            kontenVector.addElement(konto.getBenutzername());
        }
        final JComboBox cbAbsender = new JComboBox(kontenVector);
        absenderBox.add(cbAbsender);
        absenderBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        middleBox.add(absenderBox);

        /* Box mit An Feld und dazugehörigem Label */
        Box kleineBox = Box.createHorizontalBox();
        JLabel anLabel = new JLabel(messages.getString("emailanwendung_msg14"));
        anLabel.setPreferredSize(new Dimension(120, 20));
        kleineBox.add(anLabel);
        kleineBox.add(Box.createHorizontalStrut(5));
        final JTextField anField = new JTextField();
        anField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                mailPruefen(anField);
            }
        });
        anField.setBorder(null);
        kleineBox.add(anField);
        middleBox.add(kleineBox);
        middleBox.add(Box.createVerticalStrut(3));

        /* Box mit CC Feld und dazugehörigem Label */
        kleineBox = Box.createHorizontalBox();
        JLabel ccLabel = new JLabel(messages.getString("emailanwendung_msg15"));
        ccLabel.setPreferredSize(new Dimension(120, 20));
        kleineBox.add(ccLabel);
        kleineBox.add(Box.createHorizontalStrut(5));
        final JTextField ccField = new JTextField();
        ccField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                mailPruefen(ccField);
            }

        });
        ccField.setBorder(null);
        kleineBox.add(ccField);
        middleBox.add(kleineBox);
        middleBox.add(Box.createVerticalStrut(3));

        /* Box mit CC Feld und dazugehörigem Label */
        kleineBox = Box.createHorizontalBox();
        JLabel bccLabel = new JLabel(messages.getString("emailanwendung_msg16"));
        bccLabel.setPreferredSize(new Dimension(120, 20));
        kleineBox.add(bccLabel);
        kleineBox.add(Box.createHorizontalStrut(5));
        final JTextField bccField = new JTextField();
        bccField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                mailPruefen(bccField);
            }

        });
        bccField.setBorder(null);
        kleineBox.add(bccField);
        middleBox.add(kleineBox);
        middleBox.add(Box.createVerticalStrut(10));

        /* Box mit Betreffszeile und dazugehörigem Label */
        kleineBox = Box.createHorizontalBox();
        JLabel betreffLabel = new JLabel(messages.getString("emailanwendung_msg17"));
        betreffLabel.setPreferredSize(new Dimension(120, 20));
        kleineBox.add(betreffLabel);
        kleineBox.add(Box.createHorizontalStrut(5));
        final JTextField betreffszeile = new JTextField();
        betreffszeile.setBorder(null);
        kleineBox.add(betreffszeile);
        middleBox.add(kleineBox);
        middleBox.add(Box.createVerticalStrut(5));

        final JTextArea inhaltField = new JTextArea();
        inhaltField.setPreferredSize(new Dimension(100, 300));
        middleBox.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JScrollPane inhaltScrollPane = new JScrollPane(inhaltField);
        inhaltScrollPane.setPreferredSize(new Dimension(500, 150));
        middleBox.add(inhaltScrollPane);
        verfassenPanel.add(middleBox);

        if (antwortAuf != null) {
            betreffszeile.setText(messages.getString("emailanwendung_msg18") + " " + antwortAuf.getBetreff());
            inhaltField.setText("\n\n" + antwortAuf.getAbsender().toString() + " "
                    + messages.getString("emailanwendung_msg19") + "\n" + replyLayout(antwortAuf.getText()));
            anField.setText(antwortAuf.getAbsender().toString());
        }

        inhaltField.requestFocus();
        inhaltField.grabFocus();
        inhaltField.setCaretPosition(0);

        /* ActionListener fuer Senden Button */
        ActionListener al = new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
                Email mail;
                String kontoString;
                EmailKonto versendeKonto;
                boolean eingabeFehler = false;

                mail = new Email();

                if (cbAbsender.getSelectedItem() == null) {
                    String msgNoAccountAvailable = GUIApplicationEmailAnwendungWindow.messages
                            .getString("emailanwendung_msg47");
                    GUIApplicationEmailAnwendungWindow.this.showMessageDialog(msgNoAccountAvailable);
                } else {
                    kontoString = cbAbsender.getSelectedItem().toString();
                    versendeKonto = (EmailKonto) ((EmailAnwendung) holeAnwendung()).holeKontoListe().get(kontoString);

                    mail.setAbsender(versendeKonto.getVorname()
                            + (!versendeKonto.getNachname().isEmpty() ? (" " + versendeKonto.getNachname()) : "") + " <"
                            + versendeKonto.getEmailAdresse() + ">");

                    if (!mailPruefen(anField)) {
                        eingabeFehler = true;
                    } else {
                        mail.setEmpfaenger(EmailUtils.stringToAddressEntryList(anField.getText()));
                    }

                    if (!mailPruefen(ccField)) {
                        eingabeFehler = true;
                    } else {
                        mail.setCc(EmailUtils.stringToAddressEntryList(ccField.getText()));
                    }

                    if (!mailPruefen(bccField)) {
                        eingabeFehler = true;
                    } else {
                        mail.setBcc(EmailUtils.stringToAddressEntryList(bccField.getText()));
                    }

                    if (eingabeFehler) {
                        showMessageDialog(messages.getString("emailanwendung_msg20"));
                    } else if (mail.getEmpfaenger().size() == 0 && mail.getCc().size() == 0
                            && mail.getBcc().size() == 0) {
                        showMessageDialog(messages.getString("emailanwendung_msg21"));
                    } else {
                        mail.setBetreff(betreffszeile.getText());
                        mail.setText(inhaltField.getText());

                        progressBar = new JProgressBar(0, 100);
                        progressBar.setValue(0);
                        progressBar.setIndeterminate(true);
                        progressBar.setStringPainted(true);

                        middleBox.add(progressBar);
                        middleBox.invalidate();
                        middleBox.validate();

                        progressBar.setString(messages.getString("emailanwendung_msg22"));
                        ((EmailAnwendung) holeAnwendung()).versendeEmail(versendeKonto.getSmtpserver(), mail,
                                versendeKonto.getEmailAdresse());
                        tabbedPane.setSelectedIndex(1);
                    }
                }
            }
        };

        buttonSenden.addActionListener(al);

        desktop.showModularWindow(messages.getString("emailanwendung_msg12"), verfassenPanel);
    }

    public void emailsAbholen() {
        if (!((EmailAnwendung) holeAnwendung()).holeKontoListe().isEmpty()) {
            progressBar = new JProgressBar(0, 100);
            progressBar.setValue(0);
            progressBar.setIndeterminate(true);
            progressBar.setStringPainted(true);

            progressPanel = new JPanel(new BorderLayout());
            progressPanel.add(progressBar, BorderLayout.CENTER);
            progressPanel.setPreferredSize(new Dimension(500, 40));
            desktop.showModularWindow(messages.getString("emailanwendung_msg23"), progressPanel);

            for (EmailKonto aktuellesKonto : ((EmailAnwendung) holeAnwendung()).holeKontoListe().values()) {
                progressBar
                        .setString(messages.getString("emailanwendung_msg24") + aktuellesKonto.getEmailAdresse() + ")");
                ((EmailAnwendung) holeAnwendung()).emailsAbholenEmails(aktuellesKonto.getBenutzername(),
                        aktuellesKonto.getPasswort(), aktuellesKonto.getPop3port(), aktuellesKonto.getPop3server());
                tabbedPane.setSelectedIndex(0);
            }
        }
    }

    private void posteingangAktualisieren() {
        posteingangModell.setRowCount(0);
        List<Email> empfangeneNachrichten = ((EmailAnwendung) holeAnwendung()).getEmpfangeneNachrichten();
        for (Email neueMail : empfangeneNachrichten) {
            Vector<String> v = new Vector<String>();
            String absender;
            if (neueMail.getAbsender() == null) {
                absender = "";
            } else if (neueMail.getAbsender().getName() == null) {
                absender = neueMail.getAbsender().getMailAddress();
            } else {
                absender = neueMail.getAbsender().getName();
            }
            v.add(absender);
            v.add(neueMail.getBetreff());
            posteingangModell.addRow(v);
        }
    }

    public void gesendeteAktualisieren() {
        gesendeteModell.setRowCount(0);
        List<Email> gesendeteNachrichten = ((EmailAnwendung) holeAnwendung()).getGesendeteNachrichten();
        for (Email neueMail : gesendeteNachrichten) {
            Vector<String> v = new Vector<String>();
            v.add(neueMail.holeEmpfaengerListe());
            v.add(neueMail.getBetreff());
            gesendeteModell.addRow(v);
        }
    }

    private void kontoAktualisieren() {
        Map<String, EmailKonto> kontoListe = ((EmailAnwendung) holeAnwendung()).holeKontoListe();
        for (EmailKonto konto : kontoListe.values()) {
            tfName.setText(konto.getVorname() + " " + konto.getNachname());
            tfEmailAdresse.setText(konto.getEmailAdresse());
            tfPOP3Server.setText(konto.getPop3server());
            tfPOP3Port.setText(konto.getPop3port());
            tfSMTPServer.setText(konto.getSmtpserver());
            tfSMTPPort.setText(konto.getSmtpport());
            tfBenutzername.setText(konto.getBenutzername());
            tfPasswort.setText(konto.getPasswort());
        }
    }

    private void kontoVerwalten() {
        JLabel label;
        JPanel panel;
        Box vBox, hBox;
        JButton button;
        JScrollPane scroller;

        vBox = Box.createVerticalBox();

        /* Name */
        label = new JLabel(messages.getString("emailanwendung_msg45"));
        label.setPreferredSize(new Dimension(150, 25));

        tfName = new JTextField();
        tfName.setPreferredSize(new Dimension(150, 25));

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfName);
        vBox.add(hBox);

        /* Email-Adresse */
        label = new JLabel(messages.getString("emailanwendung_msg26"));
        label.setPreferredSize(new Dimension(150, 25));

        tfEmailAdresse = new JTextField();
        tfEmailAdresse.setPreferredSize(new Dimension(150, 25));
        tfEmailAdresse.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                gueltigkeitPruefen(tfEmailAdresse, EingabenUeberpruefung.musterEmailAdresse);
            }
        });

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfEmailAdresse);
        vBox.add(hBox);

        /* POP3 Server */
        label = new JLabel(messages.getString("emailanwendung_msg27"));
        label.setPreferredSize(new Dimension(150, 25));

        tfPOP3Server = new JTextField();
        tfPOP3Server.setPreferredSize(new Dimension(150, 25));

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfPOP3Server);
        vBox.add(hBox);

        /* POP3 Port */
        label = new JLabel(messages.getString("emailanwendung_msg28"));
        label.setPreferredSize(new Dimension(150, 25));

        tfPOP3Port = new JTextField();
        tfPOP3Port.setPreferredSize(new Dimension(150, 25));
        tfPOP3Port.setText("110");
        tfPOP3Port.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                gueltigkeitPruefen(tfPOP3Port, EingabenUeberpruefung.musterPort);
            }
        });

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfPOP3Port);
        vBox.add(hBox);

        /* SMTP Server */
        label = new JLabel(messages.getString("emailanwendung_msg29"));
        label.setPreferredSize(new Dimension(150, 25));

        tfSMTPServer = new JTextField();
        tfSMTPServer.setPreferredSize(new Dimension(150, 25));

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfSMTPServer);
        vBox.add(hBox);

        /* SMTP Port */
        label = new JLabel(messages.getString("emailanwendung_msg30"));
        label.setPreferredSize(new Dimension(150, 25));

        tfSMTPPort = new JTextField();
        tfSMTPPort.setPreferredSize(new Dimension(150, 25));
        tfSMTPPort.setText("25");
        tfSMTPPort.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                gueltigkeitPruefen(tfSMTPPort, EingabenUeberpruefung.musterPort);
            }
        });

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfSMTPPort);
        vBox.add(hBox);

        /* Benutzername */
        label = new JLabel(messages.getString("emailanwendung_msg31"));
        label.setPreferredSize(new Dimension(150, 25));

        tfBenutzername = new JTextField();
        tfBenutzername.setPreferredSize(new Dimension(150, 25));
        tfBenutzername.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                gueltigkeitPruefen(tfBenutzername, EingabenUeberpruefung.musterEmailBenutzername);
            }
        });

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfBenutzername);
        vBox.add(hBox);

        /* Passwort */
        label = new JLabel(messages.getString("emailanwendung_msg32"));
        label.setPreferredSize(new Dimension(150, 25));

        tfPasswort = new JPasswordField();
        tfPasswort.setPreferredSize(new Dimension(150, 25));

        hBox = Box.createHorizontalBox();
        hBox.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        hBox.add(label);
        hBox.add(tfPasswort);
        vBox.add(hBox);

        hBox = Box.createHorizontalBox();

        /* Erstellen-Button */
        button = new JButton(messages.getString("emailanwendung_msg33"));
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (kontoSpeichern()) {
                    GUIApplicationEmailAnwendungWindow.this.desktop.closeModularWindow();
                } else {
                    showMessageDialog(messages.getString("emailanwendung_msg46"));
                }
            }
        });
        hBox.add(button);
        hBox.add(Box.createHorizontalStrut(5));

        button = new JButton(messages.getString("emailanwendung_msg37"));
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                GUIApplicationEmailAnwendungWindow.this.desktop.closeModularWindow();
            }
        });

        hBox.add(button);
        vBox.add(Box.createVerticalStrut(10));
        vBox.add(hBox);

        panel = new JPanel();
        panel.add(vBox);

        scroller = new JScrollPane(panel);
        scroller.setPreferredSize(new Dimension(340, 300));

        desktop.showModularWindow(messages.getString("emailanwendung_msg25"), scroller);
    }

    private boolean kontoSpeichern() {
        String[] teilStrings;

        if (EingabenUeberpruefung.isGueltig(tfPOP3Port.getText(), EingabenUeberpruefung.musterPort)
                && EingabenUeberpruefung.isGueltig(tfSMTPPort.getText(), EingabenUeberpruefung.musterPort)
                && EingabenUeberpruefung.isGueltig(tfBenutzername.getText(),
                        EingabenUeberpruefung.musterEmailBenutzername)) {

            EmailKonto konto = new EmailKonto();
            if (tfName.getText().trim().equals("")) {
                konto.setVorname("");
                konto.setNachname("");
            } else {
                teilStrings = tfName.getText().split(" ");
                if (teilStrings.length == 1) {
                    konto.setVorname(tfName.getText().trim());
                } else if (teilStrings.length >= 2) {
                    konto.setNachname(teilStrings[teilStrings.length - 1]);
                    String tmp = "";
                    for (int i = 0; i < teilStrings.length - 1; i++)
                        tmp += teilStrings[i] + " ";
                    konto.setVorname(tmp.trim());
                }
            }
            konto.setBenutzername(tfBenutzername.getText());
            konto.setPasswort(new String(tfPasswort.getPassword()));
            konto.setPop3port(tfPOP3Port.getText());
            konto.setPop3server(tfPOP3Server.getText());
            konto.setSmtpport(tfSMTPPort.getText());
            konto.setSmtpserver(tfSMTPServer.getText());
            konto.setEmailAdresse(tfEmailAdresse.getText());

            ((EmailAnwendung) holeAnwendung()).setzeKonto(konto);

            speichern();
            return true;
        } else {
            return false;
        }
    }

    private void speichern() {
        ((EmailAnwendung) holeAnwendung()).speichern();
    }

    private void laden() {
        ((EmailAnwendung) holeAnwendung()).laden();
    }

    /**
     * Ueberprueft Eingabefelder auf Richtigkeit
     * 
     * @author Johannes Bade & Thomas Gerding
     * @param pruefRegel
     * @param feld
     */
    public void gueltigkeitPruefen(JTextField feld, Pattern pruefRegel) {
        if (EingabenUeberpruefung.isGueltig(feld.getText(), pruefRegel)) {
            feld.setForeground(EingabenUeberpruefung.farbeRichtig);
            JTextField temp = new JTextField();
            feld.setBorder(temp.getBorder());

        } else {
            feld.setForeground(EingabenUeberpruefung.farbeFalsch);
            feld.setBorder(BorderFactory.createLineBorder(EingabenUeberpruefung.farbeFalsch, 1));

        }

    }

    /**
     * Funktion die während der Eingabe überprüft ob die bisherige Eingabe einen korrekten Wert darstellt.
     * 
     * @author Johannes Bade & Thomas Gerding
     * @param pruefRegel
     * @param feld
     */
    private boolean mailPruefen(JTextField feld) {
        String[] adressen;
        boolean fehler = false;

        if (!feld.getText().trim().equals("")) {
            adressen = feld.getText().split(",");

            for (int i = 0; i < adressen.length; i++) {
                if (!EingabenUeberpruefung.isGueltig(adressen[i].trim(), EingabenUeberpruefung.musterEmailAdresse)) {
                    fehler = true;
                }
            }
        }

        if (!fehler) {
            feld.setForeground(EingabenUeberpruefung.farbeRichtig);
            feld.setBorder(null);
        } else {
            feld.setForeground(EingabenUeberpruefung.farbeFalsch);
            feld.setBorder(BorderFactory.createLineBorder(EingabenUeberpruefung.farbeFalsch, 1));
        }

        return !fehler;
    }

    public void update(Observable arg0, Object arg1) {
        posteingangAktualisieren();
        gesendeteAktualisieren();

        if (arg1 instanceof Exception) {
            showMessageDialog(((Exception) arg1).getMessage());
        }

        if (arg1 == null || arg1.equals("") || arg1 instanceof Exception) {
            desktop.closeModularWindow(progressPanel);
            desktop.closeModularWindow(verfassenPanel);
        }
    }

}
