package com.github.tvbox.osc.ui.fragment;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.event.HomeFocusEvent;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.util.DoubanApi;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 豆瓣推荐（首页第一个 tab）。
 *
 * 为什么由它顶替「首页」：原来那个 tab 是把第一个真实分类（内地）深拷贝出来改个名字，
 * 内容和「内地」一模一样，等于白占一个 tab。豆瓣这份榜单来自另一套数据，
 * 且带真实评分，是唯一能撑起「推荐」两个字的内容。
 *
 * 豆瓣只给元信息、不给播放地址，所以点进去是拿片名去各站点搜 —— 和原版影视仓的做法一致。
 */
public class DoubanFragment extends BaseLazyFragment {

    /** 与其他网格页保持同一套列数与间距 */
    /** 与 GridFragment 一致：影片卡片一行 5 个 */
    private static final int SPAN_COUNT = 5;

    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;
    private RecyclerView.ItemDecoration gridDecoration;

    /** 网络请求中，避免重复拉 */
    private boolean loading = false;
    /** 已经拉过一次（缓存或网络），用于 onFragmentResume 判断要不要补拉 */
    private boolean loadedOnce = false;

    public static DoubanFragment newInstance() {
        return new DoubanFragment();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        initView();
        loadDouban();
    }

    @Override
    public void onFragmentResume() {
        super.onFragmentResume();
        // 上次彻底失败（没缓存也没网络）时，回到这个页面自动再试一次。
        // 只在「一条都没有」时才补拉，免得每次切 tab 都打一遍豆瓣。
        if (!loading && (!loadedOnce || gridAdapter == null || gridAdapter.getData().isEmpty())) {
            loadDouban();
        }
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        gridAdapter = new GridAdapter(false, null);
        // 纯海报 + 评分角标：豆瓣这条路能拿到真实评分，不用像站点数据那样藏着
        gridAdapter.plainPoster = true;
        gridAdapter.showRate = true;
        mGridView.setAdapter(gridAdapter);
        mGridView.setLayoutManager(new V7GridLayoutManager(mContext, SPAN_COUNT));
        applyGridSpacing(SPAN_COUNT);
        mGridView.post(new Runnable() {
            @Override
            public void run() {
                applyCellHeight(SPAN_COUNT);
            }
        });

        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f)
                        .translationY(0f)
                        .setDuration(130).start();
                setCardCaption(itemView, false);
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f)
                        .translationY(-AutoSizeUtils.mm2px(mContext, 3))
                        .setDuration(130).start();
                setCardCaption(itemView, true);
                postFocusInfo(position);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });

        gridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video == null) return;
                // 豆瓣条目没有站内 id：直接把片名丢给搜索页，由各站点去命中
                Bundle bundle = new Bundle();
                bundle.putString("id", video.id);
                bundle.putString("sourceKey", video.sourceKey);
                bundle.putString("title", video.name);
                if (video.id != null && !video.id.isEmpty() && !video.id.startsWith("msearch:")) {
                    jumpActivity(DetailActivity.class, bundle);
                } else if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)) {
                    jumpActivity(FastSearchActivity.class, bundle);
                } else {
                    jumpActivity(SearchActivity.class, bundle);
                }
            }
        });
        // 长按直接进聚合搜索：豆瓣条目本来就只能搜，长按省掉一次「点进去再等」
        gridAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", video.name);
                    jumpActivity(FastSearchActivity.class, bundle);
                }
                return true;
            }
        });

        setLoadSir(mGridView);
    }

    private void loadDouban() {
        if (loading) return;
        loading = true;
        if (gridAdapter.getData().isEmpty()) showLoading();
        DoubanApi.load(new DoubanApi.Callback() {
            @Override
            public void onResult(List<Movie.Video> list, boolean fromCache) {
                if (list == null || list.isEmpty()) {
                    // 缓存也没有、网络也没回来：首屏给空态，不用一直转圈
                    if (!fromCache && gridAdapter.getData().isEmpty()) showEmpty();
                    if (!fromCache) loading = false;
                    return;
                }
                loadedOnce = true;
                showSuccess();
                // 缓存先铺过一次，网络回来如果内容一样就别再 setNewData ——
                // 重绘会把焦点打回第一张、滚动位置清空，用户正在看的那张会突然跳走。
                if (fromCache || !sameAsNow(list)) {
                    gridAdapter.setNewData(list);
                }
                // 列表铺好后才有宽度，这时再量一次卡片高（首次进页面时 mGridView 可能还没排版）
                if (mGridView != null) {
                    mGridView.post(new Runnable() {
                        @Override
                        public void run() {
                            applyCellHeight(SPAN_COUNT);
                        }
                    });
                }
                if (!fromCache) loading = false;
            }
        });
    }

    /** 网络结果和当前列表是不是同一批（按片名顺序比；豆瓣条目没有 id） */
    private boolean sameAsNow(List<Movie.Video> list) {
        if (gridAdapter == null || list == null) return false;
        List<Movie.Video> now = gridAdapter.getData();
        if (now == null || now.size() != list.size() || now.isEmpty()) return false;
        for (int i = 0; i < list.size(); i++) {
            String a = now.get(i).name;
            String b = list.get(i).name;
            if (a == null ? b != null : !a.equals(b)) return false;
        }
        return true;
    }

    /**
     * 返回键用：列表还没到顶就先滚回顶部，已经到顶返回 false 让首页决定是退到分类条还是退出。
     * 和 GridFragment 一样，避免「按一下返回直接把 App 关掉」。
     */
    public boolean scrollToTopIfNeeded() {
        if (mGridView == null) return false;
        if (mGridView.canScrollVertically(-1)) {
            mGridView.scrollToPosition(0);
            return true;
        }
        return false;
    }

    /**
     * 按实际可用宽算「中间列宽 × 1.5」作为统一卡片高度，和 GridFragment 保持一致。
     * 写死 mm 会在别的分辨率上错位，所以每次排版完成后现算。
     */
    private void applyCellHeight(int spanCount) {
        if (mGridView == null || gridAdapter == null) return;
        int usable = mGridView.getWidth() - mGridView.getPaddingLeft() - mGridView.getPaddingRight();
        if (usable <= 0) return;
        int gap = AutoSizeUtils.mm2px(mContext, 16);
        int cellW = (usable - gap * (spanCount - 1)) / spanCount;
        int cellH = (int) (cellW * 1.5f);
        if (cellH > 0 && gridAdapter.cellHeight != cellH) {
            gridAdapter.cellHeight = cellH;
            gridAdapter.notifyDataSetChanged();
        }
    }

    /** 间距用 ItemDecoration（左右各半），与 GridFragment 同一套算法，7 列正好铺满可用宽 */
    private void applyGridSpacing(final int spanCount) {
        final int gap = AutoSizeUtils.mm2px(mContext, 16);
        final int half = gap / 2;
        if (gridDecoration != null) {
            mGridView.removeItemDecoration(gridDecoration);
        }
        gridDecoration = new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                if (pos == RecyclerView.NO_POSITION) return;
                outRect.left = half;
                outRect.right = half;
                outRect.top = 0;
                outRect.bottom = gap;
            }
        };
        mGridView.addItemDecoration(gridDecoration);
    }

    /** 只有焦点那张露出片名与主演，其余保持纯海报 */
    private void setCardCaption(View itemView, boolean show) {
        if (itemView == null) return;
        View box = itemView.findViewById(R.id.captionBox);
        View scrim = itemView.findViewById(R.id.scrim);
        if (box != null) box.setVisibility(show ? View.VISIBLE : View.GONE);
        if (scrim != null) scrim.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** 焦点变化只为一件事：让首页背景换成这张海报的模糊图 */
    private void postFocusInfo(int position) {
        try {
            List<Movie.Video> data = gridAdapter.getData();
            if (data == null || position < 0 || position >= data.size()) return;
            Movie.Video video = data.get(position);
            if (video == null) return;
            EventBus.getDefault().post(new HomeFocusEvent(video.name, video.note, video.year, "", video.pic));
        } catch (Throwable ignored) {
        }
    }
}
