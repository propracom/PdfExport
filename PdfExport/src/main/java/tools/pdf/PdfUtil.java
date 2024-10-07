package tools.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSmartCopy;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import lib.tools.util.ObjectUtil;

public class PdfUtil {

    static int ENCRYPTION_AES_256 = PdfWriter.ENCRYPTION_AES_128;

    /**
     * 加文字浮水印
     *
     * @param inputFilePath - pdf 輸入路徑檔名
     * @param outputFilePath - pdf 輸出路徑檔名
     * @param watermark - 浮水印文字
     * @throws Exception
     * 
     */
    public static void addITextWatermark(String inputFilePath, String outputFilePath, String watermark) throws Exception {
        FileInputStream inputStream = new FileInputStream(inputFilePath);
        FileOutputStream outputStream = new FileOutputStream(outputFilePath);

        addITextWatermark(inputStream, outputStream, watermark);
    }

    /**
     * 加文字浮水印
     *
     * @param inputStream - pdf 輸入流
     * @param outputStream - pdf 輸出流
     * @param watermark - 浮水印文字
     * @throws Exception
     *
     */
    public static void addITextWatermark(InputStream inputStream, OutputStream outputStream, String watermark) throws Exception {
        byte[] outBytes = addITextWatermark(inputStream, watermark);
        outputStream.write(outBytes);
    }

    /**
     * 加文字浮水印
     *
     * @param inputStream - pdf 輸入流
     * @param watermark - 浮水印文字
     * @throws Exception
     * 
     */
    public static byte[] addITextWatermark(InputStream inputStream, String watermark) throws Exception {
        Document document = new Document(PageSize.A4);
        PdfUtil pdf = new PdfUtil();
        WatermarkStyle DefaultStyle = pdf.new WatermarkStyle(); // @@@
        DefaultStyle.setAlign(1);
        DefaultStyle.setFontName("simsun.ttc,1");
        DefaultStyle.setFontSize(64);
        DefaultStyle.setColor(Color.RED);
        DefaultStyle.setX(document.getPageSize().getWidth() / 2.0F);
        DefaultStyle.setY(document.getPageSize().getHeight() / 2.0F);
        DefaultStyle.setRotation(45.0F);
        DefaultStyle.setOverUnder(1);
        return addITextWatermark(inputStream, watermark, DefaultStyle);
    }

    /**
     * 加文字浮水印
     *
     * @param inputStream - pdf 輸入流
     * @param watermark - 浮水印文字
     * @param style - 浮水印風格
     * @return
     * @throws Exception
     * 
     */
    public static byte[] addITextWatermark(InputStream inputStream, String watermark, WatermarkStyle style) throws Exception {
        Document document = new Document(PageSize.A4);

        if (style.getAlign() == -1) {
            style.setAlign(Element.ALIGN_CENTER);
        }
        if (ObjectUtil.isNull(style.getFontName())) {
            style.setFontName("simsun.ttc,1");
        }
        if (style.getFontSize() == 0) {
            style.setFontSize(64);
        }
        if (ObjectUtil.isNull(style.getColor())) {
            style.setColor(Color.RED);
        }
        if (style.getX() == 0.0F) {
            style.setX(document.getPageSize().getWidth() / 2.0F);
        }
        if (style.getY() == 0.0F) {
            style.setY(document.getPageSize().getHeight() / 2.0F);
        }
        if (style.getOpacity() == 0.0F) {
            style.setOpacity(0.7F);
        }
        if ((style.getOverUnder() != 1) || (style.getOverUnder() != 2)) {
            style.setOverUnder(1);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PdfReader pdfReader = new PdfReader(inputStream);
        PdfStamper pdfStamper = new PdfStamper(pdfReader, bos);

        int i = 1;
        for (int pdfPageSize = pdfReader.getNumberOfPages() + 1; i < pdfPageSize; i++) {
            PdfContentByte pageContent = null;
            if (style.getOverUnder() == 1) {
                pageContent = pdfStamper.getOverContent(i);
            } else {
                pageContent = pdfStamper.getUnderContent(i);
            }
            pageContent.setGState(getPdfGState(style.getOpacity()));
            pageContent.beginText();
            pageContent.setFontAndSize(getBaseFont(style.getFontName()), style.getFontSize());
            pageContent.setColorFill(style.getColor());
            pageContent.showTextAligned(style.getAlign(), watermark, style.getX(), style.getY(), style.getRotation());
            pageContent.endText();
        }
        pdfStamper.close();

        return bos.toByteArray();
    }

    /**
     * pdf 旋轉
     * 
     * @param inputStream - 輸入流
     * @param outputStream - 輸出流
     * @param degrees - 旋轉度數, 90,180,270，正數為順時針，負數為逆時針
     * @throws IOException
     * @throws DocumentException
     */
    public static void doRotation(InputStream inputStream, OutputStream outputStream, int degrees) throws IOException, DocumentException {
        byte[] outBytes = doRotation(inputStream, degrees);
        outputStream.write(outBytes);
    }

    /**
     * pdf 旋轉
     * 
     * @param inputFilePath - pdf 輸入路徑檔名
     * @param outputFilePath - pdf 輸出路徑檔名
     * @param degrees - 旋轉度數, 90,180,270，正數為順時針，負數為逆時針
     * @throws IOException
     * @throws DocumentException
     */
    public static void doRotation(String inputFilePath, String outputFilePath, int degrees) throws IOException, DocumentException {
        FileInputStream inputStream = new FileInputStream(inputFilePath);
        FileOutputStream outputStream = new FileOutputStream(outputFilePath);
        doRotation(inputStream, outputStream, degrees);
    }

    /**
     * pdf 旋轉
     * 
     * @param inputFilePath - pdf 輸入路徑檔名
     * @param degrees - 旋轉度數, 90,180,270，正數為順時針，負數為逆時針
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static byte[] doRotation(String inputFilePath, int degrees) throws IOException, DocumentException {
        FileInputStream inputStream = new FileInputStream(inputFilePath);
        return doRotation(inputStream, degrees);
    }

    /**
     * pdf 旋轉
     * 
     * @param inputStream - 輸入流
     * @param degrees - 旋轉度數, 90,180,270，正數為順時針，負數為逆時針
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static byte[] doRotation(InputStream inputStream, int degrees) throws IOException, DocumentException {
        PdfReader pdfReader = new PdfReader(inputStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4.rotate());
        PdfCopy pc = new PdfSmartCopy(document, outputStream);
        document.open();

        int n = pdfReader.getNumberOfPages(); // 獲取原始檔案的頁數
        PdfDictionary pd;
        for (int j = 1; j <= n; j++) {
            pd = pdfReader.getPageN(j);
            pd.put(PdfName.ROTATE, new PdfNumber(degrees)); // 順時針旋轉 degrees
        }
        for (int page = 0; page < n;) {
            pc.addPage(pc.getImportedPage(pdfReader, ++page));
        }
        document.close();

        // Close document and outputStream.
        outputStream.flush();
        // document.close();
        outputStream.close();
        return outputStream.toByteArray();
    }

    /**
     * 將多個pdf合併為一個pdf，預設不依原pdf旋轉
     * 
     * @param insList - list of pdf input stream
     * @throws Exception
     *
     */
    public static byte[] doMerge(List<InputStream> insList) throws Exception {
        return doMerge(insList, false);
    }

    /**
     * 將多個pdf合併為一個pdf
     * 
     * @param insList - list of pdf input stream
     * @param whirl - 是否依原pdf旋轉
     * @throws Exception
     *
     */
    public static byte[] doMerge(List<InputStream> insList, boolean whirl) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Create document and pdfReader objects.
        Document document = new Document();
        List<PdfReader> readers = new ArrayList<PdfReader>();
        int totalPages = 0;

        // Create pdf Iterator object using inputPdfList.
        Iterator<InputStream> pdfIterator = insList.iterator();

        // Create reader list for the pdf InputStream.
        while (pdfIterator.hasNext()) {
            InputStream pdf = pdfIterator.next();
            PdfReader pdfReader = new PdfReader(pdf);
            readers.add(pdfReader);
            totalPages = totalPages + pdfReader.getNumberOfPages();
        }

        // Create writer for the outputStream
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);

        // Open document.
        document.open();

        // Contain the pdf data.
        PdfContentByte pageContentByte = writer.getDirectContent();

        PdfImportedPage pdfImportedPage;
        int currentPdfReaderPage = 1;
        Iterator<PdfReader> iteratorPDFReader = readers.iterator();

        // Iterate and process the reader list.
        while (iteratorPDFReader.hasNext()) {
            PdfReader pdfReader = iteratorPDFReader.next();
            // Create page and add content.
            while (currentPdfReaderPage <= pdfReader.getNumberOfPages()) {
                PdfDictionary pdfdict = pdfReader.getPageN(currentPdfReaderPage);
                PdfNumber rotate = pdfdict.getAsNumber(PdfName.ROTATE);
                Rectangle rect;

                if (rotate == null || rotate.intValue() == 0 || !whirl) {
                    rect = PageSize.A4;
                } else {
                    rect = PageSize.A4.rotate();
                }
                document.setPageSize(rect);
                document.newPage();
                pdfImportedPage = writer.getImportedPage(pdfReader, currentPdfReaderPage);
                pageContentByte.addTemplate(pdfImportedPage, 0, 0);
                currentPdfReaderPage++;
            }
            currentPdfReaderPage = 1;
        }

        // Close document and outputStream.
        outputStream.flush();
        document.close();
        outputStream.close();

        return outputStream.toByteArray();
    }

    /**
     * 將多個pdf合併為一個pdf
     * 
     * @param inputFileList - pdf 輸入流列表
     * @param outputFilePath - 輸出路徑檔案
     * @throws Exception
     *
     */
    public static void doMerge(List<String> inputFileList, String outputFilePath) throws Exception {
        List<InputStream> insList = new ArrayList<InputStream>();
        for (String inputPath : inputFileList) {
            FileInputStream inputStream = new FileInputStream(inputPath);
            insList.add(inputStream);
        }
        FileOutputStream outputStream = new FileOutputStream(outputFilePath);
        doMerge(insList, outputStream);
    }

    /**
     * 將多個pdf合併為一個pdf
     * 
     * @param insList - pdf 輸入流列表
     * @param outputStream - pdf 輸出流
     * @throws Exception
     *
     */
    public static void doMerge(List<InputStream> insList, OutputStream outputStream) throws Exception {
        byte[] inBytes = doMerge(insList);
        outputStream.write(inBytes);
    }

    /**
     * Pdf 加密
     * 
     * @param USER_PASSWORD - 使用者密碼
     * @param OWNER_PASSWORD - 擁有者密碼
     * @param inputStream - 輸入流
     * @param outputStream - 輸出流
     * @param permissions - 權限
     * @param encryptionType - 加密強度
     * @throws IOException
     * @throws DocumentException
     */
    public static void doProtected(String USER_PASSWORD, String OWNER_PASSWORD, InputStream inputStream, OutputStream outputStream, int permissions, int encryptionType) throws IOException, DocumentException {
        // ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        PdfReader pdfReader = new PdfReader(inputStream);
        PdfStamper pdfStamper = new PdfStamper(pdfReader, outputStream);

        PdfWriter writer = pdfStamper.getWriter(); // PdfWriter.getInstance(document, outputStream);

        writer.setEncryption(USER_PASSWORD.getBytes(), OWNER_PASSWORD.getBytes(), PdfWriter.ALLOW_PRINTING,
                PdfWriter.ENCRYPTION_AES_128); // PdfWriter.ENCRYPTION_AES_128

        pdfStamper.close();
        pdfReader.close();
    }

    /**
     * Pdf 加密
     * 
     * @param USER_PASSWORD - 使用者密碼
     * @param OWNER_PASSWORD - 擁有者密碼
     * @param inputStream - 輸入流
     * @param outputStream - 輸出流
     * @throws IOException
     * @throws DocumentException
     */
    public static void doProtected(String USER_PASSWORD, String OWNER_PASSWORD, InputStream inputStream,
            OutputStream outputStream) throws IOException, DocumentException {
        doProtected(USER_PASSWORD, OWNER_PASSWORD, inputStream, outputStream, PdfWriter.ALLOW_PRINTING,
                ENCRYPTION_AES_256); // PdfWriter.ENCRYPTION_AES_128
    }

    /**
     * Pdf 加密
     * 
     * @param USER_PASSWORD - 使用者密碼
     * @param OWNER_PASSWORD - 擁有者密碼
     * @param inputStream - 輸入流
     * @param permissions - 權限
     * @param encryptionType - 加密強度
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static byte[] doProtected(String USER_PASSWORD, String OWNER_PASSWORD, InputStream inputStream,
            int permissions, int encryptionType) throws IOException, DocumentException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        doProtected(USER_PASSWORD, OWNER_PASSWORD, inputStream, outputStream, permissions, encryptionType);
        return outputStream.toByteArray();
    }

    /**
     * Pdf 加密
     * 
     * @param USER_PASSWORD - 使用者密碼
     * @param OWNER_PASSWORD - 擁有者密碼
     * @param inputStream - 輸入流
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static byte[] doProtected(String USER_PASSWORD, String OWNER_PASSWORD, InputStream inputStream)
            throws IOException, DocumentException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        doProtected(USER_PASSWORD, OWNER_PASSWORD, inputStream, outputStream, PdfWriter.ALLOW_PRINTING,
                ENCRYPTION_AES_256); // PdfWriter.ENCRYPTION_AES_128
        return outputStream.toByteArray();
    }

    /**
     * 單個Pdf文件分割成N個文件
     * 
     * @param filepath
     * @param N
     * @throws DocumentException
     * @throws IOException
     */
    public static void split(String filepath, int N) throws DocumentException, IOException {
        Document document = null;
        PdfCopy copy = null;

        PdfReader reader = new PdfReader(filepath);
        int n = reader.getNumberOfPages();
        if (n < N) {
            System.out.println("The document does not have " + N + " pages to partition !");
            return;
        }
        int size = n / N;
        String staticpath = filepath.substring(0, filepath.lastIndexOf("\\") + 1);
        String savepath = null;

        List<String> savepaths = new ArrayList<String>();
        for (int i = 1; i <= N; i++) {
            if (i < 10) {
                savepath = filepath.substring(filepath.lastIndexOf("\\") + 1, filepath.length() - 4);
                savepath = staticpath + savepath + "0" + i + ".pdf";
                savepaths.add(savepath);
            } else {
                savepath = filepath.substring(filepath.lastIndexOf("\\") + 1, filepath.length() - 4);
                savepath = staticpath + savepath + i + ".pdf";
                savepaths.add(savepath);
            }
        }

        for (int i = 0; i < N - 1; i++) {
            document = new Document(reader.getPageSize(1));
            copy = new PdfCopy(document, new FileOutputStream(savepaths.get(i)));
            document.open();
            for (int j = size * i + 1; j <= size * (i + 1); j++) {
                document.newPage();
                PdfImportedPage page = copy.getImportedPage(reader, j);
                copy.addPage(page);
            }
            document.close();
        }

        document = new Document(reader.getPageSize(1));
        copy = new PdfCopy(document, new FileOutputStream(savepaths.get(N - 1)));
        document.open();
        for (int j = size * (N - 1) + 1; j <= n; j++) {
            document.newPage();
            PdfImportedPage page = copy.getImportedPage(reader, j);
            copy.addPage(page);
        }
        document.close();
    }

    private static BaseFont getBaseFont(String fontName) throws Exception {
        return BaseFont.createFont(getFontPath(fontName), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
    }

    /**
     * 建立字體風格
     * 
     * @param fontName 字型檔案
     * @param fontSize 字體大小
     * @param fontStyle 字體風格, Font.BOLD 粗體/Font.ITALIC 斜體/Font.BOLDITALIC 粗斜體/....
     * @param fontColor 字體顏色
     * @see java.awt.Color
     * @return
     * @throws Exception
     */
    public static Font createFontStyle(String fontName, int fontSize, int fontStyle, Color fontColor) throws Exception {
        BaseFont bf = BaseFont.createFont(getFontPath(fontName), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
        return new Font(bf, fontSize, fontStyle, fontColor);
    }

    private static String getFontPath(String fontName) {
        String fontPath = "C:\\Windows\\Fonts\\" + fontName;

        Properties prop = System.getProperties();
        String osName = prop.getProperty("os.name").toLowerCase();
        if (osName.indexOf("linux") > -1) {
            fontPath = "/usr/share/fonts/" + fontName;
        }
        return fontPath;
    }

    private static PdfGState getPdfGState(float Opacity) {
        PdfGState graphicState = new PdfGState();
        graphicState.setFillOpacity(Opacity);
        // graphicState.setStrokeOpacity(1.0F);

        return graphicState;
    }

    /**
     * 浮水印風格
     * 
     */
    public class WatermarkStyle {
        private String fontName;
        private int fontSize;
        private Color color;
        private int align = -1;
        private float x;
        private float y;
        private float rotation;
        private float Opacity;
        private int OverUnder;

        public WatermarkStyle() {
        }

        /**
         * 取字體名稱
         * 
         * @return
         */
        public String getFontName() {
            return this.fontName;
        }

        /**
         * 設定字體名稱
         * 
         * @param fontName
         */
        public void setFontName(String fontName) {
            this.fontName = fontName;
        }

        /**
         * 取字體大小
         * 
         * @return
         */
        public int getFontSize() {
            return this.fontSize;
        }

        /**
         * 設定字體大小
         * 
         * @param fontSize
         */
        public void setFontSize(int fontSize) {
            this.fontSize = fontSize;
        }

        /**
         * 取字體顏色
         * 
         * @return
         */
        public Color getColor() {
            return this.color;
        }

        /**
         * 設定顏色
         * 
         * @param color
         */
        public void setColor(Color color) {
            this.color = color;
        }

        /**
         * 取對齊方式
         * 
         * @return
         */
        public int getAlign() {
            return this.align;
        }

        /**
         * 設定對齊方式
         * 
         * @param align
         */
        public void setAlign(int align) {
            this.align = align;
        }

        /**
         * 取座標 X
         * 
         * @return
         */
        public float getX() {
            return this.x;
        }

        /**
         * 設定座標 X
         * 
         * @param x
         */
        public void setX(float x) {
            this.x = x;
        }

        /**
         * 取座標 Y
         * 
         * @return
         */
        public float getY() {
            return this.y;
        }

        /**
         * 設定座標 Y
         * 
         * @param y
         */
        public void setY(float y) {
            this.y = y;
        }

        /**
         * 取旋轉角度
         * 
         * @return OverUnder
         */
        public float getRotation() {
            return this.rotation;
        }

        /**
         * 設定旋轉角度
         * 
         * @param rotation
         */
        public void setRotation(float rotation) {
            this.rotation = rotation;
        }

        /**
         * 設定透明度
         * 
         * @param opacity
         */
        public void setOpacity(float opacity) {
            Opacity = opacity;
        }

        /**
         * 取透明度值
         * 
         * @return Opacity
         */
        public float getOpacity() {
            return Opacity;
        }

        /**
         * 取印原始文件的頁面上或下
         * 
         * @return
         */
        public int getOverUnder() {
            return this.OverUnder;
        }

        /**
         * 設定浮水印，印原始文件的頁面上或下<br>
         * PdfContentByte pageContent = pdfStamper.getOverContent(i);<br>
         * 代表變數 pageContent 所加入的浮水印會在 PDF 內容最上層<br>
         * <br>
         * 改變為 PdfContentByte pageContent = pdfStamper.getUnderContent(i);<br>
         * 則代表變數 pageContent 所加入的浮水印會在 PDF 內容最下層<br>
         * <br>
         * 此差別在於，當我們針對由 Power Point (*.ppt) 轉出來的 PDF 加浮水印時，<br>
         * 若使用 pdfStamper.getUnderContent(i) 會導致加入的浮水印被內容蓋掉而看不到浮水印，因此要改用 pdfStamper.getOverContent(i)。<br>
         * 
         * @param overUnder - 1：上面，2：下面
         */
        public void setOverUnder(int overUnder) {
            this.OverUnder = overUnder;
        }
    }

}
