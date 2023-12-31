/*
 ** This file is part of Filius, a network construction and simulation software.
 ** 
 ** Originally created at the University of Siegen, Institute "Didactics of
 ** Informatics and E-Learning" by a students' project group:
 **     members (2006-2007): 
 **         AndrÃ© Asschoff, Johannes Bade, Carsten Dittich, Thomas Gerding,
 **         Nadja HaÃŸler, Ernst Johannes Klebert, Michell Weyer
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
package filius.gui.documentation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.Section;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPHeaderCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import filius.gui.GUIContainer;
import filius.gui.netzwerksicht.GUIKnotenItem;
import filius.hardware.NetzwerkInterface;
import filius.hardware.knoten.InternetKnoten;
import filius.rahmenprogramm.I18n;
import filius.rahmenprogramm.SzenarioVerwaltung;
import filius.rahmenprogramm.nachrichten.Lauscher;
import filius.software.Anwendung;
import filius.software.dns.DNSServer;
import filius.software.dns.ResourceRecord;
import filius.software.system.Betriebssystem;
import filius.software.system.InternetKnotenBetriebssystem;

public class ReportGenerator {
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.BLACK);
    private static final Font SMALL_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.BLACK);
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.BLACK);
    private static final Font DEFAULT_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
    private Lauscher lauscher;
    private static ReportGenerator singleton;
    private int sectionNoLevel1;
    private int sectionNoLevel2;

    ReportGenerator() {}

    public static ReportGenerator getInstance() {
        if (null == singleton) {
            singleton = new ReportGenerator();
            singleton.lauscher = Lauscher.getLauscher();
        }
        return singleton;
    }

    private void resetSections() {
        sectionNoLevel1 = 0;
        sectionNoLevel2 = 0;
    }

    public void generateReport(String pdfFilepath) throws DocumentException, IOException {
        Document document = initDocument(pdfFilepath);
        resetSections();

        addOverviewSection(document);
        document.add(Chunk.NEXTPAGE);

        addComponentConfigSection(document);
        document.add(Chunk.NEXTPAGE);

        addNetworkTrafficSection(document);

        closeDocument(document);
    }

    void addOverviewSection(Document document) throws BadElementException, IOException, DocumentException {
        createSection(document, I18n.messages.getString("report_overview"), 1);

        Image img = Image.getInstance(GUIContainer.getGUIContainer().createNetworkImage(), null);
        float percent = (document.right() - document.left()) / img.getWidth() * 100;
        img.scalePercent(percent);
        document.add(img);
    }

    void addComponentConfigSection(Document document) throws BadElementException, IOException, DocumentException {
        createSection(document, I18n.messages.getString("report_component_config"), 1);

        List<GUIKnotenItem> components = GUIContainer.getGUIContainer().getKnotenItems();
        for (GUIKnotenItem item : components) {
            if (item.getKnoten() instanceof InternetKnoten) {
                InternetKnotenBetriebssystem systemSoftware = (InternetKnotenBetriebssystem) item.getKnoten()
                        .getSystemSoftware();

                createSection(document, item.getKnoten().holeAnzeigeName(), 2);
                createSection(document, I18n.messages.getString("report_base_config"), 3);

                PdfPTable configTable = new PdfPTable(2);
                configTable.setHorizontalAlignment(Element.ALIGN_LEFT);
                configTable.setTotalWidth(new float[] { 180, 360 });
                configTable.setLockedWidth(false);

                addConfigParam(I18n.messages.getString("jhostkonfiguration_msg5"), systemSoftware.getStandardGateway(),
                        configTable);
                addConfigParam(I18n.messages.getString("jhostkonfiguration_msg6"), systemSoftware.getDNSServer(),
                        configTable);
                if (systemSoftware instanceof Betriebssystem) {
                    addConfigParam(I18n.messages.getString("jhostkonfiguration_msg7"),
                            String.valueOf(((Betriebssystem) systemSoftware).isDHCPKonfiguration()), configTable);
                }
                document.add(configTable);
                document.add(Chunk.NEWLINE);

                InternetKnoten node = (InternetKnoten) item.getKnoten();
                PdfPTable interfaceTable = new PdfPTable(3);
                interfaceTable.setHorizontalAlignment(Element.ALIGN_LEFT);
                interfaceTable.setTotalWidth(new float[] { 180, 180, 180 });
                interfaceTable.setLockedWidth(false);
                addHeaderCell(I18n.messages.getString("jhostkonfiguration_msg9"), interfaceTable);
                addHeaderCell(I18n.messages.getString("jhostkonfiguration_msg3"), interfaceTable);
                addHeaderCell(I18n.messages.getString("jhostkonfiguration_msg4"), interfaceTable);

                createSection(document, I18n.messages.getString("report_nics"), 3);
                for (NetzwerkInterface networkInterface : node.getNetzwerkInterfaces()) {
                    addCell(networkInterface.getMac(), interfaceTable);
                    addCell(networkInterface.getIp(), interfaceTable);
                    addCell(networkInterface.getSubnetzMaske(), interfaceTable);
                }

                document.add(interfaceTable);
                document.add(Chunk.NEWLINE);

                if (systemSoftware instanceof Betriebssystem) {
                    createSection(document, I18n.messages.getString("report_apps"), 3);
                    Chunk chunk = new Chunk(I18n.messages.getString("installationsdialog_msg3") + " ", BOLD_FONT);
                    document.add(chunk);
                    Anwendung[] apps = ((Betriebssystem) systemSoftware).holeArrayInstallierteSoftware();
                    String[] appNames = new String[apps.length];
                    for (int i = 0; i < apps.length; i++) {
                        appNames[i] = apps[i].holeAnwendungsName();
                    }
                    String appList = StringUtils.join(appNames, ", ");
                    chunk = new Chunk(StringUtils.isBlank(appList) ? "-" : appList, DEFAULT_FONT);
                    document.add(chunk);
                    document.add(Chunk.NEWLINE);
                    document.add(Chunk.NEWLINE);
                }

                DNSServer dnsServer = (DNSServer) systemSoftware.holeSoftware("filius.software.dns.DNSServer");
                if (dnsServer != null) {
                    createSection(document, I18n.messages.getString("report_dns_rr"), 3);
                    PdfPTable dnsTable = new PdfPTable(3);
                    dnsTable.setHorizontalAlignment(Element.ALIGN_LEFT);
                    dnsTable.setTotalWidth(new float[] { 180, 60, 260 });
                    dnsTable.setLockedWidth(false);

                    addHeaderCell(I18n.messages.getString("report_domain"), dnsTable);
                    addHeaderCell(I18n.messages.getString("report_type"), dnsTable);
                    addHeaderCell(I18n.messages.getString("report_data"), dnsTable);

                    for (ResourceRecord resourceRecord : dnsServer.holeResourceRecords()) {
                        addCell(resourceRecord.getDomainname(), dnsTable);
                        addCell(resourceRecord.getType(), dnsTable);
                        addCell(resourceRecord.getRdata(), dnsTable);
                    }
                    document.add(dnsTable);
                    document.add(Chunk.NEWLINE);
                }

                createSection(document, I18n.messages.getString("report_routing_table"), 3);
                PdfPTable forwardingTable = new PdfPTable(4);
                forwardingTable.setHorizontalAlignment(Element.ALIGN_LEFT);
                forwardingTable.setTotalWidth(new float[] { 180, 180, 180, 180 });
                forwardingTable.setLockedWidth(false);

                addHeaderCell(I18n.messages.getString("jweiterleitungstabelle_msg3"), forwardingTable);
                addHeaderCell(I18n.messages.getString("jweiterleitungstabelle_msg4"), forwardingTable);
                addHeaderCell(I18n.messages.getString("jweiterleitungstabelle_msg5"), forwardingTable);
                addHeaderCell(I18n.messages.getString("jweiterleitungstabelle_msg6"), forwardingTable);

                for (String[] routeEntry : systemSoftware.getWeiterleitungstabelle().holeTabelle()) {
                    for (int i = 0; i < routeEntry.length; i++) {
                        addCell(routeEntry[i], forwardingTable);
                    }
                }
                document.add(forwardingTable);
                document.add(Chunk.NEWLINE);
            }
        }
    }

    private void addConfigParam(String key, String value, PdfPTable table) {
        addHeaderCell(key, table);
        addCell(value, table);
    }

    private void addCell(String value, PdfPTable table) {
        PdfPCell cell = new PdfPCell(new Phrase(value, DEFAULT_FONT));
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addHeaderCell(String key, PdfPTable table) {
        PdfPCell header = new PdfPHeaderCell();
        header.setBorder(Rectangle.NO_BORDER);
        header.setPhrase(new Phrase(key, BOLD_FONT));
        table.addCell(header);
    }

    void addNetworkTrafficSection(Document document) throws BadElementException, IOException, DocumentException {
        String[] columnHeader = lauscher.getHeader();
        Collection<String> interfaceIDs = lauscher.getInterfaceIDs();
        if (interfaceIDs.size() > 0) {
            createSection(document, I18n.messages.getString("report_network_traffic"), 1);
        }
        for (String interfaceId : interfaceIDs) {
            String hostname = "Unknown";
            String ipAddress = "0.0.0.0";
            for (GUIKnotenItem item : GUIContainer.getGUIContainer().getKnotenItems()) {
                if (item.getKnoten() instanceof InternetKnoten) {
                    InternetKnoten node = (InternetKnoten) item.getKnoten();
                    NetzwerkInterface nic = node.getNetzwerkInterfaceByMac(interfaceId);
                    if (nic != null) {
                        hostname = item.getKnoten().holeAnzeigeName();
                        ipAddress = nic.getIp();
                        break;
                    }
                }
            }
            createSection(document, hostname + " - " + ipAddress, 2);

            PdfPTable table = new PdfPTable(columnHeader.length);
            table.setTotalWidth(new float[] { 20, 60, 80, 80, 40, 50, 180 });
            table.setLockedWidth(true);

            for (int i = 0; i < columnHeader.length; i++) {
                PdfPCell header = new PdfPHeaderCell();
                header.setBackgroundColor(new BaseColor(230, 240, 255));
                header.setBorder(Rectangle.NO_BORDER);
                header.setPhrase(new Phrase(columnHeader[i], SMALL_BOLD_FONT));
                table.addCell(header);
            }

            Object[][] data = lauscher.getDaten(interfaceId, true, 0);
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < data[i].length; j++) {
                    PdfPCell cell = new PdfPCell(new Phrase((String) data[i][j], SMALL_FONT));
                    cell.setBorder(Rectangle.NO_BORDER);
                    if (i % 2 == 1) {
                        cell.setBackgroundColor(new BaseColor(240, 240, 240));
                    }
                    table.addCell(cell);
                }
            }
            document.add(table);
            document.add(Chunk.NEWLINE);
        }
    }

    private void createSection(Document document, String title, int level) throws DocumentException {
        ArrayList<Integer> numbers = new ArrayList<Integer>();
        if (level == 1) {
            numbers.add(++sectionNoLevel1);
            sectionNoLevel2 = 0;
        } else if (level == 2) {
            numbers.add(++sectionNoLevel2);
            numbers.add(sectionNoLevel1);
        }
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16 - 2 * level, BaseColor.DARK_GRAY);
        Paragraph sectionHeader = Section.constructTitle(new Paragraph(title, font), numbers, level,
                Section.NUMBERSTYLE_DOTTED);
        document.add(sectionHeader);
    }

    void closeDocument(Document document) {
        document.close();
        GUIContainer.getGUIContainer().updateViewport();
    }

    Document initDocument(String pdfFilepath) throws DocumentException, FileNotFoundException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(pdfFilepath));

        document.open();

        String storagePath = SzenarioVerwaltung.getInstance().holePfad();
        String title;
        if (storagePath != null) {
            String filename = new File(storagePath).getName();
            if (filename.contains(".")) {
                filename = filename.substring(0, filename.lastIndexOf('.'));
            }
            title = I18n.messages.getString("report_title") + ": " + filename;
        } else {
            title = I18n.messages.getString("report_title");
        }
        document.addTitle(title);
        document.addCreator("Filius (www.lernsoftware-filius.de)");

        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new BaseColor(30, 50, 120));
        Paragraph paragraph = new Paragraph(title, font);
        paragraph.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(paragraph);
        if (null != storagePath) {
            document.add(new Chunk(storagePath, SMALL_FONT));
        }
        document.add(Chunk.NEWLINE);
        return document;
    }

}
