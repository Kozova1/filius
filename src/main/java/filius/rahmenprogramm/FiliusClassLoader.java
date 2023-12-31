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
package filius.rahmenprogramm;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author freischlad
 * 
 * 
 *         Aus der Java-Doc fuer die Klasse ClassLoader: <br />
 *         The ClassLoader class uses a delegation model to search for classes and resources. Each instance of
 *         ClassLoader has an associated parent class loader. When requested to find a class or resource, a ClassLoader
 *         instance will delegate the search for the class or resource to its parent class loader before attempting to
 *         find the class or resource itself. The virtual machine's built-in class loader, called the "bootstrap class
 *         loader", does not itself have a parent but may serve as the parent of a ClassLoader instance.
 */
public class FiliusClassLoader extends URLClassLoader {
    private static Logger LOG = LoggerFactory.getLogger(FiliusClassLoader.class);

    private static FiliusClassLoader classLoader;

    protected FiliusClassLoader(ClassLoader parent) throws MalformedURLException {
        super(new URL[] { new File(Information.getInformation().getAnwendungenPfad()).toURI().toURL() }, parent);
        LOG.trace("INVOKED-2 (" + this.hashCode() + ") " + getClass() + ", constr: FiliusClassLoader(" + parent + ")");
    }

    public static FiliusClassLoader getInstance(ClassLoader parent) {
        LOG.trace("INVOKED (static) filius.rahmenprogramm.FiliusClassLoader, getInstance()");
        if (classLoader == null) {
            try {
                classLoader = new FiliusClassLoader(parent);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        return classLoader;
    }
}
