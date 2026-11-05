package com.github.tvbox.osc.ui.fragment;
import android.content.res.TypedArray;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.blankj.utilcode.util.GsonUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.HomeFocusEvent;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.ui.adapter.GridFilterKVAdapter;
import com.github.tvbox.osc.ui.dialog.GridFilterDialog;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HomeCache;
import com.google.gson.reflect.TypeToken;
import androidx.recyclerview.widget.GridLayoutManager;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import me.jessyan.autosize.utils.AutoSizeUtils;

import java.util.ArrayList;
import java.util.List;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Stack;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class GridFragment extends BaseLazyFragment {
    private MovieSort.SortData sortData = null;
    private TvRecyclerView mGridView;
    private SourceViewModel sourceViewModel;
    private GridFilterDialog gridFilterDialog;
    private GridAdapter gridAdapter;
    private int page = 1;
    private int maxPage = 1;
    private boolean isLoad = false;
    private boolean isTop = true;
    private View focusedView = null;
    private RecyclerView.ItemDecoration gridDecoration;
    /** 本次进页面是否先铺了本地缓存（用于判断网络回来后要不要重绘） */
    private boolean cachedShown = false;

    private static class GridInfo{
        public String sortID="";
        public TvRecyclerView mGridView;
        public GridAdapter gridAdapter;
        public int page = 1;
        public int maxPage = 1;
        public boolean isLoad = false;
        public View focusedView = null;
    }

    Stack<GridInfo> mGrids = new Stack<GridInfo>(); //ui栈

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return new GridFragment().setArguments(sortData);
    }

    public GridFragment setArguments(MovieSort.SortData sortData) {
        this.sortData = sortData;
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && this.sortData == null) {
            this.sortData = GsonUtils.fromJson(savedInstanceState.getString("sortDataJson"), MovieSort.SortData.class);
        }
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("sortDataJson", GsonUtils.toJson(sortData));        
    }

    private void changeView(String id,Boolean isFolder){
        if(isFolder){
            this.sortData.flag =style==null?"1":"2"; // 修改sortData.flag
        }else {
            this.sortData.flag ="2"; // 修改sortData.flag
        }
        initView();
        this.sortData.id = id; // 修改sortData.id为新的ID
        initViewModel();
        initData();
    }

    public boolean isFolederMode() {
        return (getUITag() == '1');
    }

    // 获取当前页面UI的显示模式 ‘0’ 正常模式 '1' 文件夹模式 '2' 显示缩略图的文件夹模式
    public char getUITag() {
        return (sortData == null || sortData.flag == null || sortData.flag.length() == 0) ? '0' : sortData.flag.charAt(0);
    }

    // 是否允许聚合搜索 sortData.flag的第二个字符为‘1’时允许聚搜
    public boolean enableFastSearch() {  return sortData.flag == null || sortData.flag.length() < 2 || (sortData.flag.charAt(1) == '1'); }
    //public boolean enableFastSearch() {  return (sortData.flag == null || sortData.flag.length() < 2) ? true : (sortData.flag.charAt(1) == '1'); }

    // 保存当前页面
    private void saveCurrentView() {
        if (this.mGridView == null) return;
        GridInfo info = new GridInfo();
        info.sortID = this.sortData.id;
        info.mGridView = this.mGridView;
        info.gridAdapter = this.gridAdapter;
        info.page = this.page;
        info.maxPage = this.maxPage;
        info.isLoad = this.isLoad;
        info.focusedView = this.focusedView;
        this.mGrids.push(info);
    }

    // 丢弃当前页面，将页面还原成上一个保存的页面
    public boolean restoreView() {
        if (mGrids.empty()) return false;
        this.showSuccess();
        ((ViewGroup) mGridView.getParent()).removeView(this.mGridView); // 重父窗口移除当前控件
        GridInfo info = mGrids.pop();// 还原上次保存的控件
        this.sortData.id = info.sortID;
        this.mGridView = info.mGridView;
        this.gridAdapter = info.gridAdapter;
        this.page = info.page;
        this.maxPage = info.maxPage;
        this.isLoad = info.isLoad;
        this.focusedView = info.focusedView;
        this.mGridView.setVisibility(View.VISIBLE);
//        if(this.focusedView != null){ this.focusedView.requestFocus(); }
        if (mGridView != null) mGridView.requestFocus();
        return true;
    }

    private ImgUtil.Style style;
    // 更改当前页面
    private void createView() {
        this.saveCurrentView(); // 保存当前页面
        if (mGridView == null) { // 从layout中拿view
            mGridView = findViewById(R.id.mGridView);
        } else { // 复制当前view
            TvRecyclerView v3 = new TvRecyclerView(this.mContext);
            v3.setSpacingWithMargins(10, 10);
            v3.setLayoutParams(mGridView.getLayoutParams());
            v3.setPadding(mGridView.getPaddingLeft(), mGridView.getPaddingTop(), mGridView.getPaddingRight(), mGridView.getPaddingBottom());
            v3.setClipToPadding(mGridView.getClipToPadding());
            ((ViewGroup) mGridView.getParent()).addView(v3);
            mGridView.setVisibility(View.GONE);
            mGridView = v3;
            mGridView.setVisibility(View.VISIBLE);
        }
        mGridView.setHasFixedSize(true);
        // 网格模式不再让 ImgUtil 的 style 插手尺寸：style 会按自己的 ratio 重设卡片宽高，
        // 和列宽对不上就会第 7 列溢出屏幕。现在尺寸全部交给 PosterCardLayout（宽跟列、高 2:3）。
        style = isFolederMode() ? ImgUtil.initStyle() : null;
        gridAdapter = new GridAdapter(isFolederMode(), style);
        // 海报墙只放图；片名走底部信息条。网盘浏览（文件夹模式）例外，那里必须靠文字认目录
        gridAdapter.plainPoster = !isFolederMode();
        this.page = 1;
        this.maxPage = 1;
        this.isLoad = false;
    }

    private void initView() {
        this.createView();
        mGridView.setAdapter(gridAdapter);
        if (isFolederMode()) {
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        } else {
            // 固定 5 列（用户 2026-11-04 统一要求：所有影片卡片一行 5 个）。
            // 卡片宽度由 GridLayoutManager 把可用宽等分后交给 PosterCardLayout，
            // 高度按 2:3 自动算，所以无论屏幕多宽都不会出现「第 5 列被切掉」。
            final int spanCount = 5;
            int realSpan = spanCount;
            if (style != null) {
                if (ImgUtil.spanCountByStyle(style, spanCount) == 1) realSpan = 1;
            }
            if (realSpan == 1) {
                mGridView.setLayoutManager(new V7LinearLayoutManager(mContext, realSpan, false));
            } else {
                mGridView.setLayoutManager(new V7GridLayoutManager(mContext, realSpan));
                applyGridSpacing(realSpan);
                mGridView.post(new Runnable() {
                    @Override
                    public void run() {
                        applyCellHeight(spanCount);
                    }
                });
            }
        }

        gridAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                gridAdapter.setEnableLoadMore(true);
                sourceViewModel.getList(sortData, page);
            }
        }, mGridView);
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                // 焦点离开：不做缩放，只把轻微上浮收回去，并收起片名浮层。
                // 缩放会盖住邻居，投影仪上尤其明显；焦点识别交给白描边。
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
        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                }
                return false;
            }
        });
        gridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    if( video.tag !=null && (video.tag.equals("folder") || video.tag.equals("cover"))){
                        focusedView = view;
                        if(("12".indexOf(getUITag()) != -1)){
                            changeView(video.id,video.tag.equals("folder"));
                        }else {
                            changeView(video.id,false);
                        }
                    } else {
                        if (video.id == null || video.id.isEmpty() || video.id.startsWith("msearch:")) {
                            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) && enableFastSearch()){
                                jumpActivity(FastSearchActivity.class, bundle);
                            }else {
                                jumpActivity(SearchActivity.class, bundle);
                            }
                        } else {
                            jumpActivity(DetailActivity.class, bundle);
                        }
                    }

                }
            }
        });
        // takagen99 : Long Press to Fast Search
        gridAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    jumpActivity(FastSearchActivity.class, bundle);
                }
                return true;
            }
        });
        gridAdapter.setLoadMoreView(new LoadMoreView());
        setLoadSir(mGridView);
    }

    /**
     * 按实际可用宽算出「中间列宽 × 1.5」作为统一卡片高度。
     *
     * 必须在布局完成后再算：屏幕宽、侧栏宽、左右留白都可能因设备而异，
     * 写死 mm 值就会在别的分辨率上错位（上一版 7 列溢出就是这么来的）。
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

    /**
     * 行列间距用 ItemDecoration 给，不用 TvRecyclerView 的 spacingWithMargins。
     *
     * 原因：spacingWithMargins 是往 item 上加 margin，而卡片宽是 match_parent（等于 span 宽），
     * 再加 margin 就会把 7 列撑出屏幕——上一版「最右边那张被切掉三分之一」就是这么来的。
     * 改成 decoration：GridLayoutManager 在测量时会把 decoration 的宽从 span 里扣掉，
     * 列宽自然变成 (可用宽 - 6×gap) / 7，永远严丝合缝。
     */
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
                // 左右一律各半：容器宽度已经为一个 gap 留了余量，
                // 所以首末列不必特殊处理，7 列等宽、边缘正好压在设计稿的 136 / 1240 上。
                outRect.left = half;
                outRect.right = half;
                outRect.top = 0;
                outRect.bottom = gap;
            }
        };
        mGridView.addItemDecoration(gridDecoration);
    }

    /**
     * 只给「当前焦点」那张海报打开片名浮层。
     *
     * 首页 14 张卡片如果每张都印片名+备注，屏幕上就是 28 行小字互相打架；
     * 常态留纯海报，焦点这一张才浮出字，信息密度和画面干净两头都占住。
     * 文件夹模式（plainPoster=false）不走这里，那里必须常显文字才能认出目录。
     */
    private void setCardCaption(View itemView, boolean show) {
        if (itemView == null || gridAdapter == null || !gridAdapter.plainPoster) return;
        View box = itemView.findViewById(R.id.captionBox);
        View scrim = itemView.findViewById(R.id.scrim);
        if (box != null) box.setVisibility(show ? View.VISIBLE : View.GONE);
        if (scrim != null) scrim.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * 把当前焦点海报的元信息送到首页底部信息条。
     * position 可能是加载更多的那一行 footer，所以这里全部做边界保护。
     */
    private void postFocusInfo(int position) {
        try {
            if (gridAdapter == null) return;
            List<Movie.Video> data = gridAdapter.getData();
            if (data == null || position < 0 || position >= data.size()) {
                EventBus.getDefault().post(new HomeFocusEvent(null, null, 0, null, null));
                return;
            }
            Movie.Video video = data.get(position);
            if (video == null) return;
            String sourceName = "";
            SourceBean sb = ApiConfig.get().getSource(video.sourceKey);
            if (sb != null && sb.getName() != null) sourceName = sb.getName();
            EventBus.getDefault().post(new HomeFocusEvent(video.name, video.note, video.year, sourceName, video.pic));
        } catch (Exception ignored) {
        }
    }

    private void initViewModel() {
        if (sourceViewModel != null) {
            return;
        }
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
//                if(mGridView != null) mGridView.requestFocus();
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    if (page == 1) {
                        showSuccess();
                        isLoad = true;
                        if (!(cachedShown && sameAsNow(absXml.movie.videoList))) {
                            replaceKeepPosition(absXml.movie.videoList);
                        }
                        cachedShown = false;
                        HomeCache.put(cacheKey(), GsonUtils.toJson(absXml.movie.videoList));
                    } else {
                        gridAdapter.addData(absXml.movie.videoList);
                    }
                    page++;
                    maxPage = absXml.movie.pagecount;
                    if (page > maxPage && maxPage!=0) {
                        gridAdapter.loadMoreEnd();
                        gridAdapter.setEnableLoadMore(false);
                    } else {
                        gridAdapter.loadMoreComplete();
                        gridAdapter.setEnableLoadMore(true);
                    }
                } else {
                    if (page == 1) {
                        showEmpty();
                    }
                    if (page > maxPage && maxPage!=0) {
                        Toast.makeText(getContext(), "没有更多了", Toast.LENGTH_SHORT).show();
                        gridAdapter.loadMoreEnd();
                    } else {
                        gridAdapter.loadMoreComplete();
                    }
                    gridAdapter.setEnableLoadMore(false);
                }
            }
        });
    }

    public boolean isLoad() {
        return isLoad || !mGrids.empty(); //如果有缓存页的话也可以认为是加载了数据的
    }

    private void initData() {
    	if (ApiConfig.get().getHomeSourceBean().getApi()==null) {
            showEmpty();
            return;
        }
        isLoad = false;
        scrollTop();
        toggleFilterStatus();

        // 先铺上次的缓存，再发请求。
        // 这一步是"冷启动不黑屏"的关键：配置和分类都从本地读出来之后，
        // 海报其实已经在硬盘里躺着了，没有必要让用户盯着一块空白等网络。
        cachedShown = (page == 1) && showCached();
        if (!cachedShown) showLoading();
        sourceViewModel.getList(sortData, page);
    }

    /** 缓存文件的 key：站点 + 分类 + 当前筛选条件，换源或换筛选自然不命中 */
    private String cacheKey() {
        String sourceKey = ApiConfig.get().getHomeSourceBean() == null ? "" : ApiConfig.get().getHomeSourceBean().getKey();
        String sortId = sortData == null ? "" : sortData.id;
        String filter = (sortData == null || sortData.filterSelect == null) ? "" : sortData.filterSelect.toString();
        return HomeCache.PREFIX_LIST + sourceKey + "_" + sortId + "_" + filter;
    }

    private boolean showCached() {
        if (gridAdapter == null || gridAdapter.getData().size() > 0) return false;
        String json = HomeCache.get(cacheKey());
        if (json == null) return false;
        try {
            List<Movie.Video> list = GsonUtils.fromJson(json, new TypeToken<List<Movie.Video>>() {
            }.getType());
            if (list == null || list.isEmpty()) return false;
            showSuccess();
            gridAdapter.setNewData(list);
            isLoad = true;
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    /** 网络回来后判断和缓存是不是同一批：同一批就不重绘，免得焦点被打回第一张 */
    private boolean sameAsNow(List<Movie.Video> list) {
        if (gridAdapter == null) return false;
        List<Movie.Video> now = gridAdapter.getData();
        if (now == null || now.size() != list.size()) return false;
        for (int i = 0; i < list.size(); i++) {
            String a = now.get(i).id;
            String b = list.get(i).id;
            if (a == null ? b != null : !a.equals(b)) return false;
        }
        return true;
    }

    /** 替换数据时把滚动位置留住，别让用户正在看的那一排突然跳走 */
    private void replaceKeepPosition(List<Movie.Video> list) {
        int keep = 0;
        if (mGridView != null && mGridView.getLayoutManager() instanceof GridLayoutManager) {
            keep = ((GridLayoutManager) mGridView.getLayoutManager()).findFirstVisibleItemPosition();
            if (keep < 0) keep = 0;
        }
        gridAdapter.setNewData(list);
        if (keep > 0 && keep < list.size()) mGridView.scrollToPosition(keep);
    }

    private void toggleFilterStatus() {
        if (sortData!=null && sortData.filters != null && !sortData.filters.isEmpty()) {
            int count = sortData.filterSelectCount();
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_FILTER_CHANGE, count));
        }
    }

    public boolean isTop() {
        return isTop;
    }

    public void scrollTop() {
        isTop = true;
        mGridView.scrollToPosition(0);
    }

    public void showFilter() {
    	if (sortData!=null && !sortData.filters.isEmpty() && gridFilterDialog == null) {
            gridFilterDialog = new GridFilterDialog(mContext);
//            gridFilterDialog.setData(sortData);
//            gridFilterDialog.setOnDismiss(new GridFilterDialog.Callback() {
//                @Override
//                public void change() {
//                    page = 1;
//                    initData();
//                }
//            });
            setFilterDialogData();
        }
        if (gridFilterDialog != null)
            gridFilterDialog.show();
    }

    public void setFilterDialogData() {
        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        assert context != null;

        // 获取动态主题颜色
        TypedArray a = getContext().obtainStyledAttributes(R.styleable.themeColor);
        int selectedColor = a.getColor(R.styleable.themeColor_color_theme, 0); // 选择的颜色
        int defaultColor = ContextCompat.getColor(context, R.color.color_FFFFFF);
        // 释放 TypedArray 资源
        a.recycle();
        // 遍历过滤条件数据
        for (MovieSort.SortFilter filter : sortData.filters) {
            View line = inflater.inflate(R.layout.item_grid_filter, gridFilterDialog.filterRoot, false);
            TextView filterNameTv = line.findViewById(R.id.filterName);
            filterNameTv.setText(filter.name);
            TvRecyclerView gridView = line.findViewById(R.id.mFilterKv);
            gridView.setHasFixedSize(true);
            gridView.setLayoutManager(new V7LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            GridFilterKVAdapter adapter = new GridFilterKVAdapter();
            gridView.setAdapter(adapter);
            final String key = filter.key;
            final ArrayList<String> values = new ArrayList<>(filter.values.keySet());
            final ArrayList<String> keys = new ArrayList<>(filter.values.values());
            adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
                // 用于记录上一次选中的 view
                View previousSelectedView = null;
                @Override
                public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                    String currentSelection = sortData.filterSelect.get(key);
                    String newSelection = keys.get(position);
                    if (currentSelection == null || !currentSelection.equals(newSelection)) {
                        // 更新选中状态
                        sortData.filterSelect.put(key, newSelection);
                        updateViewStyle(view, selectedColor, true);
                        if (previousSelectedView != null) {
                            updateViewStyle(previousSelectedView, defaultColor, false);
                        }
                        previousSelectedView = view;
                    } else {
                        // 取消选中
                        sortData.filterSelect.remove(key);
                        if (previousSelectedView != null) {
                            updateViewStyle(previousSelectedView, defaultColor, false);
                        }
                        previousSelectedView = null;
                    }
                    forceRefresh();
                }
                private void updateViewStyle(View view, int color, boolean isBold) {
                    TextView valueTv = view.findViewById(R.id.filterValue);
                    valueTv.getPaint().setFakeBoldText(isBold);
                    valueTv.setTextColor(color);
                }
            });
            adapter.setNewData(values);
            gridFilterDialog.filterRoot.addView(line);
        }
    }

    public void forceRefresh() {
        page = 1;
        initData();
    }
}
