package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 首页离线缓存：把「上次成功加载过的东西」落到本地，下次冷启动先拿出来铺屏。
 *
 * 为什么不用 Hawk：Hawk 最终落在 SharedPreferences，一条分类数据就有几十 KB，
 * 存十来个分类会把一个 XML 撑到几百 KB，每次初始化都要整份解析，反而更慢。
 * 这里直接写文件，读的时候只读当前 tab 的那一个文件，互不影响。
 *
 * key 的构成：类型 + 站点 key + 分类 id。换源之后自然读不到旧源的数据，不会串台。
 */
public class HomeCache {

    /** 首页某个分类的第一页列表 */
    public static final String PREFIX_LIST = "list_";
    /** 站点分类（tab 列表） */
    public static final String PREFIX_SORT = "sort_";

    private static File dir() {
        File d = new File(App.getInstance().getFilesDir(), "homecache");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(String key) {
        return new File(dir(), MD5.string2MD5(key) + ".json");
    }

    public static void put(String key, String json) {
        if (key == null || json == null || json.length() < 8) return;
        FileOutputStream fos = null;
        try {
            File f = file(key);
            fos = new FileOutputStream(f);
            fos.write(json.getBytes("UTF-8"));
            fos.flush();
        } catch (Throwable ignored) {
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static String get(String key) {
        File f = file(key);
        if (!f.exists() || f.length() == 0) return null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) != -1) bos.write(buf, 0, len);
            return bos.toString("UTF-8");
        } catch (Throwable th) {
            return null;
        } finally {
            try {
                if (fis != null) fis.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 换源、清缓存时调用 */
    public static void clearAll() {
        File[] files = dir().listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                f.delete();
            } catch (Throwable ignored) {
            }
        }
    }
}
