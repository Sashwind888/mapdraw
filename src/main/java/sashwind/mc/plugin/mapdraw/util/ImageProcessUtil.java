package sashwind.mc.plugin.mapdraw.util;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ImageProcessUtil {

    static {
        try {
            IIORegistry.getDefaultInstance().registerServiceProvider(new WebPImageReaderSpi());
            ImageIO.scanForPlugins();
        } catch (Throwable ignored) {
        }
    }

    public static class MapSlice {
        public final int col; // 1-based, 横向第几列 (从左到右 1..N)
        public final int row; // 1-based, 纵向第几行 (从上到下 1..N)
        public final byte[] pixels; // 首帧静态像素
        public final List<byte[]> animationFrames; // 包含所有动态帧
        public final boolean isAnimated;

        public MapSlice(int col, int row, byte[] pixels, List<byte[]> animationFrames) {
            this.col = col;
            this.row = row;
            this.pixels = pixels;
            this.animationFrames = animationFrames;
            this.isAnimated = animationFrames != null && animationFrames.size() > 1;
        }
    }

    public static class ProcessedImage {
        public final int cols;
        public final int rows;
        public final List<MapSlice> slices;
        public final boolean isAnimated;
        public final int frameCount;

        public ProcessedImage(int cols, int rows, List<MapSlice> slices, boolean isAnimated, int frameCount) {
            this.cols = cols;
            this.rows = rows;
            this.slices = slices;
            this.isAnimated = isAnimated;
            this.frameCount = frameCount;
        }
    }

    /**
     * 下载网络图片并缩放居中处理为单张或多联画布地图切片（支持静态图片及多帧 GIF 动图）
     *
     * @param urlString      图片URL (支持 PNG, WEBP, JPEG, GIF 等)
     * @param algorithm      算法: "dither" (Floyd-Steinberg 抖动混色) 或 "none" (无抖动最近邻)
     * @param cols           横向地图张数 (>= 1)
     * @param rows           纵向地图张数 (>= 1)
     * @param defaultBgColor 空白区域填充的背景色字节
     * @param maxGifFrames   允许解析的最大 GIF 帧数
     */
    public static ProcessedImage downloadAndProcess(String urlString, String algorithm, int cols, int rows, byte defaultBgColor, int maxGifFrames) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MapDrawPlugin/1.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);

        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            byte[] bytes = in.readAllBytes();
            return downloadAndProcessFromBytes(bytes, algorithm, cols, rows, defaultBgColor, maxGifFrames);
        }
    }

    /**
     * 直接从图片二进制字节数组解码处理（供客户端 Mod 原生分包上传使用）
     */
    public static ProcessedImage downloadAndProcessFromBytes(byte[] imageBytes, String algorithm, int cols, int rows, byte defaultBgColor, int maxGifFrames) throws Exception {
        cols = Math.max(1, cols);
        rows = Math.max(1, rows);

        ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes));
        if (iis == null) {
            throw new IllegalArgumentException("无法打开图片输入流");
        }

        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            throw new IllegalArgumentException("无法解析为有效图片 (支持 PNG, WEBP, JPEG, GIF 等主流格式)");
        }

        ImageReader reader = readers.next();
        reader.setInput(iis);

        int numImages = 1;
        try {
            numImages = reader.getNumImages(true);
        } catch (Throwable t) {
            numImages = 1;
        }

        int frameLimit = Math.max(1, Math.min(numImages, maxGifFrames));
        List<BufferedImage> rawFrames = new ArrayList<>();

        for (int i = 0; i < frameLimit; i++) {
            try {
                BufferedImage f = reader.read(i);
                if (f != null) {
                    rawFrames.add(f);
                }
            } catch (Throwable ignored) {
                break;
            }
        }

        reader.dispose();
        iis.close();

        if (rawFrames.isEmpty()) {
            throw new IllegalArgumentException("未能读取到有效的图片帧数据");
        }

        int origW = rawFrames.get(0).getWidth();
        int origH = rawFrames.get(0).getHeight();

        // 防解压炸弹限制
        if (origW > 4096 || origH > 4096) {
            throw new IllegalArgumentException("图片单帧分辨率过大 (最大允许 4096x4096)");
        }

        int totalW = cols * 128;
        int totalH = rows * 128;

        double scale = Math.min((double) totalW / origW, (double) totalH / origH);
        int targetW = (int) Math.round(origW * scale);
        int targetH = (int) Math.round(origH * scale);

        targetW = Math.max(1, Math.min(totalW, targetW));
        targetH = Math.max(1, Math.min(totalH, targetH));

        int offsetX = (totalW - targetW) / 2;
        int offsetY = (totalH - targetH) / 2;
        Color bgAwtColor = ColorUtil.getPaletteColor(defaultBgColor);

        boolean useDither = !"none".equalsIgnoreCase(algorithm) && !"nearest".equalsIgnoreCase(algorithm);

        // 存储切片结果：每个切片位置对应一组帧
        int totalSlices = cols * rows;
        List<List<byte[]>> sliceFrameLists = new ArrayList<>(totalSlices);
        for (int i = 0; i < totalSlices; i++) {
            sliceFrameLists.add(new ArrayList<>());
        }

        for (BufferedImage rawFrame : rawFrames) {
            Image scaled = rawFrame.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
            BufferedImage scaledImage = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gScaled = scaledImage.createGraphics();
            gScaled.drawImage(scaled, 0, 0, null);
            gScaled.dispose();

            BufferedImage canvasImg = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gCanvas = canvasImg.createGraphics();
            gCanvas.setColor(bgAwtColor);
            gCanvas.fillRect(0, 0, totalW, totalH);
            gCanvas.drawImage(scaledImage, offsetX, offsetY, null);
            gCanvas.dispose();

            byte[][] fullPixels = processPixels(canvasImg, totalW, totalH, useDither);

            int sliceIndex = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    byte[] sliceBytes = new byte[CanvasData.TOTAL_PIXELS];
                    int startX = c * 128;
                    int startY = r * 128;
                    for (int y = 0; y < 128; y++) {
                        for (int x = 0; x < 128; x++) {
                            sliceBytes[y * 128 + x] = fullPixels[startY + y][startX + x];
                        }
                    }
                    sliceFrameLists.get(sliceIndex).add(sliceBytes);
                    sliceIndex++;
                }
            }
        }

        List<MapSlice> slices = new ArrayList<>();
        int sliceIndex = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                List<byte[]> frames = sliceFrameLists.get(sliceIndex);
                byte[] firstFrame = frames.get(0);
                slices.add(new MapSlice(c + 1, r + 1, firstFrame, frames));
                sliceIndex++;
            }
        }

        boolean isAnimated = rawFrames.size() > 1;
        return new ProcessedImage(cols, rows, slices, isAnimated, rawFrames.size());
    }

    private static byte[][] processPixels(BufferedImage canvasImg, int totalW, int totalH, boolean useDither) {
        byte[][] fullMapPixels = new byte[totalH][totalW];

        if (useDither) {
            float[][] rArr = new float[totalH][totalW];
            float[][] gArr = new float[totalH][totalW];
            float[][] bArr = new float[totalH][totalW];

            for (int y = 0; y < totalH; y++) {
                for (int x = 0; x < totalW; x++) {
                    int rgb = canvasImg.getRGB(x, y);
                    rArr[y][x] = (rgb >> 16) & 0xFF;
                    gArr[y][x] = (rgb >> 8) & 0xFF;
                    bArr[y][x] = rgb & 0xFF;
                }
            }

            for (int y = 0; y < totalH; y++) {
                for (int x = 0; x < totalW; x++) {
                    int cr = Math.min(255, Math.max(0, Math.round(rArr[y][x])));
                    int cg = Math.min(255, Math.max(0, Math.round(gArr[y][x])));
                    int cb = Math.min(255, Math.max(0, Math.round(bArr[y][x])));

                    byte mapColor = ColorUtil.toMapColor(new Color(cr, cg, cb));
                    fullMapPixels[y][x] = mapColor;

                    Color matched = ColorUtil.getPaletteColor(mapColor);
                    float errR = cr - matched.getRed();
                    float errG = cg - matched.getGreen();
                    float errB = cb - matched.getBlue();

                    if (x + 1 < totalW) {
                        rArr[y][x + 1] += errR * 7.0f / 16.0f;
                        gArr[y][x + 1] += errG * 7.0f / 16.0f;
                        bArr[y][x + 1] += errB * 7.0f / 16.0f;
                    }
                    if (x - 1 >= 0 && y + 1 < totalH) {
                        rArr[y + 1][x - 1] += errR * 3.0f / 16.0f;
                        gArr[y + 1][x - 1] += errG * 3.0f / 16.0f;
                        bArr[y + 1][x - 1] += errB * 3.0f / 16.0f;
                    }
                    if (y + 1 < totalH) {
                        rArr[y + 1][x] += errR * 5.0f / 16.0f;
                        gArr[y + 1][x] += errG * 5.0f / 16.0f;
                        bArr[y + 1][x] += errB * 5.0f / 16.0f;
                    }
                    if (x + 1 < totalW && y + 1 < totalH) {
                        rArr[y + 1][x + 1] += errR * 1.0f / 16.0f;
                        gArr[y + 1][x + 1] += errG * 1.0f / 16.0f;
                        bArr[y + 1][x + 1] += errB * 1.0f / 16.0f;
                    }
                }
            }
        } else {
            for (int y = 0; y < totalH; y++) {
                for (int x = 0; x < totalW; x++) {
                    int rgb = canvasImg.getRGB(x, y);
                    int cr = (rgb >> 16) & 0xFF;
                    int cg = (rgb >> 8) & 0xFF;
                    int cb = rgb & 0xFF;
                    fullMapPixels[y][x] = ColorUtil.toMapColor(new Color(cr, cg, cb));
                }
            }
        }

        return fullMapPixels;
    }
}
