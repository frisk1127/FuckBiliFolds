package io.github.frisk1127.bilifolds;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BiliFoldsHook implements IXposedHookLoadPackage {
    private static final String TAG = "BiliFolds";
    private static final String BUILD_TAG = "build-" + BuildConfig.GIT_SHA;
    private static final List<String> TARGET_PACKAGES = Arrays.asList(
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "com.bilibili.app.blue",
            "com.bilibili.app.dev"
    );

    private static final String FOLD_TAG_TEXT = "\u5df2\u5c55\u5f00";
    private static final String FOLD_TAG_TEXT_COLOR_DAY = "#FF888888";
    private static final String FOLD_TAG_TEXT_COLOR_NIGHT = "#FFAAAAAA";
    private static final String FOLD_TAG_BG_DAY = "#00000000";
    private static final String FOLD_TAG_BG_NIGHT = "#00000000";
    private static final String FOLD_TAG_JUMP = "";

    private static final ConcurrentHashMap<Long, ArrayList<Object>> FOLD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ArrayList<Object>> FOLD_CACHE_BY_OFFSET = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ArrayList<Object>> FOLD_CACHE_BY_SUBJECT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> OFFSET_TO_ROOT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> AUTO_FETCHING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> FOLDED_IDS = new ConcurrentHashMap<>();
    private static final String FOLD_MARK_TEXT = "\u5df2\u5c55\u5f00";
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_FOLD_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> OFFSET_INSERT_INDEX = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> SUBJECT_HAS_FOLD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> SUBJECT_EXPANDED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_MARK_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_MARK_DETAIL_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_MARK_POS_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_MARK_PLACE_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_MARK_MORE_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_MARK_LAYOUT_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_LIKE_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_CLICK_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> DEBUG_H0_CLICK_LOGGED = new ConcurrentHashMap<>();
    private static final Set<Object> AUTO_EXPAND_ZIP = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());

    private static final String AUTO_EXPAND_TEXT = "\u5df2\u81ea\u52a8\u5c55\u5f00\u6298\u53e0\u8bc4\u8bba";

    private static final ConcurrentHashMap<String, AtomicInteger> FOOTER_RETRY_COUNT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> FOOTER_RETRY_PENDING = new ConcurrentHashMap<>();
    private static final int FOOTER_RETRY_LIMIT = 12;

    private static volatile WeakReference<Object> LAST_SUBJECT_ID = new WeakReference<>(null);
    private static volatile String LAST_SUBJECT_KEY = null;
    private static volatile String LAST_EXTRA = null;
    private static volatile Object LAST_SORT_MODE = null;
    private static volatile WeakReference<Object> LAST_COMMENT_ADAPTER = new WeakReference<>(null);
    private static volatile Field COMMENT_ID_FIELD = null;
    private static final ThreadLocal<Boolean> RESUBMITTING = new ThreadLocal<>();
    private static volatile android.os.Handler MAIN_HANDLER = null;
    private static volatile ClassLoader APP_CL = null;

    private static volatile Object FOLD_TAG_TEMPLATE = null;
    private static volatile ClassLoader FOLD_TAG_CL = null;
    private static volatile Field COMMENT_TIME_FIELD = null;
    private static volatile boolean COMMENT_TIME_IS_MILLIS = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }
        log("active " + BUILD_TAG + " pkg=" + lpparam.packageName);
        APP_CL = lpparam.classLoader;
        safeHook("hookReplyControl", new Runnable() { @Override public void run() { hookReplyControl(APP_CL); } });
        safeHook("hookCommentItemTags", new Runnable() { @Override public void run() { hookCommentItemTags(APP_CL); } });
        safeHook("hookCommentItemFoldFlags", new Runnable() { @Override public void run() { hookCommentItemFoldFlags(APP_CL); } });
        safeHook("hookActionDispatcher", new Runnable() { @Override public void run() { hookActionDispatcher(APP_CL); } });
        safeHook("hookZipCardView", new Runnable() { @Override public void run() { hookZipCardView(APP_CL); } });
        safeHook("hookZipDataSource", new Runnable() { @Override public void run() { hookZipDataSource(APP_CL); } });
        safeHook("hookDetailListDataSource", new Runnable() { @Override public void run() { hookDetailListDataSource(APP_CL); } });
        safeHook("hookCommentListAdapter", new Runnable() { @Override public void run() { hookCommentListAdapter(APP_CL); } });
        safeHook("hookH0ClickTrace", new Runnable() { @Override public void run() { hookH0ClickTrace(APP_CL); } });
    }

    private static void safeHook(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log(name + " failed: " + Log.getStackTraceString(t));
        }
    }

    private static void hookReplyControl(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.ReplyControl",
                cl
        );
        if (c == null) {
            log("ReplyControl class not found");
            return;
        }
        XposedHelpers.findAndHookMethod(c, "getIsFoldedReply", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return false;
            }
        });
        XposedBridge.hookAllMethods(c, "getBlocked", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isReplyControlMarked(param.thisObject)) {
                    param.setResult(false);
                }
            }
        });
        XposedBridge.hookAllMethods(c, "getInvisible", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isReplyControlMarked(param.thisObject)) {
                    param.setResult(false);
                }
            }
        });
        XposedBridge.hookAllMethods(c, "getAction", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isReplyControlMarked(param.thisObject)) {
                    param.setResult(Long.MAX_VALUE);
                }
            }
        });
    }

    private static void hookZipDataSource(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.source.v1.ZipDataSourceV1",
                cl
        );
        if (c == null) {
            log("ZipDataSourceV1 class not found");
            return;
        }
        XposedBridge.hookAllMethods(c, "a", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 2) return;
                if (param.args[0] != null) {
                    LAST_SUBJECT_ID = new WeakReference<>(param.args[0]);
                    updateSubjectKey(param.args[0]);
                }
                Object offset = param.args[1];
                if (offset instanceof String) {
                    String extra = param.args.length >= 3 ? safeToString(param.args[2]) : "";
                    if ("null".equals(extra)) extra = "";
                    if (extra != null && !extra.isEmpty()) {
                        LAST_EXTRA = extra;
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 2) return;
                Object offset = param.args[1];
                if (offset instanceof String) {
                    cacheFoldListResult("ZipDataSourceV1.a.result", (String) offset, param.getResult());
                }
            }
        });
    }

    private static void hookDetailListDataSource(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.source.v1.DetailListDataSourceV1",
                cl
        );
        if (c == null) {
            log("DetailListDataSourceV1 class not found");
            return;
        }
        XposedBridge.hookAllMethods(c, "a", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 6) return;
                if (param.args[0] != null) {
                    LAST_SUBJECT_ID = new WeakReference<>(param.args[0]);
                    updateSubjectKey(param.args[0]);
                }
                String extra = safeToString(param.args[5]);
                if ("null".equals(extra)) extra = "";
                if (extra != null && !extra.isEmpty()) {
                    LAST_EXTRA = extra;
                }
                if (param.args.length > 4 && param.args[4] != null) {
                    LAST_SORT_MODE = param.args[4];
                }
            }
        });
    }

    private static void hookCommentListAdapter(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.ui.adapter.CommentListAdapter",
                cl
        );
        if (c == null) {
            log("CommentListAdapter class not found");
            return;
        }
        XposedBridge.hookAllConstructors(c, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object adapter = param.thisObject;
                Object last = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
                if (last == null || last != adapter) {
                    clearFoldCaches();
                }
                LAST_COMMENT_ADAPTER = new WeakReference<>(adapter);
            }
        });
        XposedBridge.hookAllMethods(c, "b1", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(RESUBMITTING.get())) return;
                if (param.args == null || param.args.length == 0) return;
                Object listObj = param.args[0];
                if (!(listObj instanceof List)) return;
                List<?> list = (List<?>) listObj;
                prefetchFoldList(list);
                List<?> replaced = replaceZipCardsInList(list, "CommentListAdapter.b1");
                if (replaced != null) {
                    param.args[0] = replaced;
                }
            }
        });
        hookCommentViewHolderBind(c, cl);
    }

    private static void hookH0ClickTrace(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.ui.holder.h0",
                cl
        );
        if (c == null) {
            log("h0 class not found");
            return;
        }
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            Class<?>[] pts = m.getParameterTypes();
            if (pts == null) continue;
            boolean maybeClick = false;
            if (pts.length == 1 && View.class.isAssignableFrom(pts[0])) {
                maybeClick = true;
            } else if (pts.length == 2 && c.isAssignableFrom(pts[0]) && View.class.isAssignableFrom(pts[1])) {
                maybeClick = true;
            }
            if (!maybeClick) continue;
            final java.lang.reflect.Method method = m;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    long id = 0L;
                    Object holder = param.thisObject;
                    if (holder == null && param.args != null && param.args.length > 0 && c.isInstance(param.args[0])) {
                        holder = param.args[0];
                    }
                    if (holder != null) {
                        id = getAdditionalLong(holder, "BiliFoldsCommentId");
                        if (id == 0L) {
                            Object item = getAdditionalObject(holder, "BiliFoldsCommentItem");
                            if (isCommentItem(item)) {
                                long cid = getId(item);
                                if (cid != 0L) id = cid;
                            }
                        }
                    }
                    if (id == 0L && param.args != null) {
                        for (Object a : param.args) {
                            if (!(a instanceof View)) continue;
                            id = getAdditionalLong(a, "BiliFoldsCommentId");
                            if (id != 0L) break;
                            Object item = getAdditionalObject(a, "BiliFoldsCommentItem");
                            if (isCommentItem(item)) {
                                long cid = getId(item);
                                if (cid != 0L) {
                                    id = cid;
                                    break;
                                }
                            }
                        }
                    }
                    if (id == 0L) return;
                    if (!Boolean.TRUE.equals(FOLDED_IDS.get(id))) return;
                    String key = id + ":" + method.getName();
                    if (DEBUG_H0_CLICK_LOGGED.putIfAbsent(key, Boolean.TRUE) != null) return;
                    String viewInfo = "";
                    if (param.args != null) {
                        for (Object a : param.args) {
                            if (!(a instanceof View)) continue;
                            View v = (View) a;
                            String idName = "";
                            int vid = v.getId();
                            if (vid != View.NO_ID) {
                                try {
                                    idName = v.getResources().getResourceEntryName(vid);
                                } catch (Throwable ignored) {
                                }
                            }
                            CharSequence desc = v.getContentDescription();
                            viewInfo = " view=" + v.getClass().getSimpleName()
                                    + " idName=" + idName
                                    + " desc=" + (desc == null ? "" : desc);
                            break;
                        }
                    }
                    log("h0.click folded id=" + id + " method=" + method.getName() + viewInfo);
                }
            });
        }
    }

    private static void hookCommentViewHolderBind(Class<?> adapterCls, ClassLoader cl) {
        Class<?> vhCls = XposedHelpers.findClassIfExists(
                "androidx.recyclerview.widget.RecyclerView$ViewHolder",
                cl
        );
        if (vhCls == null) return;
        java.lang.reflect.Method[] methods = adapterCls.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            Class<?>[] pts = m.getParameterTypes();
            if (pts == null || pts.length < 2) continue;
            if (!vhCls.isAssignableFrom(pts[0])) continue;
            if (pts[1] != int.class && pts[1] != Integer.class) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object holder = param.args[0];
                        int pos = (param.args[1] instanceof Integer) ? (Integer) param.args[1] : -1;
                        if (holder == null || pos < 0) return;
                        Object itemViewObj = XposedHelpers.getObjectField(holder, "itemView");
                        if (!(itemViewObj instanceof View)) return;
                        Object item = getAdapterItemAt(adapterCls, pos);
                        if (!isCommentItem(item)) {
                            try {
                                XposedHelpers.removeAdditionalInstanceField(holder, "BiliFoldsCommentId");
                                XposedHelpers.removeAdditionalInstanceField(holder, "BiliFoldsCommentItem");
                            } catch (Throwable ignored) {
                            }
                            try {
                                XposedHelpers.removeAdditionalInstanceField(itemViewObj, "BiliFoldsCommentId");
                                XposedHelpers.removeAdditionalInstanceField(itemViewObj, "BiliFoldsCommentItem");
                            } catch (Throwable ignored) {
                            }
                            clearFoldMarkFromView((View) itemViewObj);
                            return;
                        }
                        long id = getId(item);
                        if (id == 0) {
                            try {
                                XposedHelpers.removeAdditionalInstanceField(holder, "BiliFoldsCommentId");
                                XposedHelpers.removeAdditionalInstanceField(holder, "BiliFoldsCommentItem");
                            } catch (Throwable ignored) {
                            }
                            try {
                                XposedHelpers.removeAdditionalInstanceField(itemViewObj, "BiliFoldsCommentId");
                                XposedHelpers.removeAdditionalInstanceField(itemViewObj, "BiliFoldsCommentItem");
                            } catch (Throwable ignored) {
                            }
                            clearFoldMarkFromView((View) itemViewObj);
                            return;
                        }
                        try {
                            XposedHelpers.setAdditionalInstanceField(holder, "BiliFoldsCommentId", id);
                            XposedHelpers.setAdditionalInstanceField(holder, "BiliFoldsCommentItem", item);
                        } catch (Throwable ignored) {
                        }
                        try {
                            XposedHelpers.setAdditionalInstanceField(itemViewObj, "BiliFoldsCommentId", id);
                            XposedHelpers.setAdditionalInstanceField(itemViewObj, "BiliFoldsCommentItem", item);
                        } catch (Throwable ignored) {
                        }
                        ensureFoldedActionsClickable((View) itemViewObj, id, item);
                        if (!Boolean.TRUE.equals(FOLDED_IDS.get(id))) {
                            clearFoldMarkFromView((View) itemViewObj);
                            return;
                        }
                        if (!applyFoldMarkToHolder(holder, id)) {
                            applyFoldMarkToView((View) itemViewObj, id);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    private static Object getAdapterItemAt(Class<?> adapterCls, int pos) {
        Object adapter = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
        if (adapter == null) return null;
        Object differ;
        try {
            differ = XposedHelpers.getObjectField(adapter, "c");
        } catch (Throwable ignored) {
            return null;
        }
        if (differ == null) return null;
        Object listObj;
        try {
            listObj = XposedHelpers.callMethod(differ, "a");
        } catch (Throwable ignored) {
            return null;
        }
        if (!(listObj instanceof List)) return null;
        List<?> list = (List<?>) listObj;
        if (pos < 0 || pos >= list.size()) return null;
        return list.get(pos);
    }

    private static boolean applyFoldMarkToHolder(Object holder, long id) {
        if (holder == null) return false;
        String cls = holder.getClass().getName();
        if (!"com.bilibili.app.comment3.ui.holder.h0".equals(cls)) {
            return false;
        }
        try {
            Object binding = XposedHelpers.callMethod(holder, "x1");
            if (binding == null) return false;
            Object itemViewObj = XposedHelpers.getObjectField(holder, "itemView");
            View itemView = (itemViewObj instanceof View) ? (View) itemViewObj : null;
            return applyFoldMarkToCommentActionBar(binding, id, itemView);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean applyFoldMarkToCommentActionBar(Object binding, long id, View itemView) {
        View root = getBindingRoot(binding);
        if (!(root instanceof ViewGroup)) return false;
        ViewGroup actionRow = (ViewGroup) root;
        ViewGroup markRoot = actionRow;
        removeFoldMark(markRoot);
        TextView tvI = getBindingTextView(binding, "i", "f400674i");
        TextView tvH = getBindingTextView(binding, "h", "f400673h");
        TextView tvC = getBindingTextView(binding, "c", "f400668c");
        TextView anchor = null;
        if (tvI != null && containsText(tvI, "\u67e5\u770b\u5bf9\u8bdd") && isVisibleView(tvI)) {
            TextView mark = newFoldMark(markRoot, tvI);
            setMarkId(mark, id);
            logActionRowLayoutOnce(id, actionRow);
            if (addOverlayMarkRightOf(actionRow, tvI, mark)) {
                logMarkOnce(id, "mark overlay right of viewConv(h0)");
                return true;
            }
            View more = findMoreActionView(actionRow);
            View like = findLikeActionView(actionRow);
            if (more != null && addOverlayMarkLeftOf(actionRow, more, mark)) {
                logMarkMoreOnce(id, actionRow, more, tvI, "h0.viewConv");
                logMarkOnce(id, "mark overlay left of more viewConv(h0)");
                return true;
            }
            if (like != null && addOverlayMarkLeftOf(actionRow, like, mark)) {
                logMarkMoreOnce(id, actionRow, like, tvI, "h0.like");
                logMarkOnce(id, "mark overlay left of like viewConv(h0)");
                return true;
            }
            if (addMarkAfterAnchor(actionRow, tvI, mark)) {
                logMarkOnce(id, "mark add viewConv(h0)");
                return true;
            }
            if (addOverlayMark(markRoot, tvI, mark)) {
                logMarkOnce(id, "mark overlay viewConv(h0)");
                return true;
            }
            return false;
        } else if (tvH != null && containsText(tvH, "\u67e5\u770b\u5bf9\u8bdd") && isVisibleView(tvH)) {
            TextView mark = newFoldMark(markRoot, tvH);
            setMarkId(mark, id);
            logActionRowLayoutOnce(id, actionRow);
            if (addOverlayMarkRightOf(actionRow, tvH, mark)) {
                logMarkOnce(id, "mark overlay right of viewConv(h0)");
                return true;
            }
            View more = findMoreActionView(actionRow);
            View like = findLikeActionView(actionRow);
            if (more != null && addOverlayMarkLeftOf(actionRow, more, mark)) {
                logMarkMoreOnce(id, actionRow, more, tvH, "h0.viewConv");
                logMarkOnce(id, "mark overlay left of more viewConv(h0)");
                return true;
            }
            if (like != null && addOverlayMarkLeftOf(actionRow, like, mark)) {
                logMarkMoreOnce(id, actionRow, like, tvH, "h0.like");
                logMarkOnce(id, "mark overlay left of like viewConv(h0)");
                return true;
            }
            if (addMarkAfterAnchor(actionRow, tvH, mark)) {
                logMarkOnce(id, "mark add viewConv(h0)");
                return true;
            }
            if (addOverlayMark(markRoot, tvH, mark)) {
                logMarkOnce(id, "mark overlay viewConv(h0)");
                return true;
            }
            return false;
        }
        if (tvI != null && hasText(tvI)) {
            anchor = tvI;
        } else if (tvH != null && hasText(tvH)) {
            anchor = tvH;
        } else if (tvC != null && hasText(tvC)) {
            anchor = tvC;
        } else if (tvI != null) {
            anchor = tvI;
        } else if (tvH != null) {
            anchor = tvH;
        } else if (tvC != null) {
            anchor = tvC;
        }
        if (anchor == null) {
            logMarkOnce(id, "mark skip: no anchor (h0)");
            logActionRowOnce(id, actionRow);
            return false;
        }
        logMarkPlaceOnce(id, "anchor(h0) cls=" + anchor.getClass().getSimpleName()
                + " idx=" + actionRow.indexOfChild(anchor)
                + " text=" + (anchor instanceof TextView ? ((TextView) anchor).getText() : "")
                + " rowCls=" + actionRow.getClass().getSimpleName()
                + " child=" + actionRow.getChildCount());
        if (hasFoldMark(markRoot)) return true;
        TextView base = (anchor instanceof TextView) ? (TextView) anchor : findFirstTextView(actionRow);
        TextView mark = newFoldMark(markRoot, base);
        setMarkId(mark, id);
        View comment = findCommentActionView(actionRow);
        if (comment != null && addOverlayMarkRightOf(actionRow, comment, mark)) {
            logMarkMoreOnce(id, actionRow, comment, base, "h0.comment");
            logMarkOnce(id, "mark overlay right of comment(h0)");
            return true;
        }
        View more = findMoreActionView(actionRow);
        View like = findLikeActionView(actionRow);
        if (more != null && addOverlayMarkLeftOf(actionRow, more, mark)) {
            logMarkMoreOnce(id, actionRow, more, base, "h0.anchor");
            logMarkOnce(id, "mark overlay left of more(h0)");
            return true;
        }
        if (like != null && addOverlayMarkLeftOf(actionRow, like, mark)) {
            logMarkMoreOnce(id, actionRow, like, base, "h0.like");
            logMarkOnce(id, "mark overlay left of like(h0)");
            return true;
        }
        if (addMarkAfterAnchor(actionRow, anchor, mark)) {
            logMarkOnce(id, "mark add anchor(h0)");
            return true;
        }
        if (addOverlayMark(markRoot, anchor, mark)) {
            logMarkOnce(id, "mark overlay anchor(h0)");
            return true;
        }
        return false;
    }

    private static boolean applyFoldMarkToView(View root, long id) {
        if (root == null) return false;
        ViewGroup actionRow = findActionRow(root);
        if (actionRow == null) {
            logMarkOnce(id, "mark skip: no action row (fallback)");
            return false;
        }
        ViewGroup markRoot = actionRow;
        removeFoldMark(markRoot);
        TextView viewConv = findTextViewContains(actionRow, "\u67e5\u770b\u5bf9\u8bdd");
        if (viewConv != null && isVisibleView(viewConv)) {
            TextView mark = newFoldMark(markRoot, viewConv);
            setMarkId(mark, id);
            logActionRowLayoutOnce(id, actionRow);
            if (addOverlayMarkRightOf(actionRow, viewConv, mark)) {
                logMarkOnce(id, "mark overlay right of viewConv (fallback)");
                return true;
            }
            View more = findMoreActionView(actionRow);
            View like = findLikeActionView(actionRow);
            if (more != null && addOverlayMarkLeftOf(actionRow, more, mark)) {
                logMarkMoreOnce(id, actionRow, more, viewConv, "fallback.viewConv");
                logMarkOnce(id, "mark overlay left of more viewConv (fallback)");
                return true;
            }
            if (like != null && addOverlayMarkLeftOf(actionRow, like, mark)) {
                logMarkMoreOnce(id, actionRow, like, viewConv, "fallback.like");
                logMarkOnce(id, "mark overlay left of like viewConv (fallback)");
                return true;
            }
            if (addMarkAfterAnchor(actionRow, viewConv, mark)) {
                logMarkOnce(id, "mark add viewConv (fallback)");
                return true;
            }
            if (addOverlayMark(markRoot, viewConv, mark)) {
                logMarkOnce(id, "mark overlay viewConv (fallback)");
                return true;
            }
            return true;
        }
        View anchor = findActionAnchor(actionRow);
        if (anchor == null) {
            logMarkOnce(id, "mark skip: no anchor (fallback)");
            logActionRowOnce(id, actionRow);
            return false;
        }
        logMarkPlaceOnce(id, "anchor(fallback) cls=" + anchor.getClass().getSimpleName()
                + " idx=" + actionRow.indexOfChild(anchor)
                + " text=" + (anchor instanceof TextView ? ((TextView) anchor).getText() : "")
                + " rowCls=" + actionRow.getClass().getSimpleName()
                + " child=" + actionRow.getChildCount());
        if (hasFoldMark(markRoot)) return true;
        TextView base = (anchor instanceof TextView) ? (TextView) anchor : findFirstTextView(actionRow);
        TextView mark = newFoldMark(markRoot, base);
        setMarkId(mark, id);
        View comment = findCommentActionView(actionRow);
        if (comment != null && addOverlayMarkRightOf(actionRow, comment, mark)) {
            logMarkMoreOnce(id, actionRow, comment, base, "fallback.comment");
            logMarkOnce(id, "mark overlay right of comment (fallback)");
            return true;
        }
        View more = findMoreActionView(actionRow);
        View like = findLikeActionView(actionRow);
        if (more != null && addOverlayMarkLeftOf(actionRow, more, mark)) {
            logMarkMoreOnce(id, actionRow, more, base, "fallback.anchor");
            logMarkOnce(id, "mark overlay left of more (fallback)");
            return true;
        }
        if (like != null && addOverlayMarkLeftOf(actionRow, like, mark)) {
            logMarkMoreOnce(id, actionRow, like, base, "fallback.like");
            logMarkOnce(id, "mark overlay left of like (fallback)");
            return true;
        }
        if (addMarkAfterAnchor(actionRow, anchor, mark)) {
            logMarkOnce(id, "mark add anchor (fallback)");
            return true;
        }
        if (addOverlayMark(markRoot, anchor, mark)) {
            logMarkOnce(id, "mark overlay anchor (fallback)");
            return true;
        }
        return false;
    }

    private static void clearFoldMarkFromView(View root) {
        if (root == null) return;
        removeFoldMark(root);
        stripFoldSuffixFromView(root);
    }

    private static TextView newFoldMark(ViewGroup group, TextView base) {
        TextView mark = new TextView(group.getContext());
        mark.setText(FOLD_MARK_TEXT);
        if (base != null) {
            mark.setTextColor(base.getCurrentTextColor());
            mark.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, base.getTextSize());
        }
        mark.setSingleLine(true);
        mark.setTag("BiliFoldsMark");
        mark.setClickable(false);
        mark.setFocusable(false);
        mark.setVisibility(View.VISIBLE);
        mark.setAlpha(1.0f);
        return mark;
    }

    private static void setMarkId(TextView mark, long id) {
        if (mark == null || id == 0L) return;
        try {
            XposedHelpers.setAdditionalInstanceField(mark, "BiliFoldsId", id);
        } catch (Throwable ignored) {
        }
    }

    private static void appendFoldToText(TextView tv) {
        if (tv == null) return;
        CharSequence cur = tv.getText();
        String s = cur == null ? "" : cur.toString();
        if (!s.contains(FOLD_MARK_TEXT)) {
            tv.setText(s + " \u00b7 " + FOLD_MARK_TEXT);
        }
    }

    private static void stripFoldSuffix(TextView tv) {
        if (tv == null) return;
        CharSequence cur = tv.getText();
        if (cur == null) return;
        String s = cur.toString();
        if (!(s.contains(FOLD_MARK_TEXT) || s.contains("\u5df2\u5c55\u5f00"))) return;
        String cleaned = s;
        cleaned = cleaned.replace(" \u00b7 " + FOLD_MARK_TEXT, "").replace(FOLD_MARK_TEXT, "");
        cleaned = cleaned.replace(" \u00b7 \u5df2\u5c55\u5f00", "").replace("\u5df2\u5c55\u5f00", "");
        String result = cleaned.trim();
        tv.setText(result);
        if (result.isEmpty()) {
            tv.setVisibility(View.GONE);
        }
    }

    private static void stripFoldSuffixFromView(View root) {
        if (root == null) return;
        if (root instanceof TextView) {
            stripFoldSuffix((TextView) root);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                stripFoldSuffixFromView(group.getChildAt(i));
            }
        }
    }

    private static void collectTextViews(View root, List<TextView> out) {
        if (root == null || out == null) return;
        if (root instanceof TextView) {
            out.add((TextView) root);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTextViews(group.getChildAt(i), out);
            }
        }
    }


    private static void logMarkOnce(long id, String msg) {
        if (id == 0L || msg == null) return;
        if (DEBUG_MARK_LOGGED.putIfAbsent(id, Boolean.TRUE) != null) return;
        log("mark id=" + id + " " + msg);
    }

    private static void logMarkPlaceOnce(long id, String msg) {
        if (id == 0L || msg == null) return;
        if (DEBUG_MARK_PLACE_LOGGED.putIfAbsent(id, Boolean.TRUE) != null) return;
        log("mark.place id=" + id + " " + msg);
    }

    private static void logMarkMoreOnce(long id, ViewGroup row, View more, TextView base, String tag) {
        if (id == 0L || row == null || more == null) return;
        if (DEBUG_MARK_MORE_LOGGED.putIfAbsent(id, Boolean.TRUE) != null) return;
        int idx = row.indexOfChild(more);
        String idName = "";
        try {
            if (more.getId() != View.NO_ID) {
                idName = more.getResources().getResourceEntryName(more.getId());
            }
        } catch (Throwable ignored) {
        }
        CharSequence desc = more.getContentDescription();
        CharSequence text = (more instanceof TextView) ? ((TextView) more).getText() : null;
        log("mark.more id=" + id
                + " tag=" + tag
                + " rowCls=" + row.getClass().getSimpleName()
                + " rowChild=" + row.getChildCount()
                + " moreCls=" + more.getClass().getSimpleName()
                + " moreIdx=" + idx
                + " moreId=" + idName
                + " moreDesc=" + desc
                + " moreText=" + text
                + " baseText=" + (base != null ? base.getText() : ""));
    }

    private static void logActionRowOnce(long id, ViewGroup group) {
        if (id == 0L || group == null) return;
        if (DEBUG_MARK_DETAIL_LOGGED.putIfAbsent(id, Boolean.TRUE) != null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("mark row id=").append(id)
                .append(" cls=").append(group.getClass().getName())
                .append(" child=").append(group.getChildCount());
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v == null) continue;
            sb.append(" [").append(i).append(":").append(v.getClass().getSimpleName());
            CharSequence text = (v instanceof TextView) ? ((TextView) v).getText() : null;
            CharSequence desc = v.getContentDescription();
            if (text != null) sb.append(" t=").append(text);
            if (desc != null) sb.append(" d=").append(desc);
            if (v.isClickable()) sb.append(" c=1");
            sb.append("]");
        }
        log(sb.toString());
    }

    private static void logActionRowLayoutOnce(final long id, final ViewGroup group) {
        if (id == 0L || group == null) return;
        if (DEBUG_MARK_LAYOUT_LOGGED.putIfAbsent(id, Boolean.TRUE) != null) return;
        group.post(new Runnable() {
            @Override
            public void run() {
                try {
                    int[] gp = new int[2];
                    group.getLocationOnScreen(gp);
                    StringBuilder sb = new StringBuilder();
                    sb.append("mark.layout id=").append(id)
                            .append(" rowCls=").append(group.getClass().getName())
                            .append(" rowW=").append(group.getWidth())
                            .append(" rowH=").append(group.getHeight())
                            .append(" child=").append(group.getChildCount());
                    for (int i = 0; i < group.getChildCount(); i++) {
                        View v = group.getChildAt(i);
                        if (v == null) continue;
                        int[] vp = new int[2];
                        v.getLocationOnScreen(vp);
                        int x = vp[0] - gp[0];
                        int y = vp[1] - gp[1];
                        String idName = "";
                        try {
                            if (v.getId() != View.NO_ID) {
                                idName = v.getResources().getResourceEntryName(v.getId());
                            }
                        } catch (Throwable ignored) {
                        }
                        CharSequence text = (v instanceof TextView) ? ((TextView) v).getText() : null;
                        CharSequence desc = v.getContentDescription();
                        sb.append(" [").append(i)
                                .append(":").append(v.getClass().getSimpleName())
                                .append(" id=").append(idName)
                                .append(" vis=").append(v.getVisibility())
                                .append(" x=").append(x)
                                .append(" y=").append(y)
                                .append(" w=").append(v.getWidth())
                                .append(" h=").append(v.getHeight());
                        if (text != null) sb.append(" t=").append(text);
                        if (desc != null) sb.append(" d=").append(desc);
                        sb.append("]");
                    }
                    log(sb.toString());
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static View findActionAnchor(ViewGroup actionRow) {
        TextView viewConv = findTextViewContains(actionRow, "\u67e5\u770b\u5bf9\u8bdd");
        if (viewConv != null) {
            stripFoldSuffix(viewConv);
            return viewConv;
        }
        TextView reply = findTextViewContains(actionRow, "\u56de\u590d");
        if (reply != null) return reply;
        View byDesc = findViewByDescContains(actionRow, new String[]{
                "\u56de\u590d",
                "\u8bc4\u8bba",
                "\u70b9\u8d5e",
                "\u70b9\u8e29",
                "\u66f4\u591a"
        });
        if (byDesc != null) return byDesc;
        return null;
    }

    private static View getBindingRoot(Object binding) {
        if (binding == null) return null;
        try {
            Object rootObj = XposedHelpers.callMethod(binding, "getRoot");
            if (rootObj instanceof View) return (View) rootObj;
        } catch (Throwable ignored) {
        }
        try {
            Object rootObj = XposedHelpers.getObjectField(binding, "a");
            if (rootObj instanceof View) return (View) rootObj;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static TextView getBindingTextView(Object binding, String... names) {
        if (binding == null || names == null) return null;
        for (String name : names) {
            if (name == null) continue;
            try {
                Object v = XposedHelpers.getObjectField(binding, name);
                if (v instanceof TextView) return (TextView) v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean containsText(TextView tv, String text) {
        if (tv == null || text == null) return false;
        CharSequence t = tv.getText();
        return t != null && t.toString().contains(text);
    }

    private static boolean hasText(TextView tv) {
        if (tv == null) return false;
        CharSequence t = tv.getText();
        return t != null && t.toString().trim().length() > 0;
    }

    private static boolean isVisibleView(View v) {
        if (v == null) return false;
        return v.getVisibility() == View.VISIBLE;
    }

    private static boolean addMarkAfterAnchor(ViewGroup group, View anchor, TextView mark) {
        if (group == null || anchor == null || mark == null) return false;
        if (group instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            if (anchor.getId() == View.NO_ID) {
                anchor.setId(View.generateViewId());
            }
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            lp.startToEnd = anchor.getId();
            lp.topToTop = anchor.getId();
            lp.bottomToBottom = anchor.getId();
            if (anchor instanceof TextView) {
                lp.baselineToBaseline = anchor.getId();
            }
            lp.setMarginStart(dp(group.getContext(), 6));
            mark.setLayoutParams(lp);
            mark.setId(View.generateViewId());
            group.addView(mark);
            return true;
        }
        int index = group.indexOfChild(anchor);
        if (index < 0) index = group.getChildCount();
        group.addView(mark, Math.min(index + 1, group.getChildCount()));
        return true;
    }

    private static boolean addMarkAtRowEnd(ViewGroup group, TextView base, TextView mark) {
        if (group == null || mark == null) return false;
        if (group instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            if (group.getId() == View.NO_ID) {
                group.setId(View.generateViewId());
            }
            if (base != null && base.getId() == View.NO_ID) {
                base.setId(View.generateViewId());
            }
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            lp.endToEnd = group.getId();
            lp.topToTop = group.getId();
            lp.bottomToBottom = group.getId();
            if (base != null) {
                lp.baselineToBaseline = base.getId();
            }
            lp.setMarginEnd(dp(group.getContext(), 6));
            mark.setLayoutParams(lp);
            mark.setId(View.generateViewId());
            group.addView(mark);
            return true;
        }
        if (group instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout ll = (android.widget.LinearLayout) group;
            if (ll.getOrientation() == android.widget.LinearLayout.HORIZONTAL) {
                android.widget.LinearLayout.LayoutParams lp =
                        new android.widget.LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                lp.leftMargin = dp(group.getContext(), 6);
                mark.setLayoutParams(lp);
                group.addView(mark);
                return true;
            }
        }
        return false;
    }

    private static boolean addMarkBeforeView(ViewGroup group, View before, TextView mark, TextView base) {
        if (group == null || before == null || mark == null) return false;
        if (group instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            if (group.getId() == View.NO_ID) {
                group.setId(View.generateViewId());
            }
            if (before.getId() == View.NO_ID) {
                before.setId(View.generateViewId());
            }
            if (base != null && base.getId() == View.NO_ID) {
                base.setId(View.generateViewId());
            }
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            lp.endToStart = before.getId();
            lp.topToTop = group.getId();
            lp.bottomToBottom = group.getId();
            if (base != null) {
                lp.baselineToBaseline = base.getId();
            }
            lp.setMarginEnd(dp(group.getContext(), 6));
            mark.setLayoutParams(lp);
            mark.setId(View.generateViewId());
            group.addView(mark);
            return true;
        }
        int index = group.indexOfChild(before);
        if (index >= 0) {
            mark.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            group.addView(mark, index);
            return true;
        }
        return false;
    }

    private static View findMoreActionView(ViewGroup root) {
        if (root == null) return null;
        View v = findViewByDescContains(root, new String[]{
                "\u66f4\u591a",
                "\u66f4\u591a\u64cd\u4f5c",
                "\u66f4\u591a\u8bc4\u8bba",
                "\u66f4\u591a\u5185\u5bb9"
        });
        if (v != null) return v;
        return findViewByIdNameContains(root, new String[]{"more", "menu", "overflow"});
    }

    private static View findCommentActionView(ViewGroup root) {
        if (root == null) return null;
        View v = findViewByDescContains(root, new String[]{
                "\u8bc4\u8bba",
                "\u56de\u590d",
                "\u5bf9\u8bdd"
        });
        if (v != null) return v;
        return findViewByIdNameContains(root, new String[]{"comment", "reply", "chat", "msg"});
    }

    private static View findLikeActionView(ViewGroup root) {
        if (root == null) return null;
        View v = findViewByDescContains(root, new String[]{
                "\u70b9\u8d5e",
                "\u8d5e",
                "\u559c\u6b22"
        });
        if (v != null) return v;
        return findViewByIdNameContains(root, new String[]{"like", "thumb", "up"});
    }

    private static void ensureFoldedActionsClickable(View root, long id, Object item) {
        if (root == null || id == 0L) return;
        ViewGroup actionRow = findActionRow(root);
        if (actionRow == null) return;
        ArrayList<View> targets = new ArrayList<>();
        collectViewsByIdNameContains(actionRow, new String[]{
                "like", "thumb", "up", "dislike", "down", "zan"
        }, targets);
        collectViewsByDescContains(actionRow, new String[]{
                "\u70b9\u8d5e",
                "\u8d5e",
                "\u559c\u6b22",
                "\u70b9\u8e29",
                "\u8e29",
                "\u4e0d\u559c\u6b22"
        }, targets);
        if (targets.isEmpty()) return;
        boolean folded = Boolean.TRUE.equals(FOLDED_IDS.get(id));
        int changed = 0;
        int total = 0;
        boolean logged = folded && DEBUG_LIKE_LOGGED.putIfAbsent(id, Boolean.TRUE) == null;
        for (View v : targets) {
            if (v == null) continue;
            total++;
            try {
                XposedHelpers.setAdditionalInstanceField(v, "BiliFoldsCommentId", id);
                if (item != null) {
                    XposedHelpers.setAdditionalInstanceField(v, "BiliFoldsCommentItem", item);
                }
            } catch (Throwable ignored) {
            }
            if (!folded) {
                continue;
            }
            if (!v.isEnabled()) {
                v.setEnabled(true);
                changed++;
            }
            if (!v.isClickable()) {
                v.setClickable(true);
                changed++;
            }
            if (v.getAlpha() < 1f) {
                v.setAlpha(1f);
            }
            wrapFoldedActionClick(v);
            if (logged) {
                String idName = "";
                int vid = v.getId();
                if (vid != View.NO_ID) {
                    try {
                        idName = v.getResources().getResourceEntryName(vid);
                    } catch (Throwable ignored) {
                    }
                }
                CharSequence desc = v.getContentDescription();
                Object tag = v.getTag();
                String tagCls = tag == null ? "null" : tag.getClass().getName();
                log("like.state id=" + id
                        + " view=" + v.getClass().getSimpleName()
                        + " idName=" + idName
                        + " desc=" + (desc == null ? "" : desc)
                        + " enabled=" + v.isEnabled()
                        + " clickable=" + v.isClickable()
                        + " hasClick=" + v.hasOnClickListeners()
                        + " tag=" + tagCls);
            }
        }
        if (folded && changed > 0 && !logged) {
            log("enable like for folded id=" + id + " total=" + total + " changed=" + changed);
        }
    }

    private static void wrapFoldedActionClick(View v) {
        if (v == null) return;
        try {
            Object existing = XposedHelpers.getAdditionalInstanceField(v, "BiliFoldsClickWrapped");
            if (existing instanceof Boolean && (Boolean) existing) return;
        } catch (Throwable ignored) {
        }
        try {
            Object listenerInfo = XposedHelpers.getObjectField(v, "mListenerInfo");
            if (listenerInfo == null) return;
            Object raw = XposedHelpers.getObjectField(listenerInfo, "mOnClickListener");
            if (!(raw instanceof View.OnClickListener)) return;
            final View.OnClickListener orig = (View.OnClickListener) raw;
            View.OnClickListener wrapper = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    long clickId = getAdditionalLong(view, "BiliFoldsCommentId");
                    Object item = getAdditionalObject(view, "BiliFoldsCommentItem");
                    if (clickId == 0L && isCommentItem(item)) {
                        long cid = getId(item);
                        if (cid != 0L) clickId = cid;
                    }
                    boolean folded = clickId != 0L && Boolean.TRUE.equals(FOLDED_IDS.get(clickId));
                    if (folded && isCommentItem(item)) {
                        forceUnfold(item);
                    }
                    if (folded && DEBUG_CLICK_LOGGED.putIfAbsent(clickId, Boolean.TRUE) == null) {
                        String listener = orig.getClass().getName();
                        log("like.click folded id=" + clickId
                                + " view=" + view.getClass().getSimpleName()
                                + " listener=" + listener);
                    }
                    orig.onClick(view);
                }
            };
            XposedHelpers.setObjectField(listenerInfo, "mOnClickListener", wrapper);
            XposedHelpers.setAdditionalInstanceField(v, "BiliFoldsClickWrapped", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    private static Object getAdditionalObject(Object obj, String key) {
        if (obj == null || key == null) return null;
        try {
            return XposedHelpers.getAdditionalInstanceField(obj, key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long getAdditionalLong(Object obj, String key) {
        if (obj == null || key == null) return 0L;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(obj, key);
            if (v instanceof Number) return ((Number) v).longValue();
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static View findViewByIdNameContains(View root, String[] keys) {
        if (root == null || keys == null) return null;
        int id = root.getId();
        if (id != View.NO_ID) {
            try {
                String name = root.getResources().getResourceEntryName(id);
                if (name != null) {
                    for (String k : keys) {
                        if (k != null && name.contains(k)) return root;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = findViewByIdNameContains(group.getChildAt(i), keys);
                if (v != null) return v;
            }
        }
        return null;
    }

    private static void collectViewsByIdNameContains(View root, String[] keys, ArrayList<View> out) {
        if (root == null || keys == null || out == null) return;
        int id = root.getId();
        if (id != View.NO_ID) {
            try {
                String name = root.getResources().getResourceEntryName(id);
                if (name != null) {
                    for (String k : keys) {
                        if (k != null && name.contains(k)) {
                            out.add(root);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectViewsByIdNameContains(group.getChildAt(i), keys, out);
            }
        }
    }

    private static void collectViewsByDescContains(View root, String[] keywords, ArrayList<View> out) {
        if (root == null || keywords == null || out == null) return;
        CharSequence desc = root.getContentDescription();
        if (desc != null) {
            String s = desc.toString();
            for (String k : keywords) {
                if (k != null && s.contains(k)) {
                    out.add(root);
                    break;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectViewsByDescContains(group.getChildAt(i), keywords, out);
            }
        }
    }

    private static boolean addOverlayMark(final ViewGroup host, final View anchor, final TextView mark) {
        if (host == null || anchor == null || mark == null) return false;
        try {
            Object old = XposedHelpers.getAdditionalInstanceField(host, "BiliFoldsOverlayMark");
            if (old instanceof View) {
                try {
                    android.view.ViewOverlay ov = host.getOverlay();
                    if (ov instanceof android.view.ViewGroupOverlay) {
                        ((android.view.ViewGroupOverlay) ov).remove((View) old);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        mark.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        mark.setId(View.generateViewId());
        host.post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.view.ViewOverlay ov = host.getOverlay();
                    if (ov instanceof android.view.ViewGroupOverlay) {
                        ((android.view.ViewGroupOverlay) ov).add(mark);
                    }
                } catch (Throwable ignored) {
                }
                try {
                    XposedHelpers.setAdditionalInstanceField(host, "BiliFoldsOverlayMark", mark);
                } catch (Throwable ignored) {
                }
                positionOverlayMark(host, anchor, mark);
            }
        });
        return true;
    }

    private static boolean addOverlayMarkLeftOf(final ViewGroup host, final View target, final TextView mark) {
        if (host == null || target == null || mark == null) return false;
        try {
            Object old = XposedHelpers.getAdditionalInstanceField(host, "BiliFoldsOverlayMark");
            if (old instanceof View) {
                try {
                    android.view.ViewOverlay ov = host.getOverlay();
                    if (ov instanceof android.view.ViewGroupOverlay) {
                        ((android.view.ViewGroupOverlay) ov).remove((View) old);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        mark.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        mark.setId(View.generateViewId());
        host.post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.view.ViewOverlay ov = host.getOverlay();
                    if (ov instanceof android.view.ViewGroupOverlay) {
                        ((android.view.ViewGroupOverlay) ov).add(mark);
                    }
                } catch (Throwable ignored) {
                }
                try {
                    XposedHelpers.setAdditionalInstanceField(host, "BiliFoldsOverlayMark", mark);
                } catch (Throwable ignored) {
                }
                positionOverlayLeftOf(host, target, mark);
            }
        });
        return true;
    }

    private static void positionOverlayLeftOf(ViewGroup host, View target, TextView mark) {
        if (host == null || target == null || mark == null) return;
        int hostW = host.getWidth();
        int hostH = host.getHeight();
        if (hostW <= 0 || hostH <= 0) return;
        int markW = mark.getWidth();
        int markH = mark.getHeight();
        if (markW <= 0 || markH <= 0) {
            int wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            mark.measure(wSpec, hSpec);
            markW = mark.getMeasuredWidth();
            markH = mark.getMeasuredHeight();
        }
        int[] hp = new int[2];
        int[] tp = new int[2];
        host.getLocationOnScreen(hp);
        target.getLocationOnScreen(tp);
        float targetX = tp[0] - hp[0];
        float targetY = tp[1] - hp[1];
        float x = targetX - markW - dp(host.getContext(), 6);
        if (x < 0) x = 0;
        float y = targetY + (target.getHeight() - markH) / 2.0f;
        if (y < 0) y = 0;
        if (y + markH > hostH) y = Math.max(0, hostH - markH);
        long id = 0L;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(mark, "BiliFoldsId");
            if (v instanceof Number) id = ((Number) v).longValue();
        } catch (Throwable ignored) {
        }
        int l = Math.round(x);
        int t = Math.round(y);
        if (DEBUG_MARK_POS_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
            log("mark.pos2 id=" + id
                    + " host=" + hostW + "x" + hostH
                    + " target=" + target.getWidth() + "x" + target.getHeight()
                    + " targetCls=" + target.getClass().getSimpleName()
                    + " mark=" + markW + "x" + markH
                    + " x=" + l + " y=" + t);
        }
        mark.layout(l, t, l + markW, t + markH);
    }

    private static boolean addOverlayMarkRightOf(final ViewGroup host, final View target, final TextView mark) {
        if (host == null || target == null || mark == null) return false;
        try {
            Object old = XposedHelpers.getAdditionalInstanceField(host, "BiliFoldsOverlayMark");
            if (old instanceof View) {
                try {
                    android.view.ViewOverlay ov = host.getOverlay();
                    if (ov instanceof android.view.ViewGroupOverlay) {
                        ((android.view.ViewGroupOverlay) ov).remove((View) old);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        mark.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        mark.setId(View.generateViewId());
        host.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    android.view.ViewOverlay ov = host.getOverlay();
                    if (ov instanceof android.view.ViewGroupOverlay) {
                        ((android.view.ViewGroupOverlay) ov).add(mark);
                    }
                } catch (Throwable ignored) {
                }
                try {
                    XposedHelpers.setAdditionalInstanceField(host, "BiliFoldsOverlayMark", mark);
                } catch (Throwable ignored) {
                }
                positionOverlayRightOf(host, target, mark);
            }
        }, 80);
        return true;
    }

    private static void positionOverlayRightOf(ViewGroup host, View target, TextView mark) {
        if (host == null || target == null || mark == null) return;
        View resolved = resolveAnchorView(target);
        if (resolved != null) target = resolved;
        int hostW = host.getWidth();
        int hostH = host.getHeight();
        if (hostW <= 0 || hostH <= 0) return;
        int markW = mark.getWidth();
        int markH = mark.getHeight();
        if (markW <= 0 || markH <= 0) {
            int wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            mark.measure(wSpec, hSpec);
            markW = mark.getMeasuredWidth();
            markH = mark.getMeasuredHeight();
        }
        int[] hp = new int[2];
        int[] tp = new int[2];
        host.getLocationOnScreen(hp);
        target.getLocationOnScreen(tp);
        float targetX = tp[0] - hp[0];
        float targetY = tp[1] - hp[1];
        float x = targetX + target.getWidth() + dp(host.getContext(), 6);
        if (x + markW > hostW) x = Math.max(0, hostW - markW - dp(host.getContext(), 6));
        float y = targetY + (target.getHeight() - markH) / 2.0f;
        if (y < 0) y = 0;
        if (y + markH > hostH) y = Math.max(0, hostH - markH);
        int l = Math.round(x);
        int t = Math.round(y);
        mark.layout(l, t, l + markW, t + markH);
    }

    private static void positionOverlayMark(ViewGroup host, View anchor, TextView mark) {
        if (host == null || anchor == null || mark == null) return;
        View resolvedAnchor = resolveAnchorView(anchor);
        if (resolvedAnchor != null) {
            anchor = resolvedAnchor;
        }
        if (anchor instanceof ViewGroup) {
            View rightMost = findRightmostChild(host, (ViewGroup) anchor);
            if (rightMost != null) {
                anchor = rightMost;
            }
        }
        int hostW = host.getWidth();
        int hostH = host.getHeight();
        if (hostW <= 0 || hostH <= 0) return;
        int markW = mark.getWidth();
        int markH = mark.getHeight();
        if (markW <= 0 || markH <= 0) {
            int wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            mark.measure(wSpec, hSpec);
            markW = mark.getMeasuredWidth();
            markH = mark.getMeasuredHeight();
        }
        int[] hp = new int[2];
        int[] ap = new int[2];
        host.getLocationOnScreen(hp);
        anchor.getLocationOnScreen(ap);
        float anchorX = ap[0] - hp[0];
        float anchorY = ap[1] - hp[1];
        float x = anchorX + anchor.getWidth() + dp(host.getContext(), 6);
        if (x + markW > hostW) {
            x = anchorX - markW - dp(host.getContext(), 6);
        }
        if (x < 0) x = 0;
        float y = anchorY + (anchor.getHeight() - markH) / 2.0f;
        if (y < 0) y = 0;
        if (y + markH > hostH) y = Math.max(0, hostH - markH);
        long id = 0L;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(mark, "BiliFoldsId");
            if (v instanceof Number) id = ((Number) v).longValue();
        } catch (Throwable ignored) {
        }
        int l = Math.round(x);
        int t = Math.round(y);
        if (DEBUG_MARK_POS_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
            log("mark.pos id=" + id
                    + " host=" + hostW + "x" + hostH
                    + " anchor=" + anchor.getWidth() + "x" + anchor.getHeight()
                    + " anchorCls=" + anchor.getClass().getSimpleName()
                    + " mark=" + markW + "x" + markH
                    + " x=" + l + " y=" + t);
        }
        mark.layout(l, t, l + markW, t + markH);
    }

    private static View resolveAnchorView(View anchor) {
        if (anchor == null) return null;
        if (anchor.getWidth() > 0 && anchor.getHeight() > 0) return anchor;
        View cur = anchor;
        while (cur != null && cur.getParent() instanceof View) {
            cur = (View) cur.getParent();
            if (cur.getWidth() > 0 && cur.getHeight() > 0) return cur;
        }
        return anchor;
    }

    private static View findRightmostChild(View host, ViewGroup root) {
        if (host == null || root == null) return null;
        int[] hp = new int[2];
        host.getLocationOnScreen(hp);
        RightMost best = new RightMost();
        findRightmostChildRec(host, root, hp, best);
        return best.view;
    }

    private static void findRightmostChildRec(View host, View root, int[] hp, RightMost best) {
        if (root == null || best == null) return;
        if (root.getVisibility() == View.VISIBLE && root.getWidth() > 0 && root.getHeight() > 0) {
            Object tag = root.getTag();
            if (tag == null || !"BiliFoldsMark".equals(tag)) {
                int[] rp = new int[2];
                root.getLocationOnScreen(rp);
                int right = rp[0] - hp[0] + root.getWidth();
                if (right > best.right) {
                    best.right = right;
                    best.view = root;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                findRightmostChildRec(host, group.getChildAt(i), hp, best);
            }
        }
    }

    private static final class RightMost {
        int right = Integer.MIN_VALUE;
        View view = null;
    }


    private static int dp(android.content.Context ctx, int dp) {
        if (ctx == null) return dp;
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                dp,
                ctx.getResources().getDisplayMetrics()
        );
    }

    private static View findViewByDescContains(View root, String[] keywords) {
        if (root == null || keywords == null) return null;
        CharSequence desc = root.getContentDescription();
        if (desc != null) {
            String s = desc.toString();
            for (String k : keywords) {
                if (k != null && s.contains(k)) {
                    return root;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = group.getChildAt(i);
                View hit = findViewByDescContains(v, keywords);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static TextView findFirstTextView(View root) {
        if (root instanceof TextView) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView tv = findFirstTextView(group.getChildAt(i));
                if (tv != null) return tv;
            }
        }
        return null;
    }

    private static boolean hasFoldMark(ViewGroup group) {
        try {
            Object overlay = XposedHelpers.getAdditionalInstanceField(group, "BiliFoldsOverlayMark");
            if (overlay instanceof View) return true;
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v == null) continue;
            Object tag = v.getTag();
            if (tag != null && "BiliFoldsMark".equals(tag)) return true;
        }
        return false;
    }

    private static TextView findTextViewContains(View root, String text) {
        if (root instanceof TextView) {
            CharSequence t = ((TextView) root).getText();
            if (t != null && t.toString().contains(text)) {
                return (TextView) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = group.getChildAt(i);
                TextView tv = findTextViewContains(v, text);
                if (tv != null) return tv;
            }
        }
        return null;
    }

    private static TextView findTextViewClickableContains(View root, String text) {
        if (root instanceof TextView) {
            CharSequence t = ((TextView) root).getText();
            if (t != null && t.toString().contains(text)) {
                boolean clickable = root.isClickable();
                if (!clickable && root.getParent() instanceof View) {
                    clickable = ((View) root.getParent()).isClickable();
                }
                if (clickable) return (TextView) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = group.getChildAt(i);
                TextView tv = findTextViewClickableContains(v, text);
                if (tv != null) return tv;
            }
        }
        return null;
    }

    private static TextView findTextViewExactClickable(View root, String text) {
        if (root instanceof TextView) {
            CharSequence t = ((TextView) root).getText();
            if (t != null && t.toString().trim().equals(text)) {
                boolean clickable = root.isClickable();
                if (!clickable && root.getParent() instanceof View) {
                    clickable = ((View) root.getParent()).isClickable();
                }
                if (clickable) return (TextView) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = group.getChildAt(i);
                TextView tv = findTextViewExactClickable(v, text);
                if (tv != null) return tv;
            }
        }
        return null;
    }

    private static ViewGroup findActionRow(View root) {
        BestActionRow best = new BestActionRow();
        findActionRowRec(root, best);
        return best.group;
    }

    private static void findActionRowRec(View root, BestActionRow best) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        int score = scoreActionRow(group);
        if (score > best.score) {
            best.score = score;
            best.group = group;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            findActionRowRec(group.getChildAt(i), best);
        }
    }

    private static int scoreActionRow(ViewGroup group) {
        if (group == null) return Integer.MIN_VALUE;
        String cls = group.getClass().getName();
        if (cls != null) {
            if (cls.contains("CommentIdentity") || cls.contains("Identity") || cls.contains("Avatar")) {
                return Integer.MIN_VALUE;
            }
            if (cls.contains("Content") || cls.contains("Rich") || cls.contains("Text")) {
                return Integer.MIN_VALUE / 2;
            }
        }
        int score = 0;
        int image = 0;
        int clickable = 0;
        int keyword = 0;
        int childCount = group.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = group.getChildAt(i);
            if (v == null) continue;
            if (v instanceof android.widget.ImageView) image++;
            if (v.isClickable()) clickable++;
            CharSequence desc = v.getContentDescription();
            if (desc != null && containsAny(desc.toString(), new String[]{
                    "\u56de\u590d",
                    "\u8bc4\u8bba",
                    "\u70b9\u8d5e",
                    "\u70b9\u8e29",
                    "\u66f4\u591a",
                    "\u67e5\u770b\u5bf9\u8bdd"
            })) {
                keyword += 2;
            }
            if (v instanceof TextView) {
                CharSequence t = ((TextView) v).getText();
                if (t != null && (t.toString().contains("\u67e5\u770b\u5bf9\u8bdd") || "\u56de\u590d".equals(t.toString().trim()))) {
                    keyword += 3;
                }
            }
        }
        if (group instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout ll = (android.widget.LinearLayout) group;
            if (ll.getOrientation() == android.widget.LinearLayout.HORIZONTAL) score += 2;
        }
        if (childCount >= 3 && childCount <= 12) score += 1;
        score += image;
        score += clickable;
        if (keyword > 0) score += 4 + keyword;
        return score;
    }

    private static boolean containsAny(String s, String[] keys) {
        if (s == null || keys == null) return false;
        for (String k : keys) {
            if (k != null && s.contains(k)) return true;
        }
        return false;
    }

    private static final class BestActionRow {
        int score = Integer.MIN_VALUE;
        ViewGroup group = null;
    }

    private static void removeFoldMark(View root) {
        removeOverlayMark(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View v = group.getChildAt(i);
                if (v == null) continue;
                Object tag = v.getTag();
                if (tag != null && "BiliFoldsMark".equals(tag)) {
                    group.removeViewAt(i);
                    continue;
                }
                removeFoldMark(v);
            }
        }
    }

    private static void removeOverlayMark(View root) {
        if (root == null) return;
        try {
            Object overlayObj = XposedHelpers.getAdditionalInstanceField(root, "BiliFoldsOverlayMark");
            if (overlayObj instanceof View) {
                try {
                    android.view.ViewOverlay ov = root.getOverlay();
                    if (ov instanceof android.view.ViewGroupOverlay) {
                        ((android.view.ViewGroupOverlay) ov).remove((View) overlayObj);
                    }
                } catch (Throwable ignored) {
                }
                try {
                    XposedHelpers.removeAdditionalInstanceField(root, "BiliFoldsOverlayMark");
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                removeOverlayMark(group.getChildAt(i));
            }
        }
    }

    private static boolean callCommentAdapterB1(Object adapter, List<?> list) {
        if (adapter == null || list == null) return false;
        try {
            java.lang.reflect.Method[] methods = adapter.getClass().getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!"b1".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts == null || pts.length == 0) continue;
                if (!pts[0].isAssignableFrom(list.getClass()) && !List.class.isAssignableFrom(pts[0])) {
                    continue;
                }
                Object[] args = new Object[pts.length];
                args[0] = list;
                for (int i = 1; i < pts.length; i++) {
                    Class<?> t = pts[i];
                    if (t == boolean.class || t == Boolean.class) {
                        args[i] = false;
                    } else if (t == int.class || t == Integer.class) {
                        args[i] = 0;
                    } else if (t == long.class || t == Long.class) {
                        args[i] = 0L;
                    } else if (t == float.class || t == Float.class) {
                        args[i] = 0f;
                    } else if (t == double.class || t == Double.class) {
                        args[i] = 0d;
                    } else {
                        args[i] = null;
                    }
                }
                try {
                    m.setAccessible(true);
                    m.invoke(adapter, args);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void cacheFoldListResult(String tag, String offset, Object obj) {
        List<?> list = extractList(obj);
        if (list == null || list.size() <= 1) {
            List<Object> collected = collectCommentItems(obj, 2);
            if (collected != null && !collected.isEmpty()) {
                list = collected;
            }
        }
        if (list == null || list.isEmpty()) {
            return;
        }
        ArrayList<Object> bucket = new ArrayList<>(list.size());
        long rootId = 0L;
        for (Object o : list) {
            if (o == null || !isCommentItem(o)) continue;
            long id = getId(o);
            long root = getRootId(o);
            if (rootId == 0L) {
                if (root != 0L) rootId = root;
                else if (id != 0L) rootId = id;
            }
            if (rootId != 0L) {
                if (id == rootId) continue;
                if (root != 0L && root != rootId) continue;
            }
            forceUnfold(o);
            bucket.add(o);
        }
        if (bucket.isEmpty()) return;
        String realOffset = offset != null ? offset : extractOffsetFromObj(obj);
        if (realOffset == null) {
            return;
        }
        String key = realOffset;
        if (key == null || key.isEmpty()) {
            return;
        }
        ArrayList<Object> existing = FOLD_CACHE_BY_OFFSET.computeIfAbsent(key, k -> new ArrayList<>());
        mergeUniqueById(existing, bucket);
        String subjectKey = getCurrentSubjectKey();
        if (subjectKey == null) {
            subjectKey = deriveSubjectKeyFromList(list);
        }
        if (subjectKey != null) {
            ArrayList<Object> bySubject = FOLD_CACHE_BY_SUBJECT.computeIfAbsent(subjectKey, k -> new ArrayList<>());
            mergeUniqueById(bySubject, bucket);
        }
        if (rootId != 0L) {
            ArrayList<Object> byRoot = FOLD_CACHE.computeIfAbsent(rootId, k -> new ArrayList<>());
            mergeUniqueById(byRoot, bucket);
            OFFSET_TO_ROOT.put(realOffset, rootId);
        }
        tryUpdateCommentAdapterList(realOffset);
    }

    private static void tryUpdateCommentAdapterList(String offset) {
        Object adapter = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
        if (adapter == null) return;
        Object differ;
        try {
            differ = XposedHelpers.getObjectField(adapter, "c");
        } catch (Throwable ignored) {
            return;
        }
        if (differ == null) return;
        Object listObj;
        try {
            listObj = XposedHelpers.callMethod(differ, "a");
        } catch (Throwable ignored) {
            listObj = null;
        }
        if (!(listObj instanceof List)) return;
        List<?> list = (List<?>) listObj;
        List<?> replaced = replaceZipCardsInList(list, "CommentListAdapter.cached");
        if (replaced == null) return;
        if (!containsFooterCard(list)) {
            if (scheduleFooterRetry(offset)) {
                return;
            }
        } else {
            clearFooterRetry(offset);
        }
        postToMain(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Boolean.TRUE.equals(RESUBMITTING.get())) return;
                    RESUBMITTING.set(true);
                    if (!callCommentAdapterB1(adapter, replaced)) {
                        List rawList = (List) list;
                        rawList.clear();
                        rawList.addAll((List) replaced);
                        XposedHelpers.callMethod(adapter, "notifyDataSetChanged");
                    }
                } catch (Throwable ignored) {
                } finally {
                    RESUBMITTING.set(false);
                }
            }
        });
    }

    private static void hookCommentItemTags(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.model.CommentItem",
                cl
        );
        if (c == null) {
            log("CommentItem class not found");
            return;
        }
        try {
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!"getTags".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (!(result instanceof List)) return;
                        Object item = param.thisObject;
                        long id = getId(item);
                        if (id == 0) return;
                        if (!Boolean.TRUE.equals(FOLDED_IDS.get(id))) return;
                        List<?> tags = (List<?>) result;
                        if (containsFoldTag(tags)) return;
                        Object tag = buildFoldTag(item.getClass().getClassLoader());
                        if (tag == null) return;
                        ArrayList<Object> out = new ArrayList<>(tags.size() + 1);
                        out.addAll((List<?>) tags);
                        out.add(tag);
                        param.setResult(out);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookCommentItemFoldFlags(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.model.CommentItem",
                cl
        );
        if (c == null) {
            log("CommentItem class not found");
            return;
        }
        hookBooleanMethodReturnFalse(c, "D");
        hookBooleanMethodReturnFalse(c, "isFolded");
        hookBooleanMethodReturnFalse(c, "getIsFolded");
        hookBooleanMethodReturnFalse(c, "getIsFoldedReply");
    }

    private static void hookActionDispatcher(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.action.c",
                cl
        );
        if (c == null) {
            log("action dispatcher class not found");
            return;
        }
        XposedBridge.hookAllMethods(c, "b", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    long id = extractFoldedIdFromArgs(param.args);
                    if (id == 0L) return;
                    String arg0 = param.args != null && param.args.length > 0 && param.args[0] != null
                            ? param.args[0].getClass().getName() : "null";
                    log("action.dispatch folded id=" + id + " arg0=" + arg0);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static long extractFoldedIdFromArgs(Object[] args) {
        if (args == null) return 0L;
        for (Object a : args) {
            if (a == null) continue;
            if (isCommentItem(a)) {
                long id = getId(a);
                if (id != 0L && Boolean.TRUE.equals(FOLDED_IDS.get(id))) return id;
            }
            long id = extractCommentIdFromObject(a);
            if (id != 0L && Boolean.TRUE.equals(FOLDED_IDS.get(id))) return id;
        }
        return 0L;
    }

    private static long extractCommentIdFromObject(Object obj) {
        if (obj == null) return 0L;
        try {
            Field[] fields = obj.getClass().getDeclaredFields();
            for (Field f : fields) {
                if (f == null) continue;
                Class<?> t = f.getType();
                if (t == null) continue;
                String name = f.getName() == null ? "" : f.getName().toLowerCase();
                boolean maybeId = (t == long.class || t == Long.class) && (name.contains("id") || name.contains("rpid"));
                boolean maybeItem = name.contains("comment") || name.contains("reply") || name.contains("item");
                if (!maybeId && !maybeItem && t != Object.class) {
                    continue;
                }
                f.setAccessible(true);
                Object v = f.get(obj);
                if (isCommentItem(v)) {
                    long id = getId(v);
                    if (id != 0L) return id;
                }
                if (maybeId && v instanceof Number) {
                    long id = ((Number) v).longValue();
                    if (id != 0L) return id;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static void hookBooleanMethodReturnFalse(Class<?> c, String name) {
        try {
            XposedHelpers.findAndHookMethod(c, name, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return false;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookZipCardView(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("vv.r1", cl);
        if (c == null) {
            log("vv.r1 class not found");
            return;
        }
        XposedHelpers.findAndHookMethod(c, "h", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!isAutoExpand(param.thisObject)) return;
                param.setResult(AUTO_EXPAND_TEXT);
            }
        });
        XposedHelpers.findAndHookMethod(c, "f", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!isAutoExpand(param.thisObject)) return;
                param.setResult(false);
            }
        });
    }

    private static void markAutoExpand(Object zipCard) {
        if (zipCard == null) return;
        AUTO_EXPAND_ZIP.add(zipCard);
    }

    private static boolean isAutoExpand(Object zipCard) {
        if (zipCard == null) return false;
        return AUTO_EXPAND_ZIP.contains(zipCard);
    }

    private static List<?> replaceZipCardsInList(List<?> list, String tag) {
        if (list == null || list.isEmpty()) return null;
        ArrayList<Object> out = new ArrayList<>(list.size());
        boolean changed = false;
        boolean sawZipCard = false;
        Boolean desc = Boolean.FALSE;
        String subjectKey = getCurrentSubjectKey();
        if (subjectKey == null) {
            subjectKey = deriveSubjectKeyFromList(list);
        }
        HashSet<Long> existingIds = collectCommentIds(list);
        HashMap<Long, ArrayList<Object>> tipsByRoot = new HashMap<>();
        ArrayList<Object> tipsNoRoot = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (isFooterCard(item)) {
                out.add(item);
                continue;
            }
            if (isZipCard(item)) {
                sawZipCard = true;
                if (subjectKey != null) {
                    SUBJECT_HAS_FOLD.put(subjectKey, Boolean.TRUE);
                }
                String offset = getZipCardOffset(item);
                long rootId = getZipCardRootId(item);
                if (rootId == 0L) {
                    rootId = findPrevCommentRootId(list, i);
                }
                if (offset != null && !offset.isEmpty() && rootId > 0) {
                    OFFSET_TO_ROOT.put(offset, rootId);
                }
                String key = offset;
                if (key != null && !key.isEmpty()) {
                    OFFSET_INSERT_INDEX.put(key, i);
                }
                List<Object> cached = getCachedFoldListForZip(item, offset, desc);
                if (cached == null || cached.isEmpty()) {
                    log("zip card cache miss offset=" + offset + " root=" + rootId + " tag=" + tag);
                    tryAutoFetchFoldList(offset, getCurrentSubjectKey(), rootId);
                    markAutoExpand(item);
                    out.add(item);
                    continue;
                }
                log("zip card cache hit offset=" + offset + " root=" + rootId + " size=" + cached.size() + " tag=" + tag);
                markAutoExpand(item);
                if (rootId > 0) {
                    ArrayList<Object> tips = tipsByRoot.computeIfAbsent(rootId, k -> new ArrayList<>());
                    tips.add(item);
                } else {
                    tipsNoRoot.add(item);
                }
                for (Object o : cached) {
                    long id = getId(o);
                    if (id != 0 && existingIds.contains(id)) {
                        continue;
                    }
                    forceUnfold(o);
                    if (getRootId(o) != id && id != 0) {
                        FOLDED_IDS.put(id, Boolean.TRUE);
                    }
                    out.add(o);
                    if (id != 0) existingIds.add(id);
                }
                changed = true;
                continue;
            }
            if (isCommentItem(item)) {
                debugLogCommentFold(item);
            }
            if (item != null && "vv.r1".equals(item.getClass().getName())) {
                String text = callStringMethod(item, "h");
                String offset = getZipCardOffset(item);
                if ((text != null && (text.contains("\u5c55\u5f00\u66f4\u591a\u8bc4\u8bba") || text.contains("\u5c55\u5f00\u66f4\u591a"))) ||
                        (offset != null && !offset.isEmpty())) {
                    log("skip r1 non-zip text=" + text + " offset=" + offset + " tag=" + tag);
                }
            }
            out.add(item);
        }
        if (injectCachedByPendingOffsets(out, existingIds)) {
            changed = true;
        }
        if (!sawZipCard) {
            if (injectCachedBySubject(out, existingIds, subjectKey)) {
                changed = true;
            }
        }
        if (!changed) return null;
        if (subjectKey != null) {
            SUBJECT_EXPANDED.put(subjectKey, Boolean.TRUE);
        }
        List<Object> resorted = reorderCommentsByTime(out);
        List<Object> base = resorted != null ? resorted : out;
        if (!tipsByRoot.isEmpty() || !tipsNoRoot.isEmpty()) {
            ArrayList<Object> withTips = new ArrayList<>(base.size() + tipsByRoot.size() + tipsNoRoot.size());
            for (Object item : base) {
                withTips.add(item);
                if (!isCommentItem(item)) continue;
                long id = getId(item);
                ArrayList<Object> tips = tipsByRoot.remove(id);
                if (tips == null) {
                    long root = getRootId(item);
                    tips = tipsByRoot.remove(root);
                }
                if (tips != null && !tips.isEmpty()) {
                    withTips.addAll(tips);
                }
            }
            if (!tipsByRoot.isEmpty()) {
                for (ArrayList<Object> tips : tipsByRoot.values()) {
                    if (tips != null) withTips.addAll(tips);
                }
            }
            if (!tipsNoRoot.isEmpty()) {
                withTips.addAll(tipsNoRoot);
            }
            return withTips;
        }
        return base;
    }

    private static boolean injectCachedByPendingOffsets(ArrayList<Object> out, HashSet<Long> existingIds) {
        if (out == null) return false;
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : new ArrayList<>(OFFSET_INSERT_INDEX.entrySet())) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) continue;
            ArrayList<Object> cached = FOLD_CACHE_BY_OFFSET.get(key);
            if (cached == null || cached.isEmpty()) continue;
            int idx = entry.getValue() == null ? out.size() : entry.getValue();
            if (idx < 0) idx = 0;
            if (idx > out.size()) idx = out.size();
            int inserted = 0;
            for (Object o : cached) {
                long id = getId(o);
                if (id != 0 && existingIds.contains(id)) continue;
                out.add(idx + inserted, o);
                inserted++;
                if (id != 0) existingIds.add(id);
            }
            if (inserted > 0) {
                changed = true;
                OFFSET_INSERT_INDEX.remove(key);
            }
        }
        if (changed) {
            String subjectKey = getCurrentSubjectKey();
            if (subjectKey == null) {
                subjectKey = deriveSubjectKeyFromList(out);
            }
            if (subjectKey != null) {
                SUBJECT_EXPANDED.put(subjectKey, Boolean.TRUE);
            }
        }
        return changed;
    }

    private static boolean injectCachedBySubject(ArrayList<Object> out, HashSet<Long> existingIds, String subjectKey) {
        if (out == null || subjectKey == null || subjectKey.isEmpty()) return false;
        if (!Boolean.TRUE.equals(SUBJECT_HAS_FOLD.get(subjectKey))) return false;
        ArrayList<Object> cached = FOLD_CACHE_BY_SUBJECT.get(subjectKey);
        if (cached == null || cached.isEmpty()) return false;
        int inserted = 0;
        for (Object o : cached) {
            if (o == null || !isCommentItem(o)) continue;
            long id = getId(o);
            if (id != 0 && existingIds.contains(id)) continue;
            forceUnfold(o);
            out.add(o);
            inserted++;
            if (id != 0) existingIds.add(id);
        }
        if (inserted > 0) {
            SUBJECT_EXPANDED.put(subjectKey, Boolean.TRUE);
            log("inject subject cached size=" + cached.size() + " inserted=" + inserted + " key=" + subjectKey);
            return true;
        }
        return false;
    }

    private static void debugLogCommentFold(Object item) {
        if (item == null || !isCommentItem(item)) return;
        long rootId = getRootId(item);
        if (rootId == 0L) return;
        if (DEBUG_FOLD_LOGGED.putIfAbsent(rootId, Boolean.TRUE) != null) return;
        int folded = getIntByMethodNames(item,
                "getFoldedCount",
                "getFoldedReplyCount",
                "getFoldCount",
                "getFoldedNum",
                "getFoldNum",
                "getInvisibleReplyCount",
                "getInvisibleCount"
        );
        int invisible = getIntFieldByNameContains(item, new String[]{"invisible"});
        int total = getIntByMethodNames(item,
                "getReplyCount",
                "getRepliesCount",
                "getSubReplyCount",
                "getAllCount",
                "getCount"
        );
        int child = getIntByMethodNames(item,
                "getChildCount",
                "getChildrenCount",
                "getVisibleReplyCount",
                "getVisibleCount"
        );
        if (folded > 0 || invisible > 0 || (total > 0 && child >= 0 && total - child > 0)) {
            log("comment fold stats root=" + rootId
                    + " id=" + getId(item)
                    + " folded=" + folded
                    + " invisible=" + invisible
                    + " total=" + total
                    + " child=" + child);
        }
    }

    private static int getIntByMethodNames(Object item, String... names) {
        if (item == null || names == null) return -1;
        for (String name : names) {
            int v = callIntMethod(item, name);
            if (v != Integer.MIN_VALUE) return v;
        }
        return -1;
    }

    private static int getIntFieldByNameContains(Object item, String[] must) {
        if (item == null || must == null) return -1;
        Field[] fields = item.getClass().getDeclaredFields();
        for (Field f : fields) {
            String n = f.getName();
            if (n == null) continue;
            String s = n.toLowerCase();
            boolean ok = true;
            for (String token : must) {
                if (token == null) continue;
                if (!s.contains(token)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(item);
                if (!(v instanceof Number)) continue;
                int iv = ((Number) v).intValue();
                if (iv >= 0) return iv;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private static void prefetchFoldList(List<?> list) {
        if (list == null || list.isEmpty()) return;
        int max = list.size();
        int found = 0;
        int seenR1 = 0;
        log("prefetch list size=" + list.size() + " scanMax=" + max);
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (item != null && "vv.r1".equals(item.getClass().getName())) {
                seenR1++;
                String text = callStringMethod(item, "h");
                String offset = getZipCardOffset(item);
                if ((text != null && (text.contains("\u5c55\u5f00\u66f4\u591a\u8bc4\u8bba") || text.contains("\u5c55\u5f00\u66f4\u591a"))) ||
                        (offset != null && !offset.isEmpty())) {
                    log("prefetch r1 idx=" + i + " text=" + text + " offset=" + offset);
                }
            }
            if (!isZipCard(item)) continue;
            found++;
            String subjectKey = getCurrentSubjectKey();
            if (subjectKey != null) {
                SUBJECT_HAS_FOLD.put(subjectKey, Boolean.TRUE);
            }
            String offset = getZipCardOffset(item);
            long rootId = getZipCardRootId(item);
            if (rootId == 0L) {
                rootId = findPrevCommentRootId(list, i);
            }
            log("prefetch fold card idx=" + i + " offset=" + offset + " root=" + rootId);
            tryAutoFetchFoldList(offset, getCurrentSubjectKey(), rootId);
        }
        if (found > 1) {
            log("prefetch fold cards total=" + found);
        }
        if (seenR1 > 1) {
            log("prefetch r1 total=" + seenR1);
        }
    }

    private static List<Object> getCachedFoldListForZip(Object zipCard, String offset, Boolean desc) {
        ArrayList<Object> cached = null;
        if (offset != null && !offset.isEmpty()) {
            cached = FOLD_CACHE_BY_OFFSET.get(offset);
        }
        if (cached == null || cached.isEmpty()) {
            long rootId = getZipCardRootId(zipCard);
            if (rootId > 0) {
                cached = FOLD_CACHE.get(rootId);
                if (cached != null && cached.isEmpty()) cached = null;
            }
        }
        if (cached == null || cached.isEmpty()) return null;
        if (desc != null) {
            sortByCreateTime(cached, desc);
        }
        return cached;
    }

    private static long findPrevCommentRootId(List<?> list, int index) {
        if (list == null) return 0L;
        for (int i = index - 1; i >= 0 && index - i <= 6; i--) {
            Object item = list.get(i);
            if (!isCommentItem(item)) continue;
            long id = getId(item);
            if (id != 0) return id;
            long root = getRootId(item);
            if (root != 0) return root;
        }
        return 0L;
    }

    private static void tryAutoFetchFoldList(String offset, String subjectKey, long rootId) {
        if (offset == null || offset.isEmpty()) return;
        String key = makeOffsetKey(offset, subjectKey);
        if (FOLD_CACHE_BY_OFFSET.containsKey(offset) || (key != null && FOLD_CACHE_BY_OFFSET.containsKey(key))) {
            return;
        }
        String fetchKey = key == null ? offset : key;
        if (AUTO_FETCHING.putIfAbsent(fetchKey, Boolean.TRUE) != null) return;
        final Object subjectId = LAST_SUBJECT_ID == null ? null : LAST_SUBJECT_ID.get();
        if (subjectId == null) {
            AUTO_FETCHING.remove(fetchKey);
            return;
        }
        String realSubjectKey = subjectKeyFromSubject(subjectId);
        if (realSubjectKey != null) {
            LAST_SUBJECT_KEY = realSubjectKey;
        }
        final String extra = LAST_EXTRA;
        final long rootIdFinal = rootId != 0L ? rootId : getRootForOffset(offset);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (rootIdFinal != 0L) {
                        Object detail = fetchDetailList(subjectId, rootIdFinal, extra);
                        if (detail != null) {
                            cacheFoldListResult("auto.detail", offset, detail);
                            return;
                        }
                    }
                    String curOffset = offset;
                    int pages = 0;
                    while (curOffset != null && !curOffset.isEmpty() && pages < 5) {
                        Object p0 = fetchFoldList(subjectId, curOffset, extra, true, rootIdFinal);
                        if (p0 != null) {
                            cacheFoldListResult("auto.fetch", curOffset, p0);
                            List<?> p0List = extractList(p0);
                            if (p0List != null && !p0List.isEmpty() && p0List.size() <= 1) {
                                Object first = p0List.get(0);
                                long rid = getRootId(first);
                                if (rid != 0L) {
                                    Object detail = fetchDetailList(subjectId, rid, extra);
                                    if (detail != null) {
                                        cacheFoldListResult("auto.detail", curOffset, detail);
                                    }
                                }
                            }
                            String next = extractOffsetFromObj(p0);
                            if (next == null || next.isEmpty() || next.equals(curOffset)) {
                                break;
                            }
                            curOffset = next;
                            pages++;
                        } else {
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    AUTO_FETCHING.remove(fetchKey);
                }
            }
        }, "BiliFoldsFetch").start();
    }

    private static Object fetchFoldList(Object subjectId, String offset, String extra, boolean withChildren, long rootId) {
        ClassLoader cl = subjectId.getClass().getClassLoader();
        if (cl == null) {
            cl = APP_CL;
        }
        if (cl == null) {
            return null;
        }
        long oid = callLongMethod(subjectId, "a");
        if (oid == 0) oid = callLongMethod(subjectId, "getOid");
        if (oid == 0) oid = getLongField(subjectId, "oid_");
        long type = callLongMethod(subjectId, "b");
        if (type == 0) type = callLongMethod(subjectId, "getType");
        if (type == 0) type = getLongField(subjectId, "type_");
        if (oid == 0 || type == 0) {
            return null;
        }
        Class<?> foldReqCls = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.FoldListReq",
                cl
        );
        Class<?> mossCls = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.ReplyMoss",
                cl
        );
        Class<?> mapCls = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.source.v1.e",
                cl
        );
        if (foldReqCls == null || mossCls == null || mapCls == null) {
            return null;
        }
        Object reqBuilder = XposedHelpers.callStaticMethod(foldReqCls, "newBuilder");
        if (reqBuilder == null) return null;
        XposedHelpers.callMethod(reqBuilder, "setOid", oid);
        XposedHelpers.callMethod(reqBuilder, "setType", type);
        XposedHelpers.callMethod(reqBuilder, "setExtra", extra == null ? "" : extra);
        Object pagination = setPaginationOnReqBuilder(reqBuilder, offset, cl);
        tryCall(reqBuilder, "setWithChildren", withChildren);
        int mode = getSortModeValue(LAST_SORT_MODE);
        if (mode != Integer.MIN_VALUE) {
            tryCall(reqBuilder, "setMode", mode);
            tryCall(reqBuilder, "setSortMode", mode);
            tryCall(reqBuilder, "setSort", mode);
        }
        if (pagination != null) {
            trySetField(reqBuilder, "pagination_", pagination);
            trySetField(reqBuilder, "foldPagination_", pagination);
        }
        if (mode != Integer.MIN_VALUE) {
            trySetField(reqBuilder, "mode_", mode);
        }
        if (rootId != 0L) {
            trySetField(reqBuilder, "root_", rootId);
        }
        Object req = XposedHelpers.callMethod(reqBuilder, "build");
        if (req == null) return null;
        Object moss;
        try {
            moss = XposedHelpers.newInstance(mossCls);
        } catch (Throwable ignored) {
            moss = XposedHelpers.newInstance(mossCls, null, 0, null, 7, null);
        }
        if (moss == null) return null;
        Object resp = XposedHelpers.callMethod(moss, "foldList", req);
        if (resp == null) return null;
        return XposedHelpers.callStaticMethod(mapCls, "D0", resp, withChildren);
    }

    private static Object fetchDetailList(Object subjectId, long rootId, String extra) {
        if (rootId == 0L) return null;
        ClassLoader cl = subjectId.getClass().getClassLoader();
        if (cl == null) {
            cl = APP_CL;
        }
        if (cl == null) {
            return null;
        }
        long oid = callLongMethod(subjectId, "a");
        if (oid == 0) oid = callLongMethod(subjectId, "getOid");
        if (oid == 0) oid = getLongField(subjectId, "oid_");
        long type = callLongMethod(subjectId, "b");
        if (type == 0) type = callLongMethod(subjectId, "getType");
        if (type == 0) type = getLongField(subjectId, "type_");
        if (oid == 0 || type == 0) {
            return null;
        }
        Class<?> reqCls = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.DetailListReq",
                cl
        );
        Class<?> mossCls = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.ReplyMoss",
                cl
        );
        Class<?> mapCls = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.source.v1.e",
                cl
        );
        if (reqCls == null || mossCls == null) return null;
        Object builder = XposedHelpers.callStaticMethod(reqCls, "newBuilder");
        if (builder == null) return null;
        XposedHelpers.callMethod(builder, "setOid", oid);
        XposedHelpers.callMethod(builder, "setType", type);
        tryCall(builder, "setRoot", rootId);
        tryCall(builder, "setRootRpid", rootId);
        tryCall(builder, "setRpid", 0L);
        tryCall(builder, "setReplyId", rootId);
        tryCall(builder, "setExtra", extra == null ? "" : extra);
        int mode = getSortModeValue(LAST_SORT_MODE);
        if (mode != Integer.MIN_VALUE) {
            tryCall(builder, "setMode", mode);
            tryCall(builder, "setSortMode", mode);
            tryCall(builder, "setSort", mode);
        }
        Object req = XposedHelpers.callMethod(builder, "build");
        if (req == null) return null;
        Object moss;
        try {
            moss = XposedHelpers.newInstance(mossCls);
        } catch (Throwable ignored) {
            moss = XposedHelpers.newInstance(mossCls, null, 0, null, 7, null);
        }
        if (moss == null) return null;
        Object resp = tryCallDetailList(moss, req);
        if (resp == null) return null;
        if (mapCls == null) return resp;
        Object mapped = tryMapDetail(mapCls, resp, rootId);
        return mapped == null ? resp : mapped;
    }

    private static Object tryCallDetailList(Object moss, Object req) {
        if (moss == null || req == null) return null;
        try {
            return XposedHelpers.callMethod(moss, "detailList", req);
        } catch (Throwable ignored) {
        }
        try {
            return XposedHelpers.callMethod(moss, "detailListV2", req);
        } catch (Throwable ignored) {
        }
        java.lang.reflect.Method[] methods = moss.getClass().getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            String name = m.getName();
            if (name == null || !name.toLowerCase().contains("detail")) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts == null || pts.length != 1) continue;
            if (!pts[0].isAssignableFrom(req.getClass())) continue;
            try {
                m.setAccessible(true);
                Object resp = m.invoke(moss, req);
                if (resp != null) {
                    return resp;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object tryMapDetail(Class<?> mapCls, Object resp, long rootId) {
        if (mapCls == null || resp == null) return null;
        try {
            return XposedHelpers.callStaticMethod(mapCls, "A0", resp, rootId);
        } catch (Throwable ignored) {
        }
        try {
            return XposedHelpers.callStaticMethod(mapCls, "A0", resp);
        } catch (Throwable ignored) {
        }
        try {
            return XposedHelpers.callStaticMethod(mapCls, "F0", resp);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object setPaginationOnReqBuilder(Object reqBuilder, String offset, ClassLoader cl) {
        if (reqBuilder == null) return null;
        Object pagination = null;
        java.lang.reflect.Method[] methods = reqBuilder.getClass().getMethods();
        for (java.lang.reflect.Method m : methods) {
            String name = m.getName();
            if (name == null) continue;
            if (!name.equals("setPagination") && !name.equals("setFoldPagination") && !name.equals("setPaginationReply")) {
                continue;
            }
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length != 1) continue;
            Class<?> paginationCls = pts[0];
            Object p = buildPagination(paginationCls, offset);
            if (p == null) continue;
            try {
                m.invoke(reqBuilder, p);
                pagination = p;
            } catch (Throwable ignored) {
            }
        }
        if (pagination != null) return pagination;
        Class<?> feedPaginationCls = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.pagination.FeedPagination",
                cl
        );
        if (feedPaginationCls != null) {
            Object p = buildPagination(feedPaginationCls, offset);
            if (p != null) {
                tryCall(reqBuilder, "setPagination", p);
                pagination = p;
            }
        }
        return pagination;
    }

    private static Object buildPagination(Class<?> paginationCls, String offset) {
        if (paginationCls == null) return null;
        Object builder = null;
        try {
            builder = XposedHelpers.callStaticMethod(paginationCls, "newBuilder");
        } catch (Throwable ignored) {
        }
        if (builder == null) {
            if (paginationCls.getName().endsWith("$b") || paginationCls.getName().toLowerCase().contains("builder")) {
                Class<?> outer = paginationCls.getDeclaringClass();
                if (outer != null) {
                    builder = buildPagination(outer, offset);
                }
            }
            if (builder == null) return null;
        }
        tryCall(builder, "setOffset", offset);
        tryCall(builder, "setNext", offset);
        tryCall(builder, "setCursor", offset);
        tryCall(builder, "setPageSize", 50);
        tryCall(builder, "setSize", 50);
        tryCall(builder, "setIsRefresh", true);
        try {
            Object pagination = XposedHelpers.callMethod(builder, "build");
            if (pagination != null) {
                return pagination;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void updateSubjectKey(Object subjectId) {
        String key = subjectKeyFromSubject(subjectId);
        if (key != null) {
            if (LAST_SUBJECT_KEY == null || !key.equals(LAST_SUBJECT_KEY)) {
                clearFoldCaches();
            }
            LAST_SUBJECT_KEY = key;
        }
    }

    private static String getCurrentSubjectKey() {
        if (LAST_SUBJECT_KEY != null) return LAST_SUBJECT_KEY;
        Object subjectId = LAST_SUBJECT_ID == null ? null : LAST_SUBJECT_ID.get();
        String key = subjectKeyFromSubject(subjectId);
        if (key != null) {
            if (LAST_SUBJECT_KEY == null || !key.equals(LAST_SUBJECT_KEY)) {
                clearFoldCaches();
            }
            LAST_SUBJECT_KEY = key;
        }
        return key;
    }

    private static void clearFoldCaches() {
        FOLD_CACHE.clear();
        FOLD_CACHE_BY_OFFSET.clear();
        FOLD_CACHE_BY_SUBJECT.clear();
        OFFSET_TO_ROOT.clear();
        OFFSET_INSERT_INDEX.clear();
        AUTO_FETCHING.clear();
        FOLDED_IDS.clear();
        SUBJECT_HAS_FOLD.clear();
        SUBJECT_EXPANDED.clear();
        FOOTER_RETRY_COUNT.clear();
        FOOTER_RETRY_PENDING.clear();
        AUTO_EXPAND_ZIP.clear();
    }

    private static String deriveSubjectKeyFromList(List<?> list) {
        if (list == null) return null;
        for (Object item : list) {
            if (!isCommentItem(item)) continue;
            String key = subjectKeyFromCommentItem(item);
            if (key != null) {
                LAST_SUBJECT_KEY = key;
                return key;
            }
        }
        return null;
    }

    private static String deriveSubjectKeyFromListFromCache() {
        Object adapter = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
        if (adapter == null) return null;
        Object differ;
        try {
            differ = XposedHelpers.getObjectField(adapter, "c");
        } catch (Throwable ignored) {
            return null;
        }
        if (differ == null) return null;
        Object listObj;
        try {
            listObj = XposedHelpers.callMethod(differ, "a");
        } catch (Throwable ignored) {
            return null;
        }
        if (!(listObj instanceof List)) return null;
        return deriveSubjectKeyFromList((List<?>) listObj);
    }

    private static String subjectKeyFromSubject(Object subjectId) {
        if (subjectId == null) return null;
        long oid = callLongMethod(subjectId, "a");
        if (oid == 0) oid = callLongMethod(subjectId, "getOid");
        if (oid == 0) oid = getLongField(subjectId, "oid_");
        long type = callLongMethod(subjectId, "b");
        if (type == 0) type = callLongMethod(subjectId, "getType");
        if (type == 0) type = getLongField(subjectId, "type_");
        if (oid == 0 || type == 0) return null;
        return oid + ":" + type;
    }

    private static String subjectKeyFromCommentItem(Object item) {
        if (item == null) return null;
        long oid = callLongMethod(item, "getOid");
        if (oid == 0) oid = callLongMethod(item, "getObjectId");
        if (oid == 0) oid = getLongField(item, "oid_");
        long type = callLongMethod(item, "getType");
        if (type == 0) type = callLongMethod(item, "getObjType");
        if (type == 0) type = getLongField(item, "type_");
        if (oid == 0 || type == 0) return null;
        return oid + ":" + type;
    }

    private static String makeOffsetKey(String offset, String subjectKey) {
        if (offset == null || offset.isEmpty()) return null;
        if (subjectKey == null || subjectKey.isEmpty()) return offset;
        return subjectKey + "|" + offset;
    }

    private static String resolveOffsetKey(String offset, String subjectKey) {
        if (offset == null || offset.isEmpty()) return null;
        String key = makeOffsetKey(offset, subjectKey);
        if (key != null && FOLD_CACHE_BY_OFFSET.containsKey(key)) return key;
        if (FOLD_CACHE_BY_OFFSET.containsKey(offset)) return offset;
        if (subjectKey == null || subjectKey.isEmpty()) {
            String suffix = "|" + offset;
            String found = null;
            for (String k : FOLD_CACHE_BY_OFFSET.keySet()) {
                if (k == null || !k.endsWith(suffix)) continue;
                if (found != null && !found.equals(k)) return offset;
                found = k;
            }
            if (found != null) return found;
        }
        return key;
    }

    private static long getRootForOffset(String offset) {
        if (offset == null || offset.isEmpty()) return 0L;
        Long v = OFFSET_TO_ROOT.get(offset);
        return v == null ? 0L : v;
    }

    private static boolean isCommentItem(Object item) {
        return item != null && "com.bilibili.app.comment3.data.model.CommentItem".equals(item.getClass().getName());
    }

    private static long getCreateTime(Object item) {
        if (item == null) return 0L;
        try {
            return XposedHelpers.getLongField(item, "f55118g");
        } catch (Throwable ignored) {
        }
        try {
            Object v = XposedHelpers.callMethod(item, "getCreateTime");
            if (v instanceof Long) return (Long) v;
        } catch (Throwable ignored) {
        }
        try {
            Object v = XposedHelpers.callMethod(item, "getCtime");
            if (v instanceof Long) return (Long) v;
        } catch (Throwable ignored) {
        }
        if (COMMENT_TIME_FIELD != null) {
            try {
                COMMENT_TIME_FIELD.setAccessible(true);
                Object v = COMMENT_TIME_FIELD.get(item);
                if (v instanceof Number) {
                    long t = ((Number) v).longValue();
                    if (COMMENT_TIME_IS_MILLIS && t > 0) return t / 1000L;
                    return t;
                }
            } catch (Throwable ignored) {
            }
        }
        findCommentTimeField(item);
        if (COMMENT_TIME_FIELD != null) {
            try {
                COMMENT_TIME_FIELD.setAccessible(true);
                Object v = COMMENT_TIME_FIELD.get(item);
                if (v instanceof Number) {
                    long t = ((Number) v).longValue();
                    if (COMMENT_TIME_IS_MILLIS && t > 0) return t / 1000L;
                    return t;
                }
            } catch (Throwable ignored) {
            }
        }
        return 0L;
    }

    private static void findCommentTimeField(Object item) {
        if (item == null || COMMENT_TIME_FIELD != null) return;
        Field bestField = null;
        int bestScore = 0;
        boolean bestMillis = false;
        Field[] fields = item.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(item);
                if (!(v instanceof Number)) continue;
                long t = ((Number) v).longValue();
                if (t <= 0) continue;
                boolean isMillis = t > 1_000_000_000_000L;
                long sec = isMillis ? t / 1000L : t;
                if (sec < 1_000_000_000L || sec > 4_200_000_000L) continue;
                int score = 1;
                String n = f.getName();
                if (n != null) {
                    String s = n.toLowerCase();
                    if (s.contains("ctime") || s.contains("time") || s.contains("date") || s.contains("ts")) score += 3;
                    if (s.contains("create") || s.contains("publish")) score += 2;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestField = f;
                    bestMillis = isMillis;
                }
            } catch (Throwable ignored) {
            }
        }
        if (bestField != null) {
            COMMENT_TIME_FIELD = bestField;
            COMMENT_TIME_IS_MILLIS = bestMillis;
        }
    }

    private static long getId(Object item) {
        if (item == null) return 0L;
        try {
            return XposedHelpers.getLongField(item, "f55112a");
        } catch (Throwable ignored) {
        }
        try {
            Object v = XposedHelpers.callMethod(item, "getId");
            if (v instanceof Long) return (Long) v;
        } catch (Throwable ignored) {
        }
        if (COMMENT_ID_FIELD != null) {
            try {
                COMMENT_ID_FIELD.setAccessible(true);
                Object v = COMMENT_ID_FIELD.get(item);
                if (v instanceof Number) {
                    long id = ((Number) v).longValue();
                    if (id > 0) return id;
                }
            } catch (Throwable ignored) {
            }
        }
        findCommentIdField(item);
        if (COMMENT_ID_FIELD != null) {
            try {
                COMMENT_ID_FIELD.setAccessible(true);
                Object v = COMMENT_ID_FIELD.get(item);
                if (v instanceof Number) {
                    long id = ((Number) v).longValue();
                    if (id > 0) return id;
                }
            } catch (Throwable ignored) {
            }
        }
        return 0L;
    }

    private static void findCommentIdField(Object item) {
        if (item == null || COMMENT_ID_FIELD != null) return;
        Field bestField = null;
        int bestScore = 0;
        Field[] fields = item.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(item);
                if (!(v instanceof Number)) continue;
                long id = ((Number) v).longValue();
                if (id <= 0) continue;
                if (id < 100_000_000L || id > 9_999_999_999_999L) {
                    continue;
                }
                int score = 1;
                String n = f.getName();
                if (n != null) {
                    String s = n.toLowerCase();
                    if (s.contains("rpid") || s.contains("reply") || s.contains("id")) score += 3;
                    if (s.contains("root") || s.contains("parent")) score += 1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestField = f;
                }
            } catch (Throwable ignored) {
            }
        }
        if (bestField != null) {
            COMMENT_ID_FIELD = bestField;
        }
    }

    private static long getRootId(Object item) {
        if (item == null) return 0L;
        long root = callLongMethod(item, "O");
        if (root != 0) return root;
        long parent = callLongMethod(item, "M");
        if (parent != 0) return parent;
        root = getLongField(item, "f55115d");
        if (root != 0) return root;
        parent = getLongField(item, "f55116e");
        if (parent != 0) return parent;
        long id = getId(item);
        return id;
    }

    private static long getLongField(Object item, String name) {
        try {
            return XposedHelpers.getLongField(item, name);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long callLongMethod(Object item, String name) {
        try {
            Object v = XposedHelpers.callMethod(item, name);
            if (v instanceof Number) return ((Number) v).longValue();
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static int callIntMethod(Object obj, String name) {
        if (obj == null) return Integer.MIN_VALUE;
        try {
            Object v = XposedHelpers.callMethod(obj, name);
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Throwable ignored) {
        }
        return Integer.MIN_VALUE;
    }

    private static void trySetField(Object obj, String name, Object value) {
        if (obj == null || name == null) return;
        try {
            XposedHelpers.setObjectField(obj, name, value);
        } catch (Throwable ignored) {
        }
    }

    private static void tryCall(Object obj, String name, Object... args) {
        if (obj == null || name == null) return;
        try {
            XposedHelpers.callMethod(obj, name, args);
        } catch (Throwable ignored) {
        }
    }

    private static void forceUnfold(Object item) {
        if (item == null) return;
        try {
            XposedHelpers.setBooleanField(item, "f55132u", false);
        } catch (Throwable ignored) {
        }
        normalizeFoldFlags(item, new HashSet<Object>(), getId(item));
    }

    private static void normalizeFoldFlags(Object obj, Set<Object> visited, long commentId) {
        if (obj == null) return;
        if (visited == null) visited = new HashSet<>();
        if (visited.contains(obj)) return;
        visited.add(obj);
        try {
            tryCall(obj, "setIsFolded", false);
            tryCall(obj, "setFolded", false);
            tryCall(obj, "setFold", false);
            tryCall(obj, "setIsFoldedReply", false);
            tryCall(obj, "setFoldedReply", false);
        } catch (Throwable ignored) {
        }
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            String name = f.getName();
            if (name == null) continue;
            String n = name.toLowerCase();
            boolean foldFlag = n.contains("fold") || n.contains("invisible") || n.contains("hidden");
            boolean blockFlag = n.contains("blocked") || n.contains("block");
            boolean actionFlag = n.contains("action");
            if (!foldFlag && !blockFlag && !actionFlag && !n.contains("replycontrol") && !n.contains("control")) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                Class<?> t = f.getType();
                if (t == boolean.class || t == Boolean.class) {
                    if (foldFlag || blockFlag) {
                        f.setBoolean(obj, false);
                    }
                } else if (Number.class.isAssignableFrom(t) || t.isPrimitive()) {
                    if (foldFlag) {
                        if (t == int.class || t == Integer.class) f.set(obj, 0);
                        else if (t == long.class || t == Long.class) f.set(obj, 0L);
                        else if (t == short.class || t == Short.class) f.set(obj, (short) 0);
                        else if (t == byte.class || t == Byte.class) f.set(obj, (byte) 0);
                    } else if (actionFlag) {
                        if (t == long.class || t == Long.class) f.set(obj, Long.MAX_VALUE);
                        else if (t == int.class || t == Integer.class) f.set(obj, -1);
                    }
                } else if (v != null) {
                    String typeName = t.getName();
                    if (typeName.contains("ReplyControl") || n.contains("replycontrol")) {
                        markReplyControl(v, commentId);
                        normalizeFoldFlags(v, visited, commentId);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void markReplyControl(Object obj, long commentId) {
        if (obj == null) return;
        try {
            XposedHelpers.setAdditionalInstanceField(obj, "BiliFoldsForceAction", Boolean.TRUE);
            if (commentId != 0L) {
                XposedHelpers.setAdditionalInstanceField(obj, "BiliFoldsCommentId", commentId);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isReplyControlMarked(Object obj) {
        if (obj == null) return false;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(obj, "BiliFoldsForceAction");
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markFoldedTag(Object item) {
        if (item == null || !isCommentItem(item)) return;
        try {
            Object existed = XposedHelpers.getAdditionalInstanceField(item, "BiliFoldsTag");
            if (existed != null) return;
        } catch (Throwable ignored) {
        }
        List<?> tags = getCommentTags(item);
        if (tags == null) return;
        if (containsFoldTag(tags)) {
            try {
                XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsTag", Boolean.TRUE);
            } catch (Throwable ignored) {
            }
            return;
        }
        Object tag = buildFoldTag(item.getClass().getClassLoader());
        if (tag == null) return;
        ArrayList<Object> out = new ArrayList<>(tags.size() + 1);
        out.addAll((List<?>) tags);
        out.add(tag);
        try {
            XposedHelpers.setObjectField(item, "f55127p", out);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsTag", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    private static boolean containsFoldTag(List<?> tags) {
        if (tags == null) return false;
        for (Object t : tags) {
            if (t == null) continue;
            String text = extractTagText(t);
            if (FOLD_TAG_TEXT.equals(text)) return true;
        }
        return false;
    }

    private static List<?> getCommentTags(Object item) {
        if (item == null || !isCommentItem(item)) return null;
        List<?> tags = null;
        try {
            Object v = XposedHelpers.callMethod(item, "getTags");
            if (v instanceof List) tags = (List<?>) v;
        } catch (Throwable ignored) {
        }
        if (tags == null) {
            try {
                Object v = XposedHelpers.getObjectField(item, "f55127p");
                if (v instanceof List) tags = (List<?>) v;
            } catch (Throwable ignored) {
            }
        }
        return tags;
    }

    private static String extractTagText(Object tag) {
        if (tag == null) return null;
        Object label = null;
        try {
            label = XposedHelpers.callMethod(tag, "b");
        } catch (Throwable ignored) {
            try {
                label = XposedHelpers.getObjectField(tag, "f55205c");
            } catch (Throwable ignored2) {
            }
        }
        if (label == null) return null;
        String text = callStringMethod(label, "e");
        if (text == null) {
            text = getStringField(label, "f55208a");
        }
        return text;
    }

    private static Object buildFoldTag(ClassLoader cl) {
        if (cl == null) return null;
        if (FOLD_TAG_TEMPLATE != null && FOLD_TAG_CL == cl) {
            return FOLD_TAG_TEMPLATE;
        }
        try {
            Class<?> tagCls = XposedHelpers.findClassIfExists(
                    "com.bilibili.app.comment3.data.model.CommentItem$g",
                    cl
            );
            Class<?> displayCls = XposedHelpers.findClassIfExists(
                    "com.bilibili.app.comment3.data.model.CommentItem$g$a",
                    cl
            );
            Class<?> labelCls = XposedHelpers.findClassIfExists(
                    "com.bilibili.app.comment3.data.model.CommentItem$g$b",
                    cl
            );
            if (tagCls == null || displayCls == null || labelCls == null) return null;
            Object display = XposedHelpers.newInstance(displayCls, false, 0L);
            Object label = XposedHelpers.newInstance(
                    labelCls,
                    FOLD_TAG_TEXT,
                    FOLD_TAG_TEXT_COLOR_DAY,
                    FOLD_TAG_TEXT_COLOR_NIGHT,
                    FOLD_TAG_BG_DAY,
                    FOLD_TAG_BG_NIGHT,
                    FOLD_TAG_JUMP,
                    null,
                    null
            );
            Object tag = XposedHelpers.newInstance(tagCls, display, null, label);
            FOLD_TAG_TEMPLATE = tag;
            FOLD_TAG_CL = cl;
            return tag;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isZipCard(Object item) {
        if (item == null || !"vv.r1".equals(item.getClass().getName())) return false;
        String text = callStringMethod(item, "h");
        if (text != null) {
            String t = text.trim();
            if (t.contains("\u5c55\u5f00\u66f4\u591a\u8bc4\u8bba")
                    || t.contains("\u5c55\u5f00\u66f4\u591a")
                    || t.contains("\u67e5\u770b\u66f4\u591a")) {
                return true;
            }
        }
        String offset = getZipCardOffset(item);
        return offset != null && !offset.isEmpty();
    }

    private static long getZipCardRootId(Object zipCard) {
        if (zipCard == null) return 0L;
        long v = 0L;
        String[] methods = new String[] {
                "getRootId",
                "getRoot",
                "getRpid",
                "getReplyId",
                "getId",
                "d",
                "e"
        };
        for (String m : methods) {
            v = callLongMethod(zipCard, m);
            if (v != 0) return v;
        }
        Field[] fields = zipCard.getClass().getDeclaredFields();
        for (Field f : fields) {
            String n = f.getName();
            if (n == null) continue;
            String s = n.toLowerCase();
            if (!s.contains("rpid") && !s.contains("root") && !s.contains("reply") && !s.contains("id")) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object val = f.get(zipCard);
                if (val instanceof Number) {
                    v = ((Number) val).longValue();
                    if (v != 0) return v;
                }
            } catch (Throwable ignored) {
            }
        }
        return 0L;
    }

    private static String getZipCardOffset(Object zipCard) {
        if (zipCard == null) return null;
        Object pagination = null;
        try {
            pagination = XposedHelpers.callMethod(zipCard, "g");
        } catch (Throwable ignored) {
        }
        if (pagination == null) {
            pagination = getObjectField(zipCard, "f380229b");
        }
        if (pagination == null) return null;
        String offset = callStringMethod(pagination, "e");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = callStringMethod(pagination, "getNext");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = callStringMethod(pagination, "getOffset");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = getStringField(pagination, "f380200e");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = getStringField(pagination, "next_");
        if (offset != null && !offset.isEmpty()) return offset;
        return null;
    }

    private static String extractOffsetFromObj(Object obj) {
        if (obj == null) return null;
        try {
            if ("vv.p0".equals(obj.getClass().getName())) {
                Object pagination = XposedHelpers.callMethod(obj, "b");
                return extractOffsetFromPagination(pagination);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object pagination = XposedHelpers.callMethod(obj, "getPagination");
            String off = extractOffsetFromPagination(pagination);
            if (off != null) return off;
        } catch (Throwable ignored) {
        }
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            String n = f.getName();
            if (n == null || !n.toLowerCase().contains("pagination")) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                String off = extractOffsetFromPagination(v);
                if (off != null) return off;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String extractOffsetFromPagination(Object pagination) {
        if (pagination == null) return null;
        String offset = callStringMethod(pagination, "getNext");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = callStringMethod(pagination, "getOffset");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = callStringMethod(pagination, "e");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = getStringField(pagination, "next_");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = getStringField(pagination, "offset_");
        if (offset != null && !offset.isEmpty()) return offset;
        return null;
    }

    private static List<?> extractList(Object obj) {
        if (obj == null) return null;
        if (obj instanceof List) return (List<?>) obj;
        List<?> best = null;
        int bestScore = -1;
        String[] methods = new String[] {"a", "getList", "getItems", "getReplies", "getComments", "getReplyList"};
        for (String m : methods) {
            try {
                Object v = XposedHelpers.callMethod(obj, m);
                if (v instanceof List) {
                    int score = scoreList((List<?>) v);
                    if (score > bestScore) {
                        bestScore = score;
                        best = (List<?>) v;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof List) {
                    int score = scoreList((List<?>) v);
                    if (score > bestScore) {
                        bestScore = score;
                        best = (List<?>) v;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return best;
    }

    private static int scoreList(List<?> list) {
        if (list == null) return -1;
        int score = 0;
        int checked = 0;
        int max = Math.min(20, list.size());
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (item == null) continue;
            checked++;
            if (isCommentItem(item)) {
                score += 10;
                continue;
            }
            String cn = item.getClass().getName();
            if ("vv.r1".equals(cn)) {
                score += 8;
                continue;
            }
            score += 1;
        }
        if (checked == 0) return 0;
        return score;
    }

    private static List<Object> collectCommentItems(Object obj, int depth) {
        if (obj == null || depth < 0) return null;
        java.util.IdentityHashMap<Object, Boolean> visited = new java.util.IdentityHashMap<>();
        ArrayList<Object> out = new ArrayList<>();
        collectCommentItemsInner(obj, depth, visited, out);
        return out.isEmpty() ? null : out;
    }

    private static void collectCommentItemsInner(Object obj, int depth, java.util.IdentityHashMap<Object, Boolean> visited, ArrayList<Object> out) {
        if (obj == null || depth < 0) return;
        if (visited.put(obj, Boolean.TRUE) != null) return;
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            for (Object item : list) {
                if (item == null) continue;
                if (isCommentItem(item)) {
                    out.add(item);
                } else if (depth > 0 && shouldScanObject(item)) {
                    collectCommentItemsInner(item, depth - 1, visited, out);
                }
            }
            return;
        }
        Class<?> c = obj.getClass();
        if (!shouldScanClass(c)) return;
        Field[] fields = c.getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v == null) continue;
                if (v instanceof List) {
                    collectCommentItemsInner(v, depth, visited, out);
                } else if (depth > 0 && shouldScanObject(v)) {
                    collectCommentItemsInner(v, depth - 1, visited, out);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean shouldScanObject(Object obj) {
        if (obj == null) return false;
        return shouldScanClass(obj.getClass());
    }

    private static boolean shouldScanClass(Class<?> c) {
        if (c == null) return false;
        String name = c.getName();
        if (name == null) return false;
        if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.")) return false;
        return true;
    }

    private static HashSet<Long> collectCommentIds(List<?> list) {
        HashSet<Long> ids = new HashSet<>();
        if (list == null) return ids;
        for (Object item : list) {
            if (!isCommentItem(item)) continue;
            long id = getId(item);
            if (id != 0) ids.add(id);
        }
        return ids;
    }

    private static void sortByCreateTime(List<Object> list, boolean desc) {
        if (list == null || list.size() < 2) return;
        Collections.sort(list, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                long t1 = getCreateTime(o1);
                long t2 = getCreateTime(o2);
                if (t1 == t2) {
                    long id1 = getId(o1);
                    long id2 = getId(o2);
                    return Long.compare(id1, id2);
                }
                return desc ? Long.compare(t2, t1) : Long.compare(t1, t2);
            }
        });
    }

    private static Boolean detectTimeOrderDesc(List<?> list) {
        if (list == null || list.size() < 3) return null;
        int checked = 0;
        int desc = 0;
        int asc = 0;
        long prev = 0L;
        boolean havePrev = false;
        for (Object item : list) {
            if (!isCommentItem(item)) continue;
            long t = getCreateTime(item);
            if (t == 0) continue;
            if (havePrev) {
                if (t < prev) desc++;
                else if (t > prev) asc++;
                checked++;
                if (checked >= 8) break;
            }
            prev = t;
            havePrev = true;
        }
        if (checked < 3) return null;
        if (desc >= asc + 2) return true;
        if (asc >= desc + 2) return false;
        return null;
    }

    private static boolean isSortableCommentItem(Object item) {
        return isCommentItem(item) && getCreateTime(item) > 0;
    }

    private static List<Object> reorderCommentsByTime(List<Object> list) {
        if (list == null || list.size() < 2) return null;
        ArrayList<Object> comments = new ArrayList<>();
        ArrayList<Long> original = new ArrayList<>();
        for (Object item : list) {
            if (!isSortableCommentItem(item)) continue;
            comments.add(item);
            original.add(getId(item));
        }
        if (comments.size() < 2) return null;
        Collections.sort(comments, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                long t1 = getCreateTime(o1);
                long t2 = getCreateTime(o2);
                if (t1 != t2) {
                    return Long.compare(t1, t2);
                }
                long id1 = getId(o1);
                long id2 = getId(o2);
                return Long.compare(id1, id2);
            }
        });
        ArrayList<Long> sorted = new ArrayList<>(comments.size());
        for (Object c : comments) sorted.add(getId(c));
        if (original.equals(sorted)) return null;
        ArrayList<Object> out = new ArrayList<>(list.size());
        int idx = 0;
        for (Object item : list) {
            if (isSortableCommentItem(item)) {
                out.add(comments.get(idx++));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    private static int getSortModeValue(Object sortMode) {
        if (sortMode == null) return Integer.MIN_VALUE;
        if (sortMode instanceof Number) {
            return ((Number) sortMode).intValue();
        }
        try {
            if (sortMode instanceof Enum) {
                return ((Enum<?>) sortMode).ordinal();
            }
        } catch (Throwable ignored) {
        }
        int v = callIntMethod(sortMode, "getValue");
        if (v != Integer.MIN_VALUE) return v;
        v = callIntMethod(sortMode, "getMode");
        if (v != Integer.MIN_VALUE) return v;
        return callIntMethod(sortMode, "ordinal");
    }

    private static Object getObjectField(Object obj, String name) {
        if (obj == null) return null;
        try {
            return XposedHelpers.getObjectField(obj, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String getStringField(Object obj, String name) {
        if (obj == null) return null;
        try {
            Object v = XposedHelpers.getObjectField(obj, name);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String callStringMethod(Object obj, String name) {
        if (obj == null) return null;
        try {
            Object v = XposedHelpers.callMethod(obj, name);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String safeToString(Object obj) {
        if (obj == null) return "";
        try {
            return String.valueOf(obj);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void mergeUniqueById(ArrayList<Object> dst, List<Object> src) {
        if (src == null || src.isEmpty()) return;
        Set<Long> seen = new HashSet<>();
        for (Object o : dst) {
            long id = getId(o);
            if (id != 0) seen.add(id);
        }
        for (Object o : src) {
            long id = getId(o);
            if (id != 0 && seen.contains(id)) continue;
            dst.add(o);
            if (id != 0) seen.add(id);
        }
    }

    private static boolean containsFooterCard(List<?> list) {
        if (list == null) return false;
        for (Object item : list) {
            if (isFooterCard(item)) return true;
        }
        return false;
    }

    private static boolean isFooterCard(Object item) {
        if (item == null || !"vv.r1".equals(item.getClass().getName())) return false;
        String text = callStringMethod(item, "h");
        if (text == null) return false;
        String t = text.trim();
        return t.contains("\u518d\u600e\u4e48\u627e\u4e5f\u6ca1\u6709")
                || t.contains("\u6ca1\u6709\u66f4\u591a\u8bc4\u8bba")
                || t.contains("\u6ca1\u6709\u66f4\u591a\u4e86")
                || t.contains("\u8fd9\u91cc\u662f\u8bc4\u8bba\u533a");
    }

    private static void postToMain(Runnable r) {
        try {
            android.os.Handler h = MAIN_HANDLER;
            if (h == null) {
                h = new android.os.Handler(android.os.Looper.getMainLooper());
                MAIN_HANDLER = h;
            }
            h.post(r);
        } catch (Throwable ignored) {
        }
    }

    private static void postToMainDelayed(Runnable r, long delayMs) {
        try {
            android.os.Handler h = MAIN_HANDLER;
            if (h == null) {
                h = new android.os.Handler(android.os.Looper.getMainLooper());
                MAIN_HANDLER = h;
            }
            h.postDelayed(r, delayMs);
        } catch (Throwable ignored) {
        }
    }

    private static boolean scheduleFooterRetry(String offset) {
        String key = makeOffsetKey(offset, getCurrentSubjectKey());
        if (key == null) key = offset;
        if (key == null || key.isEmpty()) return false;
        final String finalKey = key;
        final String finalOffset = offset;
        AtomicInteger cnt = FOOTER_RETRY_COUNT.computeIfAbsent(finalKey, k -> new AtomicInteger(0));
        int attempt = cnt.incrementAndGet();
        if (attempt > FOOTER_RETRY_LIMIT) {
            FOOTER_RETRY_PENDING.remove(finalKey);
            return false;
        }
        if (FOOTER_RETRY_PENDING.putIfAbsent(finalKey, Boolean.TRUE) != null) return true;
        postToMainDelayed(new Runnable() {
            @Override
            public void run() {
                FOOTER_RETRY_PENDING.remove(finalKey);
                tryUpdateCommentAdapterList(finalOffset);
            }
        }, 260L);
        return true;
    }

    private static void clearFooterRetry(String offset) {
        String key = makeOffsetKey(offset, getCurrentSubjectKey());
        if (key == null) key = offset;
        if (key == null || key.isEmpty()) return;
        FOOTER_RETRY_COUNT.remove(key);
        FOOTER_RETRY_PENDING.remove(key);
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
        Log.i(TAG, msg);
    }
}


