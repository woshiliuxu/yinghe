package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.TmdbItem;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 右上角大图切换器的小封面列表。
 *
 * 不用 BaseQuickAdapter：这个列表太薄（几张图 + 焦点缩放），
 * Base 带来的空态/点击桥接都用不上，纯 RecyclerView.Adapter 十几行就完了。
 *
 * 焦点视觉分两层：描边亮底是 background 的 selector（跟 duplicateParentState 走），
 * 「选中放大 + 没选中退后」由这里在 onItem(Pre)Selected 里做缩放和透明度。
 */
public class HeroAdapter extends RecyclerView.Adapter<HeroAdapter.Holder> {

    public interface Listener {
        /** 焦点落到某张小图：换背景大图 + 左下信息 */
        void onHeroSelected(int position);

        /** 确认键点某张小图 */
        void onHeroClick(int position);
    }

    private final List<TmdbItem> data = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<TmdbItem> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public TmdbItem get(int position) {
        if (position < 0 || position >= data.size()) return null;
        return data.get(position);
    }

    public int size() {
        return data.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
        return new Holder(android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hero_thumb, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TmdbItem item = data.get(position);
        if (item != null && !item.thumb.isEmpty()) {
            Glide.with(holder.img).load(ImgUtil.urlFor(item.thumb))
                    .centerCrop().into(holder.img);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public class Holder extends RecyclerView.ViewHolder {
        final ImageView img;

        Holder(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgHeroThumb);
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onHeroClick(getBindingAdapterPosition());
            });
            // TvRecyclerView 的 onItemSelected 在列表内部移动焦点时不触发（实测），
            // 切换大图必须用每个 item 自己的焦点监听，这个 100% 触发
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.08f : 1.0f)
                        .scaleY(hasFocus ? 1.08f : 1.0f)
                        .setDuration(130)
                        .setInterpolator(hasFocus ? new BounceInterpolator() : null)
                        .start();
                img.animate().alpha(hasFocus ? 1f : 0.72f).setDuration(130).start();
                if (hasFocus && listener != null) {
                    int pos = getBindingAdapterPosition();
                    if (pos >= 0) listener.onHeroSelected(pos);
                }
            });
        }
    }
}
