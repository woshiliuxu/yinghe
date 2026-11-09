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
import com.lzy.okgo.model.HttpHeaders;
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
 * 豆瓣推荐数据源（0.11.9：改走 rexxar 热门榜）。
 *
 * <p>为什么换：旧实现用的是 {@code movie.douban.com/j/new_search_subjects}，
 * 本质是「按评分排序的搜索」——它按 sort=S 把高分片排前面，跟豆瓣首页的
 * 「热门电影」不是一回事：新上映的热门片评分还没起来、老片评分高，所以榜单
 * 又旧又偏。现在改成豆瓣 App 首页同源的 rexxar 集合接口：
 * <ul>
 * <li>{@code movie_hot_gaia} —— 热门电影（实测 total 307）</li>
 * <li>{@code tv_hot} —— 热门剧集（实测 total 204）</li>
 * </ul>
 * 顺序就是豆瓣自己的热度序，评分 / 国家 / 类型 / 导演 / 主演 / 上映日期全都有，
 * 比旧接口（只有片名 + 评分 + 主演）信息量大。
 *
 * <p>两个坑记一下：
 * <ul>
 * <li>rexxar 单页最多 50 条：传 count=200 也只回 50，必须按 start 翻页</li>
 * <li>接口要 {@code Referer: https://m.douban.com/}，不带会被拒</li>
 * </ul>
 *
 * <p>豆瓣只给片名 / 评分 / 封面，**没有播放地址**，所以条目 id 是空的，
 * 点进去拿片名去各站点搜（走 SearchActivity）—— 壳的边界就在这。
 */
public class DoubanApi {

    /** 缓存文件 key（不含日期，日期写在内容第一行，过期就重新拉）；加过滤后换 v3 防止读到旧榜 */
    private static final String CACHE_KEY = "douban_hot_v3";

    /**
     * 评分下限：低于这个分数的条目直接不上海报墙。
     * 热门榜是热度序，低分新片常混在中间（实测 200 部电影里有 41 部 <6），
     * 不筛的话海报墙上会有一堆 5 分片。未开分（豆瓣返 0）的照留 —— 那不叫低分，
     * 是新片还没攒够评分，筛掉反而把最新的片子筛没了。
     */
    private static final double MIN_RATE = 6.0;
    private static final String CACHE_SPLIT = "\n";

    private static final String COLLECTION_BASE = "https://m.douban.com/rexxar/api/v2/subject_collection/";
    private static final String REFERER = "https://m.douban.com/";

    /** rexxar 单页硬上限；传更大的数服务端按 50 截断，所以分页得自己来 */
    private static final int PAGE = 50;
    /**
     * 采集目标：电影 200（4 页）、剧集 150（3 页）。
     * 比海报墙实际需要多一些，因为 {@link #MIN_RATE} 会筛掉一部分低分片，
     * 多拉一页刚好补回来（实测筛完电影剩约 160、剧集约 115）。
     */
    private static final int MOVIE_TARGET = 200;
    private static final int TV_TARGET = 150;

    /** 影片所属类型，用来区分电影 / 剧集（也决定解析哪套封面字段） */
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

    /**
     * 立刻回调本地缓存（如果有），然后再去网络拉一次。
     * 首屏不用等网络 —— 缓存是当天的话，网络回来内容基本相同，页面不会跳。
     */
    public static void load(final Callback callback) {
        final List<Movie.Video> cached = readCache();
        callback.onResult(cached != null ? cached : new ArrayList<Movie.Video>(), true);

        final List<Movie.Video> movies = new ArrayList<>();
        final List<Movie.Video> tvs = new ArrayList<>();
        final AtomicInteger done = new AtomicInteger(0);

        // 两路并发，各自串行翻页；两边都回来才合并。任一路失败也照样出结果。
        fetch("movie_hot_gaia", MOVIE_TARGET, KIND_MOVIE, movies, new Runnable() {
            @Override
            public void run() {
                if (done.incrementAndGet() >= 2) deliver(callback, movies, tvs);
            }
        });
        fetch("tv_hot", TV_TARGET, KIND_TV, tvs, new Runnable() {
            @Override
            public void run() {
                if (done.incrementAndGet() >= 2) deliver(callback, movies, tvs);
            }
        });
    }

    private static void deliver(final Callback callback, List<Movie.Video> movies, List<Movie.Video> tvs) {
        final List<Movie.Video> merged = merge(movies, tvs);
        if (!merged.isEmpty()) {
            writeCache(merged);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(merged, false);
                }
            });
            return;
        }
        // rexxar 两路都空（被墙 / 接口改了）：退回旧接口，别让海报墙空着
        fallback(callback);
    }

    /** 兜底：旧版「按评分搜索」接口，内容不新但至少能出片 */
    private static void fallback(final Callback callback) {
        final int y = Calendar.getInstance().get(Calendar.YEAR);
        final String yearRange = (y - 3) + "," + y;
        final List<Movie.Video> movies = new ArrayList<>();
        final List<Movie.Video> tvs = new ArrayList<>();
        final AtomicInteger done = new AtomicInteger(0);
        Callback collect = new Callback() {
            @Override
            public void onResult(List<Movie.Video> list, boolean fromCache) {
                if (done.incrementAndGet() >= 2) {
                    final List<Movie.Video> merged = merge(movies, tvs);
                    if (merged.isEmpty()) return;
                    writeCache(merged);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(merged, false);
                        }
                    });
                }
            }
        };
        requestLegacy("电影", yearRange, movies, collect);
        requestLegacy("电视剧", yearRange, tvs, collect);
    }

    /** 拉一个集合的全部页：页与页串行，避免并发被风控 */
    private static void fetch(final String collection, final int target, final int kind,
                              final List<Movie.Video> sink, final Runnable onDone) {
        fetchPage(collection, 0, target, kind, sink, onDone);
    }

    private static void fetchPage(final String collection, final int start, final int target,
                                  final int kind, final List<Movie.Video> sink, final Runnable onDone) {
        if (start >= target) {
            onDone.run();
            return;
        }
        final int count = Math.min(PAGE, target - start);
        String url = COLLECTION_BASE + collection + "/items?start=" + start + "&count=" + count;
        HttpHeaders headers = new HttpHeaders();
        headers.put("User-Agent", UA.random());
        headers.put("Referer", REFERER);
        OkGo.<String>get(url).headers(headers).execute(new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                List<Movie.Video> page = parse(response.body(), kind);
                sink.addAll(page);
                // 本页不满 = 已经到底，别再白请求一次
                if (page.size() < count) onDone.run();
                else fetchPage(collection, start + count, target, kind, sink, onDone);
            }

            @Override
            public void onError(Response<String> response) {
                onDone.run();
            }

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
        });
    }

    private static List<Movie.Video> parse(String json, int kind) {
        List<Movie.Video> result = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return result;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("subject_collection_items");
            if (array == null) return result;
            for (JsonElement ele : array) {
                if (!ele.isJsonObject()) continue;
                JsonObject obj = ele.getAsJsonObject();
                String title = optString(obj, "title");
                if (TextUtils.isEmpty(title)) continue;

                Movie.Video vod = new Movie.Video();
                vod.name = title;

                // 评分：暂无评分在豆瓣是 0，做成角标会显示「0.0」，干脆留空
                double rate = optDouble(obj, "rating", "value");
                vod.rate = rate > 0 ? String.valueOf(Math.round(rate * 10) / 10.0) : "";

                // 信息行：电影 info 已是「国家 / 类型 / 导演 / 主演」；
                // 剧集 card_subtitle 同构，再补一句更新进度更有用
                String note = kind == KIND_MOVIE
                        ? optString(obj, "info")
                        : optString(obj, "card_subtitle");
                if (kind == KIND_TV) {
                    String ep = optString(obj, "episodes_info");
                    if (!TextUtils.isEmpty(ep)) {
                        note = TextUtils.isEmpty(note) ? ep : note + " · " + ep;
                    }
                }
                vod.note = note;

                String cover = kind == KIND_MOVIE ? movieCover(obj) : tvCover(obj);
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

    /** 电影封面：cover 是个对象，取 url（m_ratio 中图，海报墙放大也不糊） */
    private static String movieCover(JsonObject obj) {
        try {
            JsonElement c = obj.get("cover");
            if (c != null && c.isJsonObject()) return optString(c.getAsJsonObject(), "url");
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 剧集封面：pic.large（没有就退 normal） */
    private static String tvCover(JsonObject obj) {
        try {
            JsonElement p = obj.get("pic");
            if (p != null && p.isJsonObject()) {
                JsonObject o = p.getAsJsonObject();
                String large = optString(o, "large");
                return TextUtils.isEmpty(large) ? optString(o, "normal") : large;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 两路交错合并：电影、剧集、电影、剧集……
     * 同一部作品按「去掉季数后的名字」去重，否则《万物生灵》第四/五/六季会并排占三格。
     *
     * <p>评分过滤放在这里而不是 {@link #parse}：parse 的返回条数被 fetchPage 用来
     * 判断「本页不满 = 已到底」，在那儿筛会让一页 50 条筛成 40 条、误判到底不再翻页。
     */
    private static List<Movie.Video> merge(List<Movie.Video> movies, List<Movie.Video> tvs) {
        List<Movie.Video> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int i = 0, j = 0;
        while (i < movies.size() || j < tvs.size()) {
            if (i < movies.size()) {
                Movie.Video v = movies.get(i++);
                if (passRate(v) && seen.add(baseName(v.name))) out.add(v);
            }
            if (j < tvs.size()) {
                Movie.Video v = tvs.get(j++);
                if (passRate(v) && seen.add(baseName(v.name))) out.add(v);
            }
        }
        return out;
    }

    /** 评分达标才上墙：无评分（空串）放行，有评分但低于 MIN_RATE 的去掉 */
    private static boolean passRate(Movie.Video v) {
        if (v == null || TextUtils.isEmpty(v.rate)) return true;
        try {
            return Double.parseDouble(v.rate.trim()) >= MIN_RATE;
        } catch (Throwable th) {
            return true;
        }
    }

    /** 「万物生灵 第六季」→「万物生灵」，用于去重 */
    private static String baseName(String name) {
        if (name == null) return "";
        return name.replaceAll("\\s*第[一二三四五六七八九十0-9]+季\\s*$", "").trim();
    }

    // ---------------- 兜底用的旧接口 ----------------

    private static void requestLegacy(String tags, String yearRange,
                                      final List<Movie.Video> sink, final Callback callback) {
        String url;
        try {
            url = "https://movie.douban.com/j/new_search_subjects?sort=S&range=0,10"
                    + "&tags=" + URLEncoder.encode(tags, "UTF-8")
                    + "&playable=1&start=0&year_range=" + yearRange;
        } catch (Throwable th) {
            callback.onResult(new ArrayList<Movie.Video>(), false);
            return;
        }
        OkGo.<String>get(url).headers("User-Agent", UA.random()).execute(new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                List<Movie.Video> list = parseLegacy(response.body());
                sink.addAll(list);
                callback.onResult(list, false);
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

    private static List<Movie.Video> parseLegacy(String json) {
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
                vod.rate = optString(obj, "rate");
                vod.note = joinCasts(obj);
                String cover = optString(obj, "cover");
                if (!TextUtils.isEmpty(cover)) {
                    vod.pic = cover + "@User-Agent=" + UA.random() + "@Referer=https://www.douban.com/";
                }
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

    // ---------------- 解析小工具 ----------------

    private static String optString(JsonObject obj, String key) {
        try {
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return "";
            return e.getAsString().trim();
        } catch (Throwable th) {
            return "";
        }
    }

    private static double optDouble(JsonObject obj, String key, String sub) {
        try {
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonObject()) return 0;
            JsonElement v = e.getAsJsonObject().get(sub);
            if (v == null || v.isJsonNull()) return 0;
            return v.getAsDouble();
        } catch (Throwable th) {
            return 0;
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
            // 只认当天的：热门榜每天都在动，隔天的旧榜不如没有
            if (!today().equals(day)) return null;
            return com.blankj.utilcode.util.GsonUtils.fromJson(
                    raw.substring(idx + CACHE_SPLIT.length()),
                    new com.google.gson.reflect.TypeToken<List<Movie.Video>>() {
                    }.getType());
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
