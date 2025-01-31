package io.metersphere.performance.parse;

import io.metersphere.commons.constants.FileType;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.performance.parse.xml.reader.JmeterDocumentParser;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class EngineSourceParserFactory {
    public static final boolean IS_TRANS = true;


    public static EngineSourceParser createEngineSourceParser(String type) {
        final FileType engineType = FileType.valueOf(type);

        if (FileType.JMX.equals(engineType)) {
            return new JmeterDocumentParser();
        }

        return null;
    }

    public static Document getDocument(InputStream source) throws DocumentException {
        SAXReader reader = new SAXReader();
        if (!IS_TRANS) {
            reader.setXMLFilter(EngineSourceParserFactory.getFilter());
        }
        return reader.read(source);
    }

    public static byte[] formatXml(Document document) throws Exception {
        OutputFormat format = OutputFormat.createPrettyPrint();
        try (
                ByteArrayOutputStream out = new ByteArrayOutputStream();
        ) {
            XMLWriter xw = new XMLWriter(out, format);
            xw.setEscapeText(IS_TRANS);
            xw.write(document);
            xw.flush();
            xw.close();
            return out.toByteArray();
        } catch (IOException e) {
            LogUtil.error(e);
        }
        return new byte[0];
    }

    public static XMLFilterImpl getFilter() {
        return new XMLFilterImpl() {
            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                String text = new String(ch, start, length);

                if (length == 1) {
                    if (StringUtils.equals("&", text)) {
                        char[] escape = "&amp;".toCharArray();
                        super.characters(escape, start, escape.length);
                        return;
                    }
                    if (StringUtils.equals("\"", text)) {
                        char[] escape = "&quot;".toCharArray();
                        super.characters(escape, start, escape.length);
                        return;
                    }
                    if (StringUtils.equals("'", text)) {
                        char[] escape = "&apos;".toCharArray();
                        super.characters(escape, start, escape.length);
                        return;
                    }
                    if (StringUtils.equals("<", text)) {
                        char[] escape = "&lt;".toCharArray();
                        super.characters(escape, start, escape.length);
                        return;
                    }
                    if (StringUtils.equals(">", text)) {
                        char[] escape = "&gt;".toCharArray();
                        super.characters(escape, start, escape.length);
                        return;
                    }
                }

                super.characters(ch, start, length);
            }
        };
    }
}
