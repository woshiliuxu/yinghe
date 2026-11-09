/* ============================================================
   影盒 · 手机助手 script.js（2026-11-05 三页面 + 玻璃导航版）
   搜索 / 推送 / 配置 各自独立页面，底部液态玻璃导航栏切换。
   ============================================================ */

/* ---------- 页面切换 ---------- */

function switchView(name) {
    $('.yhg-view').removeClass('is-active');
    $('#view' + name).addClass('is-active');
    $('.lg-dock__item').removeClass('is-on').attr('aria-selected', 'false');
    $('#dock' + name).addClass('is-on').attr('aria-selected', 'true');
    try { localStorage.setItem('yhg_view', name); } catch (e) { }
    window.scrollTo(0, 0);
}

/* ---------- 高级选项折叠 ---------- */

function toggleAdv() {
    $('#advBody').toggleClass('is-open');
    $('#advChev').toggleClass('is-open');
}

/* ---------- 动作 ---------- */

function doAction(action, kv, okMsg) {
    kv['do'] = action;
    $.post('/action', kv, function (data) {
        okToast(okMsg || '已发送到电视');
    }).fail(function () {
        warnToast('发送失败，请检查连接');
    });
    return false;
}

/* 文本域取单值：URL / Token 里不能有空白，多行粘贴时把换行也一并吃掉 */
function oneLine(sel) {
    return ($(sel).val() || '').replace(/\s+/g, '');
}

function search() {
    var word = $('#search_key_word').val();
    if (!word) return warnToast('先输入关键字');
    saveHistory(word);
    doAction('search', { word: word });
    $('#search_key_word').val('').closest('.search-bar').removeClass('has-text');
    renderHistory();
}

/* ---------- 搜索历史（手机本地保存，最近 10 条） ---------- */

var HIS_KEY = 'yhg_search_history';

function loadHistory() {
    try {
        var raw = localStorage.getItem(HIS_KEY);
        var arr = raw ? JSON.parse(raw) : [];
        return $.isArray(arr) ? arr : [];
    } catch (e) { return []; }
}

function saveHistory(word) {
    word = $.trim(word);
    if (!word) return;
    var arr = loadHistory();
    arr = $.grep(arr, function (w) { return w !== word; });
    arr.unshift(word);
    if (arr.length > 10) arr = arr.slice(0, 10);
    try { localStorage.setItem(HIS_KEY, JSON.stringify(arr)); } catch (e) { }
}

function renderHistory() {
    var arr = loadHistory();
    var box = $('#searchHistory');
    if (!arr.length) { box.hide(); return; }
    box.show();
    $('#hisChips').empty();
    $.each(arr, function (i, w) {
        $('<button type="button" class="his-chip"></button>')
            .text(w)
            .on('click', function () {
                $('#search_key_word').val(w).focus();
                $('#search_key_word').closest('.search-bar').toggleClass('has-text', !!w);
                search();
            })
            .appendTo('#hisChips');
    });
}

/* 数据源：支持一次粘贴多个（换行 / 逗号 / 分号分隔，去重保序）
   第 1 个设为当前生效，其余全部进电视端的配置列表供切换 */
function api() {
    var raw = ($('#diy_api_url').val() || '').trim();
    if (!raw) return warnToast('先粘贴配置地址');
    var list = [];
    raw.split(/[\n,;，；]+/).forEach(function (s) {
        s = s.trim();
        if (s && list.indexOf(s) < 0) list.push(s);
    });
    if (!list.length) return warnToast('没有识别到有效的配置地址');
    doAction('api', { url: list.join('\n') },
        list.length > 1
            ? '已保存 ' + list.length + ' 个数据源，当前使用第 1 个'
            : '配置已保存，电视正在重启');
    $('#diy_api_url').val('').closest('.search-bar').removeClass('has-text');
}

function live() {
    doAction('live', { url: oneLine('#diy_live_url') }, '直播源已保存，电视正在重启');
}

function epg() {
    doAction('epg', { url: oneLine('#diy_epg_url') }, 'EPG 已保存，电视正在重启');
}

function proxys() {
    doAction('proxys', { url: oneLine('#diy_proxy_server') }, '代理已保存，电视正在重启');
}

function tmdbToken() {
    var token = oneLine('#diy_tmdb_token');
    doAction('tmdb', { token: token }, token ? 'TMDB 密钥已保存' : '已清空，TMDB 大图已关闭');
    $('#diy_tmdb_token').val('');
}

function push() {
    if (!$('#push_url').val()) return warnToast('先粘贴播放地址');
    doAction('push', { url: $('#push_url').val() });
    $('#push_url').val('');
}

/* ---------- Toast 提示 ---------- */

var toastTimer = null;

function showToast(msg, type) {
    var box = $('#warnToastBox');
    box.toggleClass('is-ok', type === 'ok');
    $('#warnToastContent').html(msg);
    $('#warnToast').show();
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
        $('#warnToast').hide();
    }, 1400);
}

function warnToast(msg) {
    showToast(msg, 'warn');
}

function okToast(msg) {
    showToast(msg, 'ok');
}

/* ---------- 初始化 ---------- */

$(function () {
    // 页面由电视端服务直接提供，副标题告知当前控制的是哪台电视
    var host = (window.location.hostname || window.location.host || '').trim();
    // 品牌行已删，地址并入导航提示；页面顶部只留大标题

    // 恢复上次停留的页面；push.html / api.html 旧入口也各自直达
    // TV 端二维码指向 /#config —— 带锚点时无视 localStorage，直达配置页
    var url = window.location.href;
    var saved = null;
    try { saved = localStorage.getItem('yhg_view'); } catch (e) { }
    if (url.indexOf('#config') >= 0) switchView('Config');
    else if (url.indexOf('push.html') > 0) switchView('Push');
    else if (url.indexOf('api.html') > 0 || url.indexOf('all.html') > 0) switchView('Config');
    else if (saved === 'Push' || saved === 'Config' || saved === 'Search') switchView(saved);
    else switchView('Config');

    // 输入非空时显示清空按钮（多行文本域同样适用）
    $('.search-bar input, .search-bar textarea').on('input', function () {
        $(this).closest('.search-bar').toggleClass('has-text', !!this.value);
    });

    // 多行文本域里 Enter = 换行（批量粘贴多个源要用），提交走按钮；
    // 想键盘直接提交用 Ctrl / ⌘ + Enter
    $('#diy_api_url').on('keydown', function (e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); api(); }
    });
    $('#search_key_word').on('keydown', function (e) {
        if (e.key === 'Enter') { e.preventDefault(); search(); }
    });
    $('#push_url').on('keydown', function (e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); push(); }
    });

    // 搜索历史：初始渲染 + 清空
    renderHistory();
    $('#hisClear').on('click', function () {
        try { localStorage.removeItem(HIS_KEY); } catch (e) { }
        renderHistory();
    });
});
