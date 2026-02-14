package io.github.frisk1127.bilifolds;

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
    private static final List<String> TARGET_PACKAGES = Arrays.asList(
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "com.bilibili.app.blue",
            "com.bilibili.app.dev"
    );
    private static volatile boolean loggedFoldStack = false;
    private static final ConcurrentHashMap<String, AtomicInteger> LOG_COUNT = new ConcurrentHashMap<>();
    private static final int LOG_LIMIT = 30;
    private static final ConcurrentHashMap<Long, ArrayList<Object>> FOLD_CACHE = new ConcurrentHashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }

        log("hooked pkg=" + lpparam.packageName + " cl=" + lpparam.classLoader);
        ClassLoader cl = lpparam.classLoader;
        hookReplyControl(cl);
        hookSubjectControl(cl);
        hookCommentItem(cl);
        hookFoldCardDebug(cl);
        hookDataSourceDebug(cl);
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
                logN("ReplyControl.getIsFoldedReply", "ReplyControl.getIsFoldedReply -> false");
                return false;
            }
        });

        XposedHelpers.findAndHookMethod(c, "getHasFoldedReply", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                logN("ReplyControl.getHasFoldedReply", "ReplyControl.getHasFoldedReply -> false");
                return false;
            }
        });
    }

    private static void hookSubjectControl(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.SubjectControl",
                cl
        );
        if (c == null) {
            log("SubjectControl class not found");
            return;
        }

        XposedHelpers.findAndHookMethod(c, "getHasFoldedReply", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                logN("SubjectControl.getHasFoldedReply", "SubjectControl.getHasFoldedReply -> false");
                return false;
            }
        });
    }

    private static void hookCommentItem(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.model.CommentItem",
                cl
        );
        if (c == null) {
            log("CommentItem class not found");
            return;
        }

        XposedHelpers.findAndHookMethod(c, "D", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                logN("CommentItem.D", "CommentItem.D -> false");
                return false;
            }
        });

        XposedHelpers.findAndHookMethod(c, "V", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                logN("CommentItem.V", "CommentItem.V called");
                Object list = null;
                try {
                    list = XposedHelpers.callMethod(param.thisObject, "w");
                } catch (Throwable ignored) {
                    try {
                        list = XposedHelpers.getObjectField(param.thisObject, "f55134w");
                    } catch (Throwable ignored2) {
                    log("failed to fetch childItemList");
                    }
                }
                Object foldList = null;
                try {
                    Object foldInfo = XposedHelpers.callMethod(param.thisObject, "C");
                    if (foldInfo != null) {
                        foldList = XposedHelpers.callMethod(foldInfo, "d");
                    }
                } catch (Throwable ignored) {
                    try {
                        Object foldInfo = XposedHelpers.getObjectField(param.thisObject, "f55133v");
                        if (foldInfo != null) {
                            foldList = XposedHelpers.getObjectField(foldInfo, "f55167b");
                        }
                    } catch (Throwable ignored2) {
                        log("failed to fetch foldChildItemList");
                    }
                }

                if (foldList instanceof java.util.List && list instanceof java.util.List) {
                    java.util.List<?> child = (java.util.List<?>) list;
                    java.util.List<?> fold = (java.util.List<?>) foldList;
                    long rootId = getRootId(param.thisObject);
                    ArrayList<Object> cached = rootId > 0 ? FOLD_CACHE.get(rootId) : null;
                    int cachedSize = cached == null ? 0 : cached.size();
                    logN("CommentItem.V.counts", "child=" + child.size() + " fold=" + fold.size() + " cache=" + cachedSize + " root=" + rootId);
                    if (!fold.isEmpty() || cachedSize > 0) {
                        ArrayList<Object> merged = new ArrayList<>(child.size() + fold.size() + cachedSize);
                        Set<Long> seen = new HashSet<>();
                        appendUnique(merged, seen, child);
                        appendUnique(merged, seen, fold);
                        if (cached != null && !cached.isEmpty()) {
                            appendUnique(merged, seen, cached);
                        }
                        Collections.sort(merged, new Comparator<Object>() {
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
                        return merged;
                    }
                }

                return list;
            }
        });
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
        return 0L;
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
        return 0L;
    }

    private static void hookFoldCardDebug(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.FoldCard",
                cl
        );
        if (c == null) {
            log("FoldCard class not found");
            return;
        }

        XposedHelpers.findAndHookMethod(c, "getFoldPagination", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (loggedFoldStack) return;
                loggedFoldStack = true;
                log("FoldCard.getFoldPagination hit");
                log(Log.getStackTraceString(new Throwable("FoldPagination stack")));
            }
        });

        XposedHelpers.findAndHookMethod(c, "hasFoldPagination", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                logN("FoldCard.hasFoldPagination", "FoldCard.hasFoldPagination -> " + result);
            }
        });
    }

    private static void hookDataSourceDebug(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.source.v1.e",
                cl
        );
        if (c == null) {
            log("data source class e not found");
            return;
        }

        XposedBridge.hookAllMethods(c, "A0", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                scanForFoldReplies("e.A0.result", param.getResult());
                scanArgsForFoldReplies("e.A0.args", param.args);
            }
        });

        XposedBridge.hookAllMethods(c, "F0", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                scanForFoldReplies("e.F0.result", param.getResult());
                scanArgsForFoldReplies("e.F0.args", param.args);
            }
        });
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
        Log.i(TAG, msg);
    }

    private static void logN(String key, String msg) {
        AtomicInteger count = LOG_COUNT.computeIfAbsent(key, k -> new AtomicInteger());
        if (count.incrementAndGet() <= LOG_LIMIT) {
            log(msg);
        }
    }

    private static void appendUnique(ArrayList<Object> out, Set<Long> seen, List<?> list) {
        for (Object o : list) {
            if (o == null) continue;
            long id = getId(o);
            if (id != 0 && !seen.add(id)) {
                continue;
            }
            forceUnfold(o);
            out.add(o);
        }
    }

    private static void scanArgsForFoldReplies(String tag, Object[] args) {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a != null) {
                logN("args." + tag, tag + " arg[" + i + "]=" + a.getClass().getName());
            }
            scanForFoldReplies(tag + ".arg" + i, a);
        }
    }

    private static void scanForFoldReplies(String tag, Object obj) {
        if (obj == null) return;
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            String itemType = list.isEmpty() || list.get(0) == null ? "null" : list.get(0).getClass().getName();
            logN("list." + tag, tag + " list size=" + list.size() + " item=" + itemType);
            if (!list.isEmpty()) {
                cacheFoldReplies(tag, list);
            }
            return;
        }
        logN("obj." + tag, tag + " obj=" + obj.getClass().getName());
        Class<?> c = obj.getClass();
        java.lang.reflect.Field[] fields = c.getDeclaredFields();
        for (java.lang.reflect.Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof List) {
                    List<?> list = (List<?>) v;
                    if (!list.isEmpty()) {
                        String itemType = list.get(0) == null ? "null" : list.get(0).getClass().getName();
                        logN("field." + tag, tag + " field=" + f.getName() + " size=" + list.size() + " item=" + itemType);
                        cacheFoldReplies(tag + "." + f.getName(), list);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void cacheFoldReplies(String tag, List<?> list) {
        int cached = 0;
        for (Object o : list) {
            if (!isCommentItem(o)) continue;
            long rootId = getRootId(o);
            if (rootId <= 0) continue;
            ArrayList<Object> bucket = FOLD_CACHE.computeIfAbsent(rootId, k -> new ArrayList<>());
            long id = getId(o);
            boolean exists = false;
            if (id != 0) {
                for (Object e : bucket) {
                    if (getId(e) == id) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                forceUnfold(o);
                bucket.add(o);
                cached++;
            }
        }
        if (cached > 0) {
            logN("cache." + tag, "cache " + tag + " add=" + cached);
        }
    }

    private static boolean isCommentItem(Object item) {
        return item != null && "com.bilibili.app.comment3.data.model.CommentItem".equals(item.getClass().getName());
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
            if (v instanceof Long) return (Long) v;
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static void forceUnfold(Object item) {
        if (item == null) return;
        try {
            XposedHelpers.setBooleanField(item, "f55132u", false);
        } catch (Throwable ignored) {
        }
    }
}
