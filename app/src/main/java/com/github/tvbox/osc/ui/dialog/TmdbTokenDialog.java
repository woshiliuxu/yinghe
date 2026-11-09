package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;

import org.jetbrains.annotations.NotNull;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * TMDB 密钥弹窗（0.11.5：纯扫码面板）。
 * 遥控器没法粘贴长 Token，TV 端不再放输入框——二维码指向手机配置页
 * （配置 → 高级选项 → TMDB Token，保存即生效并通知首页重拉）。
 * 设置行的值胶囊刷新仍走 onChanged（ModelSettingFragment 传入）。
 */
public class TmdbTokenDialog extends BaseDialog {

    /** 打开过配置页后回设置页刷新值胶囊（手机保存后 RefreshEvent 也会通知，双保险） */
    public Runnable onChanged;

    public TmdbTokenDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_tmdb_token);
        setCanceledOnTouchOutside(true);

        String address = ControlManager.get().getAddress(false) + "/#config";
        int side = AutoSizeUtils.mm2px(getContext(), 160);
        ((ImageView) findViewById(R.id.ivTmdbQR)).setImageBitmap(
                QRCodeGen.generateBitmap(address, side, side));
        ((TextView) findViewById(R.id.tvTmdbAddr)).setText(address);
    }

    @Override
    public void dismiss() {
        if (onChanged != null) onChanged.run();
        super.dismiss();
    }
}
