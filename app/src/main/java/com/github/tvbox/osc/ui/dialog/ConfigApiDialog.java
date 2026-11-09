package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * 配置接口管理弹窗（0.11.5：手机优先）。
 * 影视仓式多配置：本地存一份配置列表（API_URL 为当前项，API_HISTORY 为其余），
 * 点条目即切换（写回 API_URL 并置顶历史，重启 App 后由 SplashActivity 重新拉取生效）。
 *
 * 右侧二维码指向本机 9978 配置页：新增配置一律手机扫码搞定，
 * TV 端不再套第二层输入弹窗（两层弹窗 + 遥控器打字都是浪费时间）。
 *
 * 焦点：行上按右进垃圾桶（TvRecyclerView 会吞横向键，必须在 border 回调里手动交），
 * 垃圾桶上按左回行，上下键在行间正常移动。
 */
public class ConfigApiDialog extends BaseDialog {
    private final ArrayList<String> configs = new ArrayList<>();
    private String current;
    private ConfigAdapter adapter;

    public ConfigApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_config_api);
        setCanceledOnTouchOutside(true);
        // 纯壳原则：出厂不带任何默认源，没配置过 current 就是空，列表为空时右栏二维码就是唯一的路
        current = Hawk.get(HawkConfig.API_URL, "");

        rebuild();
        adapter = new ConfigAdapter();

        final TvRecyclerView list = findViewById(R.id.listConfig);
        list.setLayoutManager(new V7LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        list.setOnItemListener(new com.owen.tvrecyclerview.widget.SimpleOnItemListener() {
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (position < 0 || position >= configs.size()) return;
                FastClickCheckUtil.check(itemView);
                switchTo(configs.get(position));
            }
        });
        // TvRecyclerView 对列表外的横向键是吞掉的（用户反馈「垃圾桶怎么都聚焦不上」），
        // 行上按右 = 手动把焦点交给当前行的垃圾桶；垃圾桶上按左 = 交还行
        list.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View view) {
                View row = list.getFocusedChild();
                if (row == null) return false;
                View trash = row.findViewById(R.id.btnConfigDel);
                if (direction == View.FOCUS_RIGHT && trash != null && trash.isFocusable()) {
                    trash.requestFocus();
                    return true;
                }
                if (direction == View.FOCUS_LEFT && trash != null && trash.hasFocus()) {
                    row.requestFocus();
                    return true;
                }
                return false;
            }
        });

        refreshQRCode();
        updateEmptyState();
    }

    /** 空态切换：列表与引导框共用同一块 170 高的区域，弹窗高度不变，标题不位移 */
    private void updateEmptyState() {
        boolean empty = configs.isEmpty();
        findViewById(R.id.llConfigEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.listConfig).setVisibility(empty ? View.GONE : View.VISIBLE);

        TextView tip = findViewById(R.id.tvConfigTip);
        if (empty) {
            tip.setText(R.string.dia_config_tip_empty);
        } else {
            // 带上条数：用户一眼知道有几个源、以及下面还能不能滚
            tip.setText(getContext().getString(R.string.dia_config_tip_list, configs.size()));
        }
        // 可视区恰好 3 行（3×50 + 2×8 = 166 < 170），超过 3 个才提示可滚
        findViewById(R.id.vConfigFade).setVisibility(configs.size() > 3 ? View.VISIBLE : View.GONE);
    }

    /** 右栏二维码：指向本机 9978 的手机配置页（#config 直达配置视图） */
    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false) + "/#config";
        int side = me.jessyan.autosize.utils.AutoSizeUtils.mm2px(getContext(), 160);
        ((ImageView) findViewById(R.id.ivConfigQR)).setImageBitmap(
                QRCodeGen.generateBitmap(address, side, side));
        ((TextView) findViewById(R.id.tvConfigAddr)).setText(address);
    }

    /** 当前项置顶，其余按历史顺序；空列表也允许（右栏二维码始终在，扫码即添加） */
    private void rebuild() {
        configs.clear();
        current = Hawk.get(HawkConfig.API_URL, "");
        if (!current.isEmpty()) configs.add(current);
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        for (String url : history) {
            if (!url.isEmpty() && !configs.contains(url)) configs.add(url);
        }
    }

    private void switchTo(String url) {
        if (url.equals(current)) {
            Toast.makeText(getContext(), getContext().getString(R.string.dia_config_in_use), Toast.LENGTH_SHORT).show();
            return;
        }
        Hawk.put(HawkConfig.API_URL, url);
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        history.remove(url);
        history.add(0, url);
        Hawk.put(HawkConfig.API_HISTORY, history);
        dismiss();
        restartApp();
    }

    /** 切配置/主题/语言同款重启：带 useCache 回主界面，Splash 按新 API_URL 重新拉配置 */
    private void restartApp() {
        Context app = getContext().getApplicationContext();
        Intent intent = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        app.startActivity(intent);
    }

    private class ConfigAdapter extends RecyclerView.Adapter<ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_config_api, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final String url = configs.get(position);
            final boolean inUse = url.equals(current);
            holder.tvName.setText(url);
            holder.tvBadge.setVisibility(inUse ? View.VISIBLE : View.GONE);
            holder.btnDel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FastClickCheckUtil.check(v);
                    if (inUse) {
                        Toast.makeText(getContext(), getContext().getString(R.string.dia_config_del_cur), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                    history.remove(url);
                    Hawk.put(HawkConfig.API_HISTORY, history);
                    rebuild();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    Toast.makeText(getContext(), getContext().getString(R.string.dia_config_deleted), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return configs.size();
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvBadge;
        View btnDel;

        ViewHolder(View view) {
            super(view);
            tvName = view.findViewById(R.id.tvConfigName);
            tvBadge = view.findViewById(R.id.tvConfigBadge);
            btnDel = view.findViewById(R.id.btnConfigDel);
        }
    }
}
