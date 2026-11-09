package com.github.tvbox.osc.bean;

import java.io.Serializable;

/**
 * TMDB 首页轮播条目。
 *
 * 故意不复用 Movie.Video：那 个 bean 被 XStream 拿去解析站源 XML，
 * 多塞一个字段都可能牵动序列化；这里的字段是自己一组，Gson 读写互不干扰。
 */
public class TmdbItem implements Serializable {

    /** 影片名（TMDB 中文标题） */
    public String name;
    /** 评分（0~10，一位小数；空串表示没有） */
    public String rate;
    /** 年份，如 2024 */
    public String year;
    /** 类型名，已翻译成中文并拼好，如「科幻 / 冒险」 */
    public String genres;
    /** 简介（TMDB overview） */
    public String overview;
    /** 全屏背景大图 w1280（宽幅剧照，铺 16:9 正合适） */
    public String backdrop;
    /** 右上角切换器小图 w300（同样是宽幅剧照，缩略用） */
    public String thumb;
    /** 电影 / 剧集（TmdbApi.KIND_MOVIE / KIND_TV） */
    public int kind;
    /** TMDB 影片 id，拉多张剧照时拼 /movie|tv/{id}/images 用 */
    public String id;
    /**
     * 该片的全部宽幅剧照（w1280），懒加载：首次展示该片时拉一次。
     * >= 2 张才轮播；缓存里没这个字段（拉图发生在缓存铺屏之后），每次启动会重拉一次，可接受。
     */
    public java.util.List<String> images;

    /** TMDB 原始 genre_ids，解析途中用来查类型字典；翻译完就不再需要，不落缓存 */
    public transient java.util.List<Integer> genreIds;
}
