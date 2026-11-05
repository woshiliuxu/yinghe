package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.ui.tv.widget.PosterCardLayout;
import java.util.ArrayList;
import me.jessyan.autosize.utils.AutoSizeUtils;
/**
 * GridAdapter 支持传入 style 来设置图片的宽高比例，
 * 如果不传 style 则保留旧的默认风格（XML 中 item_grid.xml 定义的尺寸）。
 */
public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList ;
    private int defaultWidth;
    public ImgUtil.Style style; // 动态风格，传入时调整图片宽高比

    /**
     * 是否只显示海报（隐藏卡片内的片名/备注/压暗层）。
     * 首页海报墙用 true：标题交给底部信息条，图面干净，也不会和海报自带的片名打架。
     * 收藏/历史用的是各自的适配器，不受这里影响，仍然显示文字。
     */
    public boolean plainPoster = false;

    /**
     * 是否在右上角显示评分角标。只在豆瓣推荐页打开。
     * 站点给的评分几乎全是 0，显示出来只会露馅；豆瓣的数据是真的，才可以拿出来说。
     */
    public boolean showRate = false;

    /**
     * 统一的卡片高度（px），由 GridFragment 按「中间列宽 × 1.5」算好后传进来。
     * 见 PosterCardLayout.setFixedHeight 的说明：目的是让整行底边对齐。
     */
    public int cellHeight = 0;


    /**
     * 如果 style 传 null，则采用 item_grid.xml 中的默认尺寸
     */
    public GridAdapter(boolean showList, ImgUtil.Style style) {
        super( showList ? R.layout.item_list:R.layout.item_grid, new ArrayList<>());
        this.mShowList = showList;
        if(style!=null ){
            if(style.type.equals("list"))this.mShowList=true;
            this.defaultWidth=ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (!this.mShowList && helper.itemView instanceof PosterCardLayout && cellHeight > 0) {
            ((PosterCardLayout) helper.itemView).setFixedHeight(cellHeight);
        }
        if (this.mShowList) {
            helper.setText(R.id.tvNote, item.note);
            helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            //由于部分电视机使用glide报错
            if (!TextUtils.isEmpty(item.pic)) {
            	item.pic=item.pic.trim();
                ImgUtil.load(item.pic, ivThumb, 14);
            } else {
                ivThumb.setImageResource(R.drawable.img_loading_placeholder);
            }
            return;
        }

        // 纯海报模式下压暗层和文字容器整体隐藏；回收复用必须显式设回，否则会串到别的卡片上。
        // 焦点那一张由 GridFragment 在 onItemSelected 里单独打开，见 setCardCaption()。
        helper.setVisible(R.id.scrim, !plainPoster);
        helper.setVisible(R.id.captionBox, !plainPoster);

        TextView tvYear = helper.getView(R.id.tvYear);
        if (plainPoster || item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        // 豆瓣评分角标：必须显式设回 GONE，否则滚动回收时上一张的分数会留在空着的位置上
        TextView tvRate = helper.getView(R.id.tvRate);
        if (showRate && !TextUtils.isEmpty(item.rate)) {
            tvRate.setText(item.rate);
            tvRate.setVisibility(View.VISIBLE);
        } else {
            tvRate.setVisibility(View.GONE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        /*if (TextUtils.isEmpty(item.lang)) {
            tvLang.setVisibility(View.GONE);
        } else {
            tvLang.setText(item.lang);
            tvLang.setVisibility(View.VISIBLE);
        }*/
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        /*if (TextUtils.isEmpty(item.area)) {
            tvArea.setVisibility(View.GONE);
        } else {
            tvArea.setText(item.area);
            tvArea.setVisibility(View.VISIBLE);
        }*/
        if (plainPoster) {
            // 首页纯海报模式：第二行留给「年份 · 备注」，只在焦点浮层里出现。
            // 默认 hidden，焦点那张由 GridFragment.setCardCaption 打开整个 captionBox。
            String meta = cleanMeta(item);
            helper.setVisible(R.id.tvNote, !TextUtils.isEmpty(meta));
            helper.setText(R.id.tvNote, meta);
        } else if (TextUtils.isEmpty(item.note)) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if(style!=null){
            newWidth = defaultWidth;
            newHeight = (int)(newWidth / style.ratio);
        }
        //由于部分电视机使用glide报错
        if (!TextUtils.isEmpty(item.pic)) {
            ImgUtil.load(item.pic, ivThumb, 14);
        } else {
            
            ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }
        applyStyleToImage(ivThumb);//动态设置宽高
    }

    /**
     * 焦点浮层第二行：年份 · 备注。
     *
     * 接口给的 note 里经常拼着域名、评分之类的脏东西，最典型的是「更新至45集0.0」
     * —— 那个 0.0 是没取到的评分，直接显示出来就像页面坏了。这里统一洗掉。
     */
    private String cleanMeta(Movie.Video item) {
        StringBuilder sb = new StringBuilder();
        if (item.year > 0) sb.append(item.year);
        String note = item.note == null ? "" : item.note.trim();
        // 去掉结尾的 0.0 / 0.00 这类空评分
        note = note.replaceAll("\\s*0\\.0+\\s*$", "").trim();
        // 去掉站点自带的角标文案
        note = note.replace("站点推荐", "").trim();
        if (!note.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(note);
        }
        return sb.toString();
    }

    /**
     * 根据传入的 style 动态设置 ImageView 的高度：高度 = 宽度 / ratio
     */
    private void applyStyleToImage(final ImageView ivThumb) {
        if(style!=null){
            ViewGroup container = (ViewGroup) ivThumb.getParent();
            int width = defaultWidth;
            int height = (int) (width / style.ratio);
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.height = AutoSizeUtils.mm2px(mContext, height); // 高度
            containerParams.width = AutoSizeUtils.mm2px(mContext, width); // 宽度
            container.setLayoutParams(containerParams);
        }
    }    
}
