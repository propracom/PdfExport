

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import tools.pdf.PdfTemplateConfig;
import tools.pdf.PdfTemplateExport;
import tools.pdf.PdfTemplateExport.TableFields;

public class PdfTemplateExportTest {

    public static void main(String[] args) throws Exception {
        
        String maindir = System.getProperty("user.dir");
        String configFile = "config/PdfTemplateConfig-example.xml";
        
        PdfTemplateExport PdfTemplate = new PdfTemplateExport(new PdfTemplateConfig(configFile, PdfTemplateConfig.ResourceMode.CLASSPATH));
        

        File outputFile = new File(maindir+"/target/test.pdf");
        Map<String, Object> textFields = new HashMap<String, Object>();

        textFields.put("text1", "一二三四五");

        Map<String, Object> barcodeFields = new HashMap<String, Object>();
        barcodeFields.put("barcode1", "12345");

        Map<String, Object> qrcodeFields = new HashMap<String, Object>();
        qrcodeFields.put("qrcode1", "一二三四五");

        Map<String, Object> imgFields = new HashMap<String, Object>();



        outputFile.createNewFile();
        PdfTemplate.export(new FileOutputStream(outputFile), textFields, barcodeFields, qrcodeFields, imgFields, null,null,null);
    }

}
