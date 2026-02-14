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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

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
    private static final int LOG_LIMIT = 80;
    private static final ConcurrentHashMap<Long, ArrayList<Object>> FOLD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ArrayList<Object>> FOLD_CACHE_BY_OFFSET = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<ArrayList<Object>> FOLD_CACHE_QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<Object, String> CONTINUATION_OFFSET = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> RESP_OFFSET = new ConcurrentHashMap<>();
    private static volatile WeakReference<Object> LAST_ADAPTER = new WeakReference<>(null);
    private static volatile List<?> LAST_LIST = null;
    private static volatile android.os.Handler MAIN_HANDLER = null;
    private static volatile WeakReference<Object> LAST_RV_ADAPTER = new WeakReference<>(null);
    private static volatile Field LAST_RV_LIST_FIELD = null;
    private static final ThreadLocal<Boolean> RESUBMITTING = new ThreadLocal<>();

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
        hookZipDataSource(cl);
        hookReplyMossKtx(cl);
        hookCoroutineResume(cl);
        hookListAdapter(cl);
        hookRecyclerViewAdapter(cl);
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
                Object replaced = tryReplaceFoldCard("e.A0.result", param.getResult());
                if (replaced != null) {
                    param.setResult(replaced);
                }
            }
        });

        XposedBridge.hookAllMethods(c, "F0", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                scanForFoldReplies("e.F0.result", param.getResult());
                scanArgsForFoldReplies("e.F0.args", param.args);
            }
        });

        XposedBridge.hookAllMethods(c, "D0", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object resp = param.args != null && param.args.length > 0 ? param.args[0] : null;
                String offset = resp == null ? null : RESP_OFFSET.remove(System.identityHashCode(resp));
                cacheFoldListResult("e.D0.result", offset, param.getResult());
            }
        });
    }

    private static void hookListAdapter(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "androidx.recyclerview.widget.ListAdapter",
                cl
        );
        if (c == null) {
            log("ListAdapter class not found");
            return;
        }
        XposedBridge.hookAllMethods(c, "submitList", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0) return;
                Object listObj = param.args[0];
                if (!(listObj instanceof List)) return;
                List<?> list = (List<?>) listObj;
                Object adapter = param.thisObject;
                if (!isCommentAdapter(adapter, list)) return;
                List<?> replaced = maybeReplaceFoldCardsInList(list);
                if (replaced != null) {
                    param.args[0] = replaced;
                    list = replaced;
                    logN("submitList.replace", "submitList replace fold cards size=" + replaced.size());
                }
                LAST_ADAPTER = new WeakReference<>(adapter);
                LAST_LIST = list;
            }
        });
    }

    private static void hookRecyclerViewAdapter(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "androidx.recyclerview.widget.RecyclerView$Adapter",
                cl
        );
        if (c == null) {
            log("RecyclerView.Adapter class not found");
            return;
        }
        XC_MethodHook captureHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object adapter = param.thisObject;
                captureRecyclerAdapter(adapter);
            }
        };
        XposedBridge.hookAllMethods(c, "notifyDataSetChanged", captureHook);
        XposedBridge.hookAllMethods(c, "notifyItemRangeInserted", captureHook);
        XposedBridge.hookAllMethods(c, "notifyItemRangeChanged", captureHook);
        XposedBridge.hookAllMethods(c, "notifyItemRangeRemoved", captureHook);
        XposedBridge.hookAllMethods(c, "notifyItemInserted", captureHook);
        XposedBridge.hookAllMethods(c, "notifyItemRemoved", captureHook);

        XposedBridge.hookAllMethods(c, "onBindViewHolder", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 2) return;
                Object adapter = param.thisObject;
                int pos = -1;
                try {
                    if (param.args[1] instanceof Integer) {
                        pos = (Integer) param.args[1];
                    }
                } catch (Throwable ignored) {
                }
                if (pos < 0) return;
                Field f = findListFieldByPosition(adapter, pos);
                if (f != null) {
                    LAST_RV_ADAPTER = new WeakReference<>(adapter);
                    LAST_RV_LIST_FIELD = f;
                    logN("adapter.capture.bind", "capture by bind adapter=" + adapter.getClass().getName() + " field=" + f.getName());
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
                Object offset = param.args[1];
                Object cont = param.args[param.args.length - 1];
                if (offset instanceof String && cont != null) {
                    CONTINUATION_OFFSET.put(cont, (String) offset);
                    String extra = param.args.length >= 3 ? safeToString(param.args[2]) : "";
                    logN("ZipDataSourceV1.a", "ZipDataSourceV1.a offset=" + offset + " extra=" + extra + " cont=" + cont.getClass().getName());
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

    private static void hookReplyMossKtx(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.source.v1.ReplyMossKtxKt",
                cl
        );
        if (c == null) {
            c = XposedHelpers.findClassIfExists(
                    "com.bilibili.app.comment3.data.source.ReplyMossKtxKt",
                    cl
            );
        }
        if (c == null) {
            log("ReplyMossKtxKt class not found");
            return;
        }
        XposedBridge.hookAllMethods(c, "suspendFoldList", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 2) return;
                Object req = param.args[1];
                Object cont = param.args[param.args.length - 1];
                String offset = extractOffsetFromFoldReq(req);
                if (offset != null && cont != null) {
                    CONTINUATION_OFFSET.put(cont, offset);
                    logN("suspendFoldList", "suspendFoldList offset=" + offset + " cont=" + cont.getClass().getName());
                } else {
                    logN("suspendFoldList.null", "suspendFoldList offset=null req=" + safeClass(req));
                }
            }
        });
    }

    private static void hookCoroutineResume(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "kotlin.coroutines.jvm.internal.BaseContinuationImpl",
                cl
        );
        if (c == null) {
            log("BaseContinuationImpl class not found");
            return;
        }
        XposedHelpers.findAndHookMethod(c, "resumeWith", Object.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String offset = CONTINUATION_OFFSET.remove(param.thisObject);
                if (offset == null) return;
                Object result = param.args != null && param.args.length > 0 ? param.args[0] : null;
                if (isKotlinFailure(result)) {
                    logN("resumeWith.fail", "resumeWith failure offset=" + offset + " result=" + safeClass(result));
                    return;
                }
                if (isZipListResult(result)) {
                    cacheFoldListResult("resumeWith.p0", offset, result);
                    logN("resumeWith.p0", "resumeWith p0 offset=" + offset + " result=" + safeClass(result));
                    return;
                }
                if (isFoldListResp(result)) {
                    RESP_OFFSET.put(System.identityHashCode(result), offset);
                    logN("resumeWith.fold", "resumeWith foldResp offset=" + offset + " resp=" + safeClass(result));
                } else {
                    logN("resumeWith.other", "resumeWith offset=" + offset + " result=" + safeClass(result));
                }
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

    private static void cacheFoldListResult(String tag, String offset, Object obj) {
        List<?> list = extractList(obj);
        if (list == null || list.isEmpty()) {
            if (offset != null) {
                logN("cache.empty." + tag, tag + " offset=" + offset + " empty");
            }
            return;
        }
        ArrayList<Object> bucket = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o == null) continue;
            if (!isCommentItem(o)) continue;
            forceUnfold(o);
            bucket.add(o);
        }
        if (bucket.isEmpty()) return;
        if (offset != null) {
            ArrayList<Object> existing = FOLD_CACHE_BY_OFFSET.computeIfAbsent(offset, k -> new ArrayList<>());
            mergeUniqueById(existing, bucket);
            logN("cache.offset." + tag, tag + " offset=" + offset + " add=" + bucket.size());
            tryResubmitWithCache(offset);
            tryUpdateAdapterList(offset);
        } else {
            FOLD_CACHE_QUEUE.add(bucket);
            logN("cache.queue." + tag, tag + " queued=" + bucket.size());
            tryResubmitWithCache(null);
            tryUpdateAdapterList(null);
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

    private static Object tryReplaceFoldCard(String tag, Object detailList) {
        if (detailList == null) return null;
        List<?> list = extractList(detailList);
        if (list == null || list.isEmpty()) return null;
        ArrayList<Object> out = new ArrayList<>(list.size());
        boolean changed = false;
        for (Object item : list) {
            if (isZipCard(item)) {
                String offset = getZipCardOffset(item);
                ArrayList<Object> cached = offset == null ? null : FOLD_CACHE_BY_OFFSET.get(offset);
                if (cached == null || cached.isEmpty()) {
                    int qSize = FOLD_CACHE_QUEUE.size();
                    if (offset == null || qSize == 1) {
                        cached = FOLD_CACHE_QUEUE.poll();
                    }
                }
                if (cached != null && !cached.isEmpty()) {
                    logN("replace." + tag, tag + " replace fold card offset=" + offset + " items=" + cached.size());
                    for (Object o : cached) {
                        forceUnfold(o);
                        out.add(o);
                    }
                    changed = true;
                    continue;
                } else {
                    logN("replace.none." + tag, tag + " fold card no cache offset=" + offset);
                }
            }
            out.add(item);
        }
        if (!changed) return null;
        Object newResult = null;
        try {
            newResult = XposedHelpers.callMethod(detailList, "j", out);
        } catch (Throwable ignored) {
        }
        if (newResult != null) {
            return newResult;
        }
        if (setFirstListField(detailList, out)) {
            return detailList;
        }
        return null;
    }

    private static List<?> maybeReplaceFoldCardsInList(List<?> list) {
        if (list == null || list.isEmpty()) return null;
        ArrayList<Object> out = new ArrayList<>(list.size());
        boolean changed = false;
        for (Object item : list) {
            if (isZipCard(item)) {
                String offset = getZipCardOffset(item);
                ArrayList<Object> cached = offset == null ? null : FOLD_CACHE_BY_OFFSET.get(offset);
                if (cached == null || cached.isEmpty()) {
                    out.add(item);
                    continue;
                }
                for (Object o : cached) {
                    forceUnfold(o);
                    out.add(o);
                }
                changed = true;
                continue;
            }
            out.add(item);
        }
        return changed ? out : null;
    }

    private static boolean isZipCard(Object item) {
        return item != null && "vv.r1".equals(item.getClass().getName());
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

    private static String extractOffsetFromFoldReq(Object req) {
        if (req == null) return null;
        Object pagination = null;
        try {
            pagination = XposedHelpers.callMethod(req, "getPagination");
        } catch (Throwable ignored) {
        }
        if (pagination == null) {
            pagination = getObjectField(req, "pagination_");
        }
        if (pagination == null) {
            pagination = getObjectField(req, "pagination");
        }
        if (pagination == null) return null;
        String offset = callStringMethod(pagination, "getOffset");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = callStringMethod(pagination, "getNext");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = callStringMethod(pagination, "e");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = getStringField(pagination, "offset_");
        if (offset != null && !offset.isEmpty()) return offset;
        offset = getStringField(pagination, "next_");
        if (offset != null && !offset.isEmpty()) return offset;
        return null;
    }

    private static boolean isFoldListResp(Object obj) {
        if (obj == null) return false;
        String name = obj.getClass().getName();
        return name.endsWith("FoldListResp");
    }

    private static boolean isZipListResult(Object obj) {
        if (obj == null) return false;
        String name = obj.getClass().getName();
        return "vv.p0".equals(name);
    }

    private static boolean isCommentAdapter(Object adapter, List<?> list) {
        if (adapter == null || list == null) return false;
        String name = adapter.getClass().getName();
        if (!name.contains("comment") && !name.contains("Comment")) return false;
        int max = Math.min(50, list.size());
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (item == null) continue;
            String cn = item.getClass().getName();
            if ("com.bilibili.app.comment3.data.model.CommentItem".equals(cn) || "vv.r1".equals(cn)) {
                return true;
            }
        }
        return false;
    }

    private static void tryResubmitWithCache(String offset) {
        Object adapter = LAST_ADAPTER == null ? null : LAST_ADAPTER.get();
        List<?> list = LAST_LIST;
        if (adapter == null || list == null || list.isEmpty()) return;
        List<?> replaced = maybeReplaceFoldCardsInList(list);
        if (replaced == null) return;
        postToMain(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Boolean.TRUE.equals(RESUBMITTING.get())) return;
                    RESUBMITTING.set(true);
                    XposedHelpers.callMethod(adapter, "submitList", replaced);
                    LAST_LIST = replaced;
                    logN("submitList.resubmit", "resubmit list size=" + replaced.size());
                } catch (Throwable t) {
                    try {
                        XposedHelpers.callMethod(adapter, "submitList", replaced, null);
                        LAST_LIST = replaced;
                        logN("submitList.resubmit2", "resubmit(list, null) size=" + replaced.size());
                    } catch (Throwable ignored) {
                        logN("submitList.fail", "resubmit failed: " + t.getClass().getName());
                    }
                } finally {
                    RESUBMITTING.set(false);
                }
            }
        });
    }

    private static void tryUpdateAdapterList(String offset) {
        Object adapter = LAST_RV_ADAPTER == null ? null : LAST_RV_ADAPTER.get();
        Field listField = LAST_RV_LIST_FIELD;
        if (adapter == null || listField == null) return;
        try {
            Object listObj = listField.get(adapter);
            if (!(listObj instanceof List)) return;
            List<?> list = (List<?>) listObj;
            List<?> replaced = maybeReplaceFoldCardsInList(list);
            if (replaced == null) return;
            postToMain(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (Boolean.TRUE.equals(RESUBMITTING.get())) return;
                        RESUBMITTING.set(true);
                        listField.set(adapter, replaced);
                        XposedHelpers.callMethod(adapter, "notifyDataSetChanged");
                        logN("adapter.replace", "adapter replace list size=" + replaced.size() + " field=" + listField.getName());
                    } catch (Throwable t) {
                        logN("adapter.replace.fail", "adapter replace failed: " + t.getClass().getName());
                    } finally {
                        RESUBMITTING.set(false);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void captureRecyclerAdapter(Object adapter) {
        if (adapter == null) return;
        if (Boolean.TRUE.equals(RESUBMITTING.get())) return;
        Field f = findListField(adapter);
        if (f == null) return;
        LAST_RV_ADAPTER = new WeakReference<>(adapter);
        LAST_RV_LIST_FIELD = f;
        logN("adapter.capture", "capture adapter=" + adapter.getClass().getName() + " field=" + f.getName());
    }

    private static Field findListField(Object adapter) {
        String name = adapter.getClass().getName();
        Field[] fields = adapter.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(adapter);
                if (!(v instanceof List)) continue;
                List<?> list = (List<?>) v;
                if (list.isEmpty()) continue;
                if (containsCommentOrZip(list)) {
                    return f;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Field findListFieldByPosition(Object adapter, int position) {
        if (adapter == null) return null;
        Field[] fields = adapter.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(adapter);
                if (!(v instanceof List)) continue;
                List<?> list = (List<?>) v;
                if (position < 0 || position >= list.size()) continue;
                Object item = list.get(position);
                if (isCommentOrZip(item)) {
                    return f;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isCommentOrZip(Object item) {
        if (item == null) return false;
        String cn = item.getClass().getName();
        return "com.bilibili.app.comment3.data.model.CommentItem".equals(cn) || "vv.r1".equals(cn);
    }

    private static boolean containsCommentOrZip(List<?> list) {
        int max = Math.min(50, list.size());
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (item == null) continue;
            String cn = item.getClass().getName();
            if ("com.bilibili.app.comment3.data.model.CommentItem".equals(cn) || "vv.r1".equals(cn)) {
                return true;
            }
        }
        return false;
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

    private static boolean isKotlinFailure(Object obj) {
        if (obj == null) return false;
        String name = obj.getClass().getName();
        return name.endsWith("kotlin.Result$Failure") || name.contains("kotlin.Result$Failure");
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

    private static List<?> extractList(Object obj) {
        if (obj == null) return null;
        if (obj instanceof List) return (List<?>) obj;
        try {
            Object v = XposedHelpers.callMethod(obj, "a");
            if (v instanceof List) return (List<?>) v;
        } catch (Throwable ignored) {
        }
        java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
        for (java.lang.reflect.Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof List) return (List<?>) v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean setFirstListField(Object obj, List<?> list) {
        if (obj == null) return false;
        java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
        for (java.lang.reflect.Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof List) {
                    f.set(obj, list);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
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

    private static String safeClass(Object obj) {
        return obj == null ? "null" : obj.getClass().getName();
    }

    private static String safeToString(Object obj) {
        if (obj == null) return "null";
        try {
            String s = String.valueOf(obj);
            if (s.length() > 200) {
                return s.substring(0, 200) + "...";
            }
            return s;
        } catch (Throwable ignored) {
            return safeClass(obj);
        }
    }
}
