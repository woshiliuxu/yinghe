package com.github.tvbox.osc.util;

import android.graphics.Bitmap;

/**
 * 背景模糊工具。
 *
 * <p>首页/详情的「苹果玻璃」观感，靠一张铺满全屏的高斯模糊海报托底：
 * 玻璃面板只是半透明白，真正像玻璃的是后面那层被模糊掉的内容。</p>
 *
 * <p>实现上不做全分辨率模糊——把封面缩到 128px 宽再模糊，最后让 ImageView
 * 用 centerCrop 拉回全屏。视觉上等价于大半径高斯，耗时只有几毫秒，
 * 而且不需要额外依赖（项目里的 Glide 没带 transformations 库）。</p>
 */
public final class BlurUtil {

    private BlurUtil() {
    }

    /**
     * 三次盒式模糊 ≈ 一次高斯模糊。
     *
     * @param src    源图（建议先缩到 128px 宽，越大越慢）
     * @param radius 半径（像素）。在 128px 宽的图上，6~9 之间比较自然
     */
    public static Bitmap blur(Bitmap src, int radius) {
        if (src == null || radius < 1) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return src;

        int[] pix = new int[w * h];
        src.getPixels(pix, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];

        for (int i = 0; i < 3; i++) {
            boxBlurH(pix, tmp, w, h, radius);
            boxBlurV(tmp, pix, w, h, radius);
        }

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pix, 0, w, 0, 0, w, h);
        return out;
    }

    /** 水平方向滑动窗口求均值 */
    private static void boxBlurH(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int a = 0, rr = 0, gg = 0, bb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[row + clamp(i, w)];
                a += (c >>> 24);
                rr += (c >> 16) & 0xFF;
                gg += (c >> 8) & 0xFF;
                bb += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((a / div) << 24) | ((rr / div) << 16) | ((gg / div) << 8) | (bb / div);
                int ca = in[row + clamp(x - r, w)];
                int cb = in[row + clamp(x + r + 1, w)];
                a += (cb >>> 24) - (ca >>> 24);
                rr += ((cb >> 16) & 0xFF) - ((ca >> 16) & 0xFF);
                gg += ((cb >> 8) & 0xFF) - ((ca >> 8) & 0xFF);
                bb += (cb & 0xFF) - (ca & 0xFF);
            }
        }
    }

    /** 垂直方向滑动窗口求均值 */
    private static void boxBlurV(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int a = 0, rr = 0, gg = 0, bb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[clamp(i, h) * w + x];
                a += (c >>> 24);
                rr += (c >> 16) & 0xFF;
                gg += (c >> 8) & 0xFF;
                bb += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((a / div) << 24) | ((rr / div) << 16) | ((gg / div) << 8) | (bb / div);
                int ca = in[clamp(y - r, h) * w + x];
                int cb = in[clamp(y + r + 1, h) * w + x];
                a += (cb >>> 24) - (ca >>> 24);
                rr += ((cb >> 16) & 0xFF) - ((ca >> 16) & 0xFF);
                gg += ((cb >> 8) & 0xFF) - ((ca >> 8) & 0xFF);
                bb += (cb & 0xFF) - (ca & 0xFF);
            }
        }
    }

    private static int clamp(int v, int size) {
        if (v < 0) return 0;
        if (v >= size) return size - 1;
        return v;
    }
}
