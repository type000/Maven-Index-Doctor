package io.github.mavenindexdoctor.application;

import io.github.mavenindexdoctor.domain.DiagnosticIssue;
import io.github.mavenindexdoctor.domain.DiagnosticIssueType;
import io.github.mavenindexdoctor.domain.DiagnosticSeverity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MavenSettingsScanner {

    public List<DiagnosticIssue> scan(Path settingsPath) throws IOException {
        if (Files.notExists(settingsPath)) {
            return List.of(new DiagnosticIssue(
                    DiagnosticIssueType.SETTINGS_NOT_FOUND,
                    DiagnosticSeverity.INFO,
                    "Maven settings.xml not found",
                    "Maven will use its default repository and proxy configuration.",
                    settingsPath,
                    "No action is required unless custom mirrors or proxies are expected."
            ));
        }

        try {
            Document document = createDocumentBuilderFactory().newDocumentBuilder().parse(settingsPath.toFile());
            List<DiagnosticIssue> issues = new ArrayList<>();
            inspectMirrors(document, settingsPath, issues);
            inspectProxies(document, settingsPath, issues);
            return List.copyOf(issues);
        } catch (ParserConfigurationException | SAXException exception) {
            return List.of(new DiagnosticIssue(
                    DiagnosticIssueType.SETTINGS_INVALID,
                    DiagnosticSeverity.ERROR,
                    "Invalid Maven settings.xml",
                    exception.getMessage(),
                    settingsPath,
                    "Correct the XML before refreshing Maven indexes."
            ));
        }
    }

    private DocumentBuilderFactory createDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private void inspectMirrors(Document document, Path settingsPath, List<DiagnosticIssue> issues) {
        NodeList mirrors = document.getElementsByTagNameNS("*", "mirror");
        boolean centralCovered = false;
        for (int index = 0; index < mirrors.getLength(); index++) {
            Element mirror = (Element) mirrors.item(index);
            String mirrorOf = childText(mirror, "mirrorOf");
            if (coversCentral(mirrorOf)) {
                centralCovered = true;
                issues.add(new DiagnosticIssue(
                        DiagnosticIssueType.CENTRAL_MIRROR_CONFIGURED,
                        DiagnosticSeverity.INFO,
                        "Maven Central is covered by a mirror",
                        "Mirror " + childText(mirror, "id") + " routes Central to " + childText(mirror, "url") + '.',
                        settingsPath,
                        "Verify that the mirror serves Maven index and artifact requests."
                ));
            }
        }
        if (mirrors.getLength() > 0 && !centralCovered) {
            issues.add(new DiagnosticIssue(
                    DiagnosticIssueType.CENTRAL_MIRROR_MISSING,
                    DiagnosticSeverity.WARNING,
                    "Configured mirrors do not cover Maven Central",
                    "Dependencies may come from Central while IDEA updates a different repository index.",
                    settingsPath,
                    "Review mirrorOf rules and refresh Maven indexes after changes."
            ));
        }
    }

    private void inspectProxies(Document document, Path settingsPath, List<DiagnosticIssue> issues) {
        NodeList proxies = document.getElementsByTagNameNS("*", "proxy");
        for (int index = 0; index < proxies.getLength(); index++) {
            Element proxy = (Element) proxies.item(index);
            if (!Boolean.parseBoolean(childText(proxy, "active"))) {
                continue;
            }
            String host = childText(proxy, "host");
            String port = childText(proxy, "port");
            if (host.isBlank()) {
                issues.add(new DiagnosticIssue(
                        DiagnosticIssueType.INVALID_PROXY,
                        DiagnosticSeverity.ERROR,
                        "Active Maven proxy has no host",
                        "The active proxy entry cannot route Maven or index downloads.",
                        settingsPath,
                        "Set a proxy host or deactivate the proxy."
                ));
            } else {
                issues.add(new DiagnosticIssue(
                        DiagnosticIssueType.ACTIVE_PROXY,
                        DiagnosticSeverity.WARNING,
                        "Maven proxy is active",
                        "Maven traffic is routed through " + host + (port.isBlank() ? "" : ':' + port) + '.',
                        settingsPath,
                        "Confirm that the proxy allows Maven repository index downloads."
                ));
            }
        }
    }

    private boolean coversCentral(String mirrorOf) {
        boolean included = false;
        for (String rawToken : mirrorOf.split(",")) {
            String token = rawToken.trim();
            if ("!central".equals(token)) {
                return false;
            }
            if ("central".equals(token) || "*".equals(token) || token.startsWith("external:")) {
                included = true;
            }
        }
        return included;
    }

    private String childText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && (localName.equals(child.getLocalName()) || localName.equals(child.getNodeName()))) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
}
