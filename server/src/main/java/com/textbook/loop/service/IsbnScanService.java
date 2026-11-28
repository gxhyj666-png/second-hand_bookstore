package com.textbook.loop.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.textbook.loop.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 书背条码识别（纯本地，免费离线）。
 *
 * <p>教材的 ISBN 就是 EAN-13 条码，用 ZXing 解码即可，不需要任何云 OCR：
 * 答辩现场断网也能演示「拍照/扫码 → 带出书名」。解出的数字再交给
 * {@link IsbnService#lookup(String)} 走「本地教材库 → 第三方图书库」两段式查询。</p>
 */
@Service
public class IsbnScanService {

    private static final Logger log = LoggerFactory.getLogger(IsbnScanService.class);
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final Pattern DIGITS = Pattern.compile("(97[89]\\d{10}|\\d{13}|\\d{10})");

    private final IsbnService isbnService;

    public IsbnScanService(IsbnService isbnService) {
        this.isbnService = isbnService;
    }

    /** 识别图片，返回与 {@code GET /v1/isbn/{isbn}} 相同结构，另加 decoded / format。 */
    public Map<String, Object> scan(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择一张书背条码照片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.badRequest("图片不能超过 8MB，请压缩后再试");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw BizException.badRequest("读取图片失败，请重试");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            image = null;
        }
        if (image == null) {
            throw BizException.badRequest("这不是一张能识别的图片，请换 JPG/PNG 再试");
        }

        Result result = decode(image);
        if (result == null) {
            throw BizException.badRequest("没有识别到条码，请把书背右下角的条形码对准取景框、离近一点再拍，或手动输入 ISBN");
        }
        String raw = result.getText();
        Matcher m = DIGITS.matcher(raw.replaceAll("[^0-9Xx]", ""));
        String isbn = m.find() ? m.group(1) : null;
        if (isbn == null) {
            throw BizException.badRequest("识别到条码 " + raw + "，但不是 ISBN 格式，请手动输入图书 ISBN");
        }
        log.info("条码识别成功：format={} raw={} isbn={}", result.getBarcodeFormat(), raw, isbn);

        Map<String, Object> data = isbnService.lookup(isbn);
        data.put("decoded", raw);
        data.put("format", String.valueOf(result.getBarcodeFormat()));
        return data;
    }

    private Result decode(BufferedImage image) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.CODE_128));
            return new MultiFormatReader().decode(bitmap, hints);
        } catch (Exception e) {
            return null;
        }
    }
}
