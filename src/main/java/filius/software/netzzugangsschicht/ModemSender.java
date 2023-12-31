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
package filius.software.netzzugangsschicht;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.hardware.knoten.Modem;
import filius.software.ProtokollThread;
import filius.software.system.ModemFirmware;

/**
 * Diese Klasse dient dazu, die Verbindung zu einer zweiten Instanz eines Modems ueber eine TCP/IP-Verbindung
 * herzustellen. Hiermit wird die Verbindung zu dem virtuellen Rechnernetz ueberwacht.
 */
public class ModemSender extends ProtokollThread<EthernetFrame> {
    private static Logger LOG = LoggerFactory.getLogger(ModemSender.class);

    /**
     * Der Stream zur Uebertragung der Frames ueber den TCP/IP-Socket
     */
    private ObjectOutputStream out;

    /**
     * Der Konstruktor, in dem der zu beobachtendende Puffer an den Konstruktor der Oberklasse weitergibt und die
     * Firmware und Socket initialisiert.
     */
    public ModemSender(ModemFirmware firmware, OutputStream out) {
        super(((Modem) firmware.getKnoten()).getErstenAnschluss().holeEingangsPuffer());
        LOG.trace("INVOKED-2 (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (ModemAnschlussBeobachterIntern), constr: ModemAnschlussBeobachterIntern(" + firmware + "," + out
                + ")");

        try {
            this.out = new ObjectOutputStream(out);
        } catch (IOException e) {
            LOG.debug("", e);
        }
    }

    /**
     * Methode zur Verarbeitung von Frames aus dem angeschlossenen virtuellen Rechnernetzes. <br />
     * Hier werden eingehende Frames lediglich in den Stream geschrieben.
     */
    protected void verarbeiteDatenEinheit(EthernetFrame datenEinheit) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (ModemAnschlussBeobachterIntern), verarbeiteDatenEinheit(" + datenEinheit.toString() + ")");

        try {
            out.writeObject(datenEinheit);
            out.flush();
        } catch (IOException e) {
            LOG.debug("", e);
        }
    }
}
