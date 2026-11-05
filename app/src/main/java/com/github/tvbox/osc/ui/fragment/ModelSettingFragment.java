package com.github.tvbox.osc.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.player.thirdparty.Kodi;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.ConfigApiDialog;
import com.github.tvbox.osc.ui.dialog.HomeIconDialog;
import com.github.tvbox.osc.ui.dialog.MediaSettingDialog;
import com.github.tvbox.osc.ui.dialog.ResetDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.XWalkInitDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HawkUtils;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;
import okhttp3.HttpUrl;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private TextView tvDebugOpen;
    private TextView tvApi;
    // Home Section
    private TextView tvHomeApi;
    private TextView tvHomeDefaultShow;
    private TextView tvHomeShow;
    private TextView tvHomeIcon;
    private TextView tvHomeRec;
    private TextView tvHomeNum;

    // Player Section
    private TextView tvShowPreviewText;
    private TextView tvScale;
    private TextView tvPlay;
    private TextView tvVideoPurifyText;

    // System Section
    private TextView tvLocale;
    private TextView tvTheme;
    private TextView tvRender;
    private TextView tvParseWebView;
    private TextView tvSearchView;
    private TextView tvDns;
    private TextView tvFastSearchText;

    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment().setArguments();
    }

    public ModelSettingFragment setArguments() {
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_model;
    }

    @Override
    protected void init() {
        tvFastSearchText = findViewById(R.id.showFastSearchText);
        tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "开启" : "关闭");
        tvApi = findViewById(R.id.tvApi);
        // 没有手动配置过（键不存在）时显示实际生效的内置默认源，而不是空白
        tvApi.setText(Hawk.get(HawkConfig.API_URL, getString(R.string.app_source)));
        // Home Section
        tvHomeApi = findViewById(R.id.tvHomeApi);
        tvHomeApi.setText(ApiConfig.get().getHomeSourceBean().getName());
        tvHomeShow = findViewById(R.id.tvHomeShow);
        tvHomeShow.setText(Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false) ? "开启" : "关闭");
        // 按钮位置：搜索 / 菜单两组上下位置，拼成一个值展示
        tvHomeIcon = findViewById(R.id.tvHomeIcon);
        tvHomeIcon.setText(getHomeIconText());
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvHomeNum = findViewById(R.id.tvHomeNum);
        tvHomeNum.setText(HistoryHelper.getHomeRecName(Hawk.get(HawkConfig.HOME_NUM, 0)));
        // Player Section
        tvShowPreviewText = findViewById(R.id.showPreviewText);
        tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
        tvScale = findViewById(R.id.tvScaleType);
        tvScale.setText(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0)));
        tvPlay = findViewById(R.id.tvPlay);
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0)));
        tvVideoPurifyText = findViewById(R.id.tvVideoPurifyText);
        tvVideoPurifyText.setText(Hawk.get(HawkConfig.VIDEO_PURIFY, true) ? "开启" : "关闭");
        // System Section
        tvLocale = findViewById(R.id.tvLocale);
        tvLocale.setText(getLocaleView(Hawk.get(HawkConfig.HOME_LOCALE, 0)));
        tvTheme = findViewById(R.id.tvTheme);
        tvTheme.setText(getThemeView(Hawk.get(HawkConfig.THEME_SELECT, 0)));
        tvRender = findViewById(R.id.tvRenderType);
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        tvParseWebView = findViewById(R.id.tvParseWebView);
        tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        tvSearchView = findViewById(R.id.tvSearchView);
        tvSearchView.setText(getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 0)));
        tvDns = findViewById(R.id.tvDns);
        tvDns.setText(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeDefaultShow = findViewById(R.id.tvHomeDefaultShow);
        tvHomeDefaultShow.setText(Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false) ? "开启" : "关闭");

        //takagen99 : Set first row as default（首页组首行=配置接口）
        findViewById(R.id.llApi).requestFocus();
        setupGroups();

        findViewById(R.id.llDebug).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
                tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "开启" : "关闭");
            }
        });
        // Input Source URL ------------------------------------------------------------------------
        // 2026-11-05：配置行迁入首页组首行，点行打开「配置接口」管理弹窗
        // （影视仓式多配置：切换/删除/新增；切换自动重启生效）
        findViewById(R.id.llApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                ConfigApiDialog dialog = new ConfigApiDialog(mActivity);
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ((BaseActivity) mActivity).hideSystemUI(true);
                        tvApi.setText(Hawk.get(HawkConfig.API_URL, getString(R.string.app_source)));
                    }
                });
                dialog.show();
            }
        });
        // 1. HOME Configuration ---------------------------------------------------------------- //
        // Select Home Source ------------------------------------
        findViewById(R.id.llHomeApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                List<SourceBean> sites = new ArrayList<>();
                for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
                    if (sb.getHide() == 0) sites.add(sb);
                }
                if (sites.size() > 0) {
                    SelectDialog<SourceBean> dialog = new SelectDialog<>(mActivity);

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
                            tvHomeApi.setText(ApiConfig.get().getHomeSourceBean().getName());
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
                    dialog.show();
                }
            }
        });
        // Switch to show / hide source header --------------------------
        findViewById(R.id.llHomeShow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.HOME_SHOW_SOURCE, !Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false));
                tvHomeShow.setText(Hawk.get(HawkConfig.HOME_SHOW_SOURCE, true) ? "开启" : "关闭");
            }
        });
        findViewById(R.id.llHomeIcon).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                HomeIconDialog dialog = new HomeIconDialog(mActivity);
                dialog.setOnDismissListener(dialog1 -> tvHomeIcon.setText(getHomeIconText()));
                dialog.show();
            }
        });
        // Select Home Display Type : Douban / Recommended / History -----
        findViewById(R.id.llHomeRec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_REC, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_hm_type));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_REC, value);
                        tvHomeRec.setText(getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getHomeRecName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        // History to Keep ------------------------------------------
        findViewById(R.id.llHomeNum).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_NUM, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                types.add(3);
                types.add(4);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_history));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_NUM, value);
                        tvHomeNum.setText(HistoryHelper.getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return HistoryHelper.getHomeRecName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        // 2. PLAYER Configuration -------------------------------------------------------------- //
        // Switch for Preview Window -------------------------------
        findViewById(R.id.showPreview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.SHOW_PREVIEW, !Hawk.get(HawkConfig.SHOW_PREVIEW, true));
                tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
            }
        });
        // Select Screen Ratio -------------------------------------
        findViewById(R.id.llScale).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, 0);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                players.add(4);
                players.add(5);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_ratio));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_SCALE, value);
                        tvScale.setText(PlayerHelper.getScaleName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getScaleName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, players, defaultPos);
                dialog.show();
            }
        });
        //后台播放
        View backgroundPlay = findViewById(R.id.llBackgroundPlay);
        TextView tvBgPlayType = findViewById(R.id.tvBackgroundPlayType);
        Integer defaultBgPlayTypePos = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
        ArrayList<String> bgPlayTypes = new ArrayList<>();
        bgPlayTypes.add("关闭");
        bgPlayTypes.add("开启");
        bgPlayTypes.add("画中画");
        tvBgPlayType.setText(bgPlayTypes.get(defaultBgPlayTypePos));
        backgroundPlay.setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            int bgPlayTypePos = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
            SelectDialog<String> dialog = new SelectDialog<>(mActivity);
            dialog.setTip("请选择");
            dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
                @Override
                public void click(String value, int pos) {
                    tvBgPlayType.setText(value);
                    Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE, pos);
                }
                @Override
                public String getDisplay(String val) {
                    return val;
                }
            }, new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }
                @Override
                public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }
            }, bgPlayTypes,bgPlayTypePos);
            dialog.show();
        });
        // Select PLAYER Type --------------------------------------------
        findViewById(R.id.llPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_TYPE, 0);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                if (MXPlayer.getPackageInfo()!=null){
                    players.add(10);
                }
                if (ReexPlayer.getPackageInfo() != null){
                    players.add(11);
                }
                if (Kodi.getPackageInfo() != null){
                    players.add(12);
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_player));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_TYPE, value);
                        tvPlay.setText(PlayerHelper.getPlayerName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getPlayerName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, players, defaultPos);
                dialog.show();
            }
        });


        // Select DECODER Type --------------------------------------------
        // 更改选择是否用硬解码还是软解码 改成播放器设置
        // 2026-11-05：值胶囊原来从没赋过值，一直空着像坏行；现在显示首选策略，弹窗关了再刷新
        final TextView tvMediaCodec = findViewById(R.id.tvMediaCodec);
        tvMediaCodec.setText(HawkUtils.getVodPlayerPreferredDesc());
        findViewById(R.id.llMediaSetting).setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            MediaSettingDialog mediaSettingDialog = new MediaSettingDialog(view.getContext());
            mediaSettingDialog.setOnDismissListener(dialog ->
                    tvMediaCodec.setText(HawkUtils.getVodPlayerPreferredDesc()));
            mediaSettingDialog.show();
        });

        // toggle purify video -------------------------------------
        findViewById(R.id.llVideoPurify).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            Hawk.put(HawkConfig.VIDEO_PURIFY, !Hawk.get(HawkConfig.VIDEO_PURIFY, true));
            tvVideoPurifyText.setText(Hawk.get(HawkConfig.VIDEO_PURIFY, true) ? "开启" : "关闭");
        });

        // 3. SYSTEM Configuration -------------------------------------------------------------- //
        // Select Webview ---------------------------------------------
        findViewById(R.id.llParseWebVew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
                Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem);
                tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
                if (!useSystem) {
                    Toast.makeText(mContext, "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带", Toast.LENGTH_LONG).show();
                    XWalkInitDialog dialog = new XWalkInitDialog(mContext);
                    dialog.setOnListener(new XWalkInitDialog.OnListener() {
                        @Override
                        public void onchange() {
                        }
                    });
                    dialog.show();
                }
            }
        });
        // Select System Render ( Surface/Texture View ) ---------------------
        findViewById(R.id.llRender).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0);
                ArrayList<Integer> renders = new ArrayList<>();
                renders.add(0);
                renders.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_render));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_RENDER, value);
                        tvRender.setText(PlayerHelper.getRenderName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getRenderName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        // Select DNS ---------------------------------------------
        findViewById(R.id.llDns).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);

                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_dns));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        tvDns.setText(OkGoHelper.dnsHttpsList.get(pos));
                        Hawk.put(HawkConfig.DOH_URL, pos);
                        String url = OkGoHelper.getDohUrl(pos);
                        OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                        IjkMediaPlayer.toggleDotPort(pos > 0);
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                }, new DiffUtil.ItemCallback<String>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }
                }, OkGoHelper.dnsHttpsList, dohUrl);
                dialog.show();
            }
        });
        // Select Backup / Restore -------------------------------------
        findViewById(R.id.llBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                BackupDialog dialog = new BackupDialog(mActivity);
                dialog.show();
            }
        });
        // resetApp
        findViewById(R.id.llReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                ResetDialog dialog = new ResetDialog(mActivity);
                dialog.show();
            }
        });
        // Load Wallpaper from URL -------------------------------------
        // 2026-11-05 改造：原来这里是下载配置 JSON 里的固定 wallpaper 地址，
        // 每次按都是同一张，谈不上「换」。现在走必应官方每日壁纸接口
        // （cn.bing.com，国内直连稳定），从最近 8 天里随机挑一张下载生效；
        // 接口失败时回退到配置里的 wallpaper 地址。
        findViewById(R.id.llWp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Toast.makeText(mContext, getString(R.string.mn_wall_load), Toast.LENGTH_SHORT).show();
                fetchRandomWallpaper();
            }
        });
        // Restore Default Wallpaper from system -------------------------
        findViewById(R.id.llWpRecovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                File wp = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wp");
                if (wp.exists())
                    wp.delete();
                ((BaseActivity) requireActivity()).changeWallpaper(true);
            }
        });
        // Select Search Display Results ( Text or Picture ) -------------
        findViewById(R.id.llSearchView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.SEARCH_VIEW, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_search));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.SEARCH_VIEW, value);
                        tvSearchView.setText(getSearchView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getSearchView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.showFastSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.FAST_SEARCH_MODE, !Hawk.get(HawkConfig.FAST_SEARCH_MODE, false));
                tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
            }
        });
        // Select App Language ( English / Chinese ) -----------------
        findViewById(R.id.llLocale).setOnClickListener(new View.OnClickListener() {
            private final int chkLang = Hawk.get(HawkConfig.HOME_LOCALE, 0);

            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_LOCALE, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_locale));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_LOCALE, value);
                        tvLocale.setText(getLocaleView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getLocaleView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (chkLang != Hawk.get(HawkConfig.HOME_LOCALE, 0)) {
                            reloadActivity();
                        }
                    }
                });
                dialog.show();
            }
        });
        // Select App Theme Color -------------------------------------
        findViewById(R.id.llTheme).setOnClickListener(new View.OnClickListener() {
            private final int chkTheme = Hawk.get(HawkConfig.THEME_SELECT, 0);

            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.THEME_SELECT, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                types.add(3);
                types.add(4);
                types.add(5);
                types.add(6);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_theme));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.THEME_SELECT, value);
                        tvTheme.setText(getThemeView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getThemeView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (chkTheme != Hawk.get(HawkConfig.THEME_SELECT, 0)) {
                            reloadActivity();
                        }
                    }
                });
                dialog.show();
            }
        });
        // About App -----------------------------------------------
        findViewById(R.id.llAbout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                AboutDialog dialog = new AboutDialog(mActivity);
                dialog.show();
            }
        });

        findViewById(R.id.llHomeLive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.HOME_DEFAULT_SHOW, !Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false));
                tvHomeDefaultShow.setText(Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, true) ? "开启" : "关闭");
            }
        });

        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };

    }

    /** 「按钮位置」行显示值：搜索 / 菜单两组上下位置拼一起 */
    private String getHomeIconText() {
        String search = Hawk.get(HawkConfig.HOME_SEARCH_POSITION, true) ? "搜索上" : "搜索下";
        String menu = Hawk.get(HawkConfig.HOME_MENU_POSITION, true) ? "菜单上" : "菜单下";
        return search + " · " + menu;
    }

    /**
     * A 方案分组导航（2026-11-05 拍板）：
     * 左栏三张分组卡（首页 / 播放器 / 系统），右区每次只显示当前组的选项行。
     * 交互：分组卡「聚焦」即切组（上下扫一遍就能预览各组）；
     *       「点按」切组并把焦点交给该组第一行；行上按左键由 nextFocusLeft 回到本组卡片。
     */
    private void setupGroups() {
        final TextView tvGroupTitle = findViewById(R.id.tvGroupTitle);
        final TextView tvGroupDesc = findViewById(R.id.tvGroupDesc);
        final int[] cards = {R.id.groupHome, R.id.groupPlayer, R.id.groupSystem};
        final int[] groups = {R.id.llGroupHome, R.id.llGroupPlayer, R.id.llGroupSystem};
        final int[] firstRows = {R.id.llApi, R.id.llScale, R.id.llLocale};
        final String[] titles = {
                getString(R.string.act_home), getString(R.string.act_player), getString(R.string.act_system)};
        final String[] descs = {
                "开机直接进首页的推荐内容", "画面、声音与播放行为", "外观、数据与关于"};

        for (int i = 0; i < cards.length; i++) {
            final int idx = i;
            View card = findViewById(cards[i]);
            // 初始态：首页组为当前组（android:activated 不是合法 XML 属性，只能代码设）
            card.setActivated(idx == 0);
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) switchGroup(cards, groups, idx, tvGroupTitle, titles[idx], tvGroupDesc, descs[idx]);
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchGroup(cards, groups, idx, tvGroupTitle, titles[idx], tvGroupDesc, descs[idx]);
                    findViewById(firstRows[idx]).requestFocus();
                }
            });
        }
    }

    /** 切组：只留一个容器可见，标题行同步；卡片 activated 做「当前组」标记（背景/图标/文字三态联动） */
    private void switchGroup(int[] cards, int[] groups, int idx, TextView title, String t, TextView desc, String d) {
        for (int j = 0; j < groups.length; j++) {
            findViewById(groups[j]).setVisibility(j == idx ? View.VISIBLE : View.GONE);
            findViewById(cards[j]).setActivated(j == idx);
        }
        title.setText(t);
        desc.setText(d);
    }

    /** 必应官方每日壁纸接口：国内直连稳定，idx=0&n=8 返回最近 8 天 */
    private static final String BING_WP_API = "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN";

    /**
     * 随机换壁纸：先从必应接口拿最近 8 天的图，随机挑一张下载到 filesDir/wp，
     * changeWallpaper(true) 让窗口背景立即生效。接口挂了走 fallbackWallpaper()。
     */
    private void fetchRandomWallpaper() {
        OkGo.<String>get(BING_WP_API).tag("bing_wallpaper").execute(new StringCallback() {
            @Override
            public void onSuccess(com.lzy.okgo.model.Response<String> response) {
                String url = pickBingImage(response.body());
                if (url == null) {
                    fallbackWallpaper();
                    return;
                }
                downloadWallpaper("https://cn.bing.com" + url);
            }

            @Override
            public void onError(com.lzy.okgo.model.Response<String> response) {
                fallbackWallpaper();
            }
        });
    }

    /**
     * 解析必应接口返回的 JSON，随机挑一张没换过的。
     * 把选中的 enddate 存起来，连按「换张壁纸」时尽量不重复（8 张都换过一轮后才会重来）。
     */
    private String pickBingImage(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            org.json.JSONArray images = obj.getJSONArray("images");
            if (images.length() == 0) return null;
            java.util.Random random = new java.util.Random();
            String last = Hawk.get(HawkConfig.BING_WP_LAST, "");
            // 最多重抽 4 次，避开上一张；8 张的池子重抽 4 次撞上旧图的概率很低
            for (int i = 0; i < 4; i++) {
                org.json.JSONObject img = images.getJSONObject(random.nextInt(images.length()));
                String url = img.optString("url", "");
                String date = img.optString("enddate", "");
                if (!url.isEmpty() && !date.equals(last)) {
                    Hawk.put(HawkConfig.BING_WP_LAST, date);
                    return url;
                }
            }
            // 重抽全撞了就随便拿一张兜底
            org.json.JSONObject img = images.getJSONObject(random.nextInt(images.length()));
            String url = img.optString("url", "");
            if (!url.isEmpty()) Hawk.put(HawkConfig.BING_WP_LAST, img.optString("enddate", ""));
            return url.isEmpty() ? null : url;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    /** 下载壁纸到 filesDir/wp（changeWallpaper 读的就是这个文件），完成后立即生效 */
    private void downloadWallpaper(String url) {
        OkGo.<File>get(url).tag("bing_wallpaper").execute(new FileCallback(requireActivity().getFilesDir().getAbsolutePath(), "wp") {
            @Override
            public void onSuccess(com.lzy.okgo.model.Response<File> response) {
                ((BaseActivity) requireActivity()).changeWallpaper(true);
            }

            @Override
            public void onError(com.lzy.okgo.model.Response<File> response) {
                Toast.makeText(mContext, getString(R.string.mn_wall_fail), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 必应接口挂了就回退老逻辑：下载配置 JSON 里指定的 wallpaper 地址 */
    private void fallbackWallpaper() {
        if (ApiConfig.get().wallpaper.isEmpty()) {
            Toast.makeText(mContext, getString(R.string.mn_wall_fail), Toast.LENGTH_SHORT).show();
            return;
        }
        downloadWallpaper(ApiConfig.get().wallpaper);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SettingActivity.callback = null;
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "站点推荐";
        } else if (type == 2) {
            return "观看历史";
        } else {
            return "豆瓣热播";
        }
    }

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "缩略图";
        }
    }

    String getLocaleView(int type) {
        if (type == 0) {
            return "中文";
        } else {
            return "英文";
        }
    }

    String getThemeView(int type) {
        if (type == 0) {
            return "奈飞";
        } else if (type == 1) {
            return "哆啦";
        } else if (type == 2) {
            return "百事";
        } else if (type == 3) {
            return "鸣人";
        } else if (type == 4) {
            return "小黄";
        } else if (type == 5) {
            return "八神";
        } else {
            return "樱花";
        }
    }

    void reloadActivity() {
        Intent intent = getActivity().getApplicationContext().getPackageManager().getLaunchIntentForPackage(getActivity().getApplication().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        getActivity().getApplicationContext().startActivity(intent);
        //  android.os.Process.killProcess(android.os.Process.myPid());
        //  System.exit(0);
    }

}
