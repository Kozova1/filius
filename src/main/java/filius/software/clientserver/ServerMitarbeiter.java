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
package filius.software.clientserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.exception.VerbindungsException;
import filius.rahmenprogramm.I18n;
import filius.software.transportschicht.Socket;

/**
 * Diese Klasse implementiert einen Thread, der von einem Server fuer eine eingegangene Verbindungsanfrage erzeugt wird.
 * Damit wird der neu erstellte Socket ueberwacht.
 * 
 */
public abstract class ServerMitarbeiter extends Thread implements I18n {
    private static Logger LOG = LoggerFactory.getLogger(ServerMitarbeiter.class);

    /** Die Server-Anwendung, die diesen Mitarbeiter verwaltet */
    protected ServerAnwendung server;

    /** der Socket, der fuer den Datenaustausch verwendet wird */
    protected Socket socket;

    /** Dieses Attribut zeigt an, ob der Thread laeuft. */
    protected boolean running = false;

    /**
     * Konstruktor, in dem die zugehoerige ServerAnwendung und der Socket fuer den Datenaustausch implementiert werden.
     * 
     * @param server
     * @param socket
     */
    public ServerMitarbeiter(ServerAnwendung server, Socket socket) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (ServerMitarbeiter), constr: ServerMitarbeiter(" + server + "," + socket + ")");
        this.server = server;
        this.socket = socket;
    }

    /**
     * Mit dieser Methode werden eingehende Nachrichten verarbeitet. Sie enthaelt also die eigentliche Anwendungslogik
     * und muss daher von den Unterklassen implementiert werden.
     * 
     * @param nachricht
     */
    protected abstract void verarbeiteNachricht(String nachricht);

    /**
     * Mit dieser Methode werden Nachrichten versendet und wenn dies ohne Ausnahme (Exception) stattgefunden hat, an
     * Beobachter der Server-Anwendung weiter gegeben. <br />
     * Diese Methode ist <b>blockierend</b>
     * 
     * @param nachricht
     */
    protected void sendeNachricht(String nachricht) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (ServerMitarbeiter), sendeNachricht(" + nachricht + ")");
        try {
            socket.senden(nachricht);
            server.benachrichtigeBeobachter("<<" + nachricht);
        } catch (Exception e) {
            server.benachrichtigeBeobachter(e.getMessage());
            LOG.debug("", e);
        }
    }

    /**
     * Hier wird auf eingehende Nachrichten gewartet und diese zum einen an Beobachter der Server-Anwendung und zum
     * anderen zur Verarbeitung an die Methode verarbeitenNachricht() weiter gegeben.
     */
    public void run() {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (ServerMitarbeiter), run()");
        String nachricht = null;
        while (running) {
            try {
                if (socket.istVerbunden()) {
                    nachricht = socket.empfangen(Long.MAX_VALUE);
                }

                if (nachricht != null) {
                    server.benachrichtigeBeobachter(">>" + nachricht);
                    verarbeiteNachricht(nachricht);
                } else if (socket != null) {
                    socket.schliessen();
                    running = false;
                    server.benachrichtigeBeobachter(
                            messages.getString("sw_servermitarbeiter_msg1") + " " + socket.holeZielIPAdresse() + ":"
                                    + socket.holeZielPort() + " " + messages.getString("sw_servermitarbeiter_msg2"));
                }
                nachricht = null;
            } catch (VerbindungsException e) {
                LOG.debug("", e);
                server.benachrichtigeBeobachter(e.getMessage());
                socket.schliessen();
                running = false;
                server.entferneMitarbeiter(this);
            } catch (Exception e) {
                LOG.debug("", e);
                server.benachrichtigeBeobachter(e.getMessage());
                socket.schliessen();
                running = false;
                server.entferneMitarbeiter(this);
            }
        }
    }

    /**
     * Methode zum Starten des Threads beim Wechsel vom Entwurfs- in den Aktionsmodus. Wenn sich der Thread noch in
     * einem wartenden oder blockierten Zustand wird kein neuer Thread gestartet, sondern lediglich gewaehrleistet, dass
     * der Thread nicht beendet wird.
     */
    public void starten() {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (ServerMitarbeiter), starten()");
        if (!running) {
            running = true;
            if (!getState().equals(State.WAITING) && !getState().equals(State.BLOCKED)) {
                start();
            }
        }
    }

    /**
     * Methode zum Beenden des Threads. Wenn der Thread noch in einem wartenden oder blockierten Zustand ist, wird
     * interrupt() aufgerufen, um die Verarbeitung fortzusetzen, damit der Thread dann beendet werden kann.
     */
    public void beenden() {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (ServerMitarbeiter), beenden()");
        shutdown(false);
    }

    protected void shutdown(boolean graceful) {
        running = false;

        if (socket != null && graceful) {
            socket.schliessen();
        } else if (socket != null) {
            socket.beenden();
        }
        if (getState().equals(State.WAITING) || getState().equals(State.BLOCKED)) {
            interrupt();
        }
    }
}
