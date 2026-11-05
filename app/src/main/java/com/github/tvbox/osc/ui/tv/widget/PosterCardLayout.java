package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * 海报卡片容器：宽度跟随 GridLayoutManager 分配的 span，高度强制 = 宽度 × 1.5（2:3）。
 *
 * 为什么需要它：
 * 之前卡片高宽由 item_grid.xml 里的固定 mm 值决定，而 GridLayoutManager 的列宽随屏幕变，
 * 两者对不上就会出现「卡片比列宽宽 → 第 7 列被屏幕切掉」。同时海报原图多为 1.3:1，
 * 会把 wrap_content 的容器撑成矮胖形，一行卡片的高度全看图片心情。
 *
 * 改成「宽度 match_parent + 高度由宽度算」，列宽永远等于 span，比例永远是 2:3，
 * 换任何分辨率都不会溢出，也不会变形。
 */
public class PosterCardLayout extends FrameLayout {

    /** 海报标准比例，高 / 宽 */
    private static final float RATIO = 1.5f;

    /**
     * 统一高度（px），由 GridFragment 按「中间列宽 × 1.5」算好传进来。
     *
     * 为什么不直接用自己的宽度算：一行里首列和末列的 item 只有一侧有间距，
     * 会比中间列宽出半个 gap，各列高度就会差十几像素，底边不齐。
     * 用一个统一高度，卡片宽度有几像素出入看不出来，但整行底边是干净的。
     */
    private int fixedHeight = 0;

    public void setFixedHeight(int height) {
        if (fixedHeight == height) return;
        fixedHeight = height;
        requestLayout();
    }

    public PosterCardLayout(Context context) {
        super(context);
    }

    public PosterCardLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PosterCardLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int height = fixedHeight > 0 ? fixedHeight : (int) (width * RATIO + 0.5f);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
