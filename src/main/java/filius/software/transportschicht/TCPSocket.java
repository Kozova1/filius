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
package filius.software.transportschicht;

import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.exception.SocketException;
import filius.exception.TimeOutException;
import filius.exception.VerbindungsException;
import filius.hardware.Verbindung;
import filius.software.system.InternetKnotenBetriebssystem;
import filius.software.vermittlungsschicht.IpPaket;

/**
 * <p>
 * Dieser Socket implementiert die Schnittstelle fuer den Ende-zu-Ende-Datenaustausch mit dem Transport Control
 * Protocol. Dazu werden insbesondere drei Funktionen unterschieden:
 * <ol>
 * <li>Verbindungsaufbau</li>
 * <li>Senden und</li>
 * <li>Empfangen von Nachrichten</li>
 * <li>Verbindungsabbau</li>
 * </ol>
 * Es wurden Vereinfachungen hinsichtlich Wartezeiten, Wiederholung von Datenuebertragungen und aehnliches vorgenommen.
 * </p>
 * Die vier genannten Funktionen werden jeweils durch eine Methode implementiert. Diese Methoden blockieren die
 * Ausfuehrung eines Threads. Sie stellen die Schnittstelle zur Anwendungsschicht dar. <br />
 * <p>
 * Die Verwendung des TCP-Sockets funktioniert foldendermassen:
 * <ol>
 * <li>Der Aufruf des Konstruktors initialisiert die notwendigen Attribute fuer eine Verbindung.</li>
 * <li>Der Verbindungsaufbau erfolgt durch Aufruf der Methode <b>verbinden()</b>. Diese Methode blockiert so lange, bis
 * der Verbindungsaufbau erfolgreich abgeschlossen werden konnte oder eine Ausnahme ausgeloest wurde. Der
 * Verbindungsaufbau erfolgt mit einem Three-Way-Handshake.</li>
 * <li>Mit Aufruf der Methode <b>istVerbunden()</b> kann der Verbindungsstatus abgerufen werden. Diese Methode prueft,
 * ob der Zustand des Sockets ESTABLISHED ist.</li>
 * <li>Wenn eine Verbindung erfolgreich aufgebaut werden konnte, ist es moeglich Nachrichten mit der Methode
 * <b>sende(String)</b> an den entfernten Socket zu verschicken. Diese Methode blockiert, bis die Nachricht erfolgreich
 * uebertragen werden konnte oder eine Ausnahme ausgeloest wurde. Die Uebertragung erfolgt mit einem
 * Stop-And-Wait-Algorithmus.</li>
 * <li>Ebenso kann nach efolgtem Verbindungsaufbau mit Aufruf der Methode <b>empfangen()</b> auf eine eingehende
 * Nachricht gewartet werden. Es wird ein String zurueck gegeben, wenn die Nachricht vollstaendig empfangen wurde.</li>
 * <li>Der Verbindungsabbau wird durch Aufruf der Methode <b>schliessen()</b> initiiert. Allerdings erfolgt der
 * Verbindungsabbau synchron. D. h., der Verbindungsabbau muss von beiden Seiten initiiert werden. Diese Methode
 * blockiert so lange, bis der Verbindungsabbau beendet wurde. Allerdings gibt es einen Timeout, nach dem der Socket
 * auch ohne Antwort der Gegenseite geschlossen wird.</li>
 * <li>Beim Wechsel vom Aktions- in den Entwurfsmodus muss die Methode <b>beenden()</b> aufgerufen werden, damit der
 * Socket geschlossen wird und alle Threads, die noch in einer Methode blockiert werden, wieder freigegeben werden. Der
 * Socket ist anschliessend geschlossen. Es wird jedoch kein ordentlicher Verbindungsabbau durchgefuehrt.</li>
 * </ol>
 * </p>
 * 
 * Der TCP-Socket befindet sich immer in einem der folgenden Zustaende:
 * <ul>
 * <li>1 - CLOSED</li>
 * <li>2 - LISTEN</li>
 * <li>3 - SYN_RCVD</li>
 * <li>4 - SYN_SENT</li>
 * <li>5 - ESTABLISHED</li>
 * <li>6 - CLOSE_WAIT</li>
 * <li>7 - LAST_ACK</li>
 * <li>8 - FIN_WAIT_1</li>
 * <li>9 - FIN_WAIT_2</li>
 * <li>10 - CLOSING</li>
 * <li>11 - TIME_WAIT</li>
 * </ul>
 * 
 * Uebergaenge zwischen den Zustaenden werden durch folgende Ereignisse ausgeloest:
 * <ol>
 * <li>aktiv oeffnen</li>
 * <li>passiv oeffnen</li>
 * <li>schliessen</li>
 * <li>senden</li>
 * <li>timeout</li>
 * <li>FIN</li>
 * <li>ACK+FIN</li>
 * <li>ACK</li>
 * <li>SYN+ACK</li>
 * </ol>
 * 
 * <p>
 * Oeffnen eines Server-Sockets: Das Kommando 'passiv oeffnen' ist das Ereignis, das den Uebergang vom Zustand 1
 * (CLOSED) zu 2 (LISTEN) ausloest. <br />
 * Mit dem Empfangen eines SYN-Segments wird der Uebergang zum Zustand 3 ausgeloest und zugleich ein SYN+ACK Segment
 * verschickt. <br />
 * Nach dem Empfang eines ACK-Segments wechselt der Server-Socket in den Zustand 5 (ESTABLISHED).
 * </p>
 * 
 * <p>
 * Oeffnen eines Client-Sockets: Das Kommando 'aktiv oeffnen' loest den Uebergang zum Zustand 4 aus. Zugleich wird ein
 * SYN-Segment gesendet. <br />
 * Mit dem Empfang eines SYN+ACK-Segments geht der Socket in den Zustand 5 (ESTABLISHED) ueber und sendet zugleich noch
 * ein ACK-Segment.
 * </p>
 * 
 * <p>
 * Initiieren des Verbindungsabbaus: Mit dem Kommando 'schliessen' wird der Verbindungsabbau gestartet. Zugleich wird
 * ein FIN-Segment gesendet und der Socket wechselt zum Zustand 8 (FIN_WAIT_1). <br />
 * Mit dem Empfang eines ACK+FIN-Segments erfolgt der Uebergang zum Zustand 11 und zugleich wird ein ACK-Segment
 * versendet. <br />
 * Nach einem Timeout, geht der Socket in den Zustand 1 (CLOSED) ueber.
 * </p>
 * 
 * <p>
 * Reaktion auf Verbindungsabbau: Erhaelt der Socket ein FIN-Segment, wechselt er aus Zustand 5 in Zustand 6
 * (CLOSE_WAIT) und sendet zugleich ein ACK-Segment. <br />
 * Durch das Kommando 'schliessen' geht der Socket in den Zustand 7 ueber und sendet zugleich ein FIN-Segment <br />
 * Mit dem Empfang eines ACK-Segments geht der Socket in Zustand 1 (CLOSED) ueber.
 * </p>
 */
public class TCPSocket extends Socket implements Runnable {
    private static Logger LOG = LoggerFactory.getLogger(TCPSocket.class);

    /** Die Zustaende, die ein Socket einnehmen kann */
    protected static final int CLOSED = 1, LISTEN = 2, SYN_RCVD = 3, SYN_SENT = 4, ESTABLISHED = 5, CLOSE_WAIT = 6,
            LAST_ACK = 7, FIN_WAIT_1 = 8, FIN_WAIT_2 = 9, CLOSING = 10, TIME_WAIT = 11;

    /**
     * Der aktuelle Zustand des Sockets. Zustandsuebergaenge werden in den Methoden verbinden(), schliessen() und
     * hinzufuegen() ausgeloest.
     */
    private int zustand = CLOSED;
    private boolean timeout;
    private boolean stopThread;
    private boolean closeSocket;

    /**
     * Anzahl der maximalen Sendeversuche, in Fehlersituationen, d. h., dass ein Segment nicht bestaetigt wurde.
     */
    protected static final int MAX_SENDEVERSUCHE = 3;

    /** Maximum Segment Size (MSS) */
    protected final static int MSS = 1460;

    /** Puffer fuer eingegangene Segmente. */
    private LinkedList<TcpSegment> puffer = new LinkedList<>();
    private LinkedList<String> receivedPayload = new LinkedList<>();

    private static long synInitValue = 1l;
    /**
     * dieses Attribut ist immer die Sequenznummer des als naechstes zu sendenden Segments. Die Sequenznummer wird
     * waehrend des Veringudngsaufbaus erhoeht, wenn das SYN-Flag gesetzt ist und wenn die Verbindung hergestellt ist,
     * wenn in dem Segment Nutzdaten verschickt werden.
     */
    private long nextSendSequenceNumber = synInitValue++ % (long) Math.pow(2, 32) * 1_000_000l;
    private long lastSentSequenceNumber;

    /**
     * dieses Attribut ist immer die zuletzt bestaetigte entfernte Sequenznummer <br />
     */
    private long remoteSequenceNumber = 0;

    /**
     * Konstruktor ruft den Konstruktor der Oberklasse auf. Ausserdem wird das Attribut protokoll mit dem TCP
     * initialisiert. <br />
     * Der Konstruktor ist <b> nicht blockierend</b>.
     * 
     * @param betriebssystem
     * @param zielAdresse
     * @param zielPort
     * @throws VerbindungsException
     */
    public TCPSocket(InternetKnotenBetriebssystem betriebssystem, String zielAdresse, int zielPort)
            throws VerbindungsException {
        super(betriebssystem, zielAdresse, zielPort, IpPaket.TCP);
        LOG.trace("INVOKED-2 (" + this.hashCode() + ") " + getClass() + " (TCPSocket), constr: TCPSocket("
                + betriebssystem + "," + zielAdresse + "," + zielPort + ")");
    }

    /**
     * Konstruktor ruft den Konstruktor der Oberklasse auf. Ausserdem wird das Attribut protokoll mit dem TCP
     * initialisiert. <br />
     * Der Konstruktor ist <b> nicht blockierend</b>.
     * 
     * @param betriebssystem
     * @param zielAdresse
     * @param zielPort
     * @throws VerbindungsException
     */
    public TCPSocket(InternetKnotenBetriebssystem betriebssystem, int lokalerPort) throws VerbindungsException {
        super(betriebssystem, lokalerPort, IpPaket.TCP);
        LOG.trace("INVOKED-2 (" + this.hashCode() + ") " + getClass() + " (TCPSocket), constr: TCPSocket("
                + betriebssystem + "," + lokalerPort + ")");
    }

    /**
     * Mit dieser Methode werden die Standardfelder des Kopfteils eines Segments gefuellt und dann wird es versendet.
     * <br />
     * Das heisst, dass
     * <ul>
     * <li>lokaler Port und</li>
     * <li>entfernter Port
     * </ul>
     * gesetzt werden.
     * 
     * @param repeat
     */
    private void sendeSegment(TcpSegment segment, boolean repeat) {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), sendeSegment(" + segment + ")");
        segment.setQuellPort(lokalerPort);
        segment.setZielPort(zielPort);
        if (repeat) {
            segment.setSeqNummer(lastSentSequenceNumber);
        } else {
            segment.setSeqNummer(nextSendSequenceNumber);
            nextSendSequenceNumber = nextSequenceNumber(segment);
        }
        lastSentSequenceNumber = segment.getSeqNummer();
        protokoll.senden(zielIp, segment);
    }

    /**
     * Methode zur Initialisierung des Verbindungsaufbaus und die Sequenznummer wird mit einer Zufallszahl
     * initialisiert. Ausserdem wird der Inhalt des Puffers geloescht. Abhaengig davon, ob der Socket-Modus AKTIV oder
     * PASSIV ist erfolgen verschiedene Zustandsuebergaenge. <br>
     * Diese Methode <b>blockiert</b>, bis der Verbindungsaufbau erfolgreich abgeschlossen ist, oder eine
     * VerbindungsException ausgeloest wurde. <br />
     * Die Methode kann nicht zweimal gleichzeitig aufgerufen werden. Der zweite Aufruf wartet so lange mit der
     * Ausfuehrung, bis der erste Aufruf komplett abgeschlossen und der Monitor dieser Klasse wieder frei gegeben wird.
     * <ol>
     * <li>passiver Modus: (Oeffnen eines Server-Sockets) Der Aufruf dieser Methode im PASSIV-Modus loest automatisch
     * den Uebergang vom Zustand CLOSED zu LISTEN aus. <br />
     * Mit dem Empfangen eines SYN-Segments wird der Uebergang zum Zustand SYN_RCVD ausgeloest, die
     * Acknowledge-Sequenznummer initialisiert und ein SYN+ACK Segment verschickt. <br />
     * Nach dem Empfang eines ACK-Segments wechselt der Server-Socket in den Zustand ESTABLISHED.</li>
     * <li>aktiver Modus: (Oeffnen eines Client-Sockets) Der Aufruf dieser Methode loest den Versand eines SYN-Segments
     * und damit den Uebergang zum Zustand SYN_SENT aus. <br />
     * Mit dem Empfang eines SYN+ACK-Segments geht der Socket in den Zustand ESTABLISHED ueber, initialisiert die
     * Acknowledge Sequenznummer und sendet zugleich noch ein ACK-Segment.</li>
     * </ol>
     * 
     * @throws VerbindungsException
     *             wenn ein Fehler auftritt bzw. ein unerwartetes Segment waehrend des Verbindungsaufbaus empfangen
     *             wird.
     * @throws TimeOutException
     *             wenn eine Antwort nicht innerhalb der maximalen Round-Trip-Time empfangen wird.
     */
    public synchronized void verbinden() throws VerbindungsException, TimeOutException {
        LOG.debug("initiate new tcp socket connection");
        stopThread = false;
        closeSocket = false;
        new Thread(this).start();

        while (zustand != ESTABLISHED && !closeSocket && !stopThread) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }
        if (closeSocket) {
            if (timeout) {
                throw new TimeOutException(messages.getString("sw_tcpsocket_msg2"));
            } else if (modus == AKTIV) {
                throw new VerbindungsException(messages.getString("sw_tcpsocket_msg3"));
            }
        } else if (zustand == ESTABLISHED) {
            LOG.debug("[port={}] connection established.", lokalerPort);
        }
    }

    protected void connect() {
        puffer.clear();
        if (modus == PASSIV) {
            connectServerMode();
        } else {
            connectClientMode();
        }
    }

    protected void connectServerMode() {
        zustand = LISTEN;
        LOG.debug("INFO (" + this.hashCode() + "): verbinden() [passiver Modus], Socket: " + this.toString());
        while (!closeSocket && !stopThread && zustand == LISTEN) {
            synchronized (puffer) {
                if (puffer.size() < 1) {
                    try {
                        puffer.wait();
                    } catch (InterruptedException e) {}
                } else
                    break;
            }
        }

        long sendezeit = Long.MAX_VALUE;
        for (int i = 0; !closeSocket && !stopThread && i <= MAX_SENDEVERSUCHE && zustand != ESTABLISHED; i++) {
            synchronized (puffer) {
                if (puffer.size() < 1) {
                    try {
                        puffer.wait(defaultTimeout());
                    } catch (InterruptedException e) {}
                }
            }

            if (puffer.size() >= 1) {
                sendezeit = System.currentTimeMillis();

                TcpSegment segment = (TcpSegment) puffer.removeFirst();
                if (zustand == LISTEN && segment.isSyn()) {
                    // Initialisierung der zunaechst zu sendenden
                    // ACK-Nummer anhand der empfangenen
                    // Sequenznummer
                    remoteSequenceNumber = nextSequenceNumber(segment);

                    zustand = SYN_RCVD;

                    TcpSegment tmp = new TcpSegment();
                    tmp.setSyn(true);
                    sendeAck(segment, tmp);
                } else if (zustand == SYN_RCVD && segment.isAck()) {
                    try {
                        eintragenPort();
                        zustand = ESTABLISHED;
                    } catch (SocketException e) {
                        LOG.debug("Port for new socket could not be registered.", e);
                        closeSocket = true;
                    }
                } else {
                    closeSocket = true;
                }
            } else if (System.currentTimeMillis() - sendezeit > defaultTimeout()) {
                timeout = true;
                closeSocket = true;
            }
        }
    }

    protected void connectClientMode() {
        try {
            eintragenPort();
            LOG.debug("INFO (" + this.hashCode() + "): verbinden() [aktiver Modus], Socket: " + this.toString());
            for (int i = 0; !closeSocket && !stopThread && zustand != ESTABLISHED && i < MAX_SENDEVERSUCHE; i++) {
                TcpSegment tmp = new TcpSegment();
                tmp.setSyn(true);

                sendeSegment(tmp, i > 0);
                zustand = SYN_SENT;
                synchronized (puffer) {
                    if (puffer.size() < 1) {
                        try {
                            puffer.wait(defaultTimeout());
                        } catch (InterruptedException e) {}
                    }
                }
                if (!closeSocket && !stopThread && puffer.size() >= 1) {
                    TcpSegment segment = (TcpSegment) puffer.removeFirst();
                    if (zustand == SYN_SENT && segment.isAck() && segment.isSyn()) {
                        // Initialisierung der zunaechst zu sendenden
                        // ACK-Nummer anhand der empfangenen
                        // Sequenznummer
                        remoteSequenceNumber = nextSequenceNumber(segment);
                        zustand = ESTABLISHED;
                        sendeAck(segment, null);
                    } else {
                        beenden();
                    }
                } else if (zustand != CLOSED) {
                    timeout = true;
                    closeSocket = true;
                }
            }
            if (zustand != ESTABLISHED) {
                closeSocket = true;
            }
        } catch (SocketException e1) {
            closeSocket = true;
            LOG.debug("Port for socket could not be registered.", e1);
        }
    }

    /**
     * Diese Methode erstellt die entsprechende Anzahl von Segmenten zur Uebertragung einer Nachricht und gibt diese in
     * einer Liste zurueck. Das letzte Segment wird mit dem Flag 'Ende' markiert.
     * 
     * @author carsten
     * @param daten
     *            - Datenstring, der in den Segmenten als Nutzdaten uebertragen werden soll
     * @return - Gibt eine Liste mit den erstellten TcpSegmenten zurueck.
     */
    protected LinkedList<TcpSegment> erstelleSegmente(String daten) {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), erstelleSegment(" + daten + ")");
        LinkedList<TcpSegment> segmenteListe;
        int paketeAnzahl;
        TcpSegment segment;

        paketeAnzahl = Math.max(1, (int) Math.ceil((float) daten.length() / (float) MSS));
        segmenteListe = new LinkedList<TcpSegment>();
        for (int i = 1; i <= paketeAnzahl; i++) {
            segment = new TcpSegment();

            if (daten.length() < MSS) {
                segment.setDaten(daten);
            } else {
                segment.setDaten(daten.substring(0, MSS));
                daten = daten.substring(MSS);
            }

            if (i == paketeAnzahl) {
                segment.setPush(true);
            }
            segmenteListe.add(segment);
        }
        return segmenteListe;
    }

    /**
     * Mit dieser Methode wird eine Nachricht auf Segmente aufgeteilt und versendet. Bevor das naechste Segment
     * verschickt wird, blockiert die Methode bis zur Bestaetigung mit einem ACK-Segment. Es wird also ein
     * Stop-and-Wait-Algorithmus umgesetzt. <br />
     * Diese Methode ist <b>synchronized</b>, weil sonst die durchgaengig aufsteigende Sequenznummer der Segmente nicht
     * gewaehrleistet wird.
     * 
     * @throws VerbindungsException
     *             wenn beim Aufruf keine Verbindung hergestellt ist oder waehrend der Uebertragung die Verbindung
     *             beendet wird.
     * @throws TimeOutException
     *             wenn eine Bestaetigung nicht rechtzeitig eintrifft
     */
    public synchronized void senden(String nachricht) throws VerbindungsException, TimeOutException {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), senden(" + nachricht + ")");
        TcpSegment segment;
        boolean bestaetigt = true;
        long versendeZeitpunkt = Long.MAX_VALUE;
        long rtt;

        if (zustand != ESTABLISHED) {
            LOG.debug("EXCEPTION: " + getClass() + " (" + this.hashCode() + "); zustand=" + zustand);
            beenden();
            throw new VerbindungsException(messages.getString("sw_tcpsocket_msg6"));
        }

        LinkedList<TcpSegment> liste = erstelleSegmente(nachricht);

        // Die erstellten Segmente werden verschickt
        // und auf die Bestaetigung gewartet, bevor das
        // naechste Segment verschickt wird.
        ListIterator<TcpSegment> it = liste.listIterator();
        while (it.hasNext() && !closeSocket && !stopThread && zustand == ESTABLISHED) {
            bestaetigt = false;
            segment = (TcpSegment) it.next();

            // Versand eines einzelnen Segments mit dem
            // Stop-and-Wait-Algorithmus
            // Wenn diese Schleife beendet ist, muss das Segment entweder
            // verschickt und der Empfang bestaetigt worden sein und damit
            // auch die Sequenznummer inkrementiert sein oder der Versand
            // war nicht erfolgreich
            for (int i = 0; !closeSocket && !stopThread && i < MAX_SENDEVERSUCHE && !bestaetigt; i++) {
                if (zustand != ESTABLISHED) {
                    // Wenn die Verbindung zwischenzeitlich unterbrochen
                    // wurde, wird eine Verbindungsexception ausgeloest.
                    closeSocket = true;
                    throw new VerbindungsException(messages.getString("sw_tcpsocket_msg7"));
                }

                sendeSegment(segment, i > 0);
                versendeZeitpunkt = System.currentTimeMillis();

                // In dieser Schleife werden alle eingehenden
                // Segmente geprueft, ob sie das ACK-Segment und
                // damit die Bestaetigung fuer das versendete
                // Segment darstellen. Alle anderen Segmente
                // werden verworfen.
                do {
                    synchronized (puffer) {
                        if (puffer.size() < 1) {
                            try {
                                puffer.wait(defaultTimeout());
                            } catch (InterruptedException e) {
                                LOG.debug("", e);
                            }
                        } else if (!bestaetigt) {
                            if (puffer.getFirst().isAck()) {
                                TcpSegment antwort = puffer.removeFirst();
                                if (antwort.getAckNummer() == nextSendSequenceNumber) {
                                    bestaetigt = true;
                                }
                            }
                        }
                    }
                    rtt = System.currentTimeMillis() - versendeZeitpunkt;
                } while (!bestaetigt && (rtt < defaultTimeout()) && zustand == ESTABLISHED && !closeSocket
                        && !stopThread);
            }
            if (!bestaetigt && zustand != CLOSED && !stopThread) {
                LOG.debug("[port={}] message '{}' could not be transferred. socket will be closed.", lokalerPort,
                        nachricht);
                schliessen();
                throw new TimeOutException(messages.getString("sw_tcpsocket_msg8"));
            }
        }
    }

    /**
     * Beim Aufruf dieser Methode werden die eingehenden TCP-Segmente zu einer Nachricht zusammen gefuegt und wenn das
     * Ende der Nachricht erreicht ist, wird diese zurueck gegeben. Das Ende einer Nachricht wird hier mit dem Flag
     * 'Ende' gekennzeichnet. <br />
     * Diese Methode ist <b>blockierend</b>.
     * 
     * @return gibt den zusammengesetzten empfangenen Datenstring zurueck. Wenn die Verbindung vor Eingang einer
     *         Nachricht geschlossen wurde, wird null zurueck gegeben.
     * @throws VerbindungsException
     *             - wird geworfen, wenn beim Aufruf keine Verbindung hergestellt ist
     * @throws TimeOutException
     *             - wird geworfen, wenn die entfernte Anwendung nicht mehr reagiert oder Verbindung unterbrochen wurde.
     */
    public String empfangen() throws VerbindungsException, TimeOutException {
        return empfangen(0);
    }

    protected int defaultTimeout() {
        return MAX_SENDEVERSUCHE * Verbindung.holeRTT();
    }

    /**
     * Beim Aufruf dieser Methode werden die eingehenden TCP-Segmente zu einer Nachricht zusammen gefuegt und wenn das
     * Ende der Nachricht erreicht ist, wird diese zurueck gegeben. Das Ende einer Nachricht wird hier mit dem Flag
     * 'Ende' gekennzeichnet. <br />
     * Diese Methode ist <b>blockierend</b>.
     * 
     * @return gibt den zusammengesetzten empfangenen Datenstring zurueck. Wenn die Verbindung vor Eingang einer
     *         Nachricht geschlossen wurde, wird null zurueck gegeben.
     * @throws VerbindungsException
     *             - wird geworfen, wenn beim Aufruf keine Verbindung hergestellt ist
     * @throws TimeOutException
     *             - wird geworfen, wenn die entfernte Anwendung nicht mehr reagiert oder Verbindung unterbrochen wurde.
     */
    public String empfangen(long timeoutMillis) throws VerbindungsException, TimeOutException {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), empfangen()");

        if (zustand != ESTABLISHED) {
            throw new VerbindungsException(messages.getString("sw_tcpsocket_msg9"));
        }

        long startTime = System.currentTimeMillis();
        synchronized (receivedPayload) {
            if (receivedPayload.size() < 1) {
                try {
                    receivedPayload.wait(timeoutMillis);
                } catch (InterruptedException e) {}
            }
        }
        long stopTime = System.currentTimeMillis();
        String data = null;
        if (zustand == ESTABLISHED && !receivedPayload.isEmpty()) {
            synchronized (receivedPayload) {
                data = receivedPayload.removeFirst();
            }
        } else if (stopTime - startTime >= timeoutMillis) {
            throw new TimeOutException(messages.getString("sw_tcpsocket_msg10"));
        }
        return data;
    }

    protected void listen() {
        StringBuffer nachricht = new StringBuffer();
        while (!closeSocket && !stopThread && zustand == ESTABLISHED) {
            TcpSegment segment = null;
            synchronized (puffer) {
                if (puffer.size() < 1) {
                    try {
                        puffer.wait(Verbindung.holeRTT());
                    } catch (InterruptedException e) {}
                } else {
                    segment = (TcpSegment) puffer.getFirst();
                }
            }

            // waehrend des Empfangs werden keine ACK-Segmente
            // verarbeitet. Diese werden nur beim Versenden von
            // Nachrichten genutzt. Daher wartet diese Methode dann
            // auf das naechste eintreffende Segment.
            if (!closeSocket && !stopThread && zustand == ESTABLISHED && null != segment && !segment.isAck()) {
                synchronized (puffer) {
                    puffer.remove(segment);
                    puffer.notifyAll();
                }
                // ist das Segment schon bestaetigt worden?
                long ack = nextSequenceNumber(segment);
                if (ack <= remoteSequenceNumber) {
                    sendeAck(segment, null);
                } else if (ack > remoteSequenceNumber) {
                    sendeAck(segment, null);
                    remoteSequenceNumber = ack;
                    nachricht.append(segment.getDaten());
                }

                if (segment.isPush()) {
                    synchronized (receivedPayload) {
                        receivedPayload.add(nachricht.toString());
                        nachricht = new StringBuffer();
                        receivedPayload.notifyAll();
                    }
                }
            }
        }
        synchronized (receivedPayload) {
            receivedPayload.notifyAll();
        }
        LOG.debug("[port={}] stop listening for incoming data.", lokalerPort);
    }

    /**
     * Mit dieser Methode wird ein Verbindungsabbau mit dem FIN-Flag eingeleitet. <b>Diese Methode darf nicht
     * blockieren, wenn sich der Socket im Zustand LISTEN befindet!</b> <br />
     * Das Verhalten zum Schliessen des Sockets ist vom aktuellen Zustand abhaengig:
     * <ol>
     * <li>LISTEN: Der Zustand wird einfach auf CLOSED gesetzt, weiter passiert nichts.</li>
     * <li>ESTABLISHED: Dann wird ein FIN-Segment verschickt und in den Zustand FIN_WAIT_1 gewechselt. <br />
     * Mit dem Empfang eines ACK-Segments erfolgt der Zustandsuebergang zu FIN_WAIT_2 ohne eine Aktion. <br />
     * Nach dem Empfang eines FIN-Segments wird ein ACK-Segment versendet und in den Zustand TIME_WAIT gewechselt.
     * <br />
     * und zugleich wird ein ACK-Segment versendet. <br />
     * Nach einem Timeout, geht der Socket in den Zustand CLOSED ueber. <br />
     * Eine weitere Moeglichkeit ist der Uebergang vom Zustand FIN_WAIT_1 zu TIME_WAIT bei Empfang eines
     * FIN+ACK-Segments. Dann wird ein ACK zurueckgeschickt. <br />
     * Die dritte Moeglichkeit ist, das im Zustand FIN_WAIT_1 ein FIN-Segment empfangen wird. Dann wird ein ACK-Segment
     * verschickt und in den Zustand CLOSING gewechselt. Mit dem Empfang eines ACK-Segments erfolgt der Uebergang in den
     * Zustand TIME_WAIT.</li>
     * <li>SYN_RCVD: Verhalten wie in Zustand ESTABLISHED.</li>
     * <li>CLOSE_WAIT: Wenn der entfernte Socket zuerst den Verbindungsabbau eingeleitet hat, befindet sich dieser
     * Socket bereits im Zustand CLOSE_WAIT. Auch in diesem Fall wird ein FIN-Segment versendet. Damit erfolgt der
     * Uebergang zum Zustand LAST_ACK. Wenn das FIN-Segment bestaetigt erfolgt der Uebergang in den Zustand CLOSED ohne
     * weitere Aktionen.</li>
     * </ol>
     * 
     * Initiieren des Verbindungsabbaus: Mit dem Kommando 'schliessen' wird der Verbindungsabbau gestartet. Diese
     * Methode blockiert so lange, bis der Endzustand CLOSED erreicht wurde! Wenn beim Datenaustausch zum
     * Verbindungsabbau etwas nicht funktioniert, wird der Socket einseitig geschlossen
     */
    public void schliessen() {
        LOG.debug("close tcp socket");
        closeSocket = true;
        synchronized (puffer) {
            puffer.notifyAll();
        }
    }

    public void run() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), run()");
        LOG.debug("start socket connection mode: {}", modus == PASSIV ? "server" : "client");
        connect();
        LOG.debug("[port={}] start listening for incoming messages", lokalerPort);
        listen();
        LOG.debug("[port={}] close socket", lokalerPort);
        close();
        LOG.debug("[port={}] port closed", lokalerPort);
        austragenPort();
    }

    protected void close() {
        if (!stopThread && zustand != LISTEN && zustand != CLOSED) {
            TcpSegment tmp = new TcpSegment();
            tmp.setFin(true);
            switch (zustand) {
            case ESTABLISHED:
            case SYN_RCVD:
                sendeSegment(tmp, false);
                zustand = FIN_WAIT_1;
                break;
            case SYN_SENT:
                zustand = CLOSED;
                synchronized (puffer) {
                    puffer.notifyAll();
                }
                break;
            case CLOSE_WAIT:
                sendeSegment(tmp, false);
                zustand = LAST_ACK;
                break;
            }

            for (int i = 0; !stopThread && zustand != CLOSED && i < 5; i++) {

                synchronized (puffer) {
                    if (puffer.size() < 1) {
                        try {
                            puffer.wait(defaultTimeout());
                        } catch (InterruptedException e) {}
                    }
                    if (zustand == TIME_WAIT) {
                        puffer.clear();
                        zustand = CLOSED;
                    } else if (puffer.size() >= 1) {
                        tmp = (TcpSegment) puffer.removeFirst();

                        switch (zustand) {
                        case LAST_ACK:
                            if (tmp.isAck())
                                zustand = CLOSED;
                            break;
                        case CLOSING:
                            if (tmp.isAck())
                                zustand = TIME_WAIT;
                            break;
                        case FIN_WAIT_2:
                            if (tmp.isFin()) {
                                sendeAck(tmp, null);
                                zustand = TIME_WAIT;
                            }
                            break;
                        case FIN_WAIT_1:
                            if (tmp.isAck() && tmp.isFin()) {
                                sendeAck(tmp, null);
                                zustand = TIME_WAIT;
                            } else if (tmp.isAck()) {
                                zustand = FIN_WAIT_2;
                            } else if (tmp.isFin()) {
                                sendeAck(tmp, null);
                                zustand = CLOSING;
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Methode zum Senden der Bestaetigung eines TCP-Segments. Dazu wird anhand des Attibuts ackNummer zunaechst
     * geprueft, ob das Segment bestaetigt werden kann. Dann wird die Sequenznummer des uebergebenen Segments um eins
     * erhoeht und in das Acknowledge-Feld des Kopfteils im neuen Acknowledge-Segment geschrieben. Es werden also
     * <ul>
     * <li>das ACK-Flag und</li>
     * <li>die Acknowledge-Sequenznummer initialisiert</li>
     * </ul>
     * Zur weiteren Bearbeitung wird das zu sendende Segment an die Methode sendeSegment(TcpSegment) weitergegeben.
     * 
     * @param empfangSegment
     *            das empfangene Segment, dessen Empfang bestaetigt werden soll.
     * @param sendeSegment
     *            das zu sendende Segment (kann bereits vorab initialisiert werden), wenn dieser Parameter 'null' ist,
     *            wird ein neues Segment erstellt.
     */
    private void sendeAck(TcpSegment empfangSegment, TcpSegment sendeSegment) {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), sendeAck(" + empfangSegment + ","
                + sendeSegment + ")");
        if (sendeSegment == null) {
            sendeSegment = new TcpSegment();
        }

        sendeSegment.setAck(true);
        sendeSegment.setAckNummer(nextSequenceNumber(empfangSegment));
        sendeSegment(sendeSegment, false);
    }

    static long nextSequenceNumber(TcpSegment segment) {
        long nextNumber = segment.getSeqNummer();
        if (segment.isSyn() || segment.isFin()) {
            nextNumber++;
        }
        nextNumber += StringUtils.length(segment.getDaten());
        nextNumber %= 4_294_967_296l;
        return nextNumber;
    }

    /**
     * In dieser Methode werden die eingehenden Segmente in den Puffer eingefuegt. Ausserdem wird der Zustandsuebergang
     * von ESTABLISHED nach CLOSE_WAIT ausgeloest und die Methode zum Schliessen des Sockets beim Empfang eines
     * FIN-Segments aufgerufen.
     */
    public void hinzufuegen(String startIp, int startPort, Object segment) {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), hinzufuegen(" + startIp + ","
                + startPort + "," + segment + ")");
        TcpSegment tcpSegment = (TcpSegment) segment;

        if (zustand == LISTEN) {
            zielPort = startPort;
            zielIp = startIp;
        }

        if (zustand == ESTABLISHED && tcpSegment.isFin()) {
            sendeAck(tcpSegment, null);
            zustand = CLOSE_WAIT;
            synchronized (puffer) {
                puffer.notifyAll();
            }
        } else {
            synchronized (puffer) {
                puffer.addLast(tcpSegment);
                puffer.notifyAll();
            }
        }
    }

    /**
     * Diese Methode wird beim Wechsel vom Aktions- zum Entwurfsmodus aufgerufen, damit moeglicherweise blockierte
     * Threads beendet werden koennen. Hier wird der Zustand auf CLOSED gesetzt und Threads, die auf den Puffer warten,
     * aufgeweckt. Es findet kein ordentlicher Verbindungsaufbau statt! Der Zustand des Sockets ist anschliessend
     * unbestimmt. Daher sollte dieser Socket nicht weiter verwendet werden. <br />
     * Die Methode ist <b>nicht blockierend</b>!
     */
    public void beenden() {
        LOG.debug("stop tcp socket thread");
        stopThread = true;
        synchronized (puffer) {
            puffer.notifyAll();
        }
    }

    /**
     * Zur Abfrage, ob eine Verbindung aufgebaut ist. <br />
     * Diese Methode ist <b>nicht blockierend</b>.
     * 
     * @return ob der aktuelle Zustand ESTABLISHED ist
     */
    public boolean istVerbunden() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), istVerbunden(), port: "
                + this.holeLokalenPort());
        return (zustand == ESTABLISHED);
    }

    /* workaround function for more ugly exception provoking original code */
    public boolean isSortOfConnected() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (TCPSocket), isSortOfConnected(), port: "
                + this.holeLokalenPort());
        if (zustand >= 5 && zustand <= 11) {
            return true;
        } else {
            return false;
        }
    }

    public String getStateAsString() {
        switch (this.zustand) {
        case CLOSED:
            return "CLOSED";
        case LISTEN:
            return "LISTEN";
        case SYN_RCVD:
            return "SYN_RCVD";
        case SYN_SENT:
            return "SYN_SENT";
        case ESTABLISHED:
            return "ESTABLISHED";
        case CLOSE_WAIT:
            return "CLOSE_WAIT";
        case LAST_ACK:
            return "LAST_ACK";
        case FIN_WAIT_1:
            return "FIN_WAIT_1";
        case FIN_WAIT_2:
            return "FIN_WAIT_2";
        case CLOSING:
            return "CLOSING";
        case TIME_WAIT:
            return "TIME_WAIT";
        default:
            return "<unknown>";
        }
    }
}
