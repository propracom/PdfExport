package tools.pdf;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.ArrayUtils;
import com.google.gson.GsonBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.Barcode;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.Barcode39;
import com.lowagie.text.pdf.BarcodePostnet;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfRectangle;
import com.lowagie.text.pdf.PdfStamper;
import lib.tools.core.convert.Convert;
import lib.tools.exceptions.UtilException;
import lib.tools.extra.pdf.PdfTemplateConfig;
import lib.tools.extra.qrcode.QrCodeUtil;
import lib.tools.extra.qrcode.QrConfig;
import lib.tools.io.FileUtil;
import lib.tools.log.Log;
import lib.tools.log.StaticLog;
import lib.tools.map.MapUtil;
import lib.tools.system.SystemUtil;
import lib.tools.util.ArrayUtil;
import lib.tools.util.ObjectUtil;
import lib.tools.util.ReflectUtil;
import lib.tools.util.StrUtil;

/**
 * 利用 PDF Form 模版產生有資料值的 PDF
 * 
 * @since 1.9.0
 * @since 2.6.3 使用 PdfTemplateConfig
 * 
 */
public class PdfTemplateExport {
    private final static Log logger = StaticLog.get();
    private String template;
    private PdfTemplateConfig config;
    // msjh.ttc 微軟正黑, mingliu.ttc 細明體, kaiu.ttf 標楷, simhei.ttf 黑體, simsun.ttc 微軟宋體, simfang.ttf 仿宋
    private String DefaultFontName = "kaiu.ttf";
    private float DefaultFontSize = 12F;
    private Color DefaultFontColor = Color.BLACK;
    private int DefaultFontStyle = Font.NORMAL;
    private Color DefaultBroundColor = Color.WHITE;
    private float DefaultBorderWidth = 0.5F;
    private int DefaultBorder = Rectangle.NO_BORDER;
    private int DefaultVAlign = Element.ALIGN_MIDDLE;
    private int DefaultHAlign = Element.ALIGN_LEFT;

    private int DefaultRectangle = Rectangle.NO_BORDER;

    /**
     * @param template - 模版路徑檔名
     */
    public PdfTemplateExport(String template) {
        this.template = template;
    }

    public PdfTemplateExport(PdfTemplateConfig config) {
        this.config = config;
    }

    /**
     * @param config - 設定檔
     * @param template - 模版路徑檔名
     * 
     */
    public PdfTemplateExport(PdfTemplateConfig config, String template) {
        this.template = template;
        this.config = config;
    }

    /**
     * 設定模版路徑檔名
     * 
     * @param template
     */
    public void setTemplate(String template) {
        if (StrUtil.isNotEmpty(template)) {
            if (StrUtil.startWithIgnoreCase(template, PdfTemplateConfig.ResourceMode.CLASSPATH.toString())) {
                template =
                        StrUtil.removePrefixIgnoreCase(template, PdfTemplateConfig.ResourceMode.CLASSPATH.toString() + ":");
                this.template = FileUtil.getResourcesPath(template) + template;
            } else if (StrUtil.startWithIgnoreCase(template, PdfTemplateConfig.ResourceMode.WEB_ROOT.toString())) {
                template =
                        StrUtil.removePrefixIgnoreCase(template, PdfTemplateConfig.ResourceMode.WEB_ROOT.toString() + ":");
                this.template = FileUtil.getWebRootPath() + template;
            }
            if (StrUtil.startWithIgnoreCase(template, PdfTemplateConfig.ResourceMode.FILE.toString())) {
                template = StrUtil.removePrefixIgnoreCase(template, PdfTemplateConfig.ResourceMode.FILE.toString() + ":");
                this.template = template;
            } else {
                this.template = template;
            }
        }
    }

    public void setConfig(PdfTemplateConfig config) {
        this.config = config;
    }

    /**
     * 根據模版匯出PDF文件 ：
     * 
     * 單位說明：<br>
     * 1cm = 28.35pt IText使用的單位是pt而不是px，一幫情況下要想保持原來px的大小需要將px*3/4 <br>
     * pt(point，磅)：是一個物理長度單位，指的是72分之一英寸。<br>
     * px(pixel，圖元)：是一個虛擬長度單位，是電腦系統的數位化圖像長度單位<br>
     * 如果px要換算成物理長度，需要指定精度DPI(Dots Per Inch，每英吋像素數)<br>
     * 在掃描列印時一般都有DPI可選。Windows系統預設是96dpi，Apple系統預設是72dpi。<br>
     * pt = 1/72(英寸), px = 1/dpi(英寸) <br>
     * 因此 pt = px * dpi / 72，以 Windows 下的 96dpi 來計算，1 pt = px * 96/72 = px * 4/3<br>
     * 
     * @param textFields - 文字欄位
     * @param barcodeFields - 條碼欄位
     * @param qrcodeFields - 二維碼欄位
     * @param imgFields - 圖片欄位，圖片位置或 byte[]
     * @param tableFields 表格欄位
     * @param checkboxFields - CheckBox 欄位
     * @param groupFields - Group 欄位
     * @return
     * @throws Exception
     * 
     */
    @SuppressWarnings("unchecked")
    public ByteArrayOutputStream export(Map<String, Object> textFields, Map<String, Object> barcodeFields,
            Map<String, Object> qrcodeFields, Map<String, Object> imgFields, Map<String, TableFields> tableFields,
            Map<String, Object> checkboxFields, Map<String, Object> groupFields) throws Exception {

        if (MapUtil.isEmpty(textFields))
            textFields = new HashMap<String, Object>();
        if (MapUtil.isEmpty(barcodeFields))
            barcodeFields = new HashMap<String, Object>();
        if (MapUtil.isEmpty(qrcodeFields))
            qrcodeFields = new HashMap<String, Object>();
        if (MapUtil.isEmpty(imgFields))
            imgFields = new HashMap<String, Object>();
        if (MapUtil.isEmpty(tableFields))
            tableFields = new HashMap<String, TableFields>();
        if (MapUtil.isEmpty(checkboxFields))
            checkboxFields = new HashMap<String, Object>();
        if (MapUtil.isEmpty(groupFields))
            groupFields = new HashMap<String, Object>();

        // 讀取模版參數
        TreeMap<String, Object> cfg = new TreeMap<String, Object>();
        if (config.readConfig().containsKey("configuration"))
            cfg = (TreeMap<String, Object>) config.readConfig().get("configuration");
        // Console.log(new GsonBuilder().setPrettyPrinting().create().toJson(MapUtil.sort(cfg)));
        if (cfg.containsKey("Template"))
            setTemplate((String) cfg.get("Template"));

        // 讀取模版
        PdfReader reader = new PdfReader(this.template);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PdfStamper ps = new PdfStamper(reader, bos);

        // 使用中文字型
        // 在PDF檔案內容中要顯示中文，最重要的是字型設定，如果沒有正確設定中文字型，會造成中文無法顯示的問題。
        // 首先設定基本字型：kaiu.ttf 是作業系統系統提供的標楷體字型，IDENTITY_H 是指編碼(The Unicode encoding with horizontal writing)，
        // 及是否要將字型嵌入PDF 檔中。
        // 再來針對基本字型做變化，例如Font Size、粗體斜體以及顏色等。

        AcroFields acroFields = ps.getAcroFields();

        // 遍歷簽署欄位
        List<String> names = acroFields.getSignatureNames();
        for (String name : names) {
            logger.info("Signature name: " + name, new Object[0]);
            logger.info("Signature covers whole document: " + acroFields.signatureCoversWholeDocument(name), new Object[0]);
            PdfPKCS7 pk = acroFields.verifySignature(name);
//            logger.info("Subject: " + CertificateInfo.getSubjectFields(pk.getSigningCertificate()), newObject[0]);
            logger.info("Document verifies: " + pk.verify(), new Object[0]);
        }

        // 遍歷表單欄位
        TreeMap<String, Object> TextFieldsCfg = new TreeMap<String, Object>();
        if (cfg.containsKey("TextFields"))
            TextFieldsCfg = (TreeMap<String, Object>) cfg.get("TextFields");

        DefaultFontName = StrUtil.isNotEmpty((String) TextFieldsCfg.get("FontName")) ? (String) TextFieldsCfg.get("FontName") : DefaultFontName;

        DefaultFontSize = StrUtil.isNotEmpty((String) TextFieldsCfg.get("FontSize")) ? Convert.toFloat((String) TextFieldsCfg.get("FontSize"), DefaultFontSize) : DefaultFontSize;

        DefaultFontColor = StrUtil.isNotEmpty((String) TextFieldsCfg.get("FontColor")) ? getColorValues((String) TextFieldsCfg.get("FontColor")) : DefaultFontColor;

        DefaultFontStyle = StrUtil.isNotEmpty((String) TextFieldsCfg.get("FontStyle")) ? getFontStyleValues((String) TextFieldsCfg.get("FontStyle")) : DefaultFontStyle;

        for (Map.Entry<String, Object> entry : textFields.entrySet()) {
            String key = (String) entry.getKey();
            String value = ObjectUtil.isNull(entry.getValue()) ? "" : String.valueOf(entry.getValue());

            String keycfg = "";
            for (Map.Entry<String, Object> entrycfg : TextFieldsCfg.entrySet()) {
                keycfg = (String) entrycfg.getKey();

                String keyArray[] = StrUtil.splitToArray(keycfg, ',');
                if (ArrayUtil.contains(keyArray, key))
                    break;
                else
                    keycfg = "";
            }

            TreeMap<String, Object> TextCfg = new TreeMap<String, Object>();
            if (TextFieldsCfg.containsKey(keycfg)) {
                // 設定檔案有指定字體
                TextCfg = (TreeMap<String, Object>) TextFieldsCfg.get(keycfg);
                String textFontName =
                        TextCfg.containsKey("FontName")
                                ? ObjectUtil.isNotEmpty((String) TextCfg.get("FontName")) ? (String) TextCfg.get("FontName")
                                        : DefaultFontName
                                : DefaultFontName;

                if (textFontName.split(",").length > 1) {
                    // 有多個字體處理
                    for (String subtextFontName : textFontName.split(",")) {
                        BaseFont bf = BaseFont.createFont(getFontPath(subtextFontName), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                        acroFields.setFieldProperty(key, "textsize", Convert.toFloat((String) TextCfg.get("FontSize"), DefaultFontSize), null);
                        acroFields.setFieldProperty(key, "textcolor", getColorValues((String) TextCfg.get("FontColor")), null);
                        acroFields.addSubstitutionFont(bf);
                    }
                } else {
                    // 只有一個字體
                    BaseFont bf = BaseFont.createFont(getFontPath(textFontName), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                    // ArrayList<BaseFont> fontList = new ArrayList<BaseFont>();

                    // TODO: 此方式檔案會膨脹
//                  Font font2 = createFontStyle(textFontName, Convert.toFloat((String) TextCfg.get("FontSize"), DefaultFontSize),
//                                getFontStyleValues((String) TextCfg.get("FontStyle")),
//                                 getColorValues((String) TextCfg.get("FontColor")));
//                  Font font = FontFactory.getFont(textFontName, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED, 26F, Font.ITALIC, BaseColor.RED);
//                  acroFields.setFieldProperty(key, "textfont", font2.getBaseFont(), null);

                    acroFields.setFieldProperty(key, "textsize", Convert.toFloat((String) TextCfg.get("FontSize"), DefaultFontSize), null);
                    acroFields.setFieldProperty(key, "textcolor", getColorValues((String) TextCfg.get("FontColor")), null);

                    // fontList.add(bf);
                    // acroFields.setSubstitutionFonts(fontList);
                    acroFields.addSubstitutionFont(bf);
                }

            } else {
                // 設定檔案沒有指定字體，採用預設字體
                if (DefaultFontName.split(",").length > 1) {
                    // 多個字體處理
                    for (String subtextFontName : DefaultFontName.split(",")) {
                        BaseFont bf = BaseFont.createFont(getFontPath(subtextFontName), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                        acroFields.setFieldProperty(key, "textsize", Convert.toFloat((String) TextCfg.get("FontSize"), DefaultFontSize), null);
                        acroFields.setFieldProperty(key, "textcolor", getColorValues((String) TextCfg.get("FontColor")), null);
                        acroFields.addSubstitutionFont(bf);
                    }
                } else {
                    // 只有一個字體
                    BaseFont bf = BaseFont.createFont(getFontPath(DefaultFontName), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                    ArrayList<BaseFont> fontList = new ArrayList<BaseFont>();

                    acroFields.setFieldProperty(key, "textsize", DefaultFontSize, null);
                    acroFields.setFieldProperty(key, "textcolor", DefaultFontColor, null);

                    fontList.add(bf);
                    acroFields.setSubstitutionFonts(fontList);
                }
            }

            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                    System.out.println(key + ":" + value);
                }
            }
            acroFields.setField(key, value);
        }

        // 遍歷條碼欄位
        TreeMap<String, Object> BarcodeFieldsCfg = new TreeMap<String, Object>();
        if (cfg.containsKey("BarcodeFields"))
            BarcodeFieldsCfg = (TreeMap<String, Object>) cfg.get("BarcodeFields");

        for (Map.Entry<String, Object> entry : barcodeFields.entrySet()) {
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            TreeMap<String, Object> BarcodeCfg = new TreeMap<String, Object>();
            if (BarcodeFieldsCfg.containsKey(key) && BarcodeFieldsCfg.get(key) instanceof Map)
                BarcodeCfg = (TreeMap<String, Object>) BarcodeFieldsCfg.get(key);

            // 獲取屬性的型別
            if ((value != null) && (acroFields.getField(key) != null)) {
                // 獲取位置(左上右下)
                // AcroFields.FieldPosition fieldPosition = (AcroFields.FieldPosition)
                // acroFields.getFieldPositions(key).get(0);
                // 獲取 AcroFields 對象
                AcroFields fields = reader.getAcroFields();
                // 獲取指定字段的位置 (使用字段名稱)
                PdfDictionary fieldDict = fields.getFieldItem(key).getWidget(0);
                // 獲取欄位的 Rect 矩形
                PdfArray rectArray = fieldDict.getAsArray(PdfName.RECT);
                float left = rectArray.getAsNumber(0).floatValue();
                float bottom = rectArray.getAsNumber(1).floatValue();
                float right = rectArray.getAsNumber(2).floatValue();
                float top = rectArray.getAsNumber(3).floatValue();

                PdfRectangle rect = new PdfRectangle(left, bottom, right, top);
                // 欄位的邊界信息
                logger.debug("Field: " + key);
                logger.debug("Position: (" + rect.left() + ", " + rect.bottom() + ") - (" + rect.right() + ", "
                        + rect.top() + ")");

                // 繪製條碼
                Barcode barcode;
                if ("Barcode39".equals((String) BarcodeCfg.get("BarcodeFormat")))
                    barcode = new Barcode39();
                else if ("BarcodePostnet".equals((String) BarcodeCfg.get("BarcodeFormat")))
                    barcode = new BarcodePostnet();
                else if ("BarcodeCodabar".equals((String) BarcodeCfg.get("BarcodeFormat")))
                    barcode = new BarcodePostnet();
                else if ("BarcodeInter25".equals((String) BarcodeCfg.get("BarcodeFormat")))
                    barcode = new BarcodePostnet();
                else
                    barcode = new Barcode128();

                // barcode.setCodeType(codeType);
                // 字號
                barcode.setSize(Convert.toFloat((String) BarcodeCfg.get("TextFontSize"), 8F));
                // 條碼高度
                barcode.setBarHeight(Convert.toFloat((String) BarcodeCfg.get("BarcodeHeight"),
                        (rect.top() - rect.bottom()) / 2));
                // 條碼與數字間距
                barcode.setBaseline(Convert.toFloat((String) BarcodeCfg.get("Baseline"), 10F));
                // 文字風格
                BaseFont barcodeTextFont = FontFactory.getFont(getFontPath(this.DefaultFontName),
                        Convert.toFloat((String) BarcodeCfg.get("TextFontSize"), 8F),
                        getFontStyleValues("BOLDITALIC")).getBaseFont();
                barcode.setFont(barcodeTextFont);

                // 文字對齊
                barcode.setTextAlignment(getVerticalAlignValues((String) BarcodeCfg.get("TextAlignment")));
                // 條碼值
                barcode.setCode(value.toString());
                barcode.setStartStopText(Convert.toBool((String) BarcodeCfg.get("StartStopText"), true));
                barcode.setExtended(true);
                if (Convert.toBool((String) BarcodeCfg.get("AltText"), true))
                    barcode.setAltText(value.toString());
                else
                    barcode.setAltText("");
                // 繪製在第一頁
                PdfContentByte cb = ps.getOverContent(1);
                // 生成條碼圖片
                Image image128 = barcode.createImageWithBarcode(cb, getColorValues((String) BarcodeCfg.get("BarColor")),
                        getColorValues((String) BarcodeCfg.get("TextColor")));
                // 左邊距(居中處理)
                float marginLeft = (rect.right() - rect.left() - image128.getWidth()) / 2.0F;
                // 條碼位置
                image128.setAbsolutePosition(rect.left() + marginLeft / 2.0F, rect.bottom());
                image128.scaleAbsoluteWidth(rect.right() - rect.left() - marginLeft);
                // 加入條碼
                cb.addImage(image128);
            }
        }

        // 遍歷二維碼欄位
        TreeMap<String, Object> QrcodeFieldsCfg = new TreeMap<String, Object>();
        if (cfg.containsKey("QrcodeFields"))
            QrcodeFieldsCfg = (TreeMap<String, Object>) cfg.get("QrcodeFields");
        for (Map.Entry<String, Object> entry : qrcodeFields.entrySet()) {
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            TreeMap<String, Object> QrcodeCfg = new TreeMap<String, Object>();
            if (QrcodeFieldsCfg.containsKey(key) && QrcodeFieldsCfg.get(key) instanceof Map)
                QrcodeCfg = (TreeMap<String, Object>) QrcodeFieldsCfg.get(key);

            if ((value != null) && (acroFields.getField(key) != null)) {
                // AcroFields.FieldPosition fieldPosition = (AcroFields.FieldPosition) acroFields.getFieldPositions(key).get(0);
                // 獲取 AcroFields 對象
                AcroFields fields = reader.getAcroFields();
                // 獲取指定字段的位置 (使用字段名稱)
                PdfDictionary fieldDict = fields.getFieldItem(key).getWidget(0);
                // 獲取欄位的 Rect 矩形
                PdfArray rectArray = fieldDict.getAsArray(PdfName.RECT);
                float left = rectArray.getAsNumber(0).floatValue();
                float bottom = rectArray.getAsNumber(1).floatValue();
                float right = rectArray.getAsNumber(2).floatValue();
                float top = rectArray.getAsNumber(3).floatValue();

                PdfRectangle rect = new PdfRectangle(left, bottom, right, top);
                // 欄位的邊界信息
                logger.debug("Field: " + key);
                logger.debug("Position: (" + rect.left() + ", " + rect.bottom() + ") - (" + rect.right() + ", "
                        + rect.top() + ")");

                float hight = rect.height();
                float width = rect.width();

                PdfContentByte cb = ps.getUnderContent(1);

                // BarcodeQRCode pdf417 = new BarcodeQRCode(value.toString(), (int)width, (int)width, null);
                // 生成二維碼圖像
                // Image image128 = pdf417.getImage();
                // com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.forBits(int)
                QrConfig qrConf = new QrConfig();
                qrConf.setHeight((int) hight);
                qrConf.setWidth((int) width);
                qrConf.setMargin(Integer.valueOf(0));
                qrConf.setErrorCorrection(getErrorCorrectionLevel((String) QrcodeCfg.get("ErrorCorrectionLevel")));
                BitMatrix bitMatrix = QrCodeUtil.encode(value.toString(), BarcodeFormat.QR_CODE, qrConf);
                if (ObjectUtil.isNotNull(QrcodeCfg.get("RectangleMargin"))
                        && Convert.toInt((String) QrcodeCfg.get("RectangleMargin")) == 0)
                    bitMatrix = deleteWhite(bitMatrix);

                BufferedImage bufferImg = QrCodeUtil.toImage(bitMatrix, qrConf.getForeColor(), qrConf.getBackColor());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bufferImg, "png", baos);
                baos.flush();
                byte[] imageInByte = baos.toByteArray();
                baos.close();
                Image image = Image.getInstance(imageInByte);
                if (ObjectUtil.isNotNull(QrcodeCfg.get("RectangleMargin"))
                        && Convert.toInt((String) QrcodeCfg.get("RectangleMargin")) == 0)
                    image.scalePercent(hight / bufferImg.getHeight() * 100.0F);

                image.setAbsolutePosition(rect.left(), rect.bottom());

                cb.addImage(image);
            }
        }

        // 圖片類的內容處理
        TreeMap<String, Object> ImageFieldsCfg = new TreeMap<String, Object>();
        if (cfg.containsKey("ImageFields"))
            ImageFieldsCfg = (TreeMap<String, Object>) cfg.get("ImageFields");

        for (Map.Entry<String, Object> entry : imgFields.entrySet()) {
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            TreeMap<String, Object> ImageCfg = new TreeMap<String, Object>();
            if (ImageFieldsCfg.containsKey(key))
                ImageCfg = (TreeMap<String, Object>) ImageFieldsCfg.get(key);

            if ((value != null) && (acroFields.getField(key) != null)) {
                // int pageNo = ((AcroFields.FieldPosition) acroFields.getFieldPositions(key).get(0)).page;

                // Rectangle signRect = ((AcroFields.FieldPosition)
                // acroFields.getFieldPositions(key).get(0)).position;
                // 獲取 AcroFields 對象
                AcroFields fields = reader.getAcroFields();
                // 獲取指定字段的位置 (使用字段名稱)
                PdfDictionary fieldDict = fields.getFieldItem(key).getWidget(0);
                // 獲取欄位的 Rect 矩形
                PdfArray rectArray = fieldDict.getAsArray(PdfName.RECT);
                float left = rectArray.getAsNumber(0).floatValue();
                float bottom = rectArray.getAsNumber(1).floatValue();
                float right = rectArray.getAsNumber(2).floatValue();
                float top = rectArray.getAsNumber(3).floatValue();

                float[] fieldPositions = fields.getFieldPositions(key);
                int pageNo = (int) fieldPositions[0];
                // logger.debug("pageNo ：" + pageNo);

                PdfRectangle signRect = new PdfRectangle(left, bottom, right, top);
                PdfNumber pageNumber = fieldDict.getAsNumber(PdfName.PAGE);
                // logger.debug("pageNumber：" + pageNumber);
                // int pageNo = pageNumber.intValue();

                float x = signRect.left();
                float y = signRect.bottom();

                Image image = null;
                if ((value instanceof String)) {
                    image = Image.getInstance(value.toString());
                } else if ((value instanceof byte[])) {
                    image = Image.getInstance((byte[]) value);
                }
                PdfContentByte under = ps.getOverContent(pageNo);

                image.scaleToFit(signRect.width(), signRect.height());

                image.setAbsolutePosition(x, y);
                under.addImage(image);
            }
        }

        // Checkbox 類的內容處理
        for (Map.Entry<String, Object> entry : checkboxFields.entrySet()) {
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            if ((value != null) && (acroFields.getField(key) != null)) {
                acroFields.setField(key, "yes");
            }
        }

        // Group 類的內容處理
        for (Map.Entry<String, Object> entry : groupFields.entrySet()) {
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            if ((value != null) && (acroFields.getField(key) != null)) {
                acroFields.setField(key, (String) value);
            }
        }

        // 遍歷表格欄位
        TreeMap<String, Object> TableFieldsCfg = new TreeMap<String, Object>();
        if (cfg.containsKey("TableFields"))
            TableFieldsCfg = (TreeMap<String, Object>) cfg.get("TableFields");

        for (Map.Entry<String, TableFields> entry : tableFields.entrySet()) {

            String key = (String) entry.getKey();
            TableFields tableDto = (TableFields) entry.getValue();
            TreeMap<String, Object> TableCfg = new TreeMap<String, Object>();
            if (TableFieldsCfg.containsKey(key)) {
                TableCfg = (TreeMap<String, Object>) TableFieldsCfg.get(key);

                DefaultFontName = StrUtil.isNotEmpty((String) TableCfg.get("FontName")) ? (String) TableCfg.get("FontName")
                        : DefaultFontName;
                DefaultFontSize = StrUtil.isNotEmpty((String) TableCfg.get("FontSize"))
                        ? Convert.toFloat((String) TableCfg.get("FontSize"), DefaultFontSize)
                        : DefaultFontSize;
                DefaultFontColor = StrUtil.isNotEmpty((String) TableCfg.get("FontColor"))
                        ? getColorValues((String) TableCfg.get("FontColor"))
                        : DefaultFontColor;
                DefaultFontStyle = StrUtil.isNotEmpty((String) TableCfg.get("FontStyle"))
                        ? getFontStyleValues((String) TableCfg.get("FontStyle"))
                        : DefaultFontStyle;

                List<Map<String, Object>> DataList = tableDto.getDataList();
                int columnSize = 0;
                if (DataList.size() > 0)
                    columnSize = DataList.get(0).size();
                Map<String, Object> rowMap = DataList.get(0);

                List<String> colNameList = new ArrayList<String>();
                List<Float> colWidthList = new ArrayList<Float>();
                List<String> colTitleList = new ArrayList<String>();

                List<String> colTitleFontNameList = new ArrayList<String>();
                List<Float> colTitleFontSizeList = new ArrayList<Float>();
                List<Integer> colTitleFontStyleList = new ArrayList<Integer>();
                List<Color> colTitleFontColorList = new ArrayList<Color>();

                List<Integer> colTitlevAlignList = new ArrayList<Integer>();
                List<Integer> colTitlehAlignList = new ArrayList<Integer>();
                List<Integer> colTitleBorderList = new ArrayList<Integer>();
                List<Float> colTitleBorderWidthList = new ArrayList<Float>();
                List<Color> colTitleBorderColorList = new ArrayList<Color>();
                List<Color> colTitleBroundColorList = new ArrayList<Color>();

                List<String> colFontNameList = new ArrayList<String>();
                List<Float> colFontSizeList = new ArrayList<Float>();
                List<Integer> colFontStyleList = new ArrayList<Integer>();
                List<Color> colFontColorList = new ArrayList<Color>();

                List<Integer> colvAlignList = new ArrayList<Integer>();
                List<Integer> colhAlignList = new ArrayList<Integer>();
                List<Integer> colBorderList = new ArrayList<Integer>();
                List<Float> colBorderWidthList = new ArrayList<Float>();
                List<Color> colBorderColorList = new ArrayList<Color>();
                List<Color> colBroundColorList = new ArrayList<Color>();

                if ((tableDto != null) && (acroFields.getField(key) != null)) {
                    // 獲取位置(左上右下)
                    // AcroFields.FieldPosition fieldPosition = (AcroFields.FieldPosition) acroFields.getFieldPositions(key).get(0);
                    // 獲取 AcroFields 對象
                    AcroFields fields = reader.getAcroFields();
                    // 獲取指定字段的位置 (使用字段名稱)
                    PdfDictionary fieldDict = fields.getFieldItem(key).getWidget(0);
                    // 獲取欄位的 Rect 矩形
                    PdfArray rectArray = fieldDict.getAsArray(PdfName.RECT);
                    float left = rectArray.getAsNumber(0).floatValue();
                    float bottom = rectArray.getAsNumber(1).floatValue();
                    float right = rectArray.getAsNumber(2).floatValue();
                    float top = rectArray.getAsNumber(3).floatValue();

                    PdfRectangle rect = new PdfRectangle(left, bottom, right, top);
                    float width = rect.right() - rect.left();

                    for (Entry<String, Object> colID : MapUtil.sort(rowMap).entrySet()) {
                        colNameList.add((String) colID.getKey());
                        TreeMap<String, Object> columnCfg = new TreeMap<String, Object>();
                        if (TableCfg.containsKey((String) colID.getKey())
                                && TableCfg.get((String) colID.getKey()) instanceof Map) {
                            columnCfg = (TreeMap<String, Object>) TableCfg.get((String) colID.getKey());
                            colWidthList.add(Convert.toFloat(columnCfg.get("ColWidths")));
                            colTitleList.add((String) columnCfg.get("ColTitle"));
                            TreeMap<String, Object> TitleFontsCfg = new TreeMap<String, Object>();
                            if (columnCfg.containsKey("TitleFonts") && columnCfg.get("TitleFonts") instanceof Map) {
                                TitleFontsCfg = (TreeMap<String, Object>) columnCfg.get("TitleFonts");
                                if (TitleFontsCfg.containsKey("FontName"))
                                    colTitleFontNameList.add((String) TitleFontsCfg.get("FontName"));
                                else
                                    colTitleFontNameList.add(DefaultFontName.split(",")[0]);// 只取第1個字型
                                if (TitleFontsCfg.containsKey("FontSize"))
                                    colTitleFontSizeList
                                            .add(Convert.toFloat(TitleFontsCfg.get("FontSize"), DefaultFontSize));
                                else
                                    colTitleFontSizeList.add(DefaultFontSize);
                                if (TitleFontsCfg.containsKey("FontStyle"))
                                    colTitleFontStyleList.add(getFontStyleValues((String) TitleFontsCfg.get("FontStyle")));
                                else
                                    colTitleFontStyleList.add(DefaultFontStyle);
                                if (TitleFontsCfg.containsKey("FontColor"))
                                    colTitleFontColorList.add(getColorValues((String) TitleFontsCfg.get("FontColor")));
                                else
                                    colTitleFontColorList.add(DefaultFontColor);
                            } else {
                                colTitleFontNameList.add(DefaultFontName.split(",")[0]);// 只取第1個字型
                                colTitleFontSizeList.add(DefaultFontSize);
                                colTitleFontStyleList.add(DefaultFontStyle);
                                colTitleFontColorList.add(DefaultFontColor);
                            }
                            TreeMap<String, Object> TitleStyleCfg = new TreeMap<String, Object>();
                            if (columnCfg.containsKey("TitleStyles") && columnCfg.get("TitleStyles") instanceof Map) {
                                TitleStyleCfg = (TreeMap<String, Object>) columnCfg.get("TitleStyles");
                                if (TitleStyleCfg.containsKey("vAlign"))
                                    colTitlevAlignList.add(getVerticalAlignValues((String) TitleStyleCfg.get("vAlign")));
                                else
                                    colTitlevAlignList.add(DefaultVAlign);
                                if (TitleStyleCfg.containsKey("hAlign"))
                                    colTitlehAlignList.add(getHorizontalAlignValues((String) TitleStyleCfg.get("hAlign")));
                                else
                                    colTitlehAlignList.add(DefaultHAlign);
                                if (TitleStyleCfg.containsKey("Border"))
                                    colTitleBorderList.add(getRectangleValues((String) TitleStyleCfg.get("Border")));
                                else
                                    colTitleBorderList.add(DefaultBorder);
                                if (TitleStyleCfg.containsKey("BorderWidth"))
                                    colTitleBorderWidthList.add(
                                            Convert.toFloat((String) TitleStyleCfg.get("BorderWidth"), DefaultBorderWidth));
                                else
                                    colTitleBorderWidthList.add(DefaultBorderWidth);
                                if (TitleStyleCfg.containsKey("BorderColor"))
                                    colTitleBorderColorList.add(getColorValues((String) TitleStyleCfg.get("BorderColor")));
                                else
                                    colTitleBorderColorList.add(DefaultFontColor);
                                if (TitleStyleCfg.containsKey("BroundColor"))
                                    if (StrUtil.isEmpty((String) TitleStyleCfg.get("BroundColor")))
                                        colTitleBroundColorList.add(DefaultBroundColor);
                                    else
                                        colTitleBroundColorList
                                                .add(getColorValues((String) TitleStyleCfg.get("BroundColor")));
                                else
                                    colTitleBroundColorList.add(DefaultBroundColor);
                            } else {
                                colTitlevAlignList.add(DefaultVAlign);
                                colTitlehAlignList.add(DefaultHAlign);
                                colTitleBorderList.add(DefaultBorder);
                                colTitleBorderWidthList.add(DefaultBorderWidth);
                                colTitleBorderColorList.add(DefaultFontColor);
                                colTitleBroundColorList.add(DefaultBroundColor);
                            }
                            TreeMap<String, Object> ColFontsCfg = new TreeMap<String, Object>();
                            if (columnCfg.containsKey("ColFonts") && columnCfg.get("ColFonts") instanceof Map) {
                                ColFontsCfg = (TreeMap<String, Object>) columnCfg.get("ColFonts");
                                if (ColFontsCfg.containsKey("FontName"))
                                    colFontNameList.add((String) ColFontsCfg.get("FontName"));
                                else
                                    colFontNameList.add(DefaultFontName.split(",")[0]);// 只取第1個字型
                                if (ColFontsCfg.containsKey("FontSize"))
                                    colFontSizeList.add(Convert.toFloat(ColFontsCfg.get("FontSize"), DefaultFontSize));
                                else
                                    colFontSizeList.add(DefaultFontSize);
                                if (ColFontsCfg.containsKey("FontStyle"))
                                    colFontStyleList.add(getFontStyleValues((String) ColFontsCfg.get("FontStyle")));
                                else
                                    colFontStyleList.add(DefaultFontStyle);
                                if (ColFontsCfg.containsKey("FontColor"))
                                    colFontColorList.add(getColorValues((String) ColFontsCfg.get("FontColor")));
                                else
                                    colFontColorList.add(DefaultFontColor);
                            } else {
                                colFontNameList.add(DefaultFontName.split(",")[0]);// 只取第1個字型
                                colFontSizeList.add(DefaultFontSize);
                                colFontStyleList.add(DefaultFontStyle);
                                colFontColorList.add(DefaultFontColor);
                            }

                            TreeMap<String, Object> ColStyleCfg = new TreeMap<String, Object>();
                            if (columnCfg.containsKey("ColStyles") && columnCfg.get("ColStyles") instanceof Map) {
                                ColStyleCfg = (TreeMap<String, Object>) columnCfg.get("ColStyles");
                                if (ColStyleCfg.containsKey("vAlign"))
                                    colvAlignList.add(getVerticalAlignValues((String) ColStyleCfg.get("vAlign")));
                                else
                                    colvAlignList.add(DefaultVAlign);
                                if (ColStyleCfg.containsKey("hAlign"))
                                    colhAlignList.add(getHorizontalAlignValues((String) ColStyleCfg.get("hAlign")));
                                else
                                    colhAlignList.add(DefaultHAlign);
                                if (ColStyleCfg.containsKey("Border"))
                                    colBorderList.add(getRectangleValues((String) ColStyleCfg.get("Border")));
                                else
                                    colBorderList.add(DefaultBorder);
                                if (ColStyleCfg.containsKey("BorderWidth"))
                                    colBorderWidthList
                                            .add(Convert.toFloat(ColStyleCfg.get("BorderWidth"), DefaultBorderWidth));
                                else
                                    colBorderWidthList.add(DefaultBorderWidth);
                                if (ColStyleCfg.containsKey("BorderColor"))
                                    colBorderColorList.add(getColorValues((String) ColStyleCfg.get("BorderColor")));
                                else
                                    colTitleBorderColorList.add(DefaultFontColor);
                                if (ColStyleCfg.containsKey("BroundColor"))
                                    if (StrUtil.isEmpty((String) ColStyleCfg.get("BroundColor")))
                                        colBroundColorList.add(DefaultBroundColor);
                                    else
                                        colBroundColorList.add(getColorValues((String) ColStyleCfg.get("BroundColor")));
                                else
                                    colBroundColorList.add(DefaultBroundColor);
                            } else {
                                colvAlignList.add(DefaultVAlign);
                                colhAlignList.add(DefaultHAlign);
                                colBorderList.add(DefaultBorder);
                                colBorderWidthList.add(DefaultBorderWidth);
                                colBorderColorList.add(DefaultFontColor);
                                colBroundColorList.add(DefaultBroundColor);
                            }
                        } else {
                            colWidthList.add(Convert.toFloat(width / columnSize));
                            colTitleList.add("");

                            colTitleFontNameList.add(DefaultFontName.split(",")[0]);// 只取第1個字型
                            colTitleFontSizeList.add(DefaultFontSize);
                            colTitleFontStyleList.add(DefaultFontStyle);
                            colTitleFontColorList.add(DefaultFontColor);

                            colTitlevAlignList.add(DefaultVAlign);
                            colTitlehAlignList.add(DefaultHAlign);
                            colTitleBorderList.add(DefaultBorder);
                            colTitleBorderWidthList.add(DefaultBorderWidth);
                            colTitleBorderColorList.add(DefaultFontColor);
                            colTitleBroundColorList.add(DefaultBroundColor);

                            colFontNameList.add(DefaultFontName.split(",")[0]);// 只取第1個字型
                            colFontSizeList.add(DefaultFontSize);
                            colFontStyleList.add(DefaultFontStyle);
                            colFontColorList.add(DefaultFontColor);

                            colvAlignList.add(DefaultVAlign);
                            colhAlignList.add(DefaultHAlign);
                            colBorderList.add(DefaultBorder);
                            colBorderWidthList.add(DefaultBorderWidth);
                            colBorderColorList.add(DefaultFontColor);
                            colBroundColorList.add(DefaultBroundColor);
                        }
                    }

                    // 建立表格
                    String[] colFields = ArrayUtil.toArray(colNameList, String.class);
                    PdfPTable table = new PdfPTable(colFields.length);
                    float[] widths = ArrayUtils.toPrimitive(colWidthList.toArray(new Float[0]), width / colFields.length);
                    try {
                        table.setTotalWidth(width);
                        table.setLockedWidth(true);
                        table.setHorizontalAlignment(DefaultVAlign);
                        table.getDefaultCell().setBorder(DefaultBorder);
                        table.setWidths(widths);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                    // 建立表頭
                    int j = 0;
                    Font[] titleFonts = new Font[colFields.length];
                    for (int k = 0; k < colFields.length; k++) {
                        titleFonts[k] = createFontStyle(colTitleFontNameList.get(k), colTitleFontSizeList.get(k),
                                colTitleFontStyleList.get(k), colTitleFontColorList.get(k));
                    }

                    TableCellStyle[] titleStyle = new TableCellStyle[colFields.length];
                    for (int k = 0; k < colFields.length; k++) {
                        titleStyle[k] = setColStyle(colTitlevAlignList.get(k), colTitlehAlignList.get(k),
                                colTitleBorderList.get(k), colTitleBorderWidthList.get(k), colTitleBorderColorList.get(k),
                                colTitleBroundColorList.get(k));
                    }

                    String TableTitle[] = ArrayUtil.toArray(colTitleList, String.class);
                    for (String col : TableTitle) {
                        table.addCell(createCell(col, titleFonts[j], titleStyle[j]));
                        j++;
                    }
                    // 建立表體
                    Font[] colFonts = new Font[colFields.length];
                    for (int k = 0; k < colFields.length; k++) {
                        colFonts[k] = createFontStyle(colFontNameList.get(k), colFontSizeList.get(k),
                                colFontStyleList.get(k), colFontColorList.get(k));
                    }

                    TableCellStyle[] colstyle = new TableCellStyle[colFields.length];
                    for (int k = 0; k < colFields.length; k++) {
                        colstyle[k] = setColStyle(colvAlignList.get(k), colhAlignList.get(k), colBorderList.get(k),
                                colBorderWidthList.get(k), colBorderColorList.get(k), colBroundColorList.get(k));
                    }

                    List<Map<String, Object>> dataList = tableDto.getDataList();
                    if (dataList != null && dataList.size() > 0) {
                        for (int i = 0; i < dataList.size(); i++) {
                            Map<String, Object> row = dataList.get(i);
                            j = 0;
                            for (String field : colFields) {
                                table.addCell(createCell(row.get(field), colFonts[j], colstyle[j]));
                                j++;
                            }
                        }
                    }
                    PdfContentByte cb = ps.getOverContent(1);
                    table.writeSelectedRows(0, -1, 0, -1, rect.left(), rect.top(), cb);
                }
            }
        }
        ps.setFormFlattening(true);
        ps.close();
        reader.close();
        return bos;
    }

    /**
     * 根據模版匯出PDF文件 ：
     * 
     * @param textFields - 文字欄位
     * @param barcodeFields - 條碼欄位
     * @param qrcodeFields - 二維碼欄位
     * @param imgFields - 圖片欄位，圖片位置或 byte[]
     * @param tableFields 表格欄位
     * @return
     * @throws Exception
     * 
     */
    public ByteArrayOutputStream export(Map<String, Object> textFields, Map<String, Object> barcodeFields,
            Map<String, Object> qrcodeFields, Map<String, Object> imgFields, Map<String, TableFields> tableFields) throws Exception {
        return export(textFields, barcodeFields, qrcodeFields, imgFields, tableFields, null, null);
    }

    /**
     * 根據模版匯出PDF文件 <br>
     * 
     * @param os - 輸出串流
     * @param textFields - 文字欄位
     * @param barcodeFields - 條碼欄位
     * @param qrcodeFields - 二維碼欄位
     * @param imgFields - 圖片欄位，圖片位置或 byte[]
     * @param tableFields 表格欄位
     * @param checkboxFields - CheckBox 欄位
     * @param groupFields - Group 欄位
     * 
     * @throws Exception
     */
    public void export(OutputStream os, Map<String, Object> textFields, Map<String, Object> barcodeFields,
            Map<String, Object> qrcodeFields, Map<String, Object> imgFields, Map<String, TableFields> tableFields,
            Map<String, Object> checkboxFields, Map<String, Object> groupFields) throws Exception {
        ByteArrayOutputStream bos = export(textFields, barcodeFields, qrcodeFields, imgFields, tableFields, checkboxFields, groupFields);

        os.write(bos.toByteArray());
        os.flush();
        os.close();

        bos.close();
    }

    /**
     * 根據模版匯出PDF文件 <br>
     * 
     * @param os - 輸出串流
     * @param textFields - 文字欄位
     * @param barcodeFields - 條碼欄位
     * @param qrcodeFields - 二維碼欄位
     * @param imgFields - 圖片欄位，圖片位置或 byte[]
     * @param tableFields 表格欄位
     * 
     * @throws Exception
     */
    public void export(OutputStream os, Map<String, Object> textFields, Map<String, Object> barcodeFields,
            Map<String, Object> qrcodeFields, Map<String, Object> imgFields, Map<String, TableFields> tableFields)
            throws Exception {
        export(os, textFields, barcodeFields, qrcodeFields, imgFields, tableFields, null, null);
    }

    /**
     * 根據模版匯出PDF文件 <br>
     * 
     * @param outputFile - 輸出路徑檔名
     * @param textFields - 文字欄位
     * @param barcodeFields - 條碼欄位
     * @param qrcodeFields - 二維碼欄位
     * @param imgFields - 圖片欄位，圖片位置或 byte[]
     * @param tableFields - 表格欄位
     * @param checkboxFields - CheckBox 欄位
     * @param groupFields - Group 欄位
     * 
     * @throws Exception
     */
    public void export(String outputFile, Map<String, Object> textFields, Map<String, Object> barcodeFields,
            Map<String, Object> qrcodeFields, Map<String, Object> imgFields, Map<String, TableFields> tableFields,
            Map<String, Object> checkboxFields, Map<String, Object> groupFields) throws Exception {
        File outFile = new File(outputFile);
        outFile.createNewFile();

        export(new FileOutputStream(outFile), textFields, barcodeFields, qrcodeFields, imgFields, tableFields, checkboxFields, groupFields);
    }

    /**
     * 根據模版匯出PDF文件 <br>
     * 
     * @param outputFile - 輸出路徑檔名
     * @param textFields - 文字欄位
     * @param barcodeFields - 條碼欄位
     * @param qrcodeFields - 二維碼欄位
     * @param imgFields - 圖片欄位，圖片位置或 byte[]
     * @param tableFields - 表格欄位
     * 
     * @throws Exception
     */
    public void export(String outputFile, Map<String, Object> textFields, Map<String, Object> barcodeFields,
            Map<String, Object> qrcodeFields, Map<String, Object> imgFields, Map<String, TableFields> tableFields)
            throws Exception {
        export(outputFile, textFields, barcodeFields, qrcodeFields, imgFields, tableFields, null, null);
    }

    /**
     * 建立字體風格
     * 
     * @param fontName 字型檔案
     * @param fontSize 字體大小
     * @param fontStyle 字體風格, Font.BOLD 粗體/Font.ITALIC 斜體/Font.BOLDITALIC 粗斜體/....
     * @param fontColor 字體顏色
     * @return
     * @throws Exception
     */
    public Font createFontStyle(String fontName, float fontSize, int fontStyle, Color fontColor) throws Exception {
        if (StrUtil.isEmpty(fontName)) {
            fontName = DefaultFontName;
        }
        BaseFont bf = BaseFont.createFont(getFontPath(fontName), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
        return new Font(bf, fontSize, fontStyle, fontColor);
    }

    /**
     * 設定單元格風格
     * 
     * @param vAlign - 垂直對齊方式，ex: Element.ALIGN_MIDDLE，Element.ALIGN_TOP...
     * @param hAlign - 水平對齊方式，ex: Element.ALIGN_CENTER、Element.ALIGN_RIGHT...
     * @return
     * 
     */
    public TableCellStyle setColStyle(int vAlign, int hAlign) {
        Color borderColor = new Color(0, 0, 0);
        Color groundColor = new Color(255, 255, 255);
        int border = 15;

        return setColStyle(vAlign, hAlign, border, 0.5F, borderColor, groundColor);
    }

    /**
     * 設定單元格風格
     * 
     * @param vAlign - 垂直對齊方式，ex: Element.ALIGN_MIDDLE，Element.ALIGN_TOP...
     * @param hAlign - 水平對齊方式，ex: Element.ALIGN_CENTER、Element.ALIGN_RIGHT...
     * @param border - 邊框，Rectangle.BOTTOM | Rectangle.TOP | Rectangle.LEFT | Rectangle.RIGHT
     * @return
     *
     */
    public TableCellStyle setColStyle(int vAlign, int hAlign, int border) {
        Color borderColor = new Color(0, 0, 0);
        Color groundColor = new Color(255, 255, 255);

        return setColStyle(vAlign, hAlign, border, 0.5F, borderColor, groundColor);
    }

    /**
     * 設定單元格風格
     * 
     * @param vAlign - 垂直對齊方式，ex: Element.ALIGN_MIDDLE，Element.ALIGN_TOP...
     * @param hAlign - 水平對齊方式，ex: Element.ALIGN_CENTER、Element.ALIGN_RIGHT...
     * @param border - 邊框，Rectangle.BOTTOM | Rectangle.TOP | Rectangle.LEFT | Rectangle.RIGHT
     * @param borderWidth - 邊框寬度
     * @return
     * 
     */
    public TableCellStyle setColStyle(int vAlign, int hAlign, int border, float borderWidth) {
        Color borderColor = new Color(0, 0, 0);
        Color groundColor = new Color(255, 255, 255);

        return setColStyle(vAlign, hAlign, border, borderWidth, borderColor, groundColor);
    }

    /**
     * 設定單元格風格
     * 
     * @param vAlign - 垂直對齊方式，ex: Element.ALIGN_MIDDLE，Element.ALIGN_TOP...
     * @param hAlign - 水平對齊方式，ex: Element.ALIGN_CENTER、Element.ALIGN_RIGHT...
     * @param border - 邊框,，Rectangle.BOTTOM | Rectangle.TOP | Rectangle.LEFT | Rectangle.RIGHT
     * @param borderWidth - 邊框寬度
     * @param borderColor - 邊框顏色
     * @param groundColor - 背景顏色
     * @return
     * 
     */
    public TableCellStyle setColStyle(int vAlign, int hAlign, int border, float borderWidth, Color borderColor,
            Color groundColor) {
        TableCellStyle cell = new TableCellStyle();
        cell.setvAlign(vAlign);
        cell.sethAlign(hAlign);
        cell.setBorder(border);
        cell.setBorderColor(borderColor);
        cell.setGroundColor(groundColor);
        cell.setBorderWidth(borderWidth);
        return cell;
    }

    /**
     * 強制將白邊去掉<br>
     * 
     * 雖然生成二維碼時，已經將margin的值設為了0，但是在實際生成二維碼時有時候還是會生成白色的邊框，邊框的寬度為10px；<br>
     * 白邊的生成還與設定的二維碼的寬、高及二維碼內容的多少（內容越多，生成的二維碼越密集）有關；<br>
     * 因為是在生成二維碼之後，才將白邊裁掉，所以裁剪後的二維碼（實際二維碼的寬、高）肯定不是你想要的尺寸，只能自己一點點試嘍！<br>
     * 
     * @param matrix
     * @return 裁剪後的二維碼（實際二維碼的大小）
     */
    private static BitMatrix deleteWhite(BitMatrix matrix) {
        int[] rec = matrix.getEnclosingRectangle();
        int resWidth = rec[2] + 1;
        int resHeight = rec[3] + 1;

        BitMatrix resMatrix = new BitMatrix(resWidth, resHeight);
        resMatrix.clear();
        for (int i = 0; i < resWidth; i++) {
            for (int j = 0; j < resHeight; j++) {
                if (matrix.get(i + rec[0], j + rec[1])) {
                    resMatrix.set(i, j);
                }
            }
        }
        int width = resMatrix.getWidth();
        int height = resMatrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, 1);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, resMatrix.get(x, y) ? 0 : 255);
            }
        }
        return resMatrix;
    }

    /**
     * 建立單元格
     * 
     * @param value 顯示內容
     * @param font 字型
     * @param cellstyle 單元格風格
     * @return
     * @version 3.3.7.5 fax 設定若超過該行寬度不換行
     */
    private static PdfPCell createCell(Object value, Font font, TableCellStyle cellstyle) {
        PdfPCell cell = new PdfPCell();
        cell.setPhrase(new Phrase(StrUtil.nullToEmpty((String) value), font));
        // cell.setVerticalAlignment(cellstyle.getvAlign());
        cell.setVerticalAlignment(cellstyle.getvAlign());
        cell.setHorizontalAlignment(cellstyle.gethAlign());
        cell.setBorderColor(cellstyle.getBorderColor());
        cell.setBackgroundColor(cellstyle.getGroundColor());
        cell.setBorder(cellstyle.getBorder());
        cell.setBorderWidth(cellstyle.getBorderWidth());
        cell.setNoWrap(true);// 設定若超過該行寬度不換行
        return cell;
    }

    /**
     * 獲取字型檔案的路徑<br>
     * 參數中有多字型，只會取得第1個字型的路徑
     */
    private String getFontPath(String fontName) throws RuntimeException {
        String fontPath = "";
        String fontFile = fontName.replace(",0", "").replace(",1", "");
        if (fontFile.split(",").length > 1) {
            fontFile = fontFile.split(",")[0];// 只取第1個字型
            logger.info("參數[" + fontName + "]中有多字型，只取第1個字型[" + fontFile + "]");
        }
        if (SystemUtil.getOsInfo().isWindows()) {
            fontPath = SystemUtil.get("user.home", false) + "\\AppData\\Local\\Microsoft\\Windows\\Fonts\\";
            if (!FileUtil.exist(new File(fontPath + fontFile))) {
                fontPath = "C:\\Windows\\Fonts\\";
                if (!FileUtil.exist(new File(fontPath + fontFile))) {
                    fontPath = "C:\\pdf_fonts\\";
                    if (!FileUtil.exist(new File(fontPath + fontFile))) {
                        fontPath = "";
                    }
                }
            }
        } else {
            Properties prop = System.getProperties();
            String osName = prop.getProperty("os.name").toLowerCase();
            if (osName.indexOf("linux") > -1) {
                fontPath = "/usr/share/fonts/";
                if (!FileUtil.exist(new File(fontPath + fontFile))) {
                    fontPath = "";
                }
            }
        }

        if (StrUtil.isBlank(fontPath)) {
            throw new RuntimeException(fontName + "於預設的查詢路徑中都查不到!");
        } else {
            return fontPath + fontFile;
        }
    }

    private Color getColorValues(String Colors)
            throws ClassNotFoundException, UtilException, NoSuchFieldException, SecurityException {
        Color color;
        if (StrUtil.isEmpty(Colors))
            return DefaultFontColor;

        String classname = "java.awt.Color";
        Class<?> c = Class.forName(classname);
        String ColorArray[] = StrUtil.splitToArray(Colors, ',');
        if (ColorArray.length == 3) {
            int r = Convert.toInt(ColorArray[0], 0);
            int g = Convert.toInt(ColorArray[1], 0);
            int b = Convert.toInt(ColorArray[2], 0);
            color = new Color(r, g, b);
        } else {
            color = (Color) ReflectUtil.getFieldValue(c, c.getField(Colors));
        }
        return color;
    }

    private int getHorizontalAlignValues(String Elements)
            throws ClassNotFoundException, UtilException, NoSuchFieldException, SecurityException {
        String classname = "com.lowagie.text.Element";
        if (StrUtil.isEmpty(Elements))
            return DefaultHAlign;
        int values = 0;
        String ElementArray[] = StrUtil.splitToArray(Elements, '|');
        for (String Element : ElementArray) {
            Class<?> c = Class.forName(classname);
            values = values | (int) ReflectUtil.getFieldValue(c, c.getField(Element.trim()));
        }
        return values;
    }

    private int getVerticalAlignValues(String Elements)
            throws ClassNotFoundException, UtilException, NoSuchFieldException, SecurityException {
        String classname = "com.lowagie.text.Element";
        if (StrUtil.isEmpty(Elements))
            return DefaultVAlign;
        int values = 0;
        String ElementArray[] = StrUtil.splitToArray(Elements, '|');
        for (String Element : ElementArray) {
            Class<?> c = Class.forName(classname);
            values = values | (int) ReflectUtil.getFieldValue(c, c.getField(Element.trim()));
        }
        return values;
    }

    private ErrorCorrectionLevel getErrorCorrectionLevel(String Level) {
        String[] LevelArray = {"M", "L", "H", "Q"};
        int i = 0;
        if (StrUtil.isEmpty(Level))
            return ErrorCorrectionLevel.forBits(i);
        for (String level : LevelArray) {
            if (level.equalsIgnoreCase(Level)) {
                break;
            }
            i++;
        }
        return ErrorCorrectionLevel.forBits(i);
    }

    private int getFontStyleValues(String Fonts)
            throws ClassNotFoundException, UtilException, NoSuchFieldException, SecurityException {
        String classname = "com.lowagie.text.Font";
        if (StrUtil.isEmpty(Fonts))
            return DefaultFontStyle;
        int values = 0;
        String FontArray[] = StrUtil.splitToArray(Fonts, '|');
        for (String Font : FontArray) {
            Class<?> c = Class.forName(classname);
            values = values | (int) ReflectUtil.getFieldValue(c, c.getField(Font.trim()));
        }
        return values;
    }

    private int getRectangleValues(String Rectangles)
            throws ClassNotFoundException, UtilException, NoSuchFieldException, SecurityException {
        String classname = "com.lowagie.text.Rectangle";
        if (StrUtil.isEmpty(Rectangles))
            return DefaultRectangle;
        int values = 0;
        String RectangleArray[] = StrUtil.splitToArray(Rectangles, '|');
        for (String Rectangle : RectangleArray) {
            Class<?> c = Class.forName(classname);
            values = values | (int) ReflectUtil.getFieldValue(c, c.getField(Rectangle.trim()));
        }
        return values;
    }

    /**
     * 回傳此物件 GSON String，此方法會忽略空值
     * 
     * @return String
     *
     */
    public static String toGSONString(Object obj) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(obj);
    }

    /**
     * 表格欄位定義
     * 
     */
    public class TableFields {
        private String[] colNames;
        private String[] colFields;
        private float[] colWidths;
        private Font[] titleFonts;
        private PdfTemplateExport.TableCellStyle[] titleStyles;
        private Font[] colFonts;
        private PdfTemplateExport.TableCellStyle[] colStyles;
        private List<Map<String, Object>> dataList;

        public TableFields() {
        }

        public String[] getColNames() {
            return this.colNames;
        }

        public void setColNames(String[] colNames) {
            this.colNames = colNames;
        }

        public String[] getColFields() {
            return this.colFields;
        }

        public void setColFields(String[] colFields) {
            this.colFields = colFields;
        }

        public float[] getColWidths() {
            return this.colWidths;
        }

        public void setColWidths(float[] colWidths) {
            this.colWidths = colWidths;
        }

        public Font[] getColFonts() {
            return this.colFonts;
        }

        public void setColFonts(Font[] colFonts) {
            this.colFonts = colFonts;
        }

        public Font[] getTitleFonts() {
            return this.titleFonts;
        }

        public void setTitleFonts(Font[] titleFonts) {
            this.titleFonts = titleFonts;
        }

        public PdfTemplateExport.TableCellStyle[] getColStyles() {
            return this.colStyles;
        }

        public void setColStyle(PdfTemplateExport.TableCellStyle[] colStyles) {
            this.colStyles = colStyles;
        }

        public PdfTemplateExport.TableCellStyle[] getTitleStyles() {
            return this.titleStyles;
        }

        public void setTitleStyle(PdfTemplateExport.TableCellStyle[] titleStyles) {
            this.titleStyles = titleStyles;
        }

        public List<Map<String, Object>> getDataList() {
            return this.dataList;
        }

        public void setDataList(List<Map<String, Object>> dataList) {
            this.dataList = dataList;
        }
    }

    /**
     * 表格單元格風格
     * 
     */
    public class TableCellStyle {
        private int vAlign;
        private int hAlign;
        private int border;
        private Color borderColor;
        private Color groundColor;
        private float borderWidth;

        public TableCellStyle() {
        }

        public float getBorderWidth() {
            return this.borderWidth;
        }

        public void setBorderWidth(float borderWidth) {
            this.borderWidth = borderWidth;
        }

        public int getvAlign() {
            return this.vAlign;
        }

        public void setvAlign(int vAlign) {
            this.vAlign = vAlign;
        }

        public int gethAlign() {
            return this.hAlign;
        }

        public void sethAlign(int hAlign) {
            this.hAlign = hAlign;
        }

        public int getBorder() {
            return this.border;
        }

        public void setBorder(int border) {
            this.border = border;
        }

        public Color getBorderColor() {
            return this.borderColor;
        }

        public void setBorderColor(Color borderColor) {
            this.borderColor = borderColor;
        }

        public Color getGroundColor() {
            return this.groundColor;
        }

        public void setGroundColor(Color groundColor) {
            this.groundColor = groundColor;
        }
    }
}
