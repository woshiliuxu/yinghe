package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * TMDB 密钥弹窗（0.11.7：双状态）。
 *
 * 未配置：单栏，只有二维码 + 一句说明（第一次配的人一眼知道要扫码）；
 * 已配置：左栏状态卡显示「已配置 · v4 Read Token」+ 掩码 Token + 配置日期，
 *        右侧缩小为更换/清空入口，避免「永远只看到一张码，不知道配没配」。
 *
 * 遥控器没法粘贴长 Token，TV 端始终不放输入框；配置一律手机扫码完成。
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

        bindState();
    }

    /** 按「配没配」切换两种形态 */
    private void bindState() {
        String token = Hawk.get(HawkConfig.TMDB_TOKEN, "");
        boolean configured = !TextUtils.isEmpty(token);

        findViewById(R.id.llTmdbCard).setVisibility(configured ? View.VISIBLE : View.GONE);
        TextView tip = findViewById(R.id.tvTmdbTip);
        tip.setText(configured ? R.string.dia_tmdb_qr_tip : R.string.dia_tmdb_empty_tip);

        if (!configured) return;

        boolean v4 = token.startsWith("eyJ");
        ((TextView) findViewById(R.id.tvTmdbType))
                .setText(v4 ? R.string.dia_tmdb_type_v4 : R.string.dia_tmdb_type_v3);
        ((TextView) findViewById(R.id.tvTmdbMasked)).setText(maskToken(token));

        long time = Hawk.get(HawkConfig.TMDB_TOKEN_TIME, 0L);
        TextView tvTime = findViewById(R.id.tvTmdbTime);
        if (time > 0) {
            tvTime.setVisibility(View.VISIBLE);
            tvTime.setText(getContext().getString(R.string.dia_tmdb_at,
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time))));
        } else {
            tvTime.setVisibility(View.GONE);
        }
    }

    /**
     * 掩码：只露头尾，够用户认出「是不是我这把 key」就行。
     * v4 Token 很长（200+ 字符），头 7 位固定是 eyJhbGc；v3 短 key 头 4 尾 4。
     */
    private String maskToken(String token) {
        int keep = token.startsWith("eyJ") ? 7 : 4;
        if (token.length() <= keep + 6) {
            return token.substring(0, Math.min(2, token.length())) + "…";
        }
        return token.substring(0, keep) + "…" + token.substring(token.length() - 4);
    }

    @Override
    public void dismiss() {
        if (onChanged != null) onChanged.run();
        super.dismiss();
    }
}
