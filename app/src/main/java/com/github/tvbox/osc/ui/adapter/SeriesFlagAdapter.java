package com.github.tvbox.osc.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;

/**
 * 线路胶囊。
 * 「当前线路」用**数据**驱动背景（shape_flag_pill_on / off），不用 View 的 selected 状态：
 * owen 的 TvRecyclerView 把 item.selected 当成「焦点在哪一条」在用，焦点一移开就清掉，
 * 于是白底消失、只剩深色字，在黑背景上等于看不见 —— 这就是"焦点移到播放键后字变黑"的根因。
 * 文字色与背景配套：选中的那条永远是深色字；未选中项常态次要色，拿到焦点提亮。
 */
public class SeriesFlagAdapter extends BaseQuickAdapter<VodInfo.VodSeriesFlag, BaseViewHolder> {
    public SeriesFlagAdapter() {
        super(R.layout.item_series_flag, new ArrayList<>());
    }

    /**
     * 线路名来自源站配置，常带「（点击换线）」「（勿信广告）」这类提示尾巴，
     * 摆成胶囊后每条宽度都不一样、还挤成一排。这里把中英文括号连同内容一起剪掉，
     * 只留「线路二」这种主体名——能不能换线由胶囊本身的可点性表达，不用写在名字里。
     */
    private String cleanFlagName(String raw) {
        if (raw == null) return "";
        String name = raw.replaceAll("[（(][^）)]*[）)]", "").trim();
        return name.isEmpty() ? raw.trim() : name;
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeriesFlag item) {
        TextView tvSeries = helper.getView(R.id.tvSeriesFlag);

        // 背景跟着数据走，跟焦点无关：选中 = 暖白实底，未选中 = 极淡底（焦点态在 selector 里）
        helper.itemView.setBackgroundResource(item.selected
                ? R.drawable.shape_flag_pill_on
                : R.drawable.shape_flag_pill_off);

        int dark = ContextCompat.getColor(mContext, R.color.ui_on_accent);
        int secondary = ContextCompat.getColor(mContext, R.color.ui_text_2);
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

        helper.setText(R.id.tvSeriesFlag, cleanFlagName(item.name));
    }
}
