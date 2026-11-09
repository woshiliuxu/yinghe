package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.tvbox.osc.bean.TmdbItem;
import com.orhanobut.hawk.Hawk;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * TMDB（The Movie Database）推荐数据源 —— 首页大图轮播。
 *
 * 用的免费计划：/trending 每周热门（电影 + 剧集两路），language=zh-CN 拿中文标题和简介；
 * 图片走 image.tmdb.org，backdrop 本身就是宽幅剧照，w1280 铺 16:9 全屏不用模糊。
 * 认证用 v4 Read Access Token（JWT），放 Authorization 头，比拼 api_key 参数干净。
 *
 * 和 DoubanApi 一样的套路：先回调本地缓存（当天有效）再联网刷新，
 * 4 路请求（两个分类各一份榜单 + 两份类型字典）并发，全回来才合并；
 * TMDB 挂了也不影响页面 —— 首页海报墙还有豆瓣兜底，这里只是空着不显示轮播。
 */
public class TmdbApi {

    /**
     * 纯壳原则：出厂不带任何密钥。token 只来自用户自己配置
     * （设置 → 首页 → TMDB 密钥 扫码填入），没配 = TMDB 大图整体不启用，
     * 首页回落豆瓣海报墙。不内置 token 一是为了限流（免费 key 按账号算），
     * 二是把别人的密钥打进包里属于隐私泄露。
     */
    private static String getToken() {
        return Hawk.get(HawkConfig.TMDB_TOKEN, "");
    }

    /** 用户是否已经配置过 TMDB（没配过就完全不发请求） */
    public static boolean hasToken() {
        return !TextUtils.isEmpty(getToken());
    }

    /** 用户换 key 后缓存里的旧数据作废，写个过期日期让它下次重新拉 */
    public static void invalidateCache() {
        try {
            HomeCache.put(CACHE_KEY, "00000000" + CACHE_SPLIT);
        } catch (Throwable ignored) {
        }
    }

    private static final String API_PATH = "/3";
    /**
     * api.themoviedb.org 在国内直连是被墙的（DNS 污染），api.tmdb.org 是官方同一服务的
     * 另一个域名，实测直连可用。所以主用 api.tmdb.org，失败自动切回官方域名兜底。
     */
    private static final String[] HOSTS = {"https://api.tmdb.org", "https://api.themoviedb.org"};
    /** 当前生效的 host 下标，请求失败自动顺延 */
    private static volatile int hostIdx = 0;
    private static final String IMG = "https://image.tmdb.org/t/p";

    /** 缓存文件 key（不含日期，日期写在内容第一行，过期就重新拉） */
    private static final String CACHE_KEY = "tmdb_hero";
    private static final String CACHE_SPLIT = "\n";

    /** 一次最多取多少部进轮播（切换器从左到右排） */
    private static final int MAX_ITEMS = 8;

    public static final int KIND_MOVIE = 0;
    public static final int KIND_TV = 1;

    public interface Callback {
        /**
         * @param list      解析好的条目，可能为空
         * @param fromCache 是否来自本地缓存（true 表示还没联网，先铺屏用）
         */
        void onResult(List<TmdbItem> list, boolean fromCache);
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }

    /** 先给缓存（当天的），然后四路并发拉最新数据 */
    public static void load(final Callback callback) {
        // 纯壳：没配 token 就不发任何请求，直接按「TMDB 挂了」回调空列表，
        // 页面会自己把焦点还给分类胶囊，首页回落豆瓣海报墙
        if (!hasToken()) {
            callback.onResult(new ArrayList<TmdbItem>(), false);
            return;
        }

        final List<TmdbItem> cached = readCache();
        if (cached != null) {
            callback.onResult(cached, true);
        }

        final AtomicInteger done = new AtomicInteger(0);
        final Map<Integer, String> movieGenres = new HashMap<>();
        final Map<Integer, String> tvGenres = new HashMap<>();
        final List<TmdbItem> movies = new ArrayList<>();
        final List<TmdbItem> tvs = new ArrayList<>();

        // 类型字典：把 genre_ids 翻译成中文类型名
        requestGenres("movie", new GenreCallback() {
            @Override
            public void onResult(Map<Integer, String> map) {
                movieGenres.putAll(map);
                if (done.incrementAndGet() >= 4) deliver(callback, movies, tvs, movieGenres, tvGenres);
            }
        });
        requestGenres("tv", new GenreCallback() {
            @Override
            public void onResult(Map<Integer, String> map) {
                tvGenres.putAll(map);
                if (done.incrementAndGet() >= 4) deliver(callback, movies, tvs, movieGenres, tvGenres);
            }
        });
        request("trending/movie/week", KIND_MOVIE, new ItemCallback() {
            @Override
            public void onResult(List<TmdbItem> list) {
                movies.addAll(list);
                if (done.incrementAndGet() >= 4) deliver(callback, movies, tvs, movieGenres, tvGenres);
            }
        });
        request("trending/tv/week", KIND_TV, new ItemCallback() {
            @Override
            public void onResult(List<TmdbItem> list) {
                tvs.addAll(list);
                if (done.incrementAndGet() >= 4) deliver(callback, movies, tvs, movieGenres, tvGenres);
            }
        });
    }

    private static void deliver(final Callback callback, List<TmdbItem> movies, List<TmdbItem> tvs,
                                Map<Integer, String> movieGenres, Map<Integer, String> tvGenres) {
        final List<TmdbItem> merged = merge(movies, movieGenres, tvs, tvGenres);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.onResult(merged, false);
            }
        });
        if (!merged.isEmpty()) writeCache(merged);
        // 空列表也要回调：页面靠它知道「TMDB 挂了」，好把首屏焦点还回分类胶囊
    }

    /** 电影剧集交错合并（电影、剧、电影、剧……），保持两边都露脸 */
    private static List<TmdbItem> merge(List<TmdbItem> movies, Map<Integer, String> mg,
                                        List<TmdbItem> tvs, Map<Integer, String> tg) {
        List<TmdbItem> out = new ArrayList<>();
        int i = 0, j = 0;
        while ((i < movies.size() || j < tvs.size()) && out.size() < MAX_ITEMS) {
            if (i < movies.size()) out.add(bakeGenres(movies.get(i++), mg));
            if (j < tvs.size() && out.size() < MAX_ITEMS) out.add(bakeGenres(tvs.get(j++), tg));
        }
        return out;
    }

    private static TmdbItem bakeGenres(TmdbItem item, Map<Integer, String> map) {
        StringBuilder sb = new StringBuilder();
        try {
            for (int id : item.genreIds) {
                String n = map.get(id);
                if (TextUtils.isEmpty(n)) continue;
                if (sb.length() > 0) sb.append(" / ");
                sb.append(n);
                if (sb.length() > 24) break; // 类型拼两三个就够了
            }
        } catch (Throwable ignored) {
        }
        item.genres = sb.toString();
        return item;
    }

    // ---------------- 请求 ----------------

    public interface ImagesCallback {
        void onResult(List<String> urls);
    }

    private interface ItemCallback {
        void onResult(List<TmdbItem> list);
    }

    private interface GenreCallback {
        void onResult(Map<Integer, String> map);
    }

    /**
     * 拉某部片的全部宽幅剧照（backdrops，w1280，最多 5 张，首页大图轮播用）。
     * include_image_language=zh,en,null —— 带中英文或无字幕文字的图都要，纯其它语言的排除。
     */
    public static void fetchImages(final TmdbItem item, final ImagesCallback callback) {
        if (item == null || TextUtils.isEmpty(item.id) || !hasToken()) {
            callback.onResult(new ArrayList<String>());
            return;
        }
        String path = (item.kind == KIND_MOVIE ? "/movie/" : "/tv/") + item.id
                + "/images?include_image_language=zh,en,null";
        executeWithFallback(path, new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                callback.onResult(parseImages(response.body()));
            }

            @Override
            public void onError(Response<String> response) {
                callback.onResult(new ArrayList<String>());
            }

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
        });
    }

    private static List<String> parseImages(String json) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return out;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("backdrops");
            if (array == null) return out;
            for (JsonElement ele : array) {
                if (!ele.isJsonObject()) continue;
                String p = optString(ele.getAsJsonObject(), "file_path");
                if (TextUtils.isEmpty(p)) continue;
                out.add(IMG + "/w1280" + p);
                if (out.size() >= 5) break;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void request(String path, final int kind, final ItemCallback callback) {
        executeWithFallback("/" + path + "?language=zh-CN", new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                callback.onResult(parseItems(response.body(), kind));
            }

            @Override
            public void onError(Response<String> response) {
                callback.onResult(new ArrayList<TmdbItem>());
            }

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
        });
    }

    private static void requestGenres(String kind, final GenreCallback callback) {
        executeWithFallback("/genre/" + kind + "/list?language=zh-CN", new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                callback.onResult(parseGenres(response.body()));
            }

            @Override
            public void onError(Response<String> response) {
                callback.onResult(new HashMap<Integer, String>());
            }

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
        });
    }

    /**
     * 域名回退：当前 host 失败就换下一个重试一次，两个都挂才算真挂。
     * 认证两都支持：v4 Read Token（eyJ 开头，长）走 Bearer 头；
     * v3 API Key（32 位短串，遥控器打得出来）走 api_key 参数。
     */
    private static void executeWithFallback(final String pathAndQuery, final AbsCallback<String> inner) {
        final String url = HOSTS[hostIdx] + API_PATH + pathAndQuery;
        // v4 Read Token（eyJ 开头）走 Bearer 头；v3 API Key（32 位短串）走 api_key 参数
        String token = getToken();
        com.lzy.okgo.request.GetRequest<String> call;
        if (token.startsWith("eyJ")) {
            call = OkGo.<String>get(url)
                    .headers("Authorization", "Bearer " + token)
                    .headers("accept", "application/json");
        } else {
            call = OkGo.<String>get(url + "&api_key=" + token)
                    .headers("accept", "application/json");
        }
        call.execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        inner.onSuccess(response);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        if (hostIdx < HOSTS.length - 1) {
                            hostIdx++;
                            executeWithFallback(pathAndQuery, inner);
                        } else {
                            inner.onError(response);
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    // ---------------- 解析 ----------------

    private static List<TmdbItem> parseItems(String json, int kind) {
        List<TmdbItem> result = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return result;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("results");
            if (array == null) return result;
            for (JsonElement ele : array) {
                if (!ele.isJsonObject()) continue;
                JsonObject obj = ele.getAsJsonObject();
                String backdropPath = optString(obj, "backdrop_path");
                // 没有剧照的条目进不了轮播（大图和缩略图都靠它）
                if (TextUtils.isEmpty(backdropPath)) continue;

                TmdbItem item = new TmdbItem();
                item.id = optString(obj, "id");
                item.name = optString(obj, "title");
                if (TextUtils.isEmpty(item.name)) item.name = optString(obj, "name");
                if (TextUtils.isEmpty(item.name)) continue;
                item.rate = optString(obj, "vote_average");
                if (!TextUtils.isEmpty(item.rate)) {
                    try {
                        item.rate = String.format(Locale.CHINA, "%.1f", Double.parseDouble(item.rate));
                    } catch (Throwable ignored) {
                        item.rate = "";
                    }
                }
                String date = optString(obj, "release_date");
                if (TextUtils.isEmpty(date)) date = optString(obj, "first_air_date");
                item.year = date != null && date.length() >= 4 ? date.substring(0, 4) : "";
                item.overview = optString(obj, "overview");
                item.backdrop = IMG + "/w1280" + backdropPath;
                item.thumb = IMG + "/w300" + backdropPath;
                item.kind = kind;

                item.genreIds = new ArrayList<>();
                try {
                    JsonArray gids = obj.getAsJsonArray("genre_ids");
                    if (gids != null) {
                        for (JsonElement g : gids) item.genreIds.add(g.getAsInt());
                    }
                } catch (Throwable ignored) {
                }
                result.add(item);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static Map<Integer, String> parseGenres(String json) {
        Map<Integer, String> map = new HashMap<>();
        if (TextUtils.isEmpty(json)) return map;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("genres");
            if (array == null) return map;
            for (JsonElement ele : array) {
                if (!ele.isJsonObject()) continue;
                JsonObject obj = ele.getAsJsonObject();
                try {
                    map.put(obj.get("id").getAsInt(), obj.get("name").getAsString());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
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

    private static List<TmdbItem> readCache() {
        try {
            String raw = HomeCache.get(CACHE_KEY);
            if (TextUtils.isEmpty(raw)) return null;
            int idx = raw.indexOf(CACHE_SPLIT);
            if (idx <= 0) return null;
            if (!today().equals(raw.substring(0, idx))) return null;
            List<TmdbItem> list = com.blankj.utilcode.util.GsonUtils.fromJson(
                    raw.substring(idx + CACHE_SPLIT.length()),
                    new com.google.gson.reflect.TypeToken<List<TmdbItem>>() {
                    }.getType());
            return list == null || list.isEmpty() ? null : list;
        } catch (Throwable th) {
            return null;
        }
    }

    private static void writeCache(List<TmdbItem> list) {
        try {
            HomeCache.put(CACHE_KEY, today() + CACHE_SPLIT
                    + com.blankj.utilcode.util.GsonUtils.toJson(list));
        } catch (Throwable ignored) {
        }
    }
}
