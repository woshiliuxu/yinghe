package com.github.tvbox.osc.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodSeriesGroup;

import java.util.ArrayList;

/**
 * 集数分组（1-20 / 21-40）。和线路胶囊共用 item_series_flag，
 * 但分组是"弱一级"的控件：常态完全无底色（shape 里的极淡底在 40 高下几乎看不见），
 * 当前分组用 selected 白底，其余只靠焦点描边。
 */
public class SeriesGroupAdapter extends BaseQuickAdapter<VodSeriesGroup, BaseViewHolder> {
    public SeriesGroupAdapter() {
        super(R.layout.item_series_flag, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodSeriesGroup item) {
        TextView tvSeries = helper.getView(R.id.tvSeriesFlag);

        // 同线路胶囊：选中态看数据、不看 View 的 selected（TvRecyclerView 会清它）
        helper.itemView.setBackgroundResource(item.selected
                ? R.drawable.shape_flag_pill_on
                : R.drawable.shape_flag_pill_off);

        int dark = ContextCompat.getColor(mContext, R.color.ui_on_accent);
        int secondary = ContextCompat.getColor(mContext, R.color.ui_text_3);
        int accent = ContextCompat.getColor(mContext, R.color.ui_accent);

        if (item.selected) {
            tvSeries.setTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{dark, dark}));
        } else {
            tvSeries.setTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{accent, secondary}));
        }
        tvSeries.setTypeface(null, item.selected ? Typeface.BOLD : Typeface.NORMAL);

        tvSeries.setText(item.name);
    }
}
