package com.github.tvbox.osc.event;

/**
 * 海报墙焦点变化事件：把当前焦点影片的标题/备注/年份/来源/封面送到首页。
 * 由 {@link com.github.tvbox.osc.ui.fragment.GridFragment} 发出，HomeActivity 接收：
 * 标题等进底部信息条，封面拿去生成全屏模糊底图（苹果玻璃效果的基底）。
 */
public class HomeFocusEvent {

    public final String title;
    public final String note;
    public final int year;
    public final String sourceName;
    /** 封面地址：用于生成背景模糊图，可能为空 */
    public final String pic;

    public HomeFocusEvent(String title, String note, int year, String sourceName, String pic) {
        this.title = title;
        this.note = note;
        this.year = year;
        this.sourceName = sourceName;
        this.pic = pic;
    }
}
