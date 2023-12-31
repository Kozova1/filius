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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.exception.SocketException;
import filius.exception.TimeOutException;
import filius.exception.VerbindungsException;
import filius.rahmenprogramm.I18n;
import filius.software.system.InternetKnotenBetriebssystem;
import filius.software.vermittlungsschicht.IP;
import filius.software.vermittlungsschicht.IpPaket;

/**
 * Dieser Klasse ist Oberklasse von UDPSocket und TCPSocket. Da die Funktionalitaet nicht als eigener Thread
 * implementiert ist, kann immer nur entweder gesendet oder empfangen werden!
 * 
 */
public abstract class Socket implements SocketSchnittstelle, I18n {
    private static Logger LOG = LoggerFactory.getLogger(Socket.class);

    /**
     * Konstanten zur Spezifizierung des Modus des Sockets
     */
    protected static final int AKTIV = 1, PASSIV = 2;

    /**
     * Ob der Socket im Passiv- oder Aktiv-Modus betrieben wird. AKTIV bedeutet, dass der Verbindungsaufbau zu einem
     * anderen Socket initiiert wird, PASSIV bedeutet, dass der Socket auf eingehende Verbindungsanfragen wartet. Dieses
     * Attribut sollte also mit einer der Konstanten AKTIV / PASSIV initialisiert werden!
     */
    protected int modus;

    /** Das mit dem Socket verbundene Transport-Protokoll */
    protected TransportProtokoll protokoll;

    /** der lokal belegte Port */
    protected int lokalerPort = -1;

    protected String quellIp;

    /**
     * die IP-Adresse des Knotens, auf dem der entfernte Socket bereitgestellt wird
     */
    protected String zielIp;

    /** TCP-/UDP-Port auf dem entfernten Rechner */
    protected int zielPort;

    /**
     * Konstruktor zur Initialisierung eines Client-Sockets. Dazu wird das mit dem Socket verbundene Transport-Protokoll
     * initialisiert. <br />
     * Der Konstruktor ist <b> nicht blockierend</b>.
     * 
     * @param betriebssystem
     * @param zielAdresse
     * @param zielPort
     * @throws VerbindungsException
     */
    public Socket(InternetKnotenBetriebssystem betriebssystem, String zielAdresse, int zielPort, int transportProtokoll)
            throws VerbindungsException {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), constr: Socket(" + betriebssystem
                + "," + zielAdresse + "," + zielPort + "," + transportProtokoll + ")");
        String ip;

        modus = AKTIV;
        if (transportProtokoll == IpPaket.TCP)
            protokoll = betriebssystem.holeTcp();
        else
            protokoll = betriebssystem.holeUdp();

        ip = ipCheck(zielAdresse);
        if (ip != null) {
            this.zielIp = ip;
        } else {
            try {
                this.zielIp = betriebssystem.holeDNSClient().holeIPAdresse(zielAdresse);
            } catch (TimeOutException e) {
                LOG.debug("could not resolve destination IP address for new socket to " + zielAdresse, e);
            }
        }
        if (zielIp != null) {
            this.zielPort = zielPort;
        } else {
            throw new VerbindungsException(messages.getString("sw_socket_msg1"));
        }
    }

    /**
     * Konstruktor zur Initialisierung eines Client-Sockets. Dazu wird der Konstruktor verwendet, der keinen lokalen
     * Port uebergeben bekommt. <br />
     * Der Konstruktor ist <b> nicht blockierend</b>.
     * 
     * @param betriebssystem
     * @param zielAdresse
     * @param zielPort
     * @param lokalerPort
     *            ein bestimmter lokaler Port, der beim Betriebssystem reserviert werden soll. Dieser Parameter wird nur
     *            dann verwendet, wenn der Wert groesser 0 ist.
     * @throws VerbindungsException
     */
    public Socket(InternetKnotenBetriebssystem betriebssystem, String zielAdresse, int zielPort, int transportProtokoll,
            int lokalerPort) throws VerbindungsException {
        this(betriebssystem, zielAdresse, zielPort, transportProtokoll);
        LOG.trace("INVOKED-2 (" + this.hashCode() + ") " + getClass() + " (Socket), constr: Socket(" + betriebssystem
                + "," + zielAdresse + "," + zielPort + "," + transportProtokoll + "," + lokalerPort + ")");

        this.lokalerPort = lokalerPort;
    }

    /**
     * Hier wird das Attribut protokoll initialisiert. <br />
     * Der Konstruktor ist <b> nicht blockierend</b>.
     * 
     * @param betriebssystem
     * @param zielAdresse
     * @param zielPort
     * @throws VerbindungsException
     */
    public Socket(InternetKnotenBetriebssystem betriebssystem, int lokalerPort, int transportProtokoll)
            throws VerbindungsException {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), constr: Socket(" + betriebssystem
                + "," + lokalerPort + "," + transportProtokoll + ")");
        modus = PASSIV;
        if (transportProtokoll == IpPaket.TCP) {
            protokoll = betriebssystem.holeTcp();
        } else {
            protokoll = betriebssystem.holeUdp();
        }
        this.lokalerPort = lokalerPort;
    }

    /**
     * leerer Konstruktor wird von Unterklassen benoetigt
     */
    public Socket() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), constr: Socket()");
    }

    /** Zum Versenden einer Nachricht ueber den Socket */
    public abstract void senden(String nachricht) throws VerbindungsException, TimeOutException;

    /**
     * Zum Empfangen einer Nachricht ueber den Socket
     */
    public abstract String empfangen() throws VerbindungsException, TimeOutException;

    /**
     * Zum Empfangen einer Nachricht ueber den Socket
     * 
     * @param timeoutMillis
     */
    public abstract String empfangen(long timeoutMillis) throws VerbindungsException, TimeOutException;

    /**
     * Test, ob der uebergebene String eine gueltige IP-Adresse ist. Zurueckgegeben wird die IP-Adresse ohne
     * ueberfluessige Nullen.
     * 
     * @param ip
     *            die zu pruefende IP-Adresse
     * @return
     */
    protected String ipCheck(String ip) {
        return IP.ipCheck(ip);
    }

    /**
     * Methode zum Versenden eines Segments an die Ziel-IP-Adresse mit Hilfe des Transport-Protokolls
     * 
     * @param segment
     *            das zu sendende Segment
     */
    protected void sende(Segment segment) {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), sende(" + segment + ")");
        protokoll.senden(zielIp, quellIp, segment);
    }

    protected void sende(String zielIp, String quellIp, Segment segment) {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), sende(" + segment + ")");
        protokoll.senden(zielIp, quellIp, segment);
    }

    /**
     * Methode zum Schliessen eines Sockets. Damit ist gegebenenfalls auch verbunden, dass der Port freigegeben wird.
     * 
     */
    public abstract void schliessen();

    public abstract void verbinden() throws VerbindungsException, TimeOutException;

    public abstract boolean istVerbunden();

    /**
     * Methode zum Eintragen eines Ports. Wenn es Teil eines Server-Sockets ist, wird der Start- und Ziel-Port
     * 
     * @throws SocketException
     * 
     */
    protected void eintragenPort() throws SocketException {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), eintragenPort()");
        SocketSchnittstelle socket = null;

        if (lokalerPort == -1) {
            lokalerPort = protokoll.reserviereFreienPort(this);
            LOG.debug("[port={}] new client port registered.", lokalerPort);
        } else {
            if (protokoll.isUsed(lokalerPort)) {
                socket = protokoll.holeSocket(lokalerPort);
            }
            if (socket != null && socket instanceof ServerSocket) {
                ServerSocket serverSocket = (ServerSocket) socket;
                serverSocket.eintragenSocket(this);
                LOG.debug("[port={}] new server port registered for remote endpoint {}:{}.", lokalerPort,
                        holeZielIPAdresse(), holeZielPort());
            } else if (!protokoll.reservierePort(lokalerPort, this)) {
                throw new SocketException();
            }
        }
    }

    /**
     * Methode zum austragen des Ports des Sockets. Entweder wird der Port im Betriebssystem freigegeben (modus ==
     * AKTIV) oder der Socket wird in der Liste, die durch einen Server-Socket verwaltet wird, entfernt (modus ==
     * PASSIV).
     */
    protected void austragenPort() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), austragenPort()");
        SocketSchnittstelle socket = null;

        try {
            socket = protokoll.holeSocket(lokalerPort);
        } catch (SocketException e) {}

        if (socket != null && socket instanceof ServerSocket) {
            ServerSocket serverSocket = (ServerSocket) socket;
            serverSocket.austragenSocket(this);
        } else if (socket != null) {
            protokoll.gibPortFrei(lokalerPort);
        }
    }

    /**
     * Methode fuer den Zugriff auf die IP-Adresse des entfernten Sockets
     */
    public String holeZielIPAdresse() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), holeZielIPAdresse()");
        return zielIp;
    }

    /**
     * Methode fuer den Zugriff auf den TCP-Port des entfernten Sockets
     */
    public int holeZielPort() {
        LOG.trace("INVOKED (" + this.hashCode() + ") " + getClass() + " (Socket), holeZielPort()");
        return zielPort;
    }

    public int holeLokalenPort() {
        return lokalerPort;
    }

    public void bind(String quellIp) {
        this.quellIp = quellIp;
    }

    public abstract String getStateAsString();
}
