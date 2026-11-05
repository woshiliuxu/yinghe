package com.github.tvbox.osc.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;

/**
 * 剧集格。三套颜色必须成对处理，否则会出现"白底白字"：
 * 拿到焦点时卡片底色被 shape_ep_item 翻成暖白实底 → 文字必须转深色；
 * 当前正在播的那一集底色不变，只把文字提成主题色；其余走次要文字色。
 * 焦点优先级高于选中，所以用 ColorStateList 表达，不能直接 setTextColor 一个纯色。
 */
public class SeriesAdapter extends BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder> {
    public SeriesAdapter() {
        super(R.layout.item_series, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeries item) {
        TextView tvSeries = helper.getView(R.id.tvSeries);

        int dark = ContextCompat.getColor(mContext, R.color.ui_on_accent);
        int secondary = ContextCompat.getColor(mContext, R.color.ui_text_2);
        int accent = ((BaseActivity) mContext).getThemeColor();
        int rest = item.selected ? accent : secondary;

        tvSeries.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{dark, rest}));
        tvSeries.setTypeface(null, item.selected ? Typeface.BOLD : Typeface.NORMAL);

        helper.setText(R.id.tvSeries, item.name);
    }
}
