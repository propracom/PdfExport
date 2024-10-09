package tools.pdf;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class ConfigErrorHandler implements ErrorHandler {
    public void warning(SAXParseException e) throws SAXException {
        throw new RuntimeException(e);
    }

    public void error(SAXParseException e) throws SAXException {
        throw new RuntimeException(e);
    }

    public void fatalError(SAXParseException e) throws SAXException {
        throw new RuntimeException(e);
    }
}
