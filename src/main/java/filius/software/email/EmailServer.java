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
package filius.software.email;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.tree.DefaultMutableTreeNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.exception.CreateAccountException;
import filius.exception.DeleteAccountException;
import filius.rahmenprogramm.EingabenUeberpruefung;
import filius.rahmenprogramm.I18n;
import filius.software.Anwendung;
import filius.software.system.Datei;
import filius.software.system.Dateisystem;
import filius.software.system.InternetKnotenBetriebssystem;

/**
 * 
 * @author Andre Asschoff
 * 
 *         Der emailServer muss wohl neu aufgerollt werden, da ich hier überhaupt kein Thread brauche. Dies wird durch
 *         POP3- und SMTPServer realisiert. Daher wird der nun überarbeitet. 13.12.2006
 */
public class EmailServer extends Anwendung implements I18n {
    private static Logger LOG = LoggerFactory.getLogger(EmailServer.class);
    public static final String LINE_SEPARATOR = "----";

    private List<EmailKonto> listeBenutzerkonten = new LinkedList<EmailKonto>();
    private String mailDomain = "filius.de";
    private POP3Server pop3;
    private SMTPServer smtp;
    /**
     * Dieses Attribut gibt an, ob der Server bereit ist, auf eingehende Verbindungsanfragen zu antworten.
     */
    private boolean aktiv = false;
    private DefaultMutableTreeNode verzeichnis;

    // Konstruktoren
    // keine gesondert implementierten

    /**
     * Ob der Server eingehende Verbindungsanfragen annmimmt. Das gilt sowohl fuer SMTP wie auch fuer POP3
     */
    public boolean isAktiv() {
        return aktiv;
    }

    /**
     * Ob der Server eingehende Verbindungsanfragen annmimmt. Das gilt sowohl fuer SMTP wie auch fuer POP3. <br />
     * In dieser Methode wird also sowohl das Attribut dieser Klasse gesetzt, das fuer SMTP und POP3 gilt, wie auch die
     * jeweiligen Attribute des POP3Server und des SMTPServer.
     */
    public void setAktiv(boolean aktiv) {
        this.aktiv = aktiv;

        if (pop3 != null)
            pop3.setAktiv(aktiv);
        if (smtp != null)
            smtp.setAktiv(aktiv);
    }

    // Methoden

    /**
     * Hier wird die ueberladene Methode der Oberklasse aufgerufen, die Benutzerkonten gespeichert und POP3Server und
     * SMTPServer beendet.
     */
    public void beenden() {
        LOG.debug(
                "INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass() + " (EmailServer), beenden()");
        super.beenden();

        kontenSpeichern();

        if (pop3 != null)
            pop3.beenden();
        if (smtp != null)
            smtp.beenden();
    }

    /**
     * Diese Methode wird beim Wechsel vom Entwurfs- in den Aktionsmodus aufgerufen. Hier wird zuerst die ueberladene
     * Methode der Oberklasse aufgerufen. Dann werden die Benutzerkonten aus der Datei des Servers geladen. Danach wird
     * ein neuer POP3Server und ein neuer SMTPServer erzeugt und initialisiert. D. h., dass sie auch deren
     * starten()-Methode aufgerufen wird und das Attribut 'aktiv' gesetzt wird, das angibt, ob ein Server auf eingehende
     * Verbindungsanfragen wartet.
     */
    public void starten() {
        LOG.debug(
                "INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass() + " (EmailServer), starten()");
        super.starten();

        Datei konten = getSystemSoftware().getDateisystem().holeDatei(verzeichnis, "konten.txt");
        if (konten == null) {
            konten = new Datei("konten.txt", "txt", "");
            getSystemSoftware().getDateisystem().speicherDatei(verzeichnis, konten);
        }
        kontenLaden();

        pop3 = new POP3Server(110, this);
        pop3.setSystemSoftware(getSystemSoftware());
        pop3.setAktiv(aktiv);
        pop3.starten();

        smtp = new SMTPServer(25, this);
        smtp.setSystemSoftware(getSystemSoftware());
        smtp.setAktiv(aktiv);
        smtp.starten();
    }

    /**
     * FUNKTIONIERT In dieser Methode wird ein neuer Benutzer hinzugefuegt, d.h. ein neues Konto in der Liste
     * "listeBenutzerkonten" aller Nutzer erstellt. Dazu muessen der Methode alle für einen Account relevanten Daten
     * uebergeben werden. Zunaechst wird dann abgefragt, ob es in dieser Liste bereits den Benutzernamen gibt, den der
     * Client für sein Konto moechte, wenn ja, gibts einen Fehler, wenn nein, ist er akzeptiert. Hier ist darauf zu
     * achten, dass sich der Benutzername aus dem benutzernamen + "@" + Name des Email- Servers zusammensetzt. Dies muss
     * explizizt abgefragt werden (siehe letzter Satz) Danach muss abgefragt werden, ob nicht alle anderen Felder wie
     * Vorname, Strasse, usw. auch bereits einem existierenden Kontakt in der Liste entsprechen. Unterscheiden sich
     * diese ganzen sekundaeren Angaben in mindestens einem Punkt, kann der Kontakt schliesslich vervollstaendigt und
     * der "listeBenutzerkonten" hinzugefuegt werden. Der Benutzername ent- spricht dann dem gewuenschten Begriff oder
     * Namen gefolgt von einem "@" und dem Namen des EmailServers.
     * 
     * @param benutzername
     * @param passwort
     * @param nachname
     * @param vorname
     * @return boolean
     * @throws CreateAccountException
     */
    public boolean benutzerHinzufuegen(String benutzername, String passwort, String nachname, String vorname)
            throws CreateAccountException {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), benutzerHinzufuegen(" + benutzername + "," + passwort + "," + nachname + ","
                + vorname + ")");
        try {
            EmailKonto konto = new EmailKonto();

            for (EmailKonto kontoAusListe : getListeBenutzerkonten()) {
                if (kontoAusListe.getBenutzername().equalsIgnoreCase(benutzername)) {
                    return false;
                } else if (kontoAusListe.getBenutzername().equals(benutzername)
                        && (kontoAusListe.getNachname().equalsIgnoreCase(nachname))
                        && (kontoAusListe.getVorname().equals(vorname))) {
                    return false;
                } else if ((kontoAusListe.getNachname().equals("")) || (kontoAusListe.getVorname().equals(""))) {
                    return false;
                }
            }

            konto.setBenutzername(benutzername);
            konto.setPasswort(passwort);
            konto.setNachname(nachname);
            konto.setVorname(vorname);

            synchronized (getListeBenutzerkonten()) {
                getListeBenutzerkonten().add(konto);
            }
            benachrichtigeBeobachter();
        } catch (Exception e) {
            throw new CreateAccountException("-ERR This account could not be created. Please try again!");
        }
        kontenSpeichern();
        return true;
    }

    /**
     * FUNKIONIERT Diese Methode loescht einen Benutzeraccount aus der Liste "listeBenutzerkonten" des EmailServers.
     * Dazu wird die gesamte Liste durchlaufen und nach dem entsprechenden Benutzernamen gesucht, der geloescht werden
     * soll. Danach wird das Passwort abgefragt, die auch als Parameter der Methode uebergeben werden. Sind beide
     * richtig, wird das Konto aus der Liste entfernt. Klappt das wird true zurückgegeben, andernfalls eine Exception
     * geworfen. Hier muss auch wieder beachtet werden dass sich der vollständige Benutzername aus dem eigentlichen
     * benutzernamen, den der User angibt, + "@" + Name des EmailServers ergibt. Der User muss aber nur seinen
     * Benutzernamen eingeben, der wird dann vervollständigt.
     * 
     * @param benutzername
     * @param passwort
     * @return boolean
     * @throws DeleteAccountException
     */
    public boolean kontoLoeschen(String benutzername, String passwort) throws DeleteAccountException {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), kontoLoeschen(" + benutzername + "," + passwort + ")");
        try {
            for (EmailKonto konto : getListeBenutzerkonten()) {
                if (konto.getBenutzername().equals(benutzername)) {
                    if (konto.getPasswort().equals(passwort)) {
                        return kontoLoeschen(konto);
                    }
                }
            }
        } catch (Exception e) {
            throw new DeleteAccountException(messages.getString("sw_emailserver_msg1"));
        }
        return false;
    }

    public boolean kontoLoeschen(EmailKonto konto) {
        synchronized (getListeBenutzerkonten()) {
            getListeBenutzerkonten().remove(konto);
        }
        kontenSpeichern();
        benachrichtigeBeobachter();
        return true;
    }

    /**
     * FUNKTIONIERT Hier wird ein EmailKonto zu einer bestehenden Verbindung gesucht. Es wird eine VerbindungsId, die
     * beim Aufbau einer neuen Verbindung gespeichert wird, uebergeben. Mit ihr wird zunaechst die Liste aller Ver-
     * bindungen durchlaufen, und abgefragt, ob es eine Verbindung mit dieser uebergebenen Id gibt. Trifft das zu, wird
     * aus ihr der Benuztername(bname) und das Passwort herausgelesen und temporaer in der Methode ge- speichert. Mit
     * diesen beiden nun wird die "listeBenutzerkonten", die Liste aller EmailPostfächer, bzw. registrierter Benutzer
     * dieses EmailServers durchlaufen, um zu suchen, ob es einen Nutzer gibt, der dieses Konto mit dem bname und
     * Passwort hat. Gibt es das, wird dieses Konto zurueckgegeben.
     * 
     * @param verbindungsId
     * @return EmailKonto
     */
    public EmailKonto sucheKonto(String benName, String passwd) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), sucheKonto(" + benName + "," + passwd + ")");
        for (EmailKonto konto : getListeBenutzerkonten()) {
            if (konto.getBenutzername().equalsIgnoreCase(benName) && konto.getPasswort().equals(passwd)) {
                return konto;
            }
        }
        return null;
    }

    /**
     * Methode, die das Konto anhand des Namens sucht.
     * 
     * @param benName
     * @return
     */
    public EmailKonto sucheKonto(String benName) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), sucheKonto(" + benName + ")");
        for (EmailKonto tempKonto : listeBenutzerkonten) {
            if (tempKonto.getBenutzername().equalsIgnoreCase(benName)) {
                return tempKonto;
            }
        }
        return null;
    }

    /**
     * NACHTRAG 2008: Methode ist void, gibt aber boolean erfolg aus, außerdem wird sie in "nachrichtenKommunikation"
     * aufgerufen, die selbst keinerlei Aufgabe mehr hat! Hier werden die Benutzerkonten zunaechst in einer Datei
     * gespeichert, anschließend in das virtuelle Baumverzeichnis eingefuegt
     * 
     */
    public void kontenSpeichern() {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), kontenSpeichern()");

        String tmp = listeBenutzerkontenZuString(listeBenutzerkonten);
        Datei konten = getSystemSoftware().getDateisystem().holeDatei(verzeichnis, "konten.txt");
        konten.setDateiInhalt(tmp);
    }

    private String nltobr(String originalText) {
        return originalText.replaceAll("\n", "|");
    }

    private String brtonl(String originalText) {
        return originalText.replaceAll("\\|", "\n");
    }

    private String undoSpecialChar(String escapeStr) {
        String result = escapeStr.replaceAll("&&00036&&", "\\$");
        result = result.replaceAll("&&00059&&", ";");
        result = result.replaceAll("&&00124&&", "\\|");
        result = result.replaceAll("&&00035&&", "\\#");
        return result;
    }

    private String replaceSpecialChar(String mailtext) {
        String result = mailtext.replaceAll("\\$", "&&00036&&");
        result = result.replaceAll(";", "&&00059&&");
        result = result.replaceAll("\\|", "&&00124&&");
        result = result.replaceAll("#", "&&00035&&");
        return result;
    }

    /**
     * NACHTRAG 2008: 2te "while-Schleife" scheint ueberfluessig, scheint dennoch zu gehen. Hier wird ein Benutzerkonto
     * aus der Liste der Benutzerkonten in einen String umgewandelt. Der String hat die Form
     * benutzername+kontoDomain+passwort+nachname+vorname+nachrichten. Dies brauche ich fuer SPEICHERUNG
     * 
     * @param benutzerkonten
     * @return
     */
    private String listeBenutzerkontenZuString(List<EmailKonto> benutzerkonten) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), listeBenutzerkontenZuString(" + benutzerkonten + ")");
        String ergebnis = "";

        for (EmailKonto konto : benutzerkonten) {
            String emailStr = "";

            for (Email email : konto.getNachrichten()) {
                emailStr = emailStr + "#" + (email.getAbsender() != null ? email.getAbsender() : "") + "$"
                        + EmailUtils.addressEntryListToString(email.getEmpfaenger()) + "$"
                        + EmailUtils.addressEntryListToString(email.getCc()) + "$"
                        + EmailUtils.addressEntryListToString(email.getBcc()) + "$" + email.getDateReceived() + "$"
                        + (email.getBetreff() != null ? email.getBetreff() : "") + "$"
                        + nltobr(replaceSpecialChar(email.getText()));

            }
            ergebnis = ergebnis + konto.getBenutzername() + ";" + this.mailDomain + ";" + konto.getPasswort() + ";"
                    + konto.getNachname() + ";" + konto.getVorname() + ";" + emailStr + "\n";
        }
        return ergebnis;
    }

    /**
     * Das brauche ich fuers LADEN, ist der Gegensatz zu listeBenutzerkontenZuString.
     * 
     * @param speicherung
     * @return
     */
    private List<EmailKonto> stringZuListeBenutzerkonten(String speicherung) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), stringZuListeBenutzerkonten(" + speicherung + ")");
        List<EmailKonto> temp = new LinkedList<EmailKonto>();
        String[] strArray;

        StringTokenizer speicherungTokenizer = new StringTokenizer(speicherung, "\n");
        while (speicherungTokenizer.hasMoreTokens()) {
            EmailKonto konto = new EmailKonto();
            String emailKontoInString = speicherungTokenizer.nextToken();
            StringTokenizer emailKontoInStringTokenizer = new StringTokenizer(emailKontoInString, ";");

            konto.setBenutzername(emailKontoInStringTokenizer.nextToken());
            emailKontoInStringTokenizer.nextToken();
            konto.setPasswort(emailKontoInStringTokenizer.nextToken());
            konto.setNachname(emailKontoInStringTokenizer.nextToken());
            konto.setVorname(emailKontoInStringTokenizer.nextToken());
            if (emailKontoInStringTokenizer.hasMoreTokens()) {
                String nachrichten = emailKontoInStringTokenizer.nextToken();

                try {
                    String[] nachrichtenArray = nachrichten.split("#");
                    for (int i = 0; i < nachrichtenArray.length; i++) {
                        strArray = nachrichtenArray[i].split("\\$");

                        if (strArray.length == 7) {
                            Email email = new Email();
                            email.setAbsender(strArray[0]);
                            email.setEmpfaenger(EmailUtils.stringToAddressEntryList(strArray[1]));
                            email.setCc(EmailUtils.stringToAddressEntryList(strArray[2]));
                            email.setBcc(EmailUtils.stringToAddressEntryList(strArray[3]));
                            email.setDateReceived(strArray[4]);
                            email.setBetreff(strArray[5]);
                            email.setText(undoSpecialChar(brtonl(strArray[6])));

                            konto.getNachrichten().add(email);
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("", e);
                }
            }
            temp.add(konto);
        }

        return temp;
    }

    /** Hier werden die Benutzerkonten wieder geladen */
    public void kontenLaden() {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), kontenLaden()");
        Datei konten = getSystemSoftware().getDateisystem().holeDatei(verzeichnis, "konten.txt");

        if (konten != null) {
            setListeBenutzerkonten(stringZuListeBenutzerkonten(konten.getDateiInhalt()));
        } else {
            LOG.debug("ERROR (" + this.hashCode() + "): Konten laden fehlgeschlagen");
        }
    }

    public void setSystemSoftware(InternetKnotenBetriebssystem bs) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), setSystemSoftware(" + bs + ")");
        super.setSystemSoftware(bs);
        getSystemSoftware().getDateisystem().erstelleVerzeichnis(getSystemSoftware().getDateisystem().getRoot(),
                "mailserver");
        this.verzeichnis = Dateisystem.verzeichnisKnoten(getSystemSoftware().getDateisystem().getRoot(), "mailserver");
    }

    public synchronized void setListeBenutzerkonten(List<EmailKonto> listeBenutzerkonten) {
        this.listeBenutzerkonten = listeBenutzerkonten;
    }

    public void setPOP3Server(POP3Server pop3) {
        this.pop3 = pop3;
    }

    public void setSMTPServer(SMTPServer smtp) {
        this.smtp = smtp;
    }

    public synchronized List<EmailKonto> getListeBenutzerkonten() {
        return listeBenutzerkonten;
    }

    public POP3Server getPOP3Server() {
        return pop3;
    }

    public SMTPServer getSMTPServer() {
        return smtp;
    }

    public POP3Server holePop3() {
        return pop3;
    }

    public SMTPServer holeSmtp() {
        return smtp;
    }

    public String getMailDomain() {
        return mailDomain;
    }

    public void setMailDomain(String mailDomain) {
        if (mailDomain != null && EingabenUeberpruefung.isGueltig(mailDomain, EingabenUeberpruefung.musterDomain)) {
            this.mailDomain = mailDomain;
        }
    }

    public void emailWeiterleiten(Email email, String absender, String rcpt) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), emailWeiterleiten(" + email + "," + absender + "," + rcpt + ")");
        Object[] args;

        args = new Object[3];
        args[0] = email;
        args[1] = absender;
        args[2] = rcpt;

        ausfuehren("weiterleiten", args);
    }

    /**
     * hier wird die Email versendet, dazu wird der SMTPClient benutzt, dessen verwende Methode aufgerufen wird. 1.
     * erstellen eines neuen SMTPClienten 2. starten des Clienten 3. versenden Es wird die Email übergeben und genau ein
     * Empfaenger, an den die Email weiter- geleitet werden soll. Dies wird so oft wiederholt in "verarbeiteEmail", bis
     * alle Empfaenger, an die weitergeleitet werden soll, durchgelaufen sind. <br />
     * Diese Methode <b> blockiert </b> den Thread!
     * 
     * @param email
     * @param rcpt
     */
    public void weiterleiten(Email email, String absender, String rcpt) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EmailServer), weiterleiten(" + email + "," + absender + "," + rcpt + ")");
        SMTPClient clientFuerWeiterleitung = new SMTPClient(this);
        clientFuerWeiterleitung.starten();
        clientFuerWeiterleitung.versendeEmail(null, email, absender, rcpt);
    }

    public void sendUnknownReceiverResponse(Email email, String sender, List<String> unknownRecipients) {
        if (!sender.equalsIgnoreCase("<>")) {
            Email unknownRecipientResponse = new Email();
            unknownRecipientResponse.setAbsender("noreply@" + mailDomain);
            unknownRecipientResponse.setEmpfaenger(Arrays.asList(email.getAbsender()));
            unknownRecipientResponse.setBetreff("Non Delivery Notification");
            StringBuilder mailBody = new StringBuilder();
            mailBody.append("The mail could not be delivered either because user or mail domain is unknown.")
                    .append("\n");
            mailBody.append("Message could not be delivered to the following recipients:\n");
            for (String unknownRecipient : unknownRecipients) {
                mailBody.append("\t").append(unknownRecipient).append("\n");
            }
            mailBody.append("\nDate: ").append(DateFormat.getInstance().format(new Date())).append("\n");
            mailBody.append("Mail-Server: ").append(this.getSystemSoftware().primaryIPAdresse()).append("\n");
            mailBody.append("\n______________________________________________________________________\n");
            mailBody.append(email.toString());
            unknownRecipientResponse.setText(mailBody.toString());
            AddressEntry senderAddress = new AddressEntry(sender);
            if (pruefeAufSelbeDomain(senderAddress.getMailAddress())) {
                EmailKonto empfaengerKonto = sucheKonto(senderAddress.getMailAddress().split("@")[0]);
                empfaengerKonto.getNachrichten().add(unknownRecipientResponse);
                benachrichtigeBeobachter(messages.getString("sw_smtpmitarbeiter_msg12") + " "
                        + empfaengerKonto.getBenutzername() + " " + messages.getString("sw_smtpmitarbeiter_msg13"));
            } else {
                this.weiterleiten(unknownRecipientResponse, "<>", senderAddress.toString());
            }
        }
    }

    /**
     * Methode prüft, ob die Domain, an die die Email geschickt werden soll, mit der des eigenen MailServers
     * übereinstimmt.
     * 
     * @param str
     * @return boolean
     */
    public boolean pruefeAufSelbeDomain(String str) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass() + ", pruefeAufSelbeDomain("
                + str + ")");
        String[] emailAdresse = str.split("@");
        String pruefdomain = emailAdresse[1].substring(0, emailAdresse[1].length());
        if (mailDomain.equals(pruefdomain)) {
            return true;
        }
        return false;
    }
}
