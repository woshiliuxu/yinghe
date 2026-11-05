package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 左栏「个人」菜单。
 *
 * 左栏改版后底部只留一个「个人」，原先散在左栏里的设置、换源、网络、应用管理
 * 全部收到这里 —— 这些是用得最少、但每次都会占掉一个醒目位置的东西。
 * 2026-11-05 用户拍板：「网络设置」「我的应用」用不到，删掉，只留设置 + 切换接口。
 */
public class RailMoreDialog extends BaseDialog {

    public interface OnActionListener {
        void onSettings();

        void onSite();
    }

    public RailMoreDialog(@NonNull @NotNull Context context, final OnActionListener listener) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_rail_more);

        // 2026-11-05 布局重写后两行是「图标+文字」的 LinearLayout，不能再按 TextView 拿
        // （上一版把 TextView 强转这里，点「个人」必崩 ClassCastException）
        View settings = findViewById(R.id.railMenuSettings);
        View site = findViewById(R.id.railMenuSite);

        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) listener.onSettings();
            }
        });
        site.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) listener.onSite();
            }
        });

        // 电视端 Dialog 不走触摸，必须显式把焦点放到第一项，否则遥控器上下无响应
        settings.requestFocus();
    }
}
