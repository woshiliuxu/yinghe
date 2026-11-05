package com.github.tvbox.osc.ui.activity;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 启动页。
 *
 * 之所以要有这一页，是因为原来 LAUNCHER 直接是 HomeActivity，而主题的 windowBackground
 * 是 transparent —— 进程起来的头一两秒系统只能给一块黑，然后才开始下载配置和 spider。
 *
 * 这里做两件事：
 * 1. 窗口一创建就是纯黑 + 圆形图标，立刻有画面，不会再有"黑屏等半天"。
 * 2. 进度条接的是 ApiConfig 的真实回调：配置 JSON 解析完推到 0.55，spider 就绪推到 1.0，
 *    两个都好了才进主界面。HomeActivity 那边会通过 useCache 标识知道自己不必再加载一遍。
 *
 * 第二次冷启动有 12 小时本地缓存，通常 400ms 内走完；只有真的走网络刷新配置时才会看几秒。
 */
public class SplashActivity extends Activity {

    /** 再快也至少让用户看清这一屏，否则闪一下反而像闪退 */
    private static final long MIN_SHOW_MS = 750L;

    /** 兜底：网络卡死时也不能永远停在启动页 */
    private static final long HARD_TIMEOUT_MS = 15000L;

    private View barFill;
    private View bar;
    private TextView name;
    private TextView hint;

    private int barMax = 0;
    private float shown = 0f;
    private float target = 0.1f;
    private boolean finished = false;
    private boolean cacheMode = false;

    private final long startAt = SystemClock.elapsedRealtime();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator barAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        barFill = findViewById(R.id.splashBarFill);
        bar = findViewById(R.id.splashBar);
        name = findViewById(R.id.splashName);
        hint = findViewById(R.id.splashHint);

        long lastConfigTime = Hawk.get(HawkConfig.HOME_CONFIG_TIME, 0L);
        cacheMode = System.currentTimeMillis() - lastConfigTime < 12L * 60 * 60 * 1000;

        // 图标轻微上浮淡入，避免一上来就是一张死图
        View circle = findViewById(R.id.splashCircle);
        circle.setAlpha(0f);
        circle.setScaleX(0.92f);
        circle.setScaleY(0.92f);
        circle.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).start();
        name.animate().alpha(1f).setDuration(400).setStartDelay(180).start();

        bar.post(new Runnable() {
            @Override
            public void run() {
                barMax = bar.getWidth();
                pushProgress(0.12f);
            }
        });

        // 超过 3 秒还没完，补一行说明，免得进度条卡着像死机
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!finished) {
                    hint.animate().alpha(1f).setDuration(220).start();
                }
            }
        }, 3000);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                goHome();
            }
        }, HARD_TIMEOUT_MS);

        loadConfig();
    }

    private void loadConfig() {
        try {
            ApiConfig.get().loadConfig(cacheMode, new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    pushProgress(0.55f);
                    if (!cacheMode) {
                        // 真的走了网络才更新时间戳，命中缓存不算，否则永远刷新不了
                        Hawk.put(HawkConfig.HOME_CONFIG_TIME, System.currentTimeMillis());
                    }
                    loadJar();
                }

                @Override
                public void retry() {
                    success();
                }

                @Override
                public void error(String msg) {
                    // 配置没拉到就不要卡在启动页，交给主界面按原有逻辑提示重试
                    pushProgress(0.96f);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goHome();
                        }
                    }, 260);
                }
            }, this);
        } catch (Throwable th) {
            // 启动页只是"装饰层"，它自己出任何问题都不该让用户看到闪退：
            // 配置拉不动就直接进主界面，那边有一套完整的重试与提示逻辑。
            th.printStackTrace();
            goHome();
        }
    }

    private void loadJar() {
        String spider = ApiConfig.get().getSpider();
        if (TextUtils.isEmpty(spider)) {
            pushProgress(1f);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    goHome();
                }
            }, 220);
            return;
        }
        hint.setText("正在初始化解析引擎");
        hint.animate().alpha(1f).setDuration(200).start();

        try {
            ApiConfig.get().loadJar(cacheMode, spider, new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    pushProgress(1f);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goHome();
                        }
                    }, 220);
                }

                @Override
                public void retry() {
                }

                @Override
                public void error(String msg) {
                    // spider 加载失败不挡路，主界面自己会提示
                    pushProgress(1f);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goHome();
                        }
                    }, 220);
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
            goHome();
        }
    }

    /**
     * 进度只会往前走，不会因为回调顺序退回去。
     * barMax 还没量出来时（第一帧之前）只记 target，等 post 里补上。
     */
    private void pushProgress(float p) {
        target = Math.max(target, Math.min(1f, p));
        if (barMax <= 0) return;
        if (barAnim != null) barAnim.cancel();
        barAnim = ValueAnimator.ofFloat(shown, target);
        barAnim.setDuration(300);
        barAnim.setInterpolator(new DecelerateInterpolator());
        barAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                shown = (Float) animation.getAnimatedValue();
                ViewGroup.LayoutParams lp = barFill.getLayoutParams();
                lp.width = Math.round(barMax * shown);
                barFill.setLayoutParams(lp);
            }
        });
        barAnim.start();
    }

    private void goHome() {
        if (finished) return;
        long elapsed = SystemClock.elapsedRealtime() - startAt;
        if (elapsed < MIN_SHOW_MS) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    goHome();
                }
            }, MIN_SHOW_MS - elapsed);
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
        // 告诉主界面配置已经就绪，别再重复拉一遍
        intent.putExtra("useCache", cacheMode);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public void onBackPressed() {
        // 启动页不允许返回
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (barAnim != null) barAnim.cancel();
        super.onDestroy();
    }
}
