package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 详情页「更多」菜单。
 * 原来详情页六个等宽按钮并排，播放和收藏一样大、没有主次；现在只保留一个主按钮（播放），
 * 这里收容剩下的低频操作：复制播放地址 / 推送 / 简介。
 */
public class DetailMoreDialog extends BaseDialog {

    public interface OnActionListener {
        void onCopyUrl();

        void onPush();

        void onDesc();
    }

    public DetailMoreDialog(@NonNull @NotNull Context context, final OnActionListener listener) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_detail_more);

        TextView copyUrl = findViewById(R.id.menuCopyUrl);
        TextView push = findViewById(R.id.menuPush);
        TextView desc = findViewById(R.id.menuDesc);

        copyUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) listener.onCopyUrl();
            }
        });
        push.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) listener.onPush();
            }
        });
        desc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) listener.onDesc();
            }
        });

        // 电视端 Dialog 不走触摸，必须显式把焦点放到第一项，否则遥控器上下无响应
        copyUrl.requestFocus();
    }
}
