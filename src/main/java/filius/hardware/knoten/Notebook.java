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
package filius.hardware.knoten;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import filius.rahmenprogramm.I18n;

public class Notebook extends Host implements I18n {
    private static Logger LOG = LoggerFactory.getLogger(Notebook.class);

    public static final String TYPE = messages.getString("hw_notebook_msg1");

    @Override
    public String holeHardwareTyp() {
        return TYPE;
    }

    /**
     * Konstruktor wird nur zu Hilfszwecken in verschiedenen Klassen benutzt
     */
    public Notebook() {
        super();
        LOG.trace("INVOKED-2 (" + this.hashCode() + ") " + getClass() + " (Notebook), constr: Notebook()");

        this.setName(messages.getString("hw_notebook_msg2"));
    }

}
