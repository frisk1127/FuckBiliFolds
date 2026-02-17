package io.github.frisk1127.bilifolds;

import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
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
    private static final String BUILD_TAG = "build-" + BuildConfig.GIT_SHA;
    private static final List<String> TARGET_PACKAGES = Arrays.asList(
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "com.bilibili.app.blue",
            "com.bilibili.app.dev"
    );

    private static final String FOLD_TAG_TEXT = "折叠评论";
    private static final String FOLD_TAG_TEXT_COLOR_DAY = "#FFE67E22";
    private static final String FOLD_TAG_TEXT_COLOR_NIGHT = "#FFFFC107";
    private static final String FOLD_TAG_BG_DAY = "#1AE67E22";
    private static final String FOLD_TAG_BG_NIGHT = "#33FFC107";
    private static final String FOLD_TAG_JUMP = "";

    private static final ConcurrentHashMap<Long, ArrayList<Object>> FOLD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ArrayList<Object>> FOLD_CACHE_BY_OFFSET = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> KNOWN_FOLD_OFFSET = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> OFFSET_TO_ROOT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> AUTO_FETCHING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> FOLDED_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> LOG_COUNT = new ConcurrentHashMap<>();
    private static final int LOG_LIMIT = 80;
    private static final Set<String> TAG_ACCESSOR_METHODS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private static final ConcurrentHashMap<String, AtomicInteger> FOOTER_RETRY_COUNT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> FOOTER_RETRY_PENDING = new ConcurrentHashMap<>();
    private static final int FOOTER_RETRY_LIMIT = 12;

    private static volatile WeakReference<Object> LAST_SUBJECT_ID = new WeakReference<>(null);
    private static volatile String LAST_SUBJECT_KEY = null;
    private static volatile String LAST_EXTRA = null;
    private static volatile Object LAST_SORT_MODE = null;
    private static volatile WeakReference<Object> LAST_COMMENT_ADAPTER = new WeakReference<>(null);
    private static final ThreadLocal<Boolean> RESUBMITTING = new ThreadLocal<>();
    private static volatile android.os.Handler MAIN_HANDLER = null;
    private static volatile ClassLoader APP_CL = null;

    private static volatile Object FOLD_TAG_TEMPLATE = null;
    private static volatile ClassLoader FOLD_TAG_CL = null;
    private static volatile Field COMMENT_TIME_FIELD = null;
    private static volatile boolean COMMENT_TIME_IS_MILLIS = false;
    static {
        TAG_ACCESSOR_METHODS.add("getTags");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }
        log("active " + BUILD_TAG + " pkg=" + lpparam.packageName);
        APP_CL = lpparam.classLoader;
        safeHook("hookReplyControl", new Runnable() { @Override public void run() { hookReplyControl(APP_CL); } });
        safeHook("hookCommentItem", new Runnable() { @Override public void run() { hookCommentItem(APP_CL); } });
        safeHook("hookCommentItemTags", new Runnable() { @Override public void run() { hookCommentItemTags(APP_CL); } });
        safeHook("hookCommentItemFoldFlags", new Runnable() { @Override public void run() { hookCommentItemFoldFlags(APP_CL); } });
        safeHook("hookZipDataSource", new Runnable() { @Override public void run() { hookZipDataSource(APP_CL); } });
        safeHook("hookDetailListDataSource", new Runnable() { @Override public void run() { hookDetailListDataSource(APP_CL); } });
        safeHook("hookCommentListAdapter", new Runnable() { @Override public void run() { hookCommentListAdapter(APP_CL); } });
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
    }

    private static void hookCommentItem(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bilibili.app.comment3.data.model.CommentItem",
                cl
        );
        if (c == null) {
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(c, "D", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return false;
                }
            });
        } catch (Throwable ignored) {
        }
        try {
            int hookCount = 0;
            hookCount += hookCommentItemTagMethod(c, "getTags");
            hookCount += hookCommentItemTagMethod(c, "V");
            hookCount += hookCommentItemTagMethod(c, "v");
            hookCount += hookCommentItemTagMethod(c, "W");
            hookCount += hookCommentItemTagMethod(c, "w");
            if (hookCount > 0) {
                log("CommentItem tag hook count=" + hookCount);
            } else {
                log("CommentItem tag hook disabled");
            }
        } catch (Throwable ignored) {
        }
    }

    private static int hookCommentItemTagMethod(Class<?> c, String methodName) {
        if (c == null || methodName == null || methodName.isEmpty()) return 0;
        int count = 0;
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (!methodName.equals(m.getName())) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts != null && pts.length != 0) continue;
            Class<?> rt = m.getReturnType();
            if (rt == null || !List.class.isAssignableFrom(rt)) continue;
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        injectFoldTagFromTagAccessor(param, methodName);
                    }
                });
                count++;
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    private static void injectFoldTagFromTagAccessor(XC_MethodHook.MethodHookParam param, String source) {
        Object item = param.thisObject;
        if (!shouldInjectFoldTag(item)) return;
        Object r = param.getResult();
        List<?> tags = r instanceof List ? (List<?>) r : null;
        boolean knownAccessor = TAG_ACCESSOR_METHODS.contains(source);
        if (!knownAccessor) {
            if (!isLikelyTagList(tags)) {
                if (tags != null && !tags.isEmpty()) {
                    logN("tag.inject.skip", "skip non-tag list source=" + source + " size=" + tags.size());
                }
                return;
            }
            TAG_ACCESSOR_METHODS.add(source);
            logN("tag.inject.learn", "learn tag accessor source=" + source);
        }
        if (containsFoldTag(tags)) {
            try {
                XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsTag", Boolean.TRUE);
            } catch (Throwable ignored) {
            }
            return;
        }
        Object tag = buildFoldTag(item == null ? null : item.getClass().getClassLoader());
        if (tag == null) {
            logN("tag.inject.fail", "inject tag failed: buildTag null id=" + getId(item) + " via=" + source);
            return;
        }
        ArrayList<Object> out = new ArrayList<>((tags == null ? 0 : tags.size()) + 1);
        if (tags != null) {
            out.addAll((List<?>) tags);
        }
        out.add(tag);
        param.setResult(out);
        try {
            XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsTag", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
        logN("tag.inject.ok", "inject tag via " + source + " id=" + getId(item));
    }

    private static boolean isLikelyTagList(List<?> list) {
        if (list == null || list.isEmpty()) return false;
        int checked = 0;
        for (Object o : list) {
            if (o == null) continue;
            checked++;
            String cn = o.getClass().getName();
            if (cn != null && cn.contains("CommentItem$g")) {
                return true;
            }
            String text = extractTagText(o);
            if (text != null && !text.isEmpty()) {
                return true;
            }
            if (checked >= 5) break;
        }
        return false;
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
                LAST_COMMENT_ADAPTER = new WeakReference<>(param.thisObject);
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
                if (!containsFooterCard(list)) {
                    prefetchFoldList(list);
                }
                List<?> replaced = replaceZipCardsInList(list, "CommentListAdapter.b1");
                if (replaced != null) {
                    param.args[0] = replaced;
                }
            }
        });
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
        cacheFoldReplies(list);
        ArrayList<Object> bucket = new ArrayList<>(list.size());
        long rootId = 0L;
        for (Object o : list) {
            if (o == null || !isCommentItem(o)) continue;
            forceUnfold(o);
            bucket.add(o);
            if (rootId == 0L) {
                rootId = getRootId(o);
            }
        }
        if (bucket.isEmpty()) return;
        for (Object o : bucket) {
            long id = getId(o);
            if (rootId != 0L && id == rootId) {
                continue;
            }
            if (id != 0) {
                FOLDED_IDS.put(id, Boolean.TRUE);
            }
        }
        String realOffset = offset != null ? offset : extractOffsetFromObj(obj);
        if (realOffset == null) {
            return;
        }
        markFoldOffset(realOffset);
        String key = makeOffsetKey(realOffset, getCurrentSubjectKey());
        if (key == null) {
            return;
        }
        ArrayList<Object> existing = FOLD_CACHE_BY_OFFSET.computeIfAbsent(key, k -> new ArrayList<>());
        mergeUniqueById(existing, bucket);
        if (rootId != 0L) {
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
        XposedHelpers.findAndHookMethod(c, "getTags", new XC_MethodHook() {
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

    private static List<?> replaceZipCardsInList(List<?> list, String tag) {
        if (list == null || list.isEmpty()) return null;
        Boolean desc = detectTimeOrderDesc(list);
        ArrayList<Object> out = new ArrayList<>(list.size());
        boolean changed = false;
        HashSet<Long> existingIds = collectCommentIds(list);
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (isZipCard(item)) {
                String offset = getZipCardOffset(item);
                long rootCandidate = getZipCardRootId(item);
                if (rootCandidate == 0L) {
                    rootCandidate = findPrevCommentRootId(list, i);
                }
                if (rootCandidate == 0L && offset != null) {
                    rootCandidate = getRootForOffset(offset);
                }
                if (offset != null && !offset.isEmpty() && rootCandidate > 0) {
                    OFFSET_TO_ROOT.put(offset, rootCandidate);
                }
                List<Object> cached = getCachedFoldListForZip(item, offset, desc);
                if ((cached == null || cached.isEmpty()) && offset != null) {
                    long rootHint = findPrevCommentRootId(list, i);
                    if (rootHint > 0) {
                        OFFSET_TO_ROOT.put(offset, rootHint);
                        ArrayList<Object> hinted = FOLD_CACHE.get(rootHint);
                        if (hinted != null && !hinted.isEmpty()) {
                            cached = new ArrayList<>(hinted);
                            sortByCreateTime(cached, desc != null && desc);
                        }
                    }
                }
                if (cached == null || cached.isEmpty()) {
                    tryAutoFetchFoldList(offset, getCurrentSubjectKey(), rootCandidate);
                    logN("zip.cache.miss", "zip miss offset=" + offset + " tag=" + tag);
                    out.add(item);
                    continue;
                }
                int inserted = 0;
                int useful = 0;
                for (Object o : cached) {
                    long id = getId(o);
                    boolean isRootComment = rootCandidate != 0L && id == rootCandidate;
                    if (!isRootComment) {
                        useful++;
                    }
                    if (id != 0 && existingIds.contains(id)) {
                        continue;
                    }
                    forceUnfold(o);
                    if (!isRootComment && id != 0) {
                        FOLDED_IDS.put(id, Boolean.TRUE);
                        markFoldedTag(o);
                    }
                    out.add(o);
                    if (id != 0) existingIds.add(id);
                    inserted++;
                }
                if (inserted == 0) {
                    if (useful == 0) {
                        out.add(item);
                    } else {
                        changed = true;
                    }
                    logN("zip.replace.zero", "zip replace zero inserted offset=" + offset + " useful=" + useful + " tag=" + tag);
                    continue;
                }
                logN("zip.replace.ok", "zip replace inserted=" + inserted + " offset=" + offset + " tag=" + tag);
                changed = true;
                continue;
            }
            out.add(item);
        }
        if (!changed) return null;
        List<Object> resorted = reorderCommentsByTime(out);
        if (resorted != null) return resorted;
        return out;
    }

    private static void prefetchFoldList(List<?> list) {
        if (list == null || list.isEmpty()) return;
        int max = Math.min(30, list.size());
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (!isZipCard(item)) continue;
            String offset = getZipCardOffset(item);
            long rootId = getZipCardRootId(item);
            if (rootId == 0L) {
                rootId = findPrevCommentRootId(list, i);
            }
            tryAutoFetchFoldList(offset, getCurrentSubjectKey(), rootId);
            return;
        }
    }

    private static List<Object> getCachedFoldListForZip(Object zipCard, String offset, Boolean desc) {
        ArrayList<Object> cached = null;
        cached = getCachedByOffset(offset, getCurrentSubjectKey());
        if ((cached == null || cached.isEmpty()) && offset != null) {
            long offsetRoot = getRootForOffset(offset);
            if (offsetRoot > 0) {
                cached = FOLD_CACHE.get(offsetRoot);
            }
        }
        if (cached == null || cached.isEmpty()) {
            long rootId = getZipCardRootId(zipCard);
            if (rootId > 0) {
                cached = FOLD_CACHE.get(rootId);
                if (cached != null && cached.isEmpty()) cached = null;
            }
        }
        if (cached == null || cached.isEmpty()) return null;
        ArrayList<Object> out = new ArrayList<>(cached);
        sortByCreateTime(out, desc != null && desc);
        return out;
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
        if (hasCachedByOffset(offset, subjectKey)) return;
        String key = makeOffsetKey(offset, subjectKey);
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
            LAST_SUBJECT_KEY = key;
        }
    }

    private static String getCurrentSubjectKey() {
        if (LAST_SUBJECT_KEY != null) return LAST_SUBJECT_KEY;
        Object subjectId = LAST_SUBJECT_ID == null ? null : LAST_SUBJECT_ID.get();
        String key = subjectKeyFromSubject(subjectId);
        if (key != null) LAST_SUBJECT_KEY = key;
        return key;
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

    private static String makeOffsetKey(String offset, String subjectKey) {
        if (offset == null || offset.isEmpty()) return null;
        if (subjectKey == null || subjectKey.isEmpty()) return offset;
        return subjectKey + "|" + offset;
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
        return 0L;
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

    private static Object tryNewInstance(Class<?> cls, Object... args) {
        if (cls == null) return null;
        try {
            return XposedHelpers.newInstance(cls, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void forceUnfold(Object item) {
        if (item == null) return;
        try {
            XposedHelpers.setBooleanField(item, "f55132u", false);
        } catch (Throwable ignored) {
        }
    }

    private static void markFoldedTag(Object item) {
        if (item == null || !isCommentItem(item)) return;
        try {
            XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsNeedTag", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
        try {
            Object existed = XposedHelpers.getAdditionalInstanceField(item, "BiliFoldsTag");
            if (existed != null) return;
        } catch (Throwable ignored) {
        }
        List<?> tags = getCommentTags(item);
        if (containsFoldTag(tags)) {
            try {
                XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsTag", Boolean.TRUE);
            } catch (Throwable ignored) {
            }
            return;
        }
        Object tag = buildFoldTag(item.getClass().getClassLoader());
        if (tag == null) {
            logN("tag.build.fail", "build tag failed id=" + getId(item));
            return;
        }
        ArrayList<Object> out = new ArrayList<>((tags == null ? 0 : tags.size()) + 1);
        if (tags != null) {
            out.addAll((List<?>) tags);
        }
        out.add(tag);
        boolean applied = false;
        try {
            XposedHelpers.setObjectField(item, "f55127p", out);
            applied = true;
        } catch (Throwable ignored) {
        }
        if (!applied) {
            try {
                XposedHelpers.callMethod(item, "setTags", out);
                applied = true;
            } catch (Throwable ignored) {
            }
        }
        if (!applied) {
            logN("tag.apply.fail", "apply tag failed id=" + getId(item));
            return;
        }
        try {
            XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsTag", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
        logN("tag.apply.ok", "apply tag ok id=" + getId(item));
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
            Object display = buildFoldTagDisplay(displayCls);
            if (display == null) return null;
            Object label = buildFoldTagLabel(labelCls);
            if (label == null) return null;
            return buildFoldTagObject(tagCls, display, label);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object buildFoldTagDisplay(Class<?> displayCls) {
        Object display = tryNewInstance(displayCls, true, 0L);
        if (display != null) return display;
        display = tryNewInstance(displayCls, true);
        if (display != null) return display;
        display = tryNewInstance(displayCls, false, 0L);
        if (display != null) return display;
        return tryNewInstance(displayCls);
    }

    private static Object buildFoldTagLabel(Class<?> labelCls) {
        Object label = tryNewInstance(
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
        if (label != null) return label;
        label = tryNewInstance(
                labelCls,
                FOLD_TAG_TEXT,
                FOLD_TAG_TEXT_COLOR_DAY,
                FOLD_TAG_TEXT_COLOR_NIGHT,
                FOLD_TAG_BG_DAY,
                FOLD_TAG_BG_NIGHT,
                FOLD_TAG_JUMP
        );
        if (label != null) return label;
        String[] textValues = new String[] {
                FOLD_TAG_TEXT,
                FOLD_TAG_TEXT_COLOR_DAY,
                FOLD_TAG_TEXT_COLOR_NIGHT,
                FOLD_TAG_BG_DAY,
                FOLD_TAG_BG_NIGHT,
                FOLD_TAG_JUMP,
                ""
        };
        java.lang.reflect.Constructor<?>[] ctors = labelCls.getDeclaredConstructors();
        for (java.lang.reflect.Constructor<?> ctor : ctors) {
            try {
                Class<?>[] pts = ctor.getParameterTypes();
                Object[] args = new Object[pts.length];
                int sIdx = 0;
                for (int i = 0; i < pts.length; i++) {
                    Class<?> t = pts[i];
                    if (t == String.class) {
                        args[i] = sIdx < textValues.length ? textValues[sIdx++] : "";
                    } else if (t == boolean.class || t == Boolean.class) {
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
                ctor.setAccessible(true);
                Object v = ctor.newInstance(args);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object buildFoldTagObject(Class<?> tagCls, Object display, Object label) {
        Object tag = tryNewInstance(tagCls, display, null, label);
        if (tag != null) return tag;
        tag = tryNewInstance(tagCls, display, label, null);
        if (tag != null) return tag;
        tag = tryNewInstance(tagCls, label, display, null);
        if (tag != null) return tag;
        tag = tryNewInstance(tagCls, display, label);
        if (tag != null) return tag;
        java.lang.reflect.Constructor<?>[] ctors = tagCls.getDeclaredConstructors();
        for (java.lang.reflect.Constructor<?> ctor : ctors) {
            try {
                Class<?>[] pts = ctor.getParameterTypes();
                Object[] args = new Object[pts.length];
                boolean hasDisplay = false;
                boolean hasLabel = false;
                for (int i = 0; i < pts.length; i++) {
                    Class<?> t = pts[i];
                    if (!hasDisplay && display != null && t.isInstance(display)) {
                        args[i] = display;
                        hasDisplay = true;
                        continue;
                    }
                    if (!hasLabel && label != null && t.isInstance(label)) {
                        args[i] = label;
                        hasLabel = true;
                        continue;
                    }
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
                    } else if (t == String.class) {
                        args[i] = "";
                    } else {
                        args[i] = null;
                    }
                }
                if (!hasDisplay || !hasLabel) continue;
                ctor.setAccessible(true);
                Object v = ctor.newInstance(args);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isZipCard(Object item) {
        if (item == null || !"vv.r1".equals(item.getClass().getName())) return false;
        if (isFooterCard(item)) {
            logN("zip.skip.footer", "skip zip replace for footer-like r1");
            return false;
        }
        String text = callStringMethod(item, "h");
        if (text != null) {
            String t = text.trim();
            if (t.contains("没有了") || t.contains("找也没有") || t.contains("再怎么找") || t.contains("到底了")) {
                return false;
            }
            if (t.contains("折叠") || t.contains("展开")) return true;
            if (t.contains("更多评论") || t.contains("部分评论")) return true;
            return false;
        }
        String offset = getZipCardOffset(item);
        Boolean clickable = callBooleanMethod(item, "f");
        if (clickable != null && !clickable) return false;
        if (offset != null && !offset.isEmpty()) return true;
        return isKnownFoldOffset(offset);
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
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = callStringMethod(pagination, "getNext");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = callStringMethod(pagination, "getOffset");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = getStringField(pagination, "f380200e");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = getStringField(pagination, "next_");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
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
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = callStringMethod(pagination, "getOffset");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = callStringMethod(pagination, "e");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = getStringField(pagination, "next_");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
        offset = getStringField(pagination, "offset_");
        if (offset != null && !offset.isEmpty()) {
            markFoldOffset(offset);
            return offset;
        }
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

    private static void cacheFoldReplies(List<?> list) {
        if (list == null || list.isEmpty()) return;
        for (Object o : list) {
            if (!isCommentItem(o)) continue;
            long rootId = getRootId(o);
            if (rootId <= 0) continue;
            ArrayList<Object> bucket = FOLD_CACHE.computeIfAbsent(rootId, k -> new ArrayList<>());
            long id = getId(o);
            boolean exists = false;
            if (id != 0L) {
                for (Object e : bucket) {
                    if (getId(e) == id) {
                        exists = true;
                        break;
                    }
                }
            }
            if (exists) continue;
            forceUnfold(o);
            if (id != 0L && id != rootId) {
                markFoldedTag(o);
            }
            bucket.add(o);
        }
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

    private static Boolean callBooleanMethod(Object obj, String name) {
        if (obj == null) return null;
        try {
            Object v = XposedHelpers.callMethod(obj, name);
            if (v instanceof Boolean) return (Boolean) v;
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

    private static ArrayList<Object> getCachedByOffset(String offset, String subjectKey) {
        if (offset == null || offset.isEmpty()) return null;
        String key = makeOffsetKey(offset, subjectKey);
        if (key != null) {
            ArrayList<Object> own = FOLD_CACHE_BY_OFFSET.get(key);
            if (own != null && !own.isEmpty()) {
                return own;
            }
        }
        ArrayList<Object> direct = FOLD_CACHE_BY_OFFSET.get(offset);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        String suffix = "|" + offset;
        for (java.util.Map.Entry<String, ArrayList<Object>> e : FOLD_CACHE_BY_OFFSET.entrySet()) {
            String k = e.getKey();
            ArrayList<Object> v = e.getValue();
            if (k != null && k.endsWith(suffix) && v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static boolean hasCachedByOffset(String offset, String subjectKey) {
        return getCachedByOffset(offset, subjectKey) != null;
    }

    private static void markFoldOffset(String offset) {
        if (offset == null || offset.isEmpty()) return;
        KNOWN_FOLD_OFFSET.put(offset, System.currentTimeMillis());
        if (KNOWN_FOLD_OFFSET.size() > 200) {
            long now = System.currentTimeMillis();
            for (String key : KNOWN_FOLD_OFFSET.keySet()) {
                Long t = KNOWN_FOLD_OFFSET.get(key);
                if (t == null || now - t > 5 * 60_000L) {
                    KNOWN_FOLD_OFFSET.remove(key);
                }
            }
        }
    }

    private static boolean isKnownFoldOffset(String offset) {
        if (offset == null || offset.isEmpty()) return false;
        return KNOWN_FOLD_OFFSET.containsKey(offset);
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
        if (text != null) {
            String t = text.trim();
            if (t.contains("没有了") || t.contains("找也没有") || t.contains("再怎么找") || t.contains("到底了")) {
                return true;
            }
            if (t.contains("没有啦") || t.contains("没有喽") || t.contains("没有咯") || t.contains("没有更多")) {
                return true;
            }
        }
        Boolean clickable = callBooleanMethod(item, "f");
        if (clickable != null && !clickable) {
            if (text == null || text.trim().isEmpty()) return true;
            String t = text.trim();
            if (!t.contains("折叠") && !t.contains("展开") && !t.contains("更多评论") && !t.contains("部分评论")) {
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
        String key = buildFooterRetryKey(offset);
        final String finalKey = key;
        final String finalOffset = offset;
        AtomicInteger cnt = FOOTER_RETRY_COUNT.computeIfAbsent(finalKey, k -> new AtomicInteger(0));
        int attempt = cnt.incrementAndGet();
        logN("footer.retry", "footer retry key=" + finalKey + " attempt=" + attempt);
        if (attempt > FOOTER_RETRY_LIMIT) {
            FOOTER_RETRY_PENDING.remove(finalKey);
            logN("footer.retry.stop", "footer retry limit reached key=" + finalKey);
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
        String key = buildFooterRetryKey(offset);
        FOOTER_RETRY_COUNT.remove(key);
        FOOTER_RETRY_PENDING.remove(key);
    }

    private static String buildFooterRetryKey(String offset) {
        String key = makeOffsetKey(offset, getCurrentSubjectKey());
        if (key != null && !key.isEmpty()) return key;
        if (offset != null && !offset.isEmpty()) return "offset|" + offset;
        String subject = getCurrentSubjectKey();
        if (subject != null && !subject.isEmpty()) return "subject|" + subject;
        return "global";
    }

    private static boolean hasZipCard(List<?> list) {
        if (list == null || list.isEmpty()) return false;
        for (Object item : list) {
            if (isZipCard(item)) return true;
        }
        return false;
    }

    private static void primeAutoFetchFromZipCards(List<?> list) {
        if (list == null || list.isEmpty()) return;
        String subjectKey = getCurrentSubjectKey();
        for (Object item : list) {
            if (!isZipCard(item)) continue;
            String offset = getZipCardOffset(item);
            if (offset == null || offset.isEmpty()) continue;
            tryAutoFetchFoldList(offset, subjectKey);
        }
    }

    private static void logR1Summary(String tag, List<?> list) {
        if (list == null || list.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(tag).append(" r1=");
        int totalR1 = 0;
        int shown = 0;
        for (Object item : list) {
            if (item == null || !"vv.r1".equals(item.getClass().getName())) continue;
            totalR1++;
            if (shown < 4) {
                String text = callStringMethod(item, "h");
                String offset = getZipCardOffset(item);
                Boolean clickable = callBooleanMethod(item, "f");
                sb.append(" [text=").append(safeToString(text))
                        .append(",offset=").append(safeToString(offset))
                        .append(",click=").append(safeToString(clickable))
                        .append("]");
                shown++;
            }
        }
        sb.append(" total=").append(totalR1).append(" size=").append(list.size());
        logN("r1.summary." + tag, sb.toString());
    }

    private static boolean shouldInjectFoldTag(Object item) {
        if (item == null || !isCommentItem(item)) return false;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(item, "BiliFoldsNeedTag");
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
        Log.i(TAG, msg);
    }

    private static void logN(String key, String msg) {
        AtomicInteger c = LOG_COUNT.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (c.incrementAndGet() <= LOG_LIMIT) {
            log(msg);
        }
    }
}
