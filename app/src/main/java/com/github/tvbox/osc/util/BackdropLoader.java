package com.github.tvbox.osc.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * 全屏模糊底图的加载器。
 *
 * <p>首页和详情页都是「一张模糊海报铺满屏 + 半透明玻璃面板」的结构，
 * 差别只在封面来源不同，所以把这套逻辑收在一处：</p>
 *
 * <ol>
 *   <li>封面先按 160×90 解码（不是原图，4K 海报直接模糊会卡）；</li>
 *   <li>跑三次盒式模糊，视觉上等价于大半径高斯；</li>
 *   <li>淡入 320ms，避免焦点快速移动时背景闪烁。</li>
 * </ol>
 *
 * <p>用 View 的 tag 记录「当前请求的地址」，回来时对不上就丢弃，
 * 这样快速滑动不会出现背景和图对不上的情况。</p>
 */
public final class BackdropLoader {

    private BackdropLoader() {
    }

    public static void load(final Context context, final ImageView target, final String url) {
        if (context == null || target == null || url == null || url.isEmpty()) return;
        if (url.equals(target.getTag())) return;
        target.setTag(url);

        Glide.with(context).asBitmap().load(url).override(160, 90).centerCrop()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource,
                                                @Nullable Transition<? super Bitmap> transition) {
                        // 回来时焦点可能已经换片了，过期的结果直接丢掉
                        if (!url.equals(target.getTag())) return;
                        Bitmap small = Bitmap.createScaledBitmap(resource, 160, 90, true);
                        target.setImageBitmap(BlurUtil.blur(small, 7));
                        if (target.getAlpha() < 1f) {
                            target.setAlpha(0f);
                            target.animate().alpha(1f).setDuration(320).start();
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }
}
