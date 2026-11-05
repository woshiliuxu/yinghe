package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * 配置接口管理弹窗（2026-11-05）
 * 影视仓式多配置：本地存一份配置列表（API_URL 为当前项，API_HISTORY 为其余），
 * 点条目即切换（写回 API_URL 并置顶历史，重启 App 后由 SplashActivity 重新拉取生效），
 * 条目右侧垃圾桶删除，底部「新增配置」走原 ApiDialog 输入表单。
 */
public class ConfigApiDialog extends BaseDialog {
    private final ArrayList<String> configs = new ArrayList<>();
    private String current;
    private ConfigAdapter adapter;

    /** 新增配置弹窗：监听 RefreshEvent 同步输入框内容（沿用 ModelSettingFragment 的老模式） */
    private ApiDialog addDialog;

    public ConfigApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_config_api);
        setCanceledOnTouchOutside(true);
        // 与 ApiConfig.loadConfig 的回退一致：没配置过就用内置默认源，保证列表永远有「当前项」
        current = Hawk.get(HawkConfig.API_URL, getContext().getString(R.string.app_source));

        rebuild();
        adapter = new ConfigAdapter();

        TvRecyclerView list = findViewById(R.id.listConfig);
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

        findViewById(R.id.btnAddConfig).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                addDialog = new ApiDialog(getContext());
                EventBus.getDefault().register(addDialog);
                addDialog.setOnListener(new ApiDialog.OnListener() {
                    @Override
                    public void onchange(String api) {
                        // ApiDialog 只负责把新地址写进历史，正式生效必须写 API_URL（原设置页就是在这儿写的）
                        if (!api.isEmpty()) {
                            Hawk.put(HawkConfig.API_URL, api);
                        }
                        dismiss();
                        restartApp();
                    }
                });
                addDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        EventBus.getDefault().unregister(addDialog);
                        addDialog = null;
                    }
                });
                addDialog.show();
            }
        });
    }

    /** 当前项置顶，其余按历史顺序；空列表也允许（只显示新增按钮） */
    private void rebuild() {
        configs.clear();
        current = Hawk.get(HawkConfig.API_URL, getContext().getString(R.string.app_source));
        if (!current.isEmpty()) configs.add(current);
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        for (String url : history) {
            if (!url.isEmpty() && !configs.contains(url)) configs.add(url);
        }
        // 内置默认源永远可回退：没配置过或被清掉时也能一键切回去
        String def = getContext().getString(R.string.app_source);
        if (!def.isEmpty() && !configs.contains(def)) configs.add(def);
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
