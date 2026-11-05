package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.tvbox.osc.bean.Movie;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 豆瓣推荐数据源。
 *
 * 说明一个容易误会的地方：豆瓣只给「片名 / 评分 / 封面 / 主演」，**没有播放地址**。
 * 所以这里拿到的条目 id 是空的，点进去要拿片名去各站点搜（走 SearchActivity）。
 * 这也是原版影视仓的做法 —— 豆瓣负责"告诉你有什么好看"，片源站负责"能不能放"。
 *
 * 取什么：高分电影 + 高分剧集两路，各取一批后交错合并。
 * 只按评分降序拉全量会把纪录片和一些小众条目顶上来（实测「地球脉动」「人生七年」
 * 排在最前），做推荐流并不好看；分类型各取，观感稳定得多。
 */
public class DoubanApi {

    /** 缓存文件 key（不含日期，日期写在内容第一行，过期就重新拉） */
    private static final String CACHE_KEY = "douban_feed";
    private static final String CACHE_SPLIT = "\n";

    /** 影片所属类型，用来在文案上区分电影 / 剧集 */
    public static final int KIND_MOVIE = 0;
    public static final int KIND_TV = 1;

    public interface Callback {
        /**
         * @param list      解析好的条目，可能为空
         * @param fromCache 是否来自本地缓存（true 表示还没联网，先铺屏用）
         */
        void onResult(List<Movie.Video> list, boolean fromCache);
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }

    private static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    /**
     * 立刻回调本地缓存（如果有），然后再去网络拉一次。
     * 这样首屏不用等网络 —— 缓存是当天的话，网络回来内容基本相同，页面不会跳。
     */
    public static void load(final Callback callback) {
        final List<Movie.Video> cached = readCache();
        if (cached != null && !cached.isEmpty()) {
            callback.onResult(cached, true);
        } else {
            callback.onResult(new ArrayList<Movie.Video>(), true);
        }

        final int y = currentYear();
        final String yearRange = (y - 3) + "," + y;

        final List<Movie.Video> movies = new ArrayList<>();
        final List<Movie.Video> tvs = new ArrayList<>();
        final AtomicInteger done = new AtomicInteger(0);

        // 两路请求并发，两边都回来才合并。任一路失败也照样出结果，不至于整页空着。
        request("电影", yearRange, new Callback() {
            @Override
            public void onResult(List<Movie.Video> list, boolean fromCache) {
                movies.addAll(list);
                if (done.incrementAndGet() >= 2) deliver(callback, movies, tvs);
            }
        });
        request("电视剧", yearRange, new Callback() {
            @Override
            public void onResult(List<Movie.Video> list, boolean fromCache) {
                tvs.addAll(list);
                if (done.incrementAndGet() >= 2) deliver(callback, movies, tvs);
            }
        });
    }

    private static void deliver(final Callback callback, List<Movie.Video> movies, List<Movie.Video> tvs) {
        final List<Movie.Video> merged = merge(movies, KIND_MOVIE, tvs, KIND_TV);
        if (merged.isEmpty()) return; // 网络挂了且无缓存：保持页面原状，不推空数据把已经铺好的缓存冲掉
        writeCache(merged);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.onResult(merged, false);
            }
        });
    }

    /**
     * 两路交错合并：电影、剧集、电影、剧集……
     * 同一部作品按「去掉季数后的名字」去重，否则《万物生灵》第四/五/六季会并排占三格。
     */
    private static List<Movie.Video> merge(List<Movie.Video> movies, int kindMovie,
                                           List<Movie.Video> tvs, int kindTv) {
        List<Movie.Video> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int i = 0, j = 0;
        while (i < movies.size() || j < tvs.size()) {
            if (i < movies.size()) {
                Movie.Video v = movies.get(i++);
                if (seen.add(baseName(v.name))) out.add(v);
            }
            if (j < tvs.size()) {
                Movie.Video v = tvs.get(j++);
                if (seen.add(baseName(v.name))) out.add(v);
            }
        }
        return out;
    }

    /** 「万物生灵 第六季」→「万物生灵」，用于去重 */
    private static String baseName(String name) {
        if (name == null) return "";
        return name.replaceAll("\\s*第[一二三四五六七八九十0-9]+季\\s*$", "").trim();
    }

    private static void request(String tags, String yearRange, final Callback callback) {
        String url;
        try {
            url = "https://movie.douban.com/j/new_search_subjects?sort=S&range=0,10"
                    + "&tags=" + URLEncoder.encode(tags, "UTF-8")
                    + "&playable=1&start=0&year_range=" + yearRange;
        } catch (Throwable th) {
            callback.onResult(new ArrayList<Movie.Video>(), false);
            return;
        }
        // 豆瓣会按 UA 拦请求，随机 UA 是原版就在用的做法
        OkGo.<String>get(url).headers("User-Agent", UA.random()).execute(new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                callback.onResult(parse(response.body()), false);
            }

            @Override
            public void onError(Response<String> response) {
                callback.onResult(new ArrayList<Movie.Video>(), false);
            }

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
        });
    }

    private static List<Movie.Video> parse(String json) {
        List<Movie.Video> result = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return result;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("data");
            if (array == null) return result;
            for (JsonElement ele : array) {
                if (!ele.isJsonObject()) continue;
                JsonObject obj = ele.getAsJsonObject();
                String title = optString(obj, "title");
                if (TextUtils.isEmpty(title)) continue;

                Movie.Video vod = new Movie.Video();
                vod.name = title;
                // 评分单独放 rate，卡片右上角用它做角标；note 留给主演，焦点浮层第二行显示
                vod.rate = optString(obj, "rate");
                vod.note = joinCasts(obj);
                String cover = optString(obj, "cover");
                if (!TextUtils.isEmpty(cover)) {
                    // doubanio 的图要带 Referer 才给，ImgUtil 只认 douban 域名，这里显式补上
                    vod.pic = cover + "@User-Agent=" + UA.random() + "@Referer=https://www.douban.com/";
                }
                // id 故意留空：豆瓣条目没有站内 id，点进去走搜索
                vod.id = "";
                vod.sourceKey = "";
                result.add(vod);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static String joinCasts(JsonObject obj) {
        try {
            JsonArray casts = obj.getAsJsonArray("casts");
            if (casts == null || casts.size() == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < casts.size() && i < 2; i++) {
                String c = casts.get(i).getAsString();
                if (TextUtils.isEmpty(c)) continue;
                if (sb.length() > 0) sb.append(" / ");
                sb.append(c);
            }
            return sb.toString();
        } catch (Throwable th) {
            return "";
        }
    }

    private static String optString(JsonObject obj, String key) {
        try {
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return "";
            return e.getAsString().trim();
        } catch (Throwable th) {
            return "";
        }
    }

    // ---------------- 缓存 ----------------

    private static List<Movie.Video> readCache() {
        try {
            String raw = HomeCache.get(CACHE_KEY);
            if (TextUtils.isEmpty(raw)) return null;
            int idx = raw.indexOf(CACHE_SPLIT);
            if (idx <= 0) return null;
            String day = raw.substring(0, idx);
            // 只认当天的：豆瓣这份榜单本身就是按年/评分排的，隔天重拉一次足够。
            // 隔天不重拉也不会出错，但会因为「最新」类内容过期而显得不新鲜。
            if (!today().equals(day)) return null;
            List<Movie.Video> list = com.blankj.utilcode.util.GsonUtils.fromJson(
                    raw.substring(idx + CACHE_SPLIT.length()),
                    new com.google.gson.reflect.TypeToken<List<Movie.Video>>() {
                    }.getType());
            return list;
        } catch (Throwable th) {
            return null;
        }
    }

    private static void writeCache(List<Movie.Video> list) {
        try {
            HomeCache.put(CACHE_KEY, today() + CACHE_SPLIT
                    + com.blankj.utilcode.util.GsonUtils.toJson(list));
        } catch (Throwable ignored) {
        }
    }
}
