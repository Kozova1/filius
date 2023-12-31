/*
 ** This file is part of Filius, a network construction and simulation software.
 ** 
 ** Originally created at the University of Siegen, Institute "Didactics of Informatics and E-Learning" by a students'
 * project group: members (2006-2007): André Asschoff, Johannes Bade, Carsten Dittich, Thomas Gerding, Nadja Haßler,
 * Ernst Johannes Klebert, Michell Weyer supervisors: Stefan Freischlad (maintainer until 2009), Peer Stechert Project
 * is maintained since 2010 by Christian Eibl <filius@c.fameibl.de> and Stefan Freischlad Filius is free software: you
 * can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 2 of the License, or (at your option) version 3.
 ** 
 ** Filius is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 ** 
 ** You should have received a copy of the GNU General Public License along with Filius. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package filius.software.netzzugangsschicht;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.hardware.NetzwerkInterface;
import filius.rahmenprogramm.nachrichten.Lauscher;
import filius.software.ProtokollThread;
import filius.software.vermittlungsschicht.ArpPaket;
import filius.software.vermittlungsschicht.IcmpPaket;
import filius.software.vermittlungsschicht.IpPaket;

/**
 * Diese Klasse ueberwacht die Eingangspuffer von Netzwerkkarten.
 */
public class EthernetThread extends ProtokollThread<EthernetFrame> {
    private static Logger LOG = LoggerFactory.getLogger(EthernetThread.class);

    /** die Netzwerkkarte, deren Anschluss ueberwacht wird */
    private NetzwerkInterface netzwerkInterface;

    /** die Ethernet-Schicht */
    private Ethernet ethernet;

    /**
     * Der Konstruktor zur Initialisierung des zu ueberwachenden Puffers und der Ethernet-Schicht und der Netzwerkkarte
     */
    public EthernetThread(Ethernet ethernet, NetzwerkInterface nic) {
        super(nic.getPort().holeEingangsPuffer());
        LOG.trace("INVOKED-2 (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EthernetThread), constr: EthernetThread(" + ethernet + "," + nic + ")");

        this.ethernet = ethernet;
        this.netzwerkInterface = nic;
    }

    /**
     * Hier werden die Nutzdaten des ankommenden Frames entweder in den Puffer fuer IP-Pakete oder fuer ARP-Pakete
     * geschrieben.
     */
    protected void verarbeiteDatenEinheit(EthernetFrame etp) {
        LOG.trace("INVOKED (" + this.hashCode() + ", T" + this.getId() + ") " + getClass()
                + " (EthernetThread), verarbeiteDateneinheit(" + etp.toString() + ")");
        // record receipt (independent of further processing)
        Lauscher.getLauscher().addDatenEinheit(netzwerkInterface.getMac(), etp);

        // only process in case of correct MAC address, i.e., this packet is
        // addressed for this NIC (or broadcast)
        // otherwise stop processing:
        if (!etp.getZielMacAdresse().equalsIgnoreCase("FF:FF:FF:FF:FF:FF") // broadcast
                && !etp.getZielMacAdresse().equals(this.netzwerkInterface.getMac())) {
            return;
        }

        if (etp.getTyp().equals(EthernetFrame.IP)) {
            IpPaket ipPacket = (IpPaket) etp.getDaten();
            if (ipPacket.getProtocol() == IcmpPaket.ICMP_PROTOCOL) {
                synchronized (ethernet.holeICMPPuffer()) {
                    ethernet.holeICMPPuffer().add((IcmpPaket) ipPacket);
                    ethernet.holeICMPPuffer().notifyAll();
                    // 'all' means: Terminal ping command (if any in this instance) and default network packet
                    // processing
                }
            } else {
                synchronized (ethernet.holeIPPuffer()) {
                    ethernet.holeIPPuffer().add(ipPacket);
                    ethernet.holeIPPuffer().notify();
                }
            }
        } else if (etp.getTyp().equals(EthernetFrame.ARP)) {
            // if ARP packet is not addressed to this specific NIC, but possibly
            // another NIC of this node, then return
            // (this is meant to prevent routers from responding to all ARP
            // packets meant for some of their NICs
            // without even having received the packet on this specific NIC,
            // i.e., without physical connection)
            String zielIp = ((ArpPaket) etp.getDaten()).getTargetIP();
            if (!zielIp.equals(netzwerkInterface.getIp()) && !"0.0.0.0".equals(netzwerkInterface.getIp())) {
                LOG.debug("received ARP packet from (" + ((ArpPaket) etp.getDaten()).getSenderIP() + ","
                        + ((ArpPaket) etp.getDaten()).getSenderMAC() + ") is not addressed to this NIC ("
                        + netzwerkInterface.getIp() + "," + netzwerkInterface.getMac() + ") and will be discarded");
                return;
            }
            // otherwise process ARP packet
            synchronized (ethernet.holeARPPuffer()) {
                ethernet.holeARPPuffer().add((ArpPaket) etp.getDaten());
                ethernet.holeARPPuffer().notify();
            }
        } else {
            LOG.debug("ERROR (" + this.hashCode() + "): Paket konnte nicht zugeordnet werden");
        }
    }

}
