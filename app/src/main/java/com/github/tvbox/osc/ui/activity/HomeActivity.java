package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.HomeFocusEvent;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.RailMoreDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.DoubanFragment;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.blankj.utilcode.util.GsonUtils;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.github.tvbox.osc.util.BlurUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HomeCache;
import com.github.tvbox.osc.util.ImgUtil;
import com.google.gson.reflect.TypeToken;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {

    // takagen99: Added to allow read string
    private static Resources res;

    private ImageView bgBlur;
    /** 上一次用于生成背景模糊图的封面地址，避免同一张图重复解码 */
    private String lastBgPic;

    private View currentView;
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvName;
    private TextView tvSrc;
    private View leftRail;
    private View tvFind;
    private View tvHistory;
    private View tvFav;
    private View tvHome;
    private View tvPush;
    private View tvPerson;
    private boolean railExpanded = false;
    private ValueAnimator railAnim;
    /** 展开时的托底玻璃面，跟 leftRail 同步改宽 */
    private View railBackdrop;
    /** 折叠态的三个基准值（px）：左栏宽、内容区左边距、顶栏左边距。
     *  展开时按「左栏多出来的宽度」同步加到后两者上，内容是真被挤开，不是浮在下面。 */
    private int railBaseWidth;
    private int contentBaseLeft;
    private int topBaseLeft;
    private TextView tvDate;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private View chipSelectedView = null;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            Date date = new Date();
            @SuppressLint("SimpleDateFormat")
			//修改时间分隔符
            SimpleDateFormat timeFormat = new SimpleDateFormat(getString(R.string.hm_date1) + " | " + getString(R.string.hm_date2));
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        // takagen99: Added to allow read string
        res = getResources();

        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        App.startWebserver();
        initView();
        initViewModel();
        // 冷启动不再每次都重新下载配置（44KB JSON + 881KB spider jar）。
        // 12 小时内一律先用本地那份，进主界面几乎不用等网络；
        // 超过 12 小时才走一次网络刷新，避免源站换了地址还一直用旧配置。
        long lastConfigTime = Hawk.get(HawkConfig.HOME_CONFIG_TIME, 0L);
        useCacheConfig = System.currentTimeMillis() - lastConfigTime < 12L * 60 * 60 * 1000;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", useCacheConfig);
        }
        // 配置就位后，先用缓存把分类 tab 立起来，让首屏立刻有东西
        restoreSortFromCache();
        initData();
    }

    // takagen99: Added to allow read string
    public static Resources getRes() {
        // 启动页会先于 HomeActivity 调用 ApiConfig.loadConfig，那时 res 还是 null，
        // 直接 .getString() 就是空指针。这里回落到 Application 的 Resources，
        // 保证任何时机调用都拿得到（Application 的 resources 与 Activity 同源）。
        if (res == null) {
            App app = App.getInstance();
            if (app != null) res = app.getResources();
        }
        return res;
    }

    private void initView() {
        this.bgBlur = findViewById(R.id.bgBlur);
        this.topLayout = findViewById(R.id.topLayout);
        this.tvName = findViewById(R.id.tvName);
        this.tvSrc = findViewById(R.id.tvSrc);
        this.leftRail = findViewById(R.id.leftRail);
        this.tvFind = findViewById(R.id.tvFind);
        this.tvHistory = findViewById(R.id.tvHistory);
        this.tvFav = findViewById(R.id.tvFav);
        this.tvHome = findViewById(R.id.tvHome);
        this.tvPush = findViewById(R.id.tvPush);
        this.tvPerson = findViewById(R.id.tvPerson);
        // 左栏固定 76 宽只显图标，不再展开：bindRailFocus 已停用
        this.tvDate = findViewById(R.id.tvDate);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridViewCategory);
        this.mViewPager = findViewById(R.id.mViewPager);

        // 记下折叠态的基准，展开时在它上面加位移（XML 里改过数值这里会自动跟上）
        railBaseWidth = leftRail.getLayoutParams().width;
        contentBaseLeft = ((ViewGroup.MarginLayoutParams) contentLayout.getLayoutParams()).getMarginStart();
        topBaseLeft = ((ViewGroup.MarginLayoutParams) topLayout.getLayoutParams()).getMarginStart();
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(false);
                    // 不再硬写文字颜色：常态/焦点/当前分类三态由 sel_pill_text 统一表达
                    textView.invalidate();
                    view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    view.animate().scaleX(1.06f).scaleY(1.06f).setInterpolator(new BounceInterpolator()).setDuration(150).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.invalidate();
                    // 只有当前分类用主色填充，其余回落到常态胶囊，避免满屏都是高亮
                    if (chipSelectedView != null && chipSelectedView != view) {
                        chipSelectedView.setSelected(false);
                    }
                    view.setSelected(true);
                    chipSelectedView = view;
//                    if (!sortAdapter.getItem(position).filters.isEmpty())
//                        view.findViewById(R.id.tvFilter).setVisibility(View.VISIBLE);
                    if (position == -1) {
                        position = 0;
                        HomeActivity.this.mGridView.setSelection(0);
                    }
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (null != sortData && !sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    } else if (baseLazyFragment instanceof UserFragment) {
                        showSiteSwitch();
                    }
                }
            }
        });
        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (direction == View.FOCUS_UP) {
                    BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                    if ((baseLazyFragment instanceof GridFragment)) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();
            }
        });
        // Button : TVBOX >> Delete Cache / Longclick to Refresh Source --
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                File dir = getCacheDir();
                FileUtils.recursiveDelete(dir);
                dir = getExternalCacheDir();
                FileUtils.recursiveDelete(dir);
                Toast.makeText(HomeActivity.this, getString(R.string.hm_cache_del), Toast.LENGTH_SHORT).show();
                if(dataInitOk && jarInitOk){
                    String cspCachePath = FileUtils.getFilePath()+"/csp/";
                    String jar=ApiConfig.get().getHomeSourceBean().getJar();
                    String jarUrl=!jar.isEmpty()?jar:ApiConfig.get().getSpider();
                    File cspCacheDir = new File(cspCachePath + MD5.string2MD5(jarUrl)+".jar");
                    if (!cspCacheDir.exists()){
                        reloadHome();
                        return;
                    }
                    new Thread(() -> {
                        try {
                            FileUtils.deleteFile(cspCacheDir);
                            ApiConfig.get().clearJarLoader();
                            reloadHome();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                reloadHome();
                return true;
            }
        });
        // Button : Search --------------------------------------------
        tvFind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpActivity(SearchActivity.class);
            }
        });
        // Button : 首页 >> 回到推荐 tab 的第一屏 ---------------------------------
        tvHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                if (mViewPager != null) mViewPager.setCurrentItem(0, false);
                if (mGridView != null) mGridView.requestFocus();
            }
        });
        // Button : 个人 >> 设置类入口（设置 / 换源）-----------------
        tvPerson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                RailMoreDialog dialog = new RailMoreDialog(mContext, new RailMoreDialog.OnActionListener() {
                    @Override
                    public void onSettings() {
                        jumpActivity(SettingActivity.class);
                    }

                    @Override
                    public void onSite() {
                        showSiteSwitch();
                    }
                });
                dialog.show();
            }
        });
        // Button : 历史 >> 观看记录 -----------------------------------
        tvHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                jumpActivity(HistoryActivity.class);
            }
        });
        // Button : 收藏 -----------------------------------------------
        tvFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                jumpActivity(CollectActivity.class);
            }
        });
        // Button : 推送（原「我的」页里的入口，现在收进左栏）-----------
        tvPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                jumpActivity(PushActivity.class);
            }
        });

        // Button : Date >> Go into Android Date Settings --------------
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 250);
    }
    //站点切换
    public static void homeRecf() {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, -1);
        int limit = 2;
        if (homeRec == limit) homeRec = -1;
        homeRec++;
        Hawk.put(HawkConfig.HOME_REC, homeRec);
    }
    
    public static boolean reHome(Context appContext) {
        Intent intent = new Intent(appContext, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        appContext.startActivity(intent);
        return true;
    }

    private boolean skipNextUpdate = false;	
    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                showSuccess();
                List<MovieSort.SortData> incoming;
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    incoming = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true);
                    HomeCache.put(sortCacheKey(), GsonUtils.toJson(absXml.classes.sortList));
                } else {
                    incoming = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true);
                }
                if (sortRestoredFromCache && sameSortIds(incoming)) {
                    // 分类没变：缓存立起来的 tab 就是对的，不重建。
                    // 重建会让每个 GridFragment 重新请求、焦点回第一张，白闪一下。
                    sortRestoredFromCache = false;
                } else {
                    sortAdapter.setNewData(incoming);
                    initViewPager(absXml);
                    sortRestoredFromCache = false;
                }
                // takagen99 : Switch to show / hide source title
                // 源名不再顶在大标题位置（原来显示成「公众号【盒子迷MAX】」，像山寨站点招牌），
                // 改成右上角一行小字，标题固定是 App 名。
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                if (tvSrc != null && home != null && home.getName() != null) tvSrc.setText(home.getName());
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;

    // takagen99 : Switch to show / hide source title
    boolean HomeShow = Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false);

    // takagen99 : Check if network is available
    boolean isNetworkAvailable() {
        ConnectivityManager cm
                = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    /**
     * 焦点进出左栏时展开 / 收起。
     *
     * 关键点：条目的 layout_width 永远是 176，收起时靠 leftRail 自己的宽度（76）
     * 加上 clipChildren 把右边裁掉，只露图标。所以动画期间只有 leftRail 一个
     * LayoutParams 在变，里面 8 个条目的 measured width 一直是 176，不会逐帧重排。
     * 直接改条目宽度的话，每一帧都要重新 measure 8 个 LinearLayout，低端芯片必卡。
     */
    private void bindRailFocus() {
        View[] items = {tvFind, tvHistory, tvHome, tvFav, tvPush, tvPerson};
        for (View item : items) {
            if (item == null) continue;
            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        setRailExpanded(true);
                    } else {
                        // 延迟 60ms 再判断，等焦点真正落到新位置，
                        // 否则左栏内部上下移动时会被误判成“离开左栏”而收起
                        v.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (leftRail != null && !leftRail.hasFocus()) {
                                    setRailExpanded(false);
                                }
                            }
                        }, 60);
                    }
                }
            });
        }
    }

    private void setRailExpanded(boolean expand) {
        if (leftRail == null || railExpanded == expand) return;
        railExpanded = expand;
        if (railAnim != null) railAnim.cancel();
        int from = leftRail.getLayoutParams().width;
        int to = getResources().getDimensionPixelSize(expand ? R.dimen.vs_176 : R.dimen.vs_76);
        railAnim = ValueAnimator.ofInt(from, to);
        railAnim.setDuration(expand ? 220 : 160);
        // 展开用减速（一口气拉开、尾巴缓住），收起用加速（收得干脆）
        railAnim.setInterpolator(expand ? new DecelerateInterpolator() : new AccelerateInterpolator());
        railAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int w = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams lp = leftRail.getLayoutParams();
                lp.width = w;
                leftRail.setLayoutParams(lp);
                // 托底跟着一起变宽，否则文字会先于面板露出来，看着像"飘在外面"
                if (railBackdrop != null) {
                    ViewGroup.LayoutParams blp = railBackdrop.getLayoutParams();
                    blp.width = w;
                    railBackdrop.setLayoutParams(blp);
                }
                // 内容区真让位：左栏宽出来的那部分，同步加到顶栏和内容区的左边距上。
                // 代价是每帧都要重新测量内容区（海报墙会跟着变窄重排），但这是"挤过去"
                // 必须付的成本 —— 用 translationX 平移虽然不掉帧，右边缘会被裁掉，不是挤压。
                applyRailShift(w - railBaseWidth);
            }
        });
        railAnim.start();
        if (railBackdrop != null) {
            railBackdrop.animate().alpha(expand ? 1f : 0f)
                    .setDuration(expand ? 200 : 140)
                    .start();
        }
        fadeRailLabels(expand);
    }

    /**
     * 把左栏"多出来的宽度"同步给顶栏和内容区，做到真挤压而不是浮层遮挡。
     *
     * 只有数值真的变了才 setLayoutParams，避免动画停在同一像素时白触发一次整树重测。
     * 注意这里不能偷懒改成 translationX 平移：平移不掉帧，但内容宽度不变，
     * 右边缘会被推出屏幕裁掉，看着像"整块海报墙往右跑"，不是被挤窄。
     */
    private void applyRailShift(int extra) {
        if (extra < 0) extra = 0;
        if (contentLayout != null) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) contentLayout.getLayoutParams();
            int target = contentBaseLeft + extra;
            if (lp.getMarginStart() != target) {
                lp.setMarginStart(target);
                contentLayout.setLayoutParams(lp);
            }
        }
        if (topLayout != null) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams();
            int target = topBaseLeft + extra;
            if (lp.getMarginStart() != target) {
                lp.setMarginStart(target);
                topLayout.setLayoutParams(lp);
            }
        }
    }

    /**
     * 文字跟着面板走：展开时从图标底下往右推出来（起始 -10 再归 0），
     * 收起时先淡掉再回位，所以收起比展开快一档。
     */
    private void fadeRailLabels(boolean show) {
        if (!(leftRail instanceof ViewGroup)) return;
        ViewGroup rail = (ViewGroup) leftRail;
        int shift = getResources().getDimensionPixelSize(R.dimen.vs_10);
        for (int i = 0; i < rail.getChildCount(); i++) {
            View child = rail.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) child;
            for (int j = 0; j < row.getChildCount(); j++) {
                View sub = row.getChildAt(j);
                if (sub instanceof TextView) {
                    sub.animate().cancel();
                    // 首次展开时文字还没被推过，先摆到起点，否则第一下是"凭空出现"
                    if (!show || (sub.getAlpha() == 0f && sub.getTranslationX() == 0f)) {
                        sub.setTranslationX(-shift);
                    }
                    sub.animate()
                            .alpha(show ? 1f : 0f)
                            .translationX(show ? 0f : -shift)
                            .setDuration(show ? 180 : 90)
                            .setStartDelay(show ? 40 : 0)
                            .start();
                }
            }
        }
    }

    private void initData() {
        // 网络状态不再画在左栏底部（底部只留「个人」），入口收进「个人」菜单。
        // 2026 重设计：左侧导航的图标固定，不再随“网格/列表”开关换图

        mGridView.requestFocus();

        if (dataInitOk && jarInitOk) {
            sourceViewModel.getSort(ApiConfig.get().getHomeSourceBean().getKey());
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            if (Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false)) {
                jumpActivity(LivePlayActivity.class);
            }         
            return;
        }
        // 缓存命中时不要放转圈：restoreSortFromCache 已经把分类 tab 立起来、
        // GridFragment 的海报缓存也铺好了，转圈盖在上面等于让用户盯着空屏
        // 白等一次 getSort 网络（spider 源的 homeContent 动辄 20 秒）。
        // 分类数据仍会在后台刷新，回来若与缓存一致不会重建页面（见 sortResult.onChanged）。
        if (sortRestoredFromCache || (sortAdapter != null && !sortAdapter.getData().isEmpty())) {
            showSuccess();
        } else {
            showLoading();
        }
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!useCacheConfig) {
                                    Toast.makeText(HomeActivity.this, getString(R.string.hm_ok), Toast.LENGTH_SHORT).show();
                                }
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void retry() {

                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if ("".equals(msg))
                                    Toast.makeText(HomeActivity.this, getString(R.string.hm_notok), Toast.LENGTH_SHORT).show();
                                else
                                    Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        },50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            TipDialog dialog = null;

            @Override
            public void retry() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                // 只有真的走了网络才更新时间戳；命中缓存不算，否则永远刷新不了
                if (!useCacheConfig) {
                    Hawk.put(HawkConfig.HOME_CONFIG_TIME, System.currentTimeMillis());
                }
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog == null)
                            dialog = new TipDialog(HomeActivity.this, msg, getString(R.string.hm_retry), getString(R.string.hm_cancel), new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }
                            });
                        if (!dialog.isShowing())
                            dialog.show();
                    }
                });
            }
        }, this);
    }

    /** 本次 tab 是不是从缓存恢复的（网络回来后若一致就不重建，避免闪一下） */
    private boolean sortRestoredFromCache = false;

    private String sortCacheKey() {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        return HomeCache.PREFIX_SORT + (home == null ? "" : home.getKey());
    }

    /**
     * 用上次的分类列表先把 tab 立起来。
     * 这一步不依赖网络，所以首屏能立刻出现"首页/电影/剧集…"和对应的海报墙。
     */
    private void restoreSortFromCache() {
        try {
            String json = HomeCache.get(sortCacheKey());
            if (json == null) return;
            List<MovieSort.SortData> list = GsonUtils.fromJson(json, new TypeToken<List<MovieSort.SortData>>() {
            }.getType());
            if (list == null || list.isEmpty()) return;
            sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), list, true));
            initViewPager(null);
            sortRestoredFromCache = true;
        } catch (Throwable th) {
            sortRestoredFromCache = false;
        }
    }

    /** 网络回来的分类列表和缓存的是不是同一批（按 id 顺序比） */
    private boolean sameSortIds(List<MovieSort.SortData> incoming) {
        List<MovieSort.SortData> now = sortAdapter.getData();
        if (now == null || incoming == null || now.size() != incoming.size()) return false;
        for (int i = 0; i < now.size(); i++) {
            String a = now.get(i).id;
            String b = incoming.get(i).id;
            if (a == null ? b != null : !a.equals(b)) return false;
        }
        return true;
    }

    private void initViewPager(AbsSortXml absXml) {
        // 必须清空：这个方法会被调用多次（先缓存后网络），
        // 不清空的话 fragments 会一路累加，fragments.get(index) 全部错位。
        fragments.clear();
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    // 第一个 tab 挂豆瓣推荐。
                    //
                    // 演变过程：最初挂的是原版「我的」页（六个大按钮占半屏，海报被挤成两行）；
                    // 上一版改成「第一个真实分类的深拷贝」，结果和隔壁「内地」tab 内容一模一样，
                    // 白占一个位置。豆瓣推荐用的是另一套数据，还带真实评分，才配叫「推荐」。
                    fragments.add(DoubanFragment.newInstance());
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

    @Override
    public void onBackPressed() {
        //打断加载
        if(isLoading()){
            refreshEmpty();
            return;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            if (UserFragment.homeHotVodAdapter != null) UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            doExit();
            return;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
            } else {
                doExit();
            }
        } else if (baseLazyFragment instanceof DoubanFragment) {
            if (((DoubanFragment) baseLazyFragment).scrollToTopIfNeeded()) return;
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            } else {
                doExit();
            }
        } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotListForGrid != null
                && UserFragment.tvHotListForGrid.canScrollVertically(-1)) {
            // 如果 UserFragment 列表可以向上滚动，则滚动到顶部
            UserFragment.tvHotListForGrid.scrollToPosition(0);
            this.mGridView.setSelection(0);
        } else {
            doExit();
        }
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity();
            EventBus.getDefault().unregister(this);
            ControlManager.get().stopServer();
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } else {
            // 否则仅提示用户，再按一次退出应用
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, getString(R.string.hm_exit), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // takagen99 : Switch to show / hide source title
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (tvName != null) tvName.setText(R.string.app_name);
        if (tvSrc != null && home != null && home.getName() != null) tvSrc.setText(home.getName());

        // 左栏是唯一的功能入口，搜索和设置必须常驻。
        // 原来这里按 HOME_SEARCH_POSITION 隐藏 tvFind（默认值是 false，即"放下方"），
        // 而"下方"那个位置属于已被移除的「我的」页 —— 结果就是搜索按钮凭空消失。
        if (tvFind != null) tvFind.setVisibility(View.VISIBLE);
        if (tvPerson != null) tvPerson.setVisibility(View.VISIBLE);
        mHandler.post(mRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
//                showFilterIcon((int) event.obj);
            }
        }
    }

    private void showFilterIcon(int count) {
        boolean activated = count > 0;
        currentView.findViewById(R.id.tvFilter).setVisibility(View.VISIBLE);
        ImageView imgView = currentView.findViewById(R.id.tvFilter);
        imgView.setColorFilter(activated ? this.getThemeColor() : Color.WHITE);
    }

    /**
     * 焦点变更。
     *
     * 底部信息条已经撤掉（它半透明压在海报上读不清，片名还和卡片里的字重复），
     * 现在的信息全部由卡片自己的浮层承担，这里只剩一个职责：把背景换成当前海报的模糊图。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onHomeFocus(HomeFocusEvent event) {
        if (event == null) return;
        updateBackdrop(event.pic);
    }

    /**
     * 把当前焦点影片的封面换成全屏模糊底图。
     *
     * 这是「苹果玻璃」观感的根基：面板本身只是半透明白，真正像玻璃的是
     * 后面那层被模糊掉的海报。做法是把封面缩到 160x90 再跑三次盒式模糊，
     * 视觉上等价于大半径高斯，成本只有几毫秒；直接对 4K 海报做模糊会卡顿。
     */
    private void updateBackdrop(String pic) {
        if (bgBlur == null || pic == null || pic.isEmpty()) return;
        if (pic.equals(lastBgPic)) return;
        lastBgPic = pic;
        Glide.with(this).asBitmap().load(ImgUtil.urlFor(pic)).override(160, 90).centerCrop()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource,
                                                @Nullable Transition<? super Bitmap> transition) {
                        // 图片回来时焦点可能已经换人了，过期的结果直接丢掉
                        if (!lastBgPic.equals(pic)) return;
                        Bitmap small = Bitmap.createScaledBitmap(resource, 160, 90, true);
                        bgBlur.setImageBitmap(BlurUtil.blur(small, 7));
                        if (bgBlur.getAlpha() < 1f) {
                            bgBlur.setAlpha(0f);
                            bgBlur.animate().alpha(1f).setDuration(320).start();
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    changeTop(sortFocused != 0);
                }
            }
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                showSiteSwitch();
            }
//            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
//                if () {
//
//                }
//            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {

        }
        return super.dispatchKeyEvent(event);
    }

    byte topHide = 0;

    /**
     * 2026 重设计：新版首页固定「左侧导航 + 顶部标题 + 类型胶囊」框架，不再随分类切换收合顶栏。
     * 原因有二：
     * 1) 左侧导航是常驻入口，只收合标题会让人以为导航消失；
     * 2) 投影仪上画面整体位移会打断观看，收益（多出 70px）不抵这个代价。
     * 这里保留方法签名与 topHide 状态位，dispatchKeyEvent 里还依赖它。
     */
    private void changeTop(boolean hide) {
        topHide = (byte) (hide ? 1 : 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        AppManager.getInstance().appExit(0);
        ControlManager.get().stopServer();
    }

    // Site Switch on Home Button
    void showSiteSwitch() {
        List<SourceBean> sites = new ArrayList<>();
        for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
            if (sb.getHide() == 0) sites.add(sb);
        }
        if (sites.size() > 0) {
            SelectDialog<SourceBean> dialog = new SelectDialog<>(HomeActivity.this);

            // Multi Column Selection
            int spanCount = (int) Math.floor(sites.size() / 10);
            if (spanCount <= 1) spanCount = 1;
            if (spanCount >= 3) spanCount = 3;

            TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), spanCount));
            ConstraintLayout cl_root = dialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            if (spanCount != 1) {
                clp.width = AutoSizeUtils.mm2px(dialog.getContext(), 400 + 260 * (spanCount - 1));
            }

            dialog.setTip(getString(R.string.dia_source));
            dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                @Override
                public void click(SourceBean value, int pos) {
                    ApiConfig.get().setSourceBean(value);
                    reloadHome();
                }

                @Override
                public String getDisplay(SourceBean val) {
                    return val.getName();
                }
            }, new DiffUtil.ItemCallback<SourceBean>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem == newItem;
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem.getKey().equals(newItem.getKey());
                }
            }, sites, sites.indexOf(ApiConfig.get().getHomeSourceBean()));
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
//                    if (homeSourceKey != null && !homeSourceKey.equals(Hawk.get(HawkConfig.HOME_API, ""))) {
//                        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
//                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        Bundle bundle = new Bundle();
//                        bundle.putBoolean("useCache", true);
//                        intent.putExtras(bundle);
//                        HomeActivity.this.startActivity(intent);
//                    }
                }
            });
            dialog.show();
        }
    }

    void reloadHome() {
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        HomeActivity.this.startActivity(intent);
    }

    private void refreshEmpty() {
        skipNextUpdate=true;
        showSuccess();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
    }

//    public void onClick(View v) {
//        FastClickCheckUtil.check(v);
//        if (v.getId() == R.id.tvFind) {
//            jumpActivity(SearchActivity.class);
//        } else if (v.getId() == R.id.tvMenu) {
//            jumpActivity(SettingActivity.class);
//        }
//    }

}
