package com.github.tvbox.osc.server;

import java.util.ArrayList;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.TmdbApi;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    /** 与 TV 端 ConfigApiDialog.switchTo 同款重启：带 useCache 回主界面重新拉配置。
     *  组件必须显式钉到 SplashActivity：裸 LAUNCHER intent 在部分固件上会弹「打开方式」选择框 */
    private void restartApp() {
        Context app = App.getInstance();
        Intent intent = new Intent();
        intent.setClassName(app.getPackageName(), "com.github.tvbox.osc.ui.activity.SplashActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        app.startActivity(intent);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null && mDataReceiver != null) {
                    String action = params.get("do");

                    switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "api": {
                            // 手机端配置数据源：真正落盘（旧流程只发事件不改 Hawk，
                            // 「立即生效」等于没生效）。写入当前项 + 置顶历史，重启拉新配置。
                            String url = params.get("url") == null ? "" : params.get("url").trim();
                            if (url.isEmpty()) break;
                            String current = Hawk.get(HawkConfig.API_URL, "");
                            Hawk.put(HawkConfig.API_URL, url);
                            ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                            history.remove(url);
                            history.add(0, url);
                            Hawk.put(HawkConfig.API_HISTORY, history);
                            if (!url.equals(current)) restartApp();
                            break;
                        }
                        case "live": {
                            String url = params.get("url") == null ? "" : params.get("url").trim();
                            if (url.isEmpty()) break;
                            if (!url.equals(Hawk.get(HawkConfig.LIVE_URL, ""))) {
                                Hawk.put(HawkConfig.LIVE_URL, url);
                                restartApp();
                            }
                            break;
                        }
                        case "epg": {
                            String url = params.get("url") == null ? "" : params.get("url").trim();
                            if (url.isEmpty()) break;
                            if (!url.equals(Hawk.get(HawkConfig.EPG_URL, ""))) {
                                Hawk.put(HawkConfig.EPG_URL, url);
                                restartApp();
                            }
                            break;
                        }
                        case "proxys": {
                            String url = params.get("url") == null ? "" : params.get("url").trim();
                            if (url.isEmpty()) break;
                            if (!url.equals(Hawk.get(HawkConfig.PROXY_SERVER, ""))) {
                                Hawk.put(HawkConfig.PROXY_SERVER, url);
                                restartApp();
                            }
                            break;
                        }
                        case "push": {
                            // 暂未实现
                            mDataReceiver.onPushReceived(params.get("url").trim());
                            break;
                        }
                        case "tmdb": {
                            // 手机端配置 TMDB 密钥：TV 上不方便粘贴长 token，这里代劳
                            String token = params.get("token") == null ? "" : params.get("token").trim();
                            if (token.isEmpty()) {
                                Hawk.delete(HawkConfig.TMDB_TOKEN);
                            } else {
                                Hawk.put(HawkConfig.TMDB_TOKEN, token);
                            }
                            TmdbApi.invalidateCache();
                            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_TMDB_CHANGE));
                            break;
                        }
                        case "mirror": {
                            //推送当前电影、电视剧……
                            mDataReceiver.onMirrorReceived(params.get("id").trim(), params.get("sourceKey").trim());
                            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "mirrored");
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
