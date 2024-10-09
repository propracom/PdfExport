package tools.pdf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class PdfTemplateConfig implements Serializable {

    private static final long serialVersionUID = 4953379134268835529L;
    private static final String xsdFile = "config/PdfTemplateConfig.xsd";
    
    /** 範本路徑，如果ClassPath或者WebRoot模式，則表示相對路徑 */
    private String config;
    /** 範本資源載入方式 */
    private ResourceMode resourceMode;

    /**
     * 建構
     * 
     */
    public PdfTemplateConfig() {
        
    }
    
    /**
     * 建構
     * 
     * @param configFile 範本ClassPath相對路徑
     */
    public PdfTemplateConfig(String configFile) {
        this(configFile, ResourceMode.FILE);
    }

    /**
     * 建構
     * 
     * @param charset 編碼
     * @param configFile 範本路徑，如果ClassPath或者WebRoot模式，則表示相對路徑
     * @param resourceMode 範本資源載入方式
     */
    public PdfTemplateConfig(String configFile, ResourceMode resourceMode) {    
        this.resourceMode = resourceMode;
        
        switch (this.resourceMode) {
            case CLASSPATH:
                this.config = getResourcesPath(configFile) + configFile;                
                break;
            case WEB_ROOT:
                this.config = getWebRootPath() + configFile;
                break;
            case FILE:
                this.config = configFile;
                break;            
            case STRING:
                break;
            default:
                break;
            }
    }

    /**
     * 讀取設定資料
     * 
     * @return
     * @throws DocumentException
     * @throws SAXParseException
     */
    public TreeMap<String, Object> readConfig() throws SAXParseException, DocumentException {
        TreeMap<String, Object> ResultCfg = new TreeMap<String, Object>();
        if (StringUtils.isNotBlank(this.getConfig())) {
            Map<String, Object> XmlValidateResult = validateByXsd(this.getConfig(), xsdFile);
            if ((boolean) XmlValidateResult.get("Result")) {
                String xml = readXML(new File(this.getConfig())).asXML();
                ResultCfg = createMapByXml(xml);
            } else {
                throw new SAXParseException((String) XmlValidateResult.get("Description"), null);
            }
        }
        return ResultCfg;
    }

    /**
     * @param xmlFile
     * @param xsdFile
     * @return XmlValidateResult 通過Schema驗證指定的xml字串是否符合結構
     */
    public static Map<String, Object> validateByXsd(String xmlFile, String xsdFile) {
        String xsdPathFile = getResourcesPath(xsdFile) + xsdFile;

        Map<String, Object> XmlValidateResult = new HashMap<String, Object>();
        // 查找支援指定模式語言的 SchemaFactory 的實現並返回它
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // 建構Schema Source
        Source schemaFile = new StreamSource(new File(xsdPathFile));
        Schema schema;
        try {
            // 解析作為模式的指定源並以模式形式返回它
            schema = factory.newSchema(schemaFile);
            // 根據Schema檢查xml文檔的處理器,建立此 Schema 的新 Validator
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ConfigErrorHandler());
            // 執行驗證
            validator.validate(new StreamSource(xmlFile));
            XmlValidateResult.put("Result", true);
        } catch (SAXException | IOException | RuntimeException e) {
            XmlValidateResult.put("Result", false);
            XmlValidateResult.put("Description", e.toString());
        }
        return XmlValidateResult;
    }

    /**
     * 通過XML轉換為Map<String,Object>
     * 
     * @param xml 為String類型的Xml
     * @return 第一個為Root節點，Root節點之後為Root的元素，如果為多層，可以通過key獲取下一層Map
     * @throws DocumentException
     */
    private static TreeMap<String, Object> createMapByXml(String xml) throws DocumentException {
        Document doc = DocumentHelper.parseText(xml);
        TreeMap<String, Object> map = new TreeMap<String, Object>();
        if (doc == null)
            return map;
        Element rootElement = doc.getRootElement();
        elementTomap(rootElement, map);
        return map;
    }

    /***
     * XmlToMap核心方法，遞迴呼叫
     * 
     * @param outele
     * @param outmap
     * @return
     */
    @SuppressWarnings("unchecked")
    private static TreeMap<String, Object> elementTomap(Element outele, TreeMap<String, Object> outmap) {
        List<Element> list = outele.elements();
        int size = list.size();
        if (size == 0) {
            String outname = outele.attributeValue("id") == null ? outele.getName() : outele.attributeValue("id");
            outmap.put(outname, outele.getTextTrim());
        } else {
            TreeMap<String, Object> innermap = new TreeMap<String, Object>();
            for (Element ele1 : list) {
                String eleName = ele1.getName();

                Object obj = innermap.get(eleName);
                if (obj == null) {
                    elementTomap(ele1, innermap);
                } else {
                    if (obj instanceof java.util.Map) {
                        List<TreeMap<String, Object>> list1 = new ArrayList<TreeMap<String, Object>>();
                        list1.add((TreeMap<String, Object>) innermap.remove(eleName));
                        elementTomap(ele1, innermap);
                        list1.add((TreeMap<String, Object>) innermap.remove(eleName));
                        innermap.put(eleName, list1);
                    } else {
                        elementTomap(ele1, innermap);
                        ((List<TreeMap<String, Object>>) obj).add(innermap);
                    }
                }
            }

            String outname = outele.attributeValue("id") == null ? outele.getName() : outele.attributeValue("id");
            outmap.put(outname, innermap);
        }
        return outmap;
    }
    
     /**
     * 獲取範本路徑，如果ClassPath或者WebRoot模式，則表示相對路徑
     * 
     * @return 範本路徑
     */
    public String getConfig() {
        return config;
    }

    /**
     * 設置範本路徑，如果ClassPath或者WebRoot模式，則表示相對路徑
     * 
     * @param config 範本路徑
     */
    public void setConfig(String config) {
        this.config = config;
    }
    
    /**
     * 獲取範本資源載入方式
     * 
     * @return 範本資源載入方式
     */
    public ResourceMode getResourceMode() {
        return resourceMode;
    }

    /**
     * 設置範本資源載入方式
     * 
     * @param resourceMode 範本資源載入方式
     */
    public void setResourceMode(ResourceMode resourceMode) {
        this.resourceMode = resourceMode;
    }
    
    /**
     * 讀取解析XML文件
     *
     * @param file XML文件
     * @return XML文檔物件
     */
    private static Document readXML(File file) {
        if (file == null) {
        	throw new RuntimeException("Xml file is null !");	
        }
        
        if (false == file.exists()) {
            throw new RuntimeException("File [" + file.getAbsolutePath() + "] not a exist!");
        }
        if (false == file.isFile()) {
            throw new RuntimeException("[" + file.getAbsolutePath() + "] not a file!");
        }

        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            // ignore
        }

        BufferedInputStream in = null;
        try {
        	in = new BufferedInputStream(new FileInputStream(file));
            return readXML(in);
        } catch (Exception e) {
        	throw new RuntimeException("File [" + file.getAbsolutePath() + "] not a exist!");
        } finally {
        	try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    /**
     * 獲取Class 或 Resource 目錄
     * @param file
     * @return
     */
	private static String getResourcesPath(String file) {
		Class<?> clazz = PdfTemplateConfig.class;
		 // 獲取當前 ClassLoader 的資源路徑
        String classPath = clazz.getClassLoader().getResource("").getPath();
        String resourcePath = clazz.getClassLoader().getResource(file).getPath();
        if ((new File(classPath + file)).exists()) {
        	return classPath;
        } else if ((new File(resourcePath + file)).exists()) {
        	return resourcePath;
        } else {
        	return "";
        }
	}

    /**
     * 獲取專案部署的Web 根目錄
     * @return
     */
    private static String getWebRootPath() {
    	Class<?> clazz = PdfTemplateConfig.class;
        String strClassName = clazz.getName();
        String strPackageName = "";
        if (clazz.getPackage() != null) {
            strPackageName = clazz.getPackage().getName();
        }
        String strClassFileName = "";
        if (!"".equals(strPackageName)) {
            strClassFileName = strClassName.substring(strPackageName.length() + 1, strClassName.length());
        } else {
            strClassFileName = strClassName;
        }
        URL url = null;
        url = clazz.getResource(strClassFileName + ".class");
        String strURL = url.toString();
        if (strURL.contains("WEB-INF")) {
            return strURL.substring(strURL.indexOf("/") + 1, strURL.lastIndexOf("WEB-INF"));
        } else {
            strURL = strURL.substring(strURL.indexOf("/") + 1, strURL.lastIndexOf("bin"));
            return strURL + "WebContent";
        }
    }
    
    /**
     * 讀取解析XML文件<br>
     * 編碼在XML中定義
     *
     * @param inputStream 
     * @return XML文檔物件
     */
    public static Document readXML(InputStream inputStream) {
    	InputSource source = new InputSource(inputStream);
    	SAXReader reader = new SAXReader();
        try {
			Document document = reader.read(source);
			return document;
		} catch (DocumentException e) {
			 throw new RuntimeException(e);
		}
    }

    /**
     * 資源載入方式列舉
     * 
     */
    public static enum ResourceMode {
        /** 從ClassPath載入範本 */
        CLASSPATH,
        /** 從File目錄載入範本 */
        FILE,
        /** 從WebRoot目錄載入範本 */
        WEB_ROOT,
        /** 從範本文本載入範本 */
        STRING;
    }    
}
