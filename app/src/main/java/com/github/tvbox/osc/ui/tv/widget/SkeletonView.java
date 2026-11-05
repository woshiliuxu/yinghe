package com.github.tvbox.osc.ui.tv.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 加载占位骨架：按真实海报墙的排布（7 列 × 2 行、2:3、间距 16）画一层半透明的圆角块。
 *
 * 为什么不用转圈：片源首屏通常要 2~10 秒，中间只有一个 50 的 loading 转圈时，
 * 屏幕上等于什么都没有 —— 投到墙上就是一片黑。骨架屏把「马上要出现什么」先画出来，
 * 加载完成时卡片正好落在同一批位置，视觉上是"图填进来了"而不是"黑屏跳变"。
 *
 * 尺寸全部在 onDraw 里按实际宽高算，不依赖任何 mm 资源，所以在任何分辨率上都对齐。
 */
public class SkeletonView extends View {

    /** 列数，和 GridFragment 的 spanCount 保持一致 */
    private static final int COLUMN = 7;
    /** 行数 */
    private static final int ROW = 2;
    /** 卡片高 / 宽 */
    private static final float RATIO = 1.5f;
    /** 列间距（px），运行时按屏幕密度折算，避免写死 */
    private static final float GAP_DP = 16f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;

    public SkeletonView(Context context) {
        this(context, null);
    }

    public SkeletonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SkeletonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setColor(Color.WHITE);
        paint.setAlpha(18);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startBreath();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBreath();
        super.onDetachedFromWindow();
    }

    private void startBreath() {
        if (animator != null) return;
        // 透明度在 18~38 之间来回，像"正在呼吸"，但没有转圈那种催促感
        animator = ValueAnimator.ofInt(18, 38);
        animator.setDuration(1100);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                paint.setAlpha((int) animation.getAnimatedValue());
                invalidate();
            }
        });
        animator.start();
    }

    private void stopBreath() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float gap = GAP_DP * getResources().getDisplayMetrics().density;
        int left = getPaddingLeft();
        int top = getPaddingTop();
        float usable = getWidth() - getPaddingLeft() - getPaddingRight();
        if (usable <= 0) return;

        // 与真机网格严格重合：GridLayoutManager 把可用宽均分成 7 个 span，
        // ItemDecoration 再从每个 span 左右各扣 gap/2，所以卡片实际宽 = span - gap，
        // 首列左缘距容器边 gap/2。按这个反推，加载完成后卡片正好填满这些占位块。
        float span = usable / COLUMN;
        float cellW = span - gap;
        float cellH = cellW * RATIO;
        float radius = Math.min(cellW, cellH) * 0.05f;
        if (radius < 4f) radius = 4f;

        for (int row = 0; row < ROW; row++) {
            float y = top + row * (cellH + gap);
            // 超出可视区就不画了，省一层无用的绘制
            if (y > getHeight()) break;
            for (int col = 0; col < COLUMN; col++) {
                float x = left + gap / 2f + col * span;
                rect.set(x, y, x + cellW, y + cellH);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }
    }
}
