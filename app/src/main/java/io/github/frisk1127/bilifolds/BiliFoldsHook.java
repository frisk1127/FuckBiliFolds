package io.github.frisk1127.bilifolds;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import dalvik.system.DexFile;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
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
    private static final boolean DEBUG_VERBOSE = false;
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
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_LIKE_FORCE_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_REPLY_FORCE_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_LIKE_REFRESH_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_LIKE_OPT_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DEBUG_LIKE_UI_LOGGED = new ConcurrentHashMap<>();
    private static final AtomicInteger MARK_TOKEN_GEN = new AtomicInteger(0);
    private static final ThreadLocal<Long> CLICK_FOLDED_ID = new ThreadLocal<>();
    private static final ThreadLocal<java.util.ArrayDeque<Long>> CLICK_ID_STACK = new ThreadLocal<>();
    private static final ConcurrentHashMap<Long, Boolean> FOLDED_LIKE_STATE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> FOLDED_LIKE_COUNT = new ConcurrentHashMap<>();
    private static final Set<Object> AUTO_EXPAND_ZIP = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());

    private static final String AUTO_EXPAND_TEXT = "\u5df2\u81ea\u52a8\u5c55\u5f00\u6298\u53e0\u8bc4\u8bba";

    private static final ConcurrentHashMap<String, AtomicInteger> FOOTER_RETRY_COUNT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> FOOTER_RETRY_PENDING = new ConcurrentHashMap<>();
    private static final int FOOTER_RETRY_LIMIT = 12;
    private static final ConcurrentHashMap<String, Long> LAST_ADAPTER_UPDATE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> UPDATE_PENDING = new ConcurrentHashMap<>();
    private static final long UPDATE_THROTTLE_MS = 180L;

    private static volatile WeakReference<Object> LAST_SUBJECT_ID = new WeakReference<>(null);
    private static volatile String LAST_SUBJECT_KEY = null;
    private static volatile String LAST_SCOPE_KEY = null;
    private static volatile String ZIP_CARD_CLASS = null;
    private static volatile String COMMENT_HOLDER_CLASS = null;
    private static volatile String LIKE_LISTENER_CLASS = null;
    private static final Set<String> HOOKED_ZIP_CLASSES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> HOOKED_LIKE_LISTENERS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> HOOKED_HOLDER_TRACE = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> HOOKED_ADAPTER_CLASSES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> HOOKED_COMMENT_ITEM_TAGS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> HOOKED_COMMENT_ITEM_FLAGS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile String LAST_EXTRA = null;
    private static volatile Object LAST_SORT_MODE = null;
    private static volatile WeakReference<Object> LAST_COMMENT_ADAPTER = new WeakReference<>(null);
    private static volatile Field COMMENT_ID_FIELD = null;
    private static volatile Class<?> COMMENT_ID_FIELD_CLASS = null;
    private static final ThreadLocal<Boolean> RESUBMITTING = new ThreadLocal<>();
    private static volatile android.os.Handler MAIN_HANDLER = null;
    private static volatile ClassLoader APP_CL = null;

    private static volatile Object FOLD_TAG_TEMPLATE = null;
    private static volatile ClassLoader FOLD_TAG_CL = null;
    private static volatile Field COMMENT_TIME_FIELD = null;
    private static volatile Class<?> COMMENT_TIME_FIELD_CLASS = null;
    private static volatile boolean COMMENT_TIME_IS_MILLIS = false;

    private static final Object CLASS_INDEX_LOCK = new Object();
    private static volatile List<String> APP_CLASS_NAMES = null;
    private static volatile String[] APP_SOURCE_DIRS = null;

    private static volatile Class<?> REPLY_CONTROL_CLASS = null;
    private static volatile Class<?> ZIP_DATA_SOURCE_CLASS = null;
    private static volatile Class<?> DETAIL_DATA_SOURCE_CLASS = null;
    private static volatile Class<?> COMMENT_ADAPTER_CLASS = null;
    private static volatile Class<?> COMMENT_ITEM_CLASS = null;
    private static volatile Class<?> ACTION_DISPATCHER_CLASS = null;
    private static volatile Class<?> REPLY_MOSS_CLASS = null;
    private static volatile Class<?> FOLD_LIST_REQ_CLASS = null;
    private static volatile Class<?> DETAIL_LIST_REQ_CLASS = null;
    private static volatile Class<?> DATA_MAP_CLASS = null;
    private static volatile Class<?> FEED_PAGINATION_CLASS = null;
    private static volatile Class<?> COMMENT_TAG_CLASS = null;
    private static volatile Class<?> COMMENT_TAG_DISPLAY_CLASS = null;
    private static volatile Class<?> COMMENT_TAG_LABEL_CLASS = null;
    private static volatile MethodMatch ZIP_DATA_SOURCE_MATCH = null;
    private static volatile MethodMatch DETAIL_DATA_SOURCE_MATCH = null;

    private static final ConcurrentHashMap<Class<?>, AdapterListAccessor> ADAPTER_LIST_ACCESSORS = new ConcurrentHashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }
        log("active " + BUILD_TAG + " pkg=" + lpparam.packageName);
        APP_CL = lpparam.classLoader;
        if (lpparam.appInfo != null) {
            APP_SOURCE_DIRS = collectSourceDirs(lpparam.appInfo);
        }
        safeHook("hookReplyControl", new Runnable() { @Override public void run() { hookReplyControl(APP_CL); } });
        safeHook("hookCommentItemTags", new Runnable() { @Override public void run() { hookCommentItemTags(APP_CL); } });
        safeHook("hookCommentItemFoldFlags", new Runnable() { @Override public void run() { hookCommentItemFoldFlags(APP_CL); } });
        safeHook("hookActionDispatcher", new Runnable() { @Override public void run() { hookActionDispatcher(APP_CL); } });
        safeHook("hookZipCardView", new Runnable() { @Override public void run() { hookZipCardView(APP_CL); } });
        safeHook("hookZipDataSource", new Runnable() { @Override public void run() { hookZipDataSource(APP_CL); } });
        safeHook("hookDetailListDataSource", new Runnable() { @Override public void run() { hookDetailListDataSource(APP_CL); } });
        safeHook("hookCommentListAdapter", new Runnable() { @Override public void run() { hookCommentListAdapter(APP_CL); } });
        safeHook("hookH0ClickTrace", new Runnable() { @Override public void run() { hookH0ClickTrace(APP_CL); } });
        safeHook("hookLikeClickListener", new Runnable() { @Override public void run() { hookLikeClickListener(APP_CL); } });
    }

    private static void safeHook(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log(name + " failed: " + Log.getStackTraceString(t));
        }
    }

    private static String[] collectSourceDirs(android.content.pm.ApplicationInfo info) {
        if (info == null) return null;
        ArrayList<String> out = new ArrayList<>();
        if (info.sourceDir != null && !info.sourceDir.isEmpty()) {
            out.add(info.sourceDir);
        }
        if (info.splitSourceDirs != null) {
            for (String s : info.splitSourceDirs) {
                if (s != null && !s.isEmpty()) out.add(s);
            }
        }
        return out.isEmpty() ? null : out.toArray(new String[0]);
    }

    private static List<String> getAppClassNames() {
        List<String> cached = APP_CLASS_NAMES;
        if (cached != null) return cached;
        synchronized (CLASS_INDEX_LOCK) {
            if (APP_CLASS_NAMES != null) return APP_CLASS_NAMES;
            ArrayList<String> out = new ArrayList<>();
            String[] dirs = APP_SOURCE_DIRS;
            if (dirs == null || dirs.length == 0) {
                APP_CLASS_NAMES = out;
                return out;
            }
            for (String path : dirs) {
                if (path == null || path.isEmpty()) continue;
                DexFile dexFile = null;
                try {
                    dexFile = new DexFile(path);
                    Enumeration<String> entries = dexFile.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement();
                        if (name == null) continue;
                        if (name.startsWith("com.bilibili")
                                || name.startsWith("com.bapis")
                                || name.startsWith("tv.danmaku")) {
                            out.add(name);
                        }
                    }
                } catch (Throwable t) {
                    log("dex scan failed: " + t);
                } finally {
                    try {
                        if (dexFile != null) dexFile.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            APP_CLASS_NAMES = out;
            return out;
        }
    }

    private static Class<?> loadClass(ClassLoader cl, String name) {
        if (cl == null || name == null || name.isEmpty()) return null;
        try {
            return XposedHelpers.findClassIfExists(name, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean looksLikeCommentAdapterName(String name) {
        if (name == null) return false;
        String s = name.toLowerCase();
        if (s.contains("comment") || s.contains("reply")) return true;
        return s.contains(".comment3.") || s.contains(".comment.");
    }

    private static void hookAdapterListMethods(final Class<?> adapterCls) {
        if (adapterCls == null) return;
        try {
            java.lang.reflect.Method[] methods = adapterCls.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts == null || pts.length == 0) continue;
                if (!List.class.isAssignableFrom(pts[0])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(RESUBMITTING.get())) return;
                        if (param.args == null || param.args.length == 0) return;
                        Object listObj = param.args[0];
                        if (!(listObj instanceof List)) return;
                        List<?> list = (List<?>) listObj;
                        if (!looksLikeCommentList(list)) return;
                        Object adapter = param.thisObject;
                        Object last = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
                        if (last == null || last != adapter) {
                            clearFoldCaches();
                        }
                        LAST_COMMENT_ADAPTER = new WeakReference<>(adapter);
                        COMMENT_ADAPTER_CLASS = adapter == null ? null : adapter.getClass();
                        prefetchFoldList(list);
                        List<?> replaced = replaceZipCardsInList(list, "Adapter.submit");
                        if (replaced != null) {
                            param.args[0] = replaced;
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean classHasListParamMethod(Class<?> c) {
        if (c == null) return false;
        try {
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts == null || pts.length == 0) continue;
                if (List.class.isAssignableFrom(pts[0])) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean looksLikeCommentList(List<?> list) {
        if (list == null || list.isEmpty()) return false;
        int limit = Math.min(list.size(), 12);
        int score = 0;
        for (int i = 0; i < limit; i++) {
            Object item = list.get(i);
            if (item == null) continue;
            if (isZipCard(item)) {
                score += 2;
                continue;
            }
            if (isCommentItem(item)) {
                score += 2;
                continue;
            }
            if (looksLikeCommentItemInstance(item)) {
                score += 1;
                setCommentItemClass(item.getClass(), "list");
            }
        }
        return score >= 2;
    }

    private static void setCommentItemClass(Class<?> cls, String reason) {
        if (cls == null) return;
        if (COMMENT_ITEM_CLASS == cls) return;
        COMMENT_ITEM_CLASS = cls;
        COMMENT_ID_FIELD = null;
        COMMENT_ID_FIELD_CLASS = cls;
        COMMENT_TIME_FIELD = null;
        COMMENT_TIME_FIELD_CLASS = cls;
        COMMENT_TAG_CLASS = null;
        COMMENT_TAG_DISPLAY_CLASS = null;
        COMMENT_TAG_LABEL_CLASS = null;
        FOLD_TAG_TEMPLATE = null;
        FOLD_TAG_CL = null;
        hookCommentItemTagsByClass(cls);
        hookCommentItemFoldFlagsByClass(cls);
        if (DEBUG_VERBOSE) {
            log("comment item class=" + cls.getName() + " reason=" + reason);
        }
    }

    private static Class<?> resolveCommentItemClass(ClassLoader cl) {
        Class<?> known = COMMENT_ITEM_CLASS;
        if (known != null) return known;
        List<String> names = getAppClassNames();
        if (names == null || names.isEmpty()) return null;
        Class<?> best = null;
        int bestScore = 0;
        for (String name : names) {
            if (name == null) continue;
            String s = name.toLowerCase();
            if (!s.contains("comment") && !s.contains("reply")) continue;
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            int score = scoreCommentItemClass(c);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (bestScore < 6) {
            for (String name : names) {
                if (name == null) continue;
                Class<?> c = loadClass(cl, name);
                if (c == null) continue;
                int score = scoreCommentItemClass(c);
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
                if (bestScore >= 6) break;
            }
        }
        if (best != null && bestScore >= 6) {
            setCommentItemClass(best, "scan");
            return best;
        }
        return null;
    }

    private static boolean looksLikeCommentItemInstance(Object item) {
        if (item == null) return false;
        int score = scoreCommentItemClass(item.getClass());
        if (score >= 6) return true;
        if (score >= 4 && hasPositiveIdFieldValue(item)) return true;
        return false;
    }

    private static int scoreCommentItemClass(Class<?> c) {
        if (c == null) return 0;
        int score = 0;
        String name = c.getName();
        if (name != null) {
            String s = name.toLowerCase();
            if (s.contains("comment")) score += 3;
            if (s.contains("reply")) score += 2;
            if (s.contains("item")) score += 1;
            if (s.contains("data.model")) score += 1;
        }
        if (hasMethodName(c, "getTags")) score += 3;
        if (hasNoArgMethodReturningNumber(c, new String[]{"getId", "getRpid", "getRoot", "getRootId", "getReplyId"})) {
            score += 2;
        }
        if (hasFieldNameContains(c, "rpid") || hasFieldNameContains(c, "reply")
                || hasFieldNameContains(c, "root") || hasFieldNameContains(c, "id")) {
            score += 1;
        }
        if (hasListField(c)) score += 1;
        return score;
    }

    private static boolean hasPositiveIdFieldValue(Object obj) {
        if (obj == null) return false;
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            Class<?> t = f.getType();
            if (t == null || !isNumericType(t)) continue;
            String name = f.getName();
            if (name != null) {
                String s = name.toLowerCase();
                if (!s.contains("id") && !s.contains("rpid") && !s.contains("root") && !s.contains("reply")) {
                    continue;
                }
            }
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof Number) {
                    long id = ((Number) v).longValue();
                    if (id > 0) return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean hasMethodName(Class<?> c, String name) {
        if (c == null || name == null) return false;
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m != null && name.equals(m.getName())) return true;
        }
        return false;
    }

    private static boolean hasNoArgMethodReturningNumber(Class<?> c, String[] names) {
        if (c == null || names == null) return false;
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            String n = m.getName();
            if (n == null) continue;
            boolean match = false;
            for (String name : names) {
                if (name != null && name.equals(n)) {
                    match = true;
                    break;
                }
            }
            if (!match) continue;
            if (m.getParameterTypes().length != 0) continue;
            if (isNumericType(m.getReturnType())) return true;
        }
        return false;
    }

    private static boolean hasFieldNameContains(Class<?> c, String key) {
        if (c == null || key == null) return false;
        String k = key.toLowerCase();
        Field[] fields = c.getDeclaredFields();
        for (Field f : fields) {
            String n = f.getName();
            if (n != null && n.toLowerCase().contains(k)) return true;
        }
        return false;
    }

    private static boolean hasListField(Class<?> c) {
        if (c == null) return false;
        Field[] fields = c.getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            Class<?> t = f.getType();
            if (t != null && List.class.isAssignableFrom(t)) return true;
        }
        return false;
    }

    private static boolean resolveCommentTagClasses(ClassLoader cl) {
        if (COMMENT_TAG_CLASS != null && COMMENT_TAG_DISPLAY_CLASS != null && COMMENT_TAG_LABEL_CLASS != null) {
            return true;
        }
        Class<?> itemCls = resolveCommentItemClass(cl);
        if (itemCls == null) return false;
        Class<?>[] inners = itemCls.getDeclaredClasses();
        if (inners == null || inners.length == 0) return false;
        for (Class<?> inner : inners) {
            if (inner == null) continue;
            if (COMMENT_TAG_DISPLAY_CLASS == null && looksLikeTagDisplayClass(inner)) {
                COMMENT_TAG_DISPLAY_CLASS = inner;
            }
            if (COMMENT_TAG_LABEL_CLASS == null && looksLikeTagLabelClass(inner)) {
                COMMENT_TAG_LABEL_CLASS = inner;
            }
        }
        if (COMMENT_TAG_DISPLAY_CLASS != null && COMMENT_TAG_LABEL_CLASS != null) {
            for (Class<?> inner : inners) {
                if (inner == null) continue;
                if (looksLikeTagClass(inner, COMMENT_TAG_DISPLAY_CLASS, COMMENT_TAG_LABEL_CLASS)) {
                    COMMENT_TAG_CLASS = inner;
                    break;
                }
            }
        }
        return COMMENT_TAG_CLASS != null && COMMENT_TAG_DISPLAY_CLASS != null && COMMENT_TAG_LABEL_CLASS != null;
    }

    private static boolean looksLikeTagDisplayClass(Class<?> c) {
        if (c == null) return false;
        try {
            java.lang.reflect.Constructor<?>[] ctors = c.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> ctor : ctors) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts == null || pts.length < 2) continue;
                if ((pts[0] == boolean.class || pts[0] == Boolean.class) && isNumericType(pts[1])) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean looksLikeTagLabelClass(Class<?> c) {
        if (c == null) return false;
        try {
            java.lang.reflect.Constructor<?>[] ctors = c.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> ctor : ctors) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts == null || pts.length < 5) continue;
                int stringCount = 0;
                for (Class<?> p : pts) {
                    if (p == String.class) stringCount++;
                }
                if (stringCount >= 3) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean looksLikeTagClass(Class<?> c, Class<?> displayCls, Class<?> labelCls) {
        if (c == null || displayCls == null || labelCls == null) return false;
        try {
            java.lang.reflect.Constructor<?>[] ctors = c.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> ctor : ctors) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts == null || pts.length < 3) continue;
                if (displayCls.isAssignableFrom(pts[0]) && labelCls.isAssignableFrom(pts[2])) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Class<?> resolveReplyControlClass(ClassLoader cl) {
        if (REPLY_CONTROL_CLASS != null) return REPLY_CONTROL_CLASS;
        List<String> names = getAppClassNames();
        if (names == null) return null;
        for (String name : names) {
            if (name == null || !name.startsWith("com.bapis")) continue;
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            if (hasNoArgMethod(c, "getIsFoldedReply", boolean.class)
                    && hasNoArgMethod(c, "getBlocked", boolean.class)
                    && hasNoArgMethod(c, "getInvisible", boolean.class)
                    && hasNoArgMethodReturningNumber(c, new String[]{"getAction"})) {
                REPLY_CONTROL_CLASS = c;
                return c;
            }
        }
        return null;
    }

    private static MethodMatch resolveZipDataSourceMethod(ClassLoader cl) {
        MethodMatch cached = ZIP_DATA_SOURCE_MATCH;
        if (cached != null) return cached;
        List<String> names = getAppClassNames();
        if (names == null) return null;
        MethodMatch best = null;
        int bestScore = 0;
        for (String name : names) {
            if (name == null) continue;
            String s = name.toLowerCase();
            if (!s.contains("data") || !s.contains("source")) continue;
            if (!s.contains("comment") && !s.contains("reply") && !s.contains("zip") && !s.contains("fold")) {
                continue;
            }
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                MethodMatch match = matchZipDataSourceMethod(c, m);
                if (match == null) continue;
                int score = 1;
                if (s.contains("zip")) score += 3;
                if (s.contains("fold")) score += 2;
                if (s.contains("comment")) score += 1;
                if (score > bestScore) {
                    bestScore = score;
                    best = match;
                }
            }
        }
        if (best != null) {
            ZIP_DATA_SOURCE_MATCH = best;
            ZIP_DATA_SOURCE_CLASS = best.owner;
        }
        return best;
    }

    private static MethodMatch resolveDetailListDataSourceMethod(ClassLoader cl) {
        MethodMatch cached = DETAIL_DATA_SOURCE_MATCH;
        if (cached != null) return cached;
        List<String> names = getAppClassNames();
        if (names == null) return null;
        MethodMatch best = null;
        int bestScore = 0;
        for (String name : names) {
            if (name == null) continue;
            String s = name.toLowerCase();
            if (!s.contains("data") || !s.contains("source")) continue;
            if (!s.contains("detail") && !s.contains("comment") && !s.contains("reply")) continue;
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                MethodMatch match = matchDetailListDataSourceMethod(c, m);
                if (match == null) continue;
                int score = 1;
                if (s.contains("detail")) score += 3;
                if (s.contains("comment")) score += 1;
                if (score > bestScore) {
                    bestScore = score;
                    best = match;
                }
            }
        }
        if (best != null) {
            DETAIL_DATA_SOURCE_MATCH = best;
            DETAIL_DATA_SOURCE_CLASS = best.owner;
        }
        return best;
    }

    private static MethodMatch matchZipDataSourceMethod(Class<?> owner, java.lang.reflect.Method m) {
        if (owner == null || m == null) return null;
        Class<?>[] pts = m.getParameterTypes();
        if (pts == null || pts.length < 2) return null;
        int subjectIdx = indexOfSubjectIdParam(pts);
        if (subjectIdx < 0) {
            subjectIdx = firstObjectParamIndex(pts);
        }
        int offsetIdx = indexOfStringParam(pts, 0);
        if (subjectIdx < 0 || offsetIdx < 0) return null;
        int extraIdx = indexOfStringParam(pts, offsetIdx + 1);
        return new MethodMatch(owner, m, subjectIdx, offsetIdx, extraIdx, -1);
    }

    private static MethodMatch matchDetailListDataSourceMethod(Class<?> owner, java.lang.reflect.Method m) {
        if (owner == null || m == null) return null;
        Class<?>[] pts = m.getParameterTypes();
        if (pts == null || pts.length < 3) return null;
        int subjectIdx = indexOfSubjectIdParam(pts);
        if (subjectIdx < 0) {
            subjectIdx = firstObjectParamIndex(pts);
        }
        int extraIdx = indexOfStringParam(pts, 0);
        if (subjectIdx < 0 || extraIdx < 0) return null;
        int lastExtra = extraIdx;
        int next = indexOfStringParam(pts, extraIdx + 1);
        while (next >= 0) {
            lastExtra = next;
            next = indexOfStringParam(pts, next + 1);
        }
        extraIdx = lastExtra;
        int sortIdx = findSortModeIndex(pts);
        if (sortIdx < 0 && extraIdx > 0) {
            sortIdx = extraIdx - 1;
        }
        return new MethodMatch(owner, m, subjectIdx, -1, extraIdx, sortIdx);
    }

    private static int indexOfStringParam(Class<?>[] pts, int start) {
        if (pts == null) return -1;
        int s = Math.max(0, start);
        for (int i = s; i < pts.length; i++) {
            if (pts[i] == String.class) return i;
        }
        return -1;
    }

    private static int firstObjectParamIndex(Class<?>[] pts) {
        if (pts == null) return -1;
        for (int i = 0; i < pts.length; i++) {
            Class<?> t = pts[i];
            if (t == null) continue;
            if (!t.isPrimitive() && t != String.class) return i;
        }
        return -1;
    }

    private static int indexOfSubjectIdParam(Class<?>[] pts) {
        if (pts == null) return -1;
        for (int i = 0; i < pts.length; i++) {
            if (looksLikeSubjectIdClass(pts[i])) return i;
        }
        return -1;
    }

    private static boolean looksLikeSubjectIdClass(Class<?> c) {
        if (c == null) return false;
        if (hasMethodNameContains(c, "oid") && hasMethodNameContains(c, "type")) return true;
        if (hasFieldNameContains(c, "oid") && hasFieldNameContains(c, "type")) return true;
        return false;
    }

    private static int findSortModeIndex(Class<?>[] pts) {
        if (pts == null) return -1;
        for (int i = 0; i < pts.length; i++) {
            Class<?> t = pts[i];
            if (t == null) continue;
            String n = t.getName();
            if (n != null) {
                String s = n.toLowerCase();
                if (s.contains("sort") || s.contains("mode")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Class<?> resolveActionDispatcherClass(ClassLoader cl) {
        if (ACTION_DISPATCHER_CLASS != null) return ACTION_DISPATCHER_CLASS;
        List<String> names = getAppClassNames();
        if (names == null) return null;
        Class<?> itemCls = COMMENT_ITEM_CLASS;
        for (String name : names) {
            if (name == null) continue;
            String s = name.toLowerCase();
            if (!s.contains("comment") && !s.contains("reply")) continue;
            if (!s.contains("action") && !s.contains("dispatch")) continue;
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            if (itemCls != null && !hasParamAssignableFrom(c, itemCls)) {
                continue;
            }
            ACTION_DISPATCHER_CLASS = c;
            return c;
        }
        return null;
    }

    private static Class<?> resolveReplyMossClass(ClassLoader cl) {
        if (REPLY_MOSS_CLASS != null) return REPLY_MOSS_CLASS;
        List<String> names = getAppClassNames();
        if (names == null) return null;
        Class<?> best = null;
        int bestScore = 0;
        for (String name : names) {
            if (name == null || !name.startsWith("com.bapis")) continue;
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            int score = 0;
            if (hasMethodNameContains(c, "fold") && hasMethodNameContains(c, "list")) score += 3;
            if (hasMethodNameContains(c, "detail")) score += 2;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best != null && bestScore >= 3) {
            REPLY_MOSS_CLASS = best;
            return best;
        }
        return null;
    }

    private static Class<?> resolveFoldListReqClass(Class<?> mossCls) {
        if (FOLD_LIST_REQ_CLASS != null) return FOLD_LIST_REQ_CLASS;
        if (mossCls == null) return null;
        java.lang.reflect.Method[] methods = mossCls.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            String n = m.getName();
            if (n == null) continue;
            String s = n.toLowerCase();
            if (!s.contains("fold") || !s.contains("list")) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts != null && pts.length == 1) {
                FOLD_LIST_REQ_CLASS = pts[0];
                return pts[0];
            }
        }
        return null;
    }

    private static Class<?> resolveDetailListReqClass(Class<?> mossCls) {
        if (DETAIL_LIST_REQ_CLASS != null) return DETAIL_LIST_REQ_CLASS;
        if (mossCls == null) return null;
        java.lang.reflect.Method[] methods = mossCls.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            String n = m.getName();
            if (n == null) continue;
            String s = n.toLowerCase();
            if (!s.contains("detail")) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts != null && pts.length == 1) {
                DETAIL_LIST_REQ_CLASS = pts[0];
                return pts[0];
            }
        }
        return null;
    }

    private static Class<?> resolveDataMapClass(Class<?> respCls) {
        if (DATA_MAP_CLASS != null) return DATA_MAP_CLASS;
        if (respCls == null) return null;
        ClassLoader cl = respCls.getClassLoader();
        if (cl == null) cl = APP_CL;
        List<String> names = getAppClassNames();
        if (names == null) return null;
        Class<?> best = null;
        int bestScore = 0;
        for (String name : names) {
            if (name == null) continue;
            String s = name.toLowerCase();
            if (!s.contains("comment") || !s.contains("data") || !s.contains("source")) continue;
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            int score = scoreMapClass(c, respCls);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best != null && bestScore > 0) {
            DATA_MAP_CLASS = best;
            return best;
        }
        return null;
    }

    private static int scoreMapClass(Class<?> mapCls, Class<?> respCls) {
        if (mapCls == null || respCls == null) return 0;
        int best = 0;
        java.lang.reflect.Method[] methods = mapCls.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            int mod = m.getModifiers();
            if (!java.lang.reflect.Modifier.isStatic(mod)) continue;
            if (m.getReturnType() == void.class) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts == null || pts.length == 0) continue;
            if (!pts[0].isAssignableFrom(respCls)) {
                continue;
            }
            int score = 1;
            String n = m.getName();
            if (n != null) {
                if ("D0".equals(n) || "A0".equals(n) || "F0".equals(n)) score += 3;
                if (n.toLowerCase().contains("map")) score += 2;
            }
            if (pts.length == 2) score += 1;
            if (score > best) best = score;
        }
        return best;
    }

    private static Class<?> resolvePaginationClass(ClassLoader cl) {
        if (FEED_PAGINATION_CLASS != null) return FEED_PAGINATION_CLASS;
        List<String> names = getAppClassNames();
        if (names == null) return null;
        for (String name : names) {
            if (name == null) continue;
            String s = name.toLowerCase();
            if (!s.contains("pagination")) continue;
            Class<?> c = loadClass(cl, name);
            if (c == null) continue;
            if (hasStaticMethod(c, "newBuilder")) {
                FEED_PAGINATION_CLASS = c;
                return c;
            }
        }
        return null;
    }

    private static Object tryMapFoldList(Object resp, boolean withChildren) {
        if (resp == null) return null;
        Class<?> mapCls = resolveDataMapClass(resp.getClass());
        if (mapCls == null) return null;
        try {
            return XposedHelpers.callStaticMethod(mapCls, "D0", resp, withChildren);
        } catch (Throwable ignored) {
        }
        try {
            return XposedHelpers.callStaticMethod(mapCls, "D0", resp);
        } catch (Throwable ignored) {
        }
        try {
            return XposedHelpers.callStaticMethod(mapCls, "F0", resp);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void hookReplyControlBoolean(Class<?> c, String name) {
        if (c == null || name == null) return;
        try {
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null || !name.equals(m.getName())) continue;
                Class<?> rt = m.getReturnType();
                if (rt != boolean.class && rt != Boolean.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isReplyControlMarked(param.thisObject) || isClickForceActive()) {
                            param.setResult(false);
                            logReplyForceOnce(name);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookReplyControlAction(Class<?> c, String name) {
        if (c == null || name == null) return;
        try {
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null || !name.equals(m.getName())) continue;
                Class<?> rt = m.getReturnType();
                final Object max = maxValueForReturnType(rt);
                if (max == null) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isReplyControlMarked(param.thisObject) || isClickForceActive()) {
                            param.setResult(max);
                            logReplyForceOnce(name);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object maxValueForReturnType(Class<?> rt) {
        if (rt == null) return null;
        if (rt == long.class || rt == Long.class) return Long.MAX_VALUE;
        if (rt == int.class || rt == Integer.class) return Integer.MAX_VALUE;
        if (rt == short.class || rt == Short.class) return (short) Short.MAX_VALUE;
        if (rt == byte.class || rt == Byte.class) return (byte) Byte.MAX_VALUE;
        return null;
    }

    private static boolean isNumericType(Class<?> c) {
        if (c == null) return false;
        return c == long.class || c == Long.class
                || c == int.class || c == Integer.class
                || c == short.class || c == Short.class
                || c == byte.class || c == Byte.class
                || c == float.class || c == Float.class
                || c == double.class || c == Double.class
                || c == Number.class;
    }

    private static boolean hasNoArgMethod(Class<?> c, String name, Class<?> returnType) {
        if (c == null || name == null) return false;
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            if (!name.equals(m.getName())) continue;
            if (m.getParameterTypes().length != 0) continue;
            if (returnType == null) return true;
            Class<?> rt = m.getReturnType();
            if (rt == returnType) return true;
            if (returnType == boolean.class && rt == Boolean.class) return true;
            if (returnType == int.class && rt == Integer.class) return true;
            if (returnType == long.class && rt == Long.class) return true;
            if (returnType == short.class && rt == Short.class) return true;
            if (returnType == byte.class && rt == Byte.class) return true;
        }
        return false;
    }

    private static boolean hasMethodNameContains(Class<?> c, String key) {
        if (c == null || key == null) return false;
        String k = key.toLowerCase();
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            String n = m.getName();
            if (n != null && n.toLowerCase().contains(k)) return true;
        }
        return false;
    }

    private static boolean hasStaticMethod(Class<?> c, String name) {
        if (c == null || name == null) return false;
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            if (!name.equals(m.getName())) continue;
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) return true;
        }
        return false;
    }

    private static boolean hasParamAssignableFrom(Class<?> c, Class<?> paramCls) {
        if (c == null || paramCls == null) return false;
        java.lang.reflect.Method[] methods = c.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts == null) continue;
            for (Class<?> p : pts) {
                if (p != null && p.isAssignableFrom(paramCls)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class MethodMatch {
        final Class<?> owner;
        final java.lang.reflect.Method method;
        final int subjectIdx;
        final int offsetIdx;
        final int extraIdx;
        final int sortIdx;

        private MethodMatch(Class<?> owner, java.lang.reflect.Method method, int subjectIdx, int offsetIdx, int extraIdx, int sortIdx) {
            this.owner = owner;
            this.method = method;
            this.subjectIdx = subjectIdx;
            this.offsetIdx = offsetIdx;
            this.extraIdx = extraIdx;
            this.sortIdx = sortIdx;
        }
    }

    private static void hookReplyControl(ClassLoader cl) {
        Class<?> c = resolveReplyControlClass(cl);
        if (c == null) {
            log("ReplyControl class not found");
            return;
        }
        hookBooleanMethodReturnFalse(c, "getIsFoldedReply");
        hookReplyControlBoolean(c, "getBlocked");
        hookReplyControlBoolean(c, "getInvisible");
        hookReplyControlAction(c, "getAction");
    }

    private static void hookZipDataSource(ClassLoader cl) {
        MethodMatch match = resolveZipDataSourceMethod(cl);
        if (match == null || match.method == null) {
            log("ZipDataSource class not found");
            return;
        }
        final int subjectIdx = match.subjectIdx;
        final int offsetIdx = match.offsetIdx;
        final int extraIdx = match.extraIdx;
        XposedBridge.hookMethod(match.method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null) return;
                if (subjectIdx >= 0 && subjectIdx < param.args.length && param.args[subjectIdx] != null) {
                    LAST_SUBJECT_ID = new WeakReference<>(param.args[subjectIdx]);
                    updateSubjectKey(param.args[subjectIdx]);
                }
                Object offset = offsetIdx >= 0 && offsetIdx < param.args.length ? param.args[offsetIdx] : null;
                if (offset instanceof String) {
                    String extra = extraIdx >= 0 && extraIdx < param.args.length ? safeToString(param.args[extraIdx]) : "";
                    if ("null".equals(extra)) extra = "";
                    if (extra != null && !extra.isEmpty()) {
                        LAST_EXTRA = extra;
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null) return;
                Object offset = offsetIdx >= 0 && offsetIdx < param.args.length ? param.args[offsetIdx] : null;
                if (offset instanceof String) {
                    cacheFoldListResult("ZipDataSourceV1.a.result", (String) offset, param.getResult());
                }
            }
        });
    }

    private static void hookDetailListDataSource(ClassLoader cl) {
        MethodMatch match = resolveDetailListDataSourceMethod(cl);
        if (match == null || match.method == null) {
            log("DetailListDataSource class not found");
            return;
        }
        final int subjectIdx = match.subjectIdx;
        final int extraIdx = match.extraIdx;
        final int sortIdx = match.sortIdx;
        XposedBridge.hookMethod(match.method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null) return;
                if (subjectIdx >= 0 && subjectIdx < param.args.length && param.args[subjectIdx] != null) {
                    LAST_SUBJECT_ID = new WeakReference<>(param.args[subjectIdx]);
                    updateSubjectKey(param.args[subjectIdx]);
                }
                Object extraObj = extraIdx >= 0 && extraIdx < param.args.length ? param.args[extraIdx] : null;
                String extra = safeToString(extraObj);
                if ("null".equals(extra)) extra = "";
                if (extra != null && !extra.isEmpty()) {
                    LAST_EXTRA = extra;
                }
                if (sortIdx >= 0 && sortIdx < param.args.length && param.args[sortIdx] != null) {
                    LAST_SORT_MODE = param.args[sortIdx];
                }
            }
        });
    }

    private static void hookCommentListAdapter(ClassLoader cl) {
        Class<?> adapterBase = XposedHelpers.findClassIfExists(
                "androidx.recyclerview.widget.RecyclerView$Adapter",
                cl
        );
        if (adapterBase == null) {
            log("RecyclerView.Adapter class not found");
            return;
        }
        List<String> names = getAppClassNames();
        int hooked = 0;
        if (names != null) {
            for (String name : names) {
                if (!looksLikeCommentAdapterName(name)) continue;
                Class<?> c = loadClass(cl, name);
                if (c == null) continue;
                if (!adapterBase.isAssignableFrom(c)) continue;
                if (!HOOKED_ADAPTER_CLASSES.add(name)) continue;
                hooked++;
                hookAdapterListMethods(c);
                hookCommentViewHolderBind(c, cl);
            }
            if (hooked == 0) {
                for (String name : names) {
                    if (name == null) continue;
                    Class<?> c = loadClass(cl, name);
                    if (c == null) continue;
                    if (!adapterBase.isAssignableFrom(c)) continue;
                    if (!classHasListParamMethod(c)) continue;
                    if (!HOOKED_ADAPTER_CLASSES.add(name)) continue;
                    hooked++;
                    hookAdapterListMethods(c);
                    hookCommentViewHolderBind(c, cl);
                }
            }
        }
        if (hooked == 0) {
            log("CommentListAdapter class not found");
        }
    }

    private static void hookLikeClickListener(ClassLoader cl) {
        String name = LIKE_LISTENER_CLASS;
        if (name == null || name.isEmpty()) return;
        Class<?> c = XposedHelpers.findClassIfExists(name, cl);
        if (c == null) {
            log(name + " class not found");
            return;
        }
        LIKE_LISTENER_CLASS = name;
        hookLikeClickListenerByClass(c);
    }

    private static void hookLikeClickListenerByClass(Class<?> c) {
        if (c == null) return;
        String name = c.getName();
        if (!HOOKED_LIKE_LISTENERS.add(name)) return;
        XposedBridge.hookAllMethods(c, "onClick", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0) return;
                if (!(param.args[0] instanceof View)) return;
                View v = (View) param.args[0];
                long id = getAdditionalLong(v, "BiliFoldsCommentId");
                Object item = getAdditionalObject(v, "BiliFoldsCommentItem");
                if (id == 0L && isCommentItem(item)) {
                    long cid = getId(item);
                    if (cid != 0L) id = cid;
                }
                if (id == 0L) return;
                if (!isFoldedItem(item)) return;
                pushClickFoldedId(id);
                if (isCommentItem(item)) {
                    forceUnfold(item);
                }
                int marked = 0;
                marked += markReplyControlDeep(param.thisObject, id, new HashSet<Object>(), 3);
                marked += markReplyControlDeep(item, id, new HashSet<Object>(), 3);
                marked += markReplyControlDeep(v, id, new HashSet<Object>(), 2);
                if (DEBUG_VERBOSE && DEBUG_LIKE_FORCE_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
                    log("like.force folded id=" + id
                            + " listener=" + param.thisObject.getClass().getName()
                            + " marked=" + marked);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0) return;
                if (!(param.args[0] instanceof View)) return;
                View v = (View) param.args[0];
                long id = getAdditionalLong(v, "BiliFoldsCommentId");
                Object item = getAdditionalObject(v, "BiliFoldsCommentItem");
                if (id == 0L && isCommentItem(item)) {
                    long cid = getId(item);
                    if (cid != 0L) id = cid;
                }
                if (id == 0L) return;
                if (!isFoldedItem(item)) return;
                popClickFoldedId();
            }
        });
    }

    private static void hookH0ClickTrace(ClassLoader cl) {
        String name = COMMENT_HOLDER_CLASS;
        if (name == null || name.isEmpty()) return;
        hookH0ClickTraceByName(name, cl);
    }

    private static void hookH0ClickTraceByName(String name, ClassLoader cl) {
        if (name == null || name.isEmpty()) return;
        if (!HOOKED_HOLDER_TRACE.add(name)) return;
        Class<?> c = XposedHelpers.findClassIfExists(name, cl);
        if (c == null) {
            log(name + " class not found");
            HOOKED_HOLDER_TRACE.remove(name);
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
                    Object item = getAdditionalObject(holder, "BiliFoldsCommentItem");
                    if (!isFoldedItem(item)) return;
                    pushClickFoldedId(id);
                    if (isCommentItem(item)) {
                        forceUnfold(item);
                    }
                    markReplyControlDeep(holder, id, new HashSet<Object>(), 3);
                    if (!DEBUG_VERBOSE) return;
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

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
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
                    Object item = getAdditionalObject(holder, "BiliFoldsCommentItem");
                    if (!isFoldedItem(item)) return;
                    popClickFoldedId();
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
                        Object adapter = param.thisObject;
                        Object lastAdapter = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
                        if (adapter == null || lastAdapter != adapter) {
                            return;
                        }
                        Object holder = param.args[0];
                        int pos = (param.args[1] instanceof Integer) ? (Integer) param.args[1] : -1;
                        if (holder == null || pos < 0) return;
                        Object itemViewObj = XposedHelpers.getObjectField(holder, "itemView");
                        if (!(itemViewObj instanceof View)) return;
                        Object item = getAdapterItemAt(adapter, pos);
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
                        String holderName = holder.getClass().getName();
                        if (holderName != null && !holderName.isEmpty() && !holderName.equals(COMMENT_HOLDER_CLASS)) {
                            COMMENT_HOLDER_CLASS = holderName;
                            ClassLoader hcl = holder.getClass().getClassLoader();
                            if (hcl == null) hcl = APP_CL;
                            hookH0ClickTraceByName(holderName, hcl);
                        }
                        ensureFoldedActionsClickable((View) itemViewObj, id, item);
                        if (!isFoldedItem(item)) {
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

    private static Object getAdapterItemAt(Object adapter, int pos) {
        if (adapter == null) return null;
        List<?> list = getAdapterList(adapter);
        if (list == null) return null;
        if (pos < 0 || pos >= list.size()) return null;
        return list.get(pos);
    }

    private static List<?> getAdapterList(Object adapter) {
        if (adapter == null) return null;
        Class<?> cls = adapter.getClass();
        AdapterListAccessor accessor = ADAPTER_LIST_ACCESSORS.get(cls);
        if (accessor == null) {
            accessor = buildAdapterListAccessor(cls);
            if (accessor != null) {
                ADAPTER_LIST_ACCESSORS.put(cls, accessor);
            }
        }
        if (accessor == null) return null;
        return accessor.get(adapter);
    }

    private static AdapterListAccessor buildAdapterListAccessor(Class<?> adapterCls) {
        if (adapterCls == null) return null;
        java.lang.reflect.Method direct = findNoArgListMethod(adapterCls);
        if (direct != null) {
            return new AdapterListAccessor(direct, null, null, null);
        }
        Field listField = findListField(adapterCls);
        if (listField != null) {
            return new AdapterListAccessor(null, listField, null, null);
        }
        Field holderField = null;
        java.lang.reflect.Method holderListMethod = null;
        Field[] fields = adapterCls.getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            Class<?> t = f.getType();
            if (t == null) continue;
            if (List.class.isAssignableFrom(t)) {
                continue;
            }
            java.lang.reflect.Method m = findNoArgListMethod(t);
            if (m != null) {
                holderField = f;
                holderListMethod = m;
                try {
                    holderField.setAccessible(true);
                } catch (Throwable ignored) {
                }
                break;
            }
        }
        if (holderField != null && holderListMethod != null) {
            return new AdapterListAccessor(null, null, holderField, holderListMethod);
        }
        return null;
    }

    private static java.lang.reflect.Method findNoArgListMethod(Class<?> cls) {
        if (cls == null) return null;
        String[] preferred = new String[] {"getCurrentList", "getList", "getItems", "getItemList", "a"};
        for (String name : preferred) {
            try {
                java.lang.reflect.Method m = cls.getDeclaredMethod(name);
                if (m.getParameterTypes().length == 0 && List.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (Throwable ignored) {
            }
        }
        java.lang.reflect.Method[] methods = cls.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m == null) continue;
            if (m.getParameterTypes().length != 0) continue;
            if (!List.class.isAssignableFrom(m.getReturnType())) continue;
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            return m;
        }
        return null;
    }

    private static Field findListField(Class<?> cls) {
        if (cls == null) return null;
        Field[] fields = cls.getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            Class<?> t = f.getType();
            if (t == null) continue;
            if (!List.class.isAssignableFrom(t)) continue;
            try {
                f.setAccessible(true);
            } catch (Throwable ignored) {
            }
            return f;
        }
        return null;
    }

    private static final class AdapterListAccessor {
        private final java.lang.reflect.Method directMethod;
        private final Field listField;
        private final Field holderField;
        private final java.lang.reflect.Method holderListMethod;

        private AdapterListAccessor(java.lang.reflect.Method directMethod, Field listField, Field holderField, java.lang.reflect.Method holderListMethod) {
            this.directMethod = directMethod;
            this.listField = listField;
            this.holderField = holderField;
            this.holderListMethod = holderListMethod;
        }

        private List<?> get(Object adapter) {
            if (adapter == null) return null;
            try {
                if (directMethod != null) {
                    Object listObj = directMethod.invoke(adapter);
                    return (listObj instanceof List) ? (List<?>) listObj : null;
                }
                if (listField != null) {
                    Object listObj = listField.get(adapter);
                    return (listObj instanceof List) ? (List<?>) listObj : null;
                }
                if (holderField != null && holderListMethod != null) {
                    Object holder = holderField.get(adapter);
                    if (holder == null) return null;
                    Object listObj = holderListMethod.invoke(holder);
                    return (listObj instanceof List) ? (List<?>) listObj : null;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    private static boolean applyFoldMarkToHolder(Object holder, long id) {
        if (holder == null) return false;
        String cls = holder.getClass().getName();
        String target = COMMENT_HOLDER_CLASS;
        if (target != null && !target.isEmpty() && !target.equals(cls)) {
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
        try {
            XposedHelpers.setAdditionalInstanceField(actionRow, "BiliFoldsCommentId", id);
        } catch (Throwable ignored) {
        }
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
        try {
            XposedHelpers.setAdditionalInstanceField(actionRow, "BiliFoldsCommentId", id);
        } catch (Throwable ignored) {
        }
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

    private static void markFoldedItem(Object item) {
        if (item == null) return;
        try {
            XposedHelpers.setAdditionalInstanceField(item, "BiliFoldsFolded", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isFoldedItem(Object item) {
        if (item == null) return false;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(item, "BiliFoldsFolded");
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        return false;
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
        if (!DEBUG_VERBOSE) return;
        if (DEBUG_MARK_PLACE_LOGGED.putIfAbsent(id, Boolean.TRUE) != null) return;
        log("mark.place id=" + id + " " + msg);
    }

    private static void logMarkMoreOnce(long id, ViewGroup row, View more, TextView base, String tag) {
        if (id == 0L || row == null || more == null) return;
        if (!DEBUG_VERBOSE) return;
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
        if (!DEBUG_VERBOSE) return;
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
        if (!DEBUG_VERBOSE) return;
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
        if (isConstraintLayout(group)) {
            if (anchor.getId() == View.NO_ID) {
                anchor.setId(View.generateViewId());
            }
            Object lp = newConstraintLayoutParams(group);
            if (lp instanceof ViewGroup.LayoutParams) {
                setConstraintId(lp, "startToEnd", anchor.getId());
                setConstraintId(lp, "topToTop", anchor.getId());
                setConstraintId(lp, "bottomToBottom", anchor.getId());
                if (anchor instanceof TextView) {
                    setConstraintId(lp, "baselineToBaseline", anchor.getId());
                }
                setMarginStart(lp, dp(group.getContext(), 6));
                mark.setLayoutParams((ViewGroup.LayoutParams) lp);
                mark.setId(View.generateViewId());
                group.addView(mark);
                return true;
            }
        }
        int index = group.indexOfChild(anchor);
        if (index < 0) index = group.getChildCount();
        group.addView(mark, Math.min(index + 1, group.getChildCount()));
        return true;
    }

    private static boolean addMarkAtRowEnd(ViewGroup group, TextView base, TextView mark) {
        if (group == null || mark == null) return false;
        if (isConstraintLayout(group)) {
            if (group.getId() == View.NO_ID) {
                group.setId(View.generateViewId());
            }
            if (base != null && base.getId() == View.NO_ID) {
                base.setId(View.generateViewId());
            }
            Object lp = newConstraintLayoutParams(group);
            if (lp instanceof ViewGroup.LayoutParams) {
                setConstraintId(lp, "endToEnd", group.getId());
                setConstraintId(lp, "topToTop", group.getId());
                setConstraintId(lp, "bottomToBottom", group.getId());
                if (base != null) {
                    setConstraintId(lp, "baselineToBaseline", base.getId());
                }
                setMarginEnd(lp, dp(group.getContext(), 6));
                mark.setLayoutParams((ViewGroup.LayoutParams) lp);
                mark.setId(View.generateViewId());
                group.addView(mark);
                return true;
            }
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
        if (isConstraintLayout(group)) {
            if (group.getId() == View.NO_ID) {
                group.setId(View.generateViewId());
            }
            if (before.getId() == View.NO_ID) {
                before.setId(View.generateViewId());
            }
            if (base != null && base.getId() == View.NO_ID) {
                base.setId(View.generateViewId());
            }
            Object lp = newConstraintLayoutParams(group);
            if (lp instanceof ViewGroup.LayoutParams) {
                setConstraintId(lp, "endToStart", before.getId());
                setConstraintId(lp, "topToTop", group.getId());
                setConstraintId(lp, "bottomToBottom", group.getId());
                if (base != null) {
                    setConstraintId(lp, "baselineToBaseline", base.getId());
                }
                setMarginEnd(lp, dp(group.getContext(), 6));
                mark.setLayoutParams((ViewGroup.LayoutParams) lp);
                mark.setId(View.generateViewId());
                group.addView(mark);
                return true;
            }
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

    private static boolean isConstraintLayout(ViewGroup group) {
        if (group == null) return false;
        return "androidx.constraintlayout.widget.ConstraintLayout".equals(group.getClass().getName());
    }

    private static Object newConstraintLayoutParams(ViewGroup group) {
        if (group == null) return null;
        ClassLoader cl = group.getClass().getClassLoader();
        if (cl == null) {
            cl = APP_CL;
        }
        Class<?> lpCls = XposedHelpers.findClassIfExists(
                "androidx.constraintlayout.widget.ConstraintLayout$LayoutParams",
                cl
        );
        if (lpCls == null) return null;
        try {
            return XposedHelpers.newInstance(lpCls,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        } catch (Throwable ignored) {
        }
        try {
            return XposedHelpers.newInstance(lpCls);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setConstraintId(Object lp, String field, int id) {
        if (lp == null || field == null) return;
        try {
            XposedHelpers.setIntField(lp, field, id);
        } catch (Throwable ignored) {
        }
    }

    private static void setMarginStart(Object lp, int value) {
        if (lp == null) return;
        try {
            XposedHelpers.callMethod(lp, "setMarginStart", value);
            return;
        } catch (Throwable ignored) {
        }
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).leftMargin = value;
        }
    }

    private static void setMarginEnd(Object lp, int value) {
        if (lp == null) return;
        try {
            XposedHelpers.callMethod(lp, "setMarginEnd", value);
            return;
        } catch (Throwable ignored) {
        }
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).rightMargin = value;
        }
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
        boolean folded = isFoldedItem(item);
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
            captureLikeTextTemplate(v);
            applyLikeUiOverrideIfNeeded(v, id);
            if (DEBUG_VERBOSE && logged) {
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
        if (DEBUG_VERBOSE && folded && changed > 0 && !logged) {
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
            ensureLikeListenerHooked(orig);
            View.OnClickListener wrapper = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean preSelected = false;
                    try {
                        preSelected = view.isSelected() || view.isActivated();
                    } catch (Throwable ignored) {
                    }
                    long clickId = getAdditionalLong(view, "BiliFoldsCommentId");
                    Object item = getAdditionalObject(view, "BiliFoldsCommentItem");
                    if (!isCommentItem(item)) {
                        try {
                            Object tag = view.getTag();
                            if (isCommentItem(tag)) item = tag;
                        } catch (Throwable ignored) {
                        }
                    }
                    if (clickId == 0L && isCommentItem(item)) {
                        long cid = getId(item);
                        if (cid != 0L) clickId = cid;
                    }
                    boolean folded = clickId != 0L && isFoldedItem(item);
                    if (folded) {
                        pushClickFoldedId(clickId);
                        if (isCommentItem(item)) {
                            forceUnfold(item);
                        }
                        markReplyControlDeep(view, clickId, new HashSet<Object>(), 2);
                        if (DEBUG_CLICK_LOGGED.putIfAbsent(clickId, Boolean.TRUE) == null) {
                            String listener = orig.getClass().getName();
                            log("like.click folded id=" + clickId
                                    + " view=" + view.getClass().getSimpleName()
                                    + " listener=" + listener);
                        }
                    }
                    try {
                        orig.onClick(view);
                    } finally {
                        if (folded) {
                            popClickFoldedId();
                            applyLikeUiOverrideAfterClick(view, clickId, preSelected);
                        }
                    }
                }
            };
            XposedHelpers.setObjectField(listenerInfo, "mOnClickListener", wrapper);
            XposedHelpers.setAdditionalInstanceField(v, "BiliFoldsClickWrapped", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    private static void ensureLikeListenerHooked(View.OnClickListener listener) {
        if (listener == null) return;
        String name = listener.getClass().getName();
        if (name == null || name.isEmpty()) return;
        LIKE_LISTENER_CLASS = name;
        hookLikeClickListenerByClass(listener.getClass());
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

    private static void pushClickFoldedId(long id) {
        if (id == 0L) return;
        java.util.ArrayDeque<Long> stack = CLICK_ID_STACK.get();
        if (stack == null) {
            stack = new java.util.ArrayDeque<>();
            CLICK_ID_STACK.set(stack);
        }
        stack.push(id);
        CLICK_FOLDED_ID.set(id);
    }

    private static void popClickFoldedId() {
        java.util.ArrayDeque<Long> stack = CLICK_ID_STACK.get();
        if (stack == null || stack.isEmpty()) return;
        stack.pop();
        Long id = stack.peek();
        if (id == null) {
            CLICK_FOLDED_ID.remove();
            CLICK_ID_STACK.remove();
        } else {
            CLICK_FOLDED_ID.set(id);
        }
    }

    private static boolean isClickForceActive() {
        Long id = CLICK_FOLDED_ID.get();
        return id != null && id != 0L;
    }

    private static long getClickFoldedId() {
        Long id = CLICK_FOLDED_ID.get();
        return id == null ? 0L : id;
    }

    private static void logReplyForceOnce(String method) {
        long id = getClickFoldedId();
        if (id == 0L) return;
        if (DEBUG_REPLY_FORCE_LOGGED.putIfAbsent(id, Boolean.TRUE) != null) return;
        log("reply.force folded id=" + id + " method=" + method);
    }

    private static void scheduleLikeRefresh(long id) {
        if (id == 0L) return;
        postToMainDelayed(new Runnable() {
            @Override
            public void run() {
                refreshLikeState(id, "like.refresh.1");
            }
        }, 180L);
        postToMainDelayed(new Runnable() {
            @Override
            public void run() {
                refreshLikeState(id, "like.refresh.2");
            }
        }, 800L);
    }

    private static void refreshLikeState(long id, String tag) {
        Object adapter = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
        if (adapter == null) return;
        try {
            List<?> list = getAdapterList(adapter);
            if (list == null) {
                XposedHelpers.callMethod(adapter, "notifyDataSetChanged");
                return;
            }
            int idx = findCommentIndexById(list, id);
            if (idx >= 0) {
                XposedHelpers.callMethod(adapter, "notifyItemChanged", idx);
            } else {
                XposedHelpers.callMethod(adapter, "notifyDataSetChanged");
            }
            if (DEBUG_VERBOSE && DEBUG_LIKE_REFRESH_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
                log(tag + " folded id=" + id + " idx=" + idx + " size=" + list.size());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyLikeUiOverrideAfterClick(View view, long id, boolean preSelected) {
        if (view == null || id == 0L) return;
        Boolean newLiked = computeLikedState(view, preSelected);
        if (newLiked == null) return;
        Integer count = computeLikeCount(view, newLiked);
        FOLDED_LIKE_STATE.put(id, newLiked);
        if (count != null) {
            FOLDED_LIKE_COUNT.put(id, count);
        } else {
            FOLDED_LIKE_COUNT.remove(id);
        }
        applyLikeUiToView(view, newLiked, count);
        if (DEBUG_VERBOSE && DEBUG_LIKE_UI_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
            log("like.ui folded id=" + id + " liked=" + newLiked + " count=" + (count == null ? "?" : count));
        }
    }

    private static void applyLikeUiOverrideIfNeeded(View v, long id) {
        if (v == null || id == 0L) return;
        if (!isLikeView(v)) return;
        Boolean liked = FOLDED_LIKE_STATE.get(id);
        Integer count = FOLDED_LIKE_COUNT.get(id);
        if (liked == null && count == null) return;
        applyLikeUiToView(v, liked, count);
    }

    private static void applyLikeUiToView(View v, Boolean liked, Integer count) {
        if (v == null) return;
        if (liked != null) {
            try {
                v.setSelected(liked);
                v.setActivated(liked);
            } catch (Throwable ignored) {
            }
        }
        if (count != null && v instanceof TextView) {
            TextView tv = (TextView) v;
            String template = getLikeTextTemplate(tv);
            String newText = formatLikeText(template, count);
            tv.setText(newText == null ? "" : newText);
        } else if (count == null && v instanceof TextView) {
            ((TextView) v).setText("");
        }
    }

    private static Boolean computeLikedState(View v, boolean preSelected) {
        if (v == null) return null;
        boolean postSelected = false;
        try {
            postSelected = v.isSelected() || v.isActivated();
        } catch (Throwable ignored) {
        }
        if (postSelected != preSelected) return postSelected;
        return !preSelected;
    }

    private static Integer computeLikeCount(View v, boolean liked) {
        if (!(v instanceof TextView)) return null;
        TextView tv = (TextView) v;
        CharSequence text = tv.getText();
        Integer count = parseFirstNumber(text);
        if (count == null) {
            return liked ? 1 : null;
        }
        int delta = liked ? 1 : -1;
        int next = count + delta;
        if (next <= 0) return null;
        return next;
    }

    private static Integer parseFirstNumber(CharSequence text) {
        if (text == null) return null;
        String s = text.toString();
        int len = s.length();
        int start = -1;
        int end = -1;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                if (start < 0) start = i;
                end = i + 1;
            } else if (start >= 0) {
                break;
            }
        }
        if (start < 0 || end <= start) return null;
        try {
            return Integer.parseInt(s.substring(start, end));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String replaceFirstNumber(CharSequence text, int value) {
        String v = String.valueOf(Math.max(0, value));
        if (text == null) return v;
        String s = text.toString();
        int len = s.length();
        int start = -1;
        int end = -1;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                if (start < 0) start = i;
                end = i + 1;
            } else if (start >= 0) {
                break;
            }
        }
        if (start < 0) {
            return value > 0 ? v : "";
        }
        return s.substring(0, start) + v + s.substring(end);
    }

    private static void captureLikeTextTemplate(View v) {
        if (!(v instanceof TextView)) return;
        if (!isLikeView(v)) return;
        TextView tv = (TextView) v;
        CharSequence text = tv.getText();
        if (text == null) return;
        String s = text.toString();
        if (parseFirstNumber(s) == null) return;
        try {
            XposedHelpers.setAdditionalInstanceField(tv, "BiliFoldsLikeTemplate", s);
        } catch (Throwable ignored) {
        }
    }

    private static String getLikeTextTemplate(TextView tv) {
        if (tv == null) return null;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(tv, "BiliFoldsLikeTemplate");
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        CharSequence text = tv.getText();
        String s = text == null ? "" : text.toString();
        if (parseFirstNumber(s) != null) return s;
        return null;
    }

    private static String formatLikeText(String template, int count) {
        if (count <= 0) return "";
        if (template != null) {
            String replaced = replaceFirstNumber(template, count);
            if (replaced != null && !replaced.isEmpty()) return replaced;
        }
        return " " + count;
    }

    private static boolean isLikeView(View v) {
        if (v == null) return false;
        int id = v.getId();
        if (id != View.NO_ID) {
            try {
                String name = v.getResources().getResourceEntryName(id);
                if (name != null) {
                    String n = name.toLowerCase();
                    if ((n.contains("like") || n.contains("thumb") || n.contains("up") || n.contains("zan"))
                            && !n.contains("dislike") && !n.contains("down")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            CharSequence desc = v.getContentDescription();
            if (desc != null) {
                String d = desc.toString();
                if (d.contains("\u70b9\u8d5e") || d.contains("\u8d5e") || d.contains("\u559c\u6b22")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int markReplyControlDeep(Object obj, long commentId, Set<Object> visited, int depth) {
        if (obj == null || depth < 0) return 0;
        if (visited == null) visited = new HashSet<>();
        if (visited.contains(obj)) return 0;
        visited.add(obj);
        Class<?> cls = obj.getClass();
        String clsName = cls.getName();
        if (clsName.startsWith("java.") || clsName.startsWith("android.")) {
            return 0;
        }
        if (clsName.startsWith("kotlin.") && !clsName.contains("Lazy")) {
            return 0;
        }
        int marked = 0;
        if (clsName.contains("ReplyControl")) {
            markReplyControl(obj, commentId);
            return 1;
        }
        Object lazyValue = tryGetLazyValue(obj);
        if (lazyValue != null) {
            marked += markReplyControlDeep(lazyValue, commentId, visited, depth - 1);
        }
        Field[] fields = cls.getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v == null) continue;
                if (isCommentItem(v)) {
                    forceUnfold(v);
                }
                String typeName = v.getClass().getName();
                if (typeName.contains("ReplyControl")) {
                    markReplyControl(v, commentId);
                    marked++;
                }
                Object lazy = tryGetLazyValue(v);
                if (lazy != null) {
                    marked += markReplyControlDeep(lazy, commentId, visited, depth - 1);
                }
                if (depth > 0) {
                    if (v instanceof List) {
                        List<?> list = (List<?>) v;
                        int max = Math.min(8, list.size());
                        for (int i = 0; i < max; i++) {
                            marked += markReplyControlDeep(list.get(i), commentId, visited, depth - 1);
                        }
                    } else if (v != null && v.getClass().isArray()) {
                        int len = java.lang.reflect.Array.getLength(v);
                        int max = Math.min(8, len);
                        for (int i = 0; i < max; i++) {
                            marked += markReplyControlDeep(java.lang.reflect.Array.get(v, i), commentId, visited, depth - 1);
                        }
                    } else if (!typeName.startsWith("java.") && !typeName.startsWith("android.") && !typeName.startsWith("kotlin.")) {
                        marked += markReplyControlDeep(v, commentId, visited, depth - 1);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return marked;
    }

    private static Object tryGetLazyValue(Object obj) {
        if (obj == null) return null;
        String name = obj.getClass().getName();
        if (!name.contains("Lazy")) return null;
        try {
            java.lang.reflect.Method m = obj.getClass().getDeclaredMethod("getValue");
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable ignored) {
        }
        return null;
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
        final int token = nextMarkToken(host);
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
                if (!isMarkTokenValid(host, token)) return;
                if (!isMarkHostMatch(host, mark)) return;
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
        final int token = nextMarkToken(host);
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
                if (!isMarkTokenValid(host, token)) return;
                if (!isMarkHostMatch(host, mark)) return;
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
        if (DEBUG_VERBOSE && DEBUG_MARK_POS_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
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
        final int token = nextMarkToken(host);
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
                if (!isMarkTokenValid(host, token)) return;
                if (!isMarkHostMatch(host, mark)) return;
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

    private static boolean isMarkHostMatch(View host, View mark) {
        long markId = 0L;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(mark, "BiliFoldsId");
            if (v instanceof Number) markId = ((Number) v).longValue();
        } catch (Throwable ignored) {
        }
        long boundId = getBoundCommentId(host);
        if (markId != 0L && boundId != 0L && markId != boundId) {
            return false;
        }
        return true;
    }

    private static int nextMarkToken(View host) {
        int token = MARK_TOKEN_GEN.incrementAndGet();
        try {
            XposedHelpers.setAdditionalInstanceField(host, "BiliFoldsMarkToken", token);
        } catch (Throwable ignored) {
        }
        return token;
    }

    private static boolean isMarkTokenValid(View host, int token) {
        if (host == null) return false;
        try {
            Object v = XposedHelpers.getAdditionalInstanceField(host, "BiliFoldsMarkToken");
            if (v instanceof Number) {
                return ((Number) v).intValue() == token;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static long getBoundCommentId(View view) {
        View cur = view;
        for (int i = 0; i < 6 && cur != null; i++) {
            try {
                Object v = XposedHelpers.getAdditionalInstanceField(cur, "BiliFoldsCommentId");
                if (v instanceof Number) {
                    long id = ((Number) v).longValue();
                    if (id != 0L) return id;
                }
            } catch (Throwable ignored) {
            }
            if (!(cur.getParent() instanceof View)) break;
            cur = (View) cur.getParent();
        }
        return 0L;
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
        if (DEBUG_VERBOSE && DEBUG_MARK_POS_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
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
        try {
            XposedHelpers.removeAdditionalInstanceField(root, "BiliFoldsMarkToken");
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
            java.lang.reflect.Method target = null;
            String[] preferred = new String[] {"submitList", "b1", "setList", "setData", "updateList", "a"};
            for (String name : preferred) {
                for (java.lang.reflect.Method m : methods) {
                    if (m == null || !name.equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts == null || pts.length == 0) continue;
                    if (!pts[0].isAssignableFrom(list.getClass()) && !List.class.isAssignableFrom(pts[0])) {
                        continue;
                    }
                    target = m;
                    break;
                }
                if (target != null) break;
            }
            if (target == null) {
                for (java.lang.reflect.Method m : methods) {
                    if (m == null) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts == null || pts.length == 0) continue;
                    if (!List.class.isAssignableFrom(pts[0])) continue;
                    target = m;
                    break;
                }
            }
            if (target == null) return false;
            Class<?>[] pts = target.getParameterTypes();
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
                target.setAccessible(true);
                target.invoke(adapter, args);
                return true;
            } catch (Throwable ignored) {
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
        long rootId = 0L;
        boolean mixedRoot = false;
        for (Object o : list) {
            if (o == null || !isCommentItem(o)) continue;
            long root = getRootId(o);
            if (root == 0L) continue;
            if (rootId == 0L) {
                rootId = root;
            } else if (root != rootId) {
                mixedRoot = true;
                break;
            }
        }
        if (rootId == 0L || mixedRoot) {
            return;
        }
        ArrayList<Object> bucket = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o == null || !isCommentItem(o)) continue;
            long id = getId(o);
            long root = getRootId(o);
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
        String scopeKey = getCurrentScopeKey();
        if (scopeKey == null || scopeKey.isEmpty()) {
            scopeKey = getCurrentSubjectKey();
        }
        if (!shouldCacheFoldList(tag, realOffset, scopeKey)) {
            return;
        }
        String key = makeOffsetKey(realOffset, scopeKey);
        if (key == null || key.isEmpty()) {
            return;
        }
        ArrayList<Object> existing = FOLD_CACHE_BY_OFFSET.computeIfAbsent(key, k -> new ArrayList<>());
        mergeUniqueById(existing, bucket);
        String subjectKey = scopeKey;
        if (subjectKey == null) {
            subjectKey = getCurrentSubjectKey();
        }
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
            putRootForOffset(realOffset, scopeKey, rootId);
        }
        tryUpdateCommentAdapterList(realOffset);
    }

    private static boolean shouldCacheFoldList(String tag, String offset, String scopeKey) {
        if (offset == null || offset.isEmpty()) return false;
        if (tag != null && tag.startsWith("auto.")) return true;
        String key = makeOffsetKey(offset, scopeKey);
        if (key != null && !key.isEmpty()) {
            if (OFFSET_INSERT_INDEX.containsKey(key)) return true;
            if (OFFSET_TO_ROOT.containsKey(key)) return true;
            if (AUTO_FETCHING.containsKey(key)) return true;
        }
        if (OFFSET_INSERT_INDEX.containsKey(offset)) return true;
        if (OFFSET_TO_ROOT.containsKey(offset)) return true;
        if (AUTO_FETCHING.containsKey(offset)) return true;
        return false;
    }

    private static void tryUpdateCommentAdapterList(String offset) {
        if (shouldThrottleAdapterUpdate(offset)) return;
        Object adapter = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
        if (adapter == null) return;
        List<?> list = getAdapterList(adapter);
        if (list == null) return;
        List<?> replaced = replaceZipCardsInList(list, "CommentListAdapter.cached");
        if (replaced == null) return;
        if (!containsFooterCard(list)) {
            if (shouldWaitForFooter(list)) {
                if (scheduleFooterRetry(offset)) {
                    return;
                }
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

    private static boolean shouldThrottleAdapterUpdate(String offset) {
        String key = makeOffsetKey(offset, getCurrentScopeKey());
        if (key == null || key.isEmpty()) key = offset;
        if (key == null || key.isEmpty()) return false;
        final String finalKey = key;
        long now = SystemClock.uptimeMillis();
        Long last = LAST_ADAPTER_UPDATE.get(finalKey);
        if (last != null && now - last < UPDATE_THROTTLE_MS) {
            if (UPDATE_PENDING.putIfAbsent(finalKey, Boolean.TRUE) == null) {
                final String finalOffset = offset;
                postToMainDelayed(new Runnable() {
                    @Override
                    public void run() {
                        UPDATE_PENDING.remove(finalKey);
                        tryUpdateCommentAdapterList(finalOffset);
                    }
                }, UPDATE_THROTTLE_MS);
            }
            return true;
        }
        LAST_ADAPTER_UPDATE.put(finalKey, now);
        return false;
    }

    private static boolean shouldWaitForFooter(List<?> list) {
        if (list == null) return false;
        String scopeKey = getCurrentScopeKey();
        if (scopeKey != null && scopeKey.contains("|r:")) return false;
        long root = detectSingleRootId(list);
        if (root > 0) return false;
        return list.size() >= 80;
    }

    private static void hookCommentItemTags(ClassLoader cl) {
        Class<?> c = resolveCommentItemClass(cl);
        if (c == null) {
            log("CommentItem class not found");
            return;
        }
        hookCommentItemTagsByClass(c);
    }

    private static void hookCommentItemTagsByClass(Class<?> c) {
        if (c == null) return;
        if (!HOOKED_COMMENT_ITEM_TAGS.add(c.getName())) return;
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
                        if (!isFoldedItem(item)) return;
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
        Class<?> c = resolveCommentItemClass(cl);
        if (c == null) {
            log("CommentItem class not found");
            return;
        }
        hookCommentItemFoldFlagsByClass(c);
    }

    private static void hookCommentItemFoldFlagsByClass(Class<?> c) {
        if (c == null) return;
        if (!HOOKED_COMMENT_ITEM_FLAGS.add(c.getName())) return;
        hookBooleanMethodReturnFalse(c, "D");
        hookBooleanMethodReturnFalse(c, "isFolded");
        hookBooleanMethodReturnFalse(c, "getIsFolded");
        hookBooleanMethodReturnFalse(c, "getIsFoldedReply");
    }

    private static void hookActionDispatcher(ClassLoader cl) {
        Class<?> c = resolveActionDispatcherClass(cl);
        if (c == null) {
            log("action dispatcher class not found");
            return;
        }
        try {
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts == null || pts.length == 0) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
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
        } catch (Throwable ignored) {
        }
    }

    private static long extractFoldedIdFromArgs(Object[] args) {
        if (args == null) return 0L;
        for (Object a : args) {
            if (a == null) continue;
            if (isCommentItem(a)) {
                long id = getId(a);
                if (id != 0L && isFoldedItem(a)) return id;
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
        if (c == null || name == null) return;
        try {
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null) continue;
                if (!name.equals(m.getName())) continue;
                Class<?> rt = m.getReturnType();
                if (rt != boolean.class && rt != Boolean.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return false;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookZipCardView(ClassLoader cl) {
        String name = ZIP_CARD_CLASS;
        if (name == null || name.isEmpty()) return;
        hookZipCardViewByName(name, cl, true);
    }

    private static void hookZipCardViewByName(String name, ClassLoader cl, boolean logIfMissing) {
        if (name == null || name.isEmpty()) return;
        ZIP_CARD_CLASS = name;
        if (!HOOKED_ZIP_CLASSES.add(name)) return;
        Class<?> c = XposedHelpers.findClassIfExists(name, cl);
        if (c == null) {
            if (logIfMissing) {
                log(name + " class not found");
            }
            HOOKED_ZIP_CLASSES.remove(name);
            return;
        }
        hookZipCardViewByClass(c);
    }

    private static void hookZipCardViewByClass(Class<?> c) {
        if (c == null) return;
        try {
            java.lang.reflect.Method[] methods = c.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null) continue;
                String name = m.getName();
                if (name == null) continue;
                Class<?> rt = m.getReturnType();
                if ("h".equals(name) && rt != null && CharSequence.class.isAssignableFrom(rt)) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isAutoExpand(param.thisObject)) return;
                            param.setResult(AUTO_EXPAND_TEXT);
                        }
                    });
                }
                if ("f".equals(name) && (rt == boolean.class || rt == Boolean.class)) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isAutoExpand(param.thisObject)) return;
                            param.setResult(false);
                        }
                    });
                }
            }
        } catch (Throwable ignored) {
        }
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
        if (FOLD_CACHE.isEmpty() && FOLD_CACHE_BY_OFFSET.isEmpty() && FOLD_CACHE_BY_SUBJECT.isEmpty()) {
            return null;
        }
        ArrayList<Object> out = new ArrayList<>(list.size());
        boolean changed = false;
        boolean sawZipCard = false;
        Boolean desc = Boolean.FALSE;
        String subjectKey = updateScopeKeyFromList(list);
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
                    putRootForOffset(offset, subjectKey, rootId);
                }
                String key = makeOffsetKey(offset, subjectKey);
                if (key != null && !key.isEmpty()) {
                    OFFSET_INSERT_INDEX.put(key, i);
                }
                List<Object> cached = getCachedFoldListForZip(item, offset, desc, subjectKey);
                if (cached == null || cached.isEmpty()) {
                    log("zip card cache miss offset=" + offset + " root=" + rootId + " tag=" + tag);
                    tryAutoFetchFoldList(offset, subjectKey, rootId);
                    markAutoExpand(item);
                    out.add(item);
                    continue;
                }
                if (DEBUG_VERBOSE) {
                    log("zip card cache hit offset=" + offset + " root=" + rootId + " size=" + cached.size() + " tag=" + tag);
                }
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
                    markFoldedItem(o);
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
            if (isZipCardClass(item)) {
                String text = callStringMethod(item, "h");
                String offset = getZipCardOffset(item);
                if ((text != null && (text.contains("\u5c55\u5f00\u66f4\u591a\u8bc4\u8bba") || text.contains("\u5c55\u5f00\u66f4\u591a"))) ||
                        (offset != null && !offset.isEmpty())) {
                    if (DEBUG_VERBOSE) {
                        log("skip r1 non-zip text=" + text + " offset=" + offset + " tag=" + tag);
                    }
                }
            }
            out.add(item);
        }
        if (injectCachedByPendingOffsets(out, existingIds, subjectKey)) {
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

    private static boolean injectCachedByPendingOffsets(ArrayList<Object> out, HashSet<Long> existingIds, String subjectKey) {
        if (out == null) return false;
        String prefix = null;
        if (subjectKey != null && !subjectKey.isEmpty()) {
            prefix = subjectKey + "|";
        }
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : new ArrayList<>(OFFSET_INSERT_INDEX.entrySet())) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) continue;
            if (prefix != null && !key.startsWith(prefix)) continue;
            ArrayList<Object> cached = FOLD_CACHE_BY_OFFSET.get(key);
            if (cached == null || cached.isEmpty()) continue;
            int idx = entry.getValue() == null ? out.size() : entry.getValue();
            if (idx < 0) idx = 0;
            if (idx > out.size()) idx = out.size();
            int inserted = 0;
            for (Object o : cached) {
                long id = getId(o);
                if (id != 0 && existingIds.contains(id)) continue;
                markFoldedItem(o);
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
            markFoldedItem(o);
            forceUnfold(o);
            out.add(o);
            inserted++;
            if (id != 0) existingIds.add(id);
        }
        if (inserted > 0) {
            SUBJECT_EXPANDED.put(subjectKey, Boolean.TRUE);
            if (DEBUG_VERBOSE) {
                log("inject subject cached size=" + cached.size() + " inserted=" + inserted + " key=" + subjectKey);
            }
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
            if (DEBUG_VERBOSE) {
                log("comment fold stats root=" + rootId
                        + " id=" + getId(item)
                        + " folded=" + folded
                        + " invisible=" + invisible
                        + " total=" + total
                        + " child=" + child);
            }
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
        String subjectKey = updateScopeKeyFromList(list);
        int max = list.size();
        int found = 0;
        int seenR1 = 0;
        if (DEBUG_VERBOSE) {
            log("prefetch list size=" + list.size() + " scanMax=" + max);
        }
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (isZipCardClass(item)) {
                seenR1++;
                String text = callStringMethod(item, "h");
                String offset = getZipCardOffset(item);
                if ((text != null && (text.contains("\u5c55\u5f00\u66f4\u591a\u8bc4\u8bba") || text.contains("\u5c55\u5f00\u66f4\u591a"))) ||
                        (offset != null && !offset.isEmpty())) {
                    if (DEBUG_VERBOSE) {
                        log("prefetch r1 idx=" + i + " text=" + text + " offset=" + offset);
                    }
                }
            }
            if (!isZipCard(item)) continue;
            found++;
            if (subjectKey != null) {
                SUBJECT_HAS_FOLD.put(subjectKey, Boolean.TRUE);
            }
            String offset = getZipCardOffset(item);
            long rootId = getZipCardRootId(item);
            if (rootId == 0L) {
                rootId = findPrevCommentRootId(list, i);
            }
            if (DEBUG_VERBOSE) {
                log("prefetch fold card idx=" + i + " offset=" + offset + " root=" + rootId);
            }
            tryAutoFetchFoldList(offset, subjectKey, rootId);
        }
        if (DEBUG_VERBOSE && found > 1) {
            log("prefetch fold cards total=" + found);
        }
        if (DEBUG_VERBOSE && seenR1 > 1) {
            log("prefetch r1 total=" + seenR1);
        }
    }

    private static List<Object> getCachedFoldListForZip(Object zipCard, String offset, Boolean desc, String subjectKey) {
        ArrayList<Object> cached = null;
        if (offset != null && !offset.isEmpty()) {
            String key = resolveOffsetKey(offset, subjectKey);
            if (key != null) {
                cached = FOLD_CACHE_BY_OFFSET.get(key);
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
        String key = resolveOffsetKey(offset, subjectKey);
        if (key != null && FOLD_CACHE_BY_OFFSET.containsKey(key)) {
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
        final long rootIdFinal = rootId != 0L ? rootId : getRootForOffset(offset, subjectKey);
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
        Class<?> mossCls = resolveReplyMossClass(cl);
        if (mossCls == null) {
            return null;
        }
        Class<?> foldReqCls = resolveFoldListReqClass(mossCls);
        if (foldReqCls == null) {
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
        Object mapped = tryMapFoldList(resp, withChildren);
        return mapped == null ? resp : mapped;
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
        Class<?> mossCls = resolveReplyMossClass(cl);
        if (mossCls == null) return null;
        Class<?> reqCls = resolveDetailListReqClass(mossCls);
        if (reqCls == null) return null;
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
        Object mapped = tryMapDetail(null, resp, rootId);
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
        if (resp == null) return null;
        if (mapCls == null) {
            mapCls = resolveDataMapClass(resp.getClass());
        }
        if (mapCls == null) return null;
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
        Class<?> feedPaginationCls = resolvePaginationClass(cl);
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
            LAST_SCOPE_KEY = null;
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
            LAST_SCOPE_KEY = null;
        }
        return key;
    }

    private static String getCurrentScopeKey() {
        if (LAST_SCOPE_KEY != null) return LAST_SCOPE_KEY;
        String subjectKey = getCurrentSubjectKey();
        if (subjectKey != null && !subjectKey.isEmpty()) {
            LAST_SCOPE_KEY = subjectKey;
        }
        return subjectKey;
    }

    private static String updateScopeKeyFromList(List<?> list) {
        String subjectKey = getCurrentSubjectKey();
        if (subjectKey == null) {
            subjectKey = deriveSubjectKeyFromList(list);
        }
        if (subjectKey == null || subjectKey.isEmpty()) {
            LAST_SCOPE_KEY = null;
            return null;
        }
        long root = detectSingleRootId(list);
        String scopeKey = root > 0 ? subjectKey + "|r:" + root : subjectKey;
        LAST_SCOPE_KEY = scopeKey;
        return scopeKey;
    }

    private static long detectSingleRootId(List<?> list) {
        if (list == null || list.isEmpty()) return 0L;
        long root = 0L;
        int found = 0;
        int max = Math.min(20, list.size());
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (!isCommentItem(item)) continue;
            long r = getRootId(item);
            if (r == 0L) r = getId(item);
            if (r == 0L) continue;
            if (root == 0L) {
                root = r;
            } else if (root != r) {
                return 0L;
            }
            found++;
        }
        return found >= 3 ? root : 0L;
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
        LAST_ADAPTER_UPDATE.clear();
        UPDATE_PENDING.clear();
        AUTO_EXPAND_ZIP.clear();
        LAST_SCOPE_KEY = null;
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
        List<?> list = getAdapterList(adapter);
        if (list == null) return null;
        return deriveSubjectKeyFromList(list);
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
        if (subjectKey == null || subjectKey.isEmpty()) {
            if (FOLD_CACHE_BY_OFFSET.containsKey(offset)) return offset;
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

    private static long getRootForOffset(String offset, String scopeKey) {
        if (offset == null || offset.isEmpty()) return 0L;
        String key = makeOffsetKey(offset, scopeKey);
        if (key != null) {
            Long v = OFFSET_TO_ROOT.get(key);
            if (v != null) return v;
        }
        if (scopeKey != null && !scopeKey.isEmpty()) return 0L;
        Long v = OFFSET_TO_ROOT.get(offset);
        return v == null ? 0L : v;
    }

    private static void putRootForOffset(String offset, String scopeKey, long rootId) {
        if (offset == null || offset.isEmpty() || rootId == 0L) return;
        String key = makeOffsetKey(offset, scopeKey);
        if (key != null) {
            OFFSET_TO_ROOT.put(key, rootId);
            return;
        }
        OFFSET_TO_ROOT.put(offset, rootId);
    }

    private static boolean isCommentItem(Object item) {
        if (item == null) return false;
        Class<?> cls = item.getClass();
        Class<?> known = COMMENT_ITEM_CLASS;
        if (known != null) {
            return known == cls;
        }
        if (!looksLikeCommentItemInstance(item)) return false;
        setCommentItemClass(cls, "instance");
        return true;
    }

    private static int findCommentIndexById(List<?> list, long id) {
        if (list == null || id == 0L) return -1;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!isCommentItem(item)) continue;
            long cid = getId(item);
            if (cid == id) return i;
        }
        return -1;
    }

    private static Object findCommentItemById(List<?> list, long id) {
        if (list == null || id == 0L) return null;
        for (Object item : list) {
            if (!isCommentItem(item)) continue;
            if (getId(item) == id) return item;
        }
        return null;
    }

    private static Object getCommentItemFromAdapter(long id) {
        if (id == 0L) return null;
        Object adapter = LAST_COMMENT_ADAPTER == null ? null : LAST_COMMENT_ADAPTER.get();
        if (adapter == null) return null;
        try {
            List<?> list = getAdapterList(adapter);
            if (list == null) return null;
            return findCommentItemById(list, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getCommentItemFromCaches(long id, Set<Object> visited) {
        if (id == 0L) return null;
        if (visited == null) visited = new HashSet<>();
        Object item = findCommentItemById(FOLD_CACHE.get(id), id);
        if (isCommentItem(item)) return item;
        for (ArrayList<Object> list : FOLD_CACHE_BY_OFFSET.values()) {
            item = findCommentItemById(list, id);
            if (isCommentItem(item)) return item;
        }
        for (ArrayList<Object> list : FOLD_CACHE_BY_SUBJECT.values()) {
            item = findCommentItemById(list, id);
            if (isCommentItem(item)) return item;
        }
        return null;
    }

    private static Boolean optimisticLikeUpdate(long id, View view, boolean preSelected) {
        if (id == 0L) return null;
        Object item = getAdditionalObject(view, "BiliFoldsCommentItem");
        if (!isCommentItem(item)) {
            item = getCommentItemFromAdapter(id);
        }
        if (!isCommentItem(item)) {
            item = getCommentItemFromCaches(id, new HashSet<Object>());
        }
        Boolean current = guessLikeState(item, new HashSet<Object>(), 3);
        boolean newLiked = current != null ? !current : !preSelected;
        int delta = 0;
        if (current != null) {
            if (current != newLiked) delta = newLiked ? 1 : -1;
        } else {
            delta = newLiked ? 1 : 0;
        }
        HashSet<Object> updated = new HashSet<>();
        int count = updateLikeDeep(item, newLiked, delta, updated, 3);
        count += updateLikeInCaches(id, newLiked, delta, updated);
        if (DEBUG_VERBOSE && DEBUG_LIKE_OPT_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
            log("like.optimistic folded id=" + id + " liked=" + newLiked + " delta=" + delta + " updated=" + count);
        }
        return newLiked;
    }

    private static int updateLikeInCaches(long id, boolean liked, int delta, Set<Object> updated) {
        int count = 0;
        ArrayList<Object> byRoot = FOLD_CACHE.get(id);
        count += updateLikeInList(byRoot, id, liked, delta, updated);
        for (ArrayList<Object> list : FOLD_CACHE_BY_OFFSET.values()) {
            count += updateLikeInList(list, id, liked, delta, updated);
        }
        for (ArrayList<Object> list : FOLD_CACHE_BY_SUBJECT.values()) {
            count += updateLikeInList(list, id, liked, delta, updated);
        }
        return count;
    }

    private static int updateLikeInList(List<?> list, long id, boolean liked, int delta, Set<Object> updated) {
        if (list == null || id == 0L) return 0;
        int count = 0;
        for (Object item : list) {
            if (!isCommentItem(item)) continue;
            if (getId(item) != id) continue;
            count += updateLikeDeep(item, liked, delta, updated, 3);
        }
        return count;
    }

    private static Boolean guessLikeState(Object obj, Set<Object> visited, int depth) {
        if (obj == null || depth < 0) return null;
        if (visited == null) visited = new HashSet<>();
        if (visited.contains(obj)) return null;
        visited.add(obj);
        String clsName = obj.getClass().getName();
        if (clsName.startsWith("java.") || clsName.startsWith("android.")) return null;
        Object lazyValue = tryGetLazyValue(obj);
        if (lazyValue != null) {
            Boolean v = guessLikeState(lazyValue, visited, depth - 1);
            if (v != null) return v;
        }
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            String name = f.getName();
            if (name == null) continue;
            String n = name.toLowerCase();
            if (!isLikeName(n) || isDislikeName(n)) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                Class<?> t = f.getType();
                if (t == boolean.class || t == Boolean.class) {
                    return v instanceof Boolean ? (Boolean) v : null;
                }
                if ((t == int.class || t == Integer.class || t == long.class || t == Long.class) && isLikeStateName(n)) {
                    if (v instanceof Number) return ((Number) v).intValue() > 0;
                }
                if (v != null && depth > 0) {
                    Boolean child = guessLikeState(v, visited, depth - 1);
                    if (child != null) return child;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static int updateLikeDeep(Object obj, boolean liked, int delta, Set<Object> visited, int depth) {
        if (obj == null || depth < 0) return 0;
        if (visited == null) visited = new HashSet<>();
        if (visited.contains(obj)) return 0;
        visited.add(obj);
        String clsName = obj.getClass().getName();
        if (clsName.startsWith("java.") || clsName.startsWith("android.")) return 0;
        int updated = 0;
        Object lazyValue = tryGetLazyValue(obj);
        if (lazyValue != null) {
            updated += updateLikeDeep(lazyValue, liked, delta, visited, depth - 1);
        }
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f == null) continue;
            String name = f.getName();
            if (name == null) continue;
            String n = name.toLowerCase();
            boolean likeName = isLikeName(n) && !isDislikeName(n);
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                Class<?> t = f.getType();
                if (likeName && (t == boolean.class || t == Boolean.class)) {
                    f.setBoolean(obj, liked);
                    updated++;
                } else if (likeName && isLikeStateName(n) && (t == int.class || t == Integer.class || t == long.class || t == Long.class)) {
                    if (t == long.class || t == Long.class) f.set(obj, liked ? 1L : 0L);
                    else f.set(obj, liked ? 1 : 0);
                    updated++;
                } else if (likeName && isLikeCountName(n) && delta != 0 && (t == int.class || t == Integer.class || t == long.class || t == Long.class)) {
                    long base = v instanceof Number ? ((Number) v).longValue() : 0L;
                    long nv = base + delta;
                    if (nv < 0) nv = 0;
                    if (t == long.class || t == Long.class) f.set(obj, nv);
                    else f.set(obj, (int) nv);
                    updated++;
                }
                if (depth > 0 && v != null) {
                    String typeName = v.getClass().getName();
                    if (v instanceof List) {
                        List<?> list = (List<?>) v;
                        int max = Math.min(6, list.size());
                        for (int i = 0; i < max; i++) {
                            updated += updateLikeDeep(list.get(i), liked, delta, visited, depth - 1);
                        }
                    } else if (v.getClass().isArray()) {
                        int len = java.lang.reflect.Array.getLength(v);
                        int max = Math.min(6, len);
                        for (int i = 0; i < max; i++) {
                            updated += updateLikeDeep(java.lang.reflect.Array.get(v, i), liked, delta, visited, depth - 1);
                        }
                    } else if (!typeName.startsWith("java.") && !typeName.startsWith("android.")) {
                        updated += updateLikeDeep(v, liked, delta, visited, depth - 1);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return updated;
    }

    private static boolean isLikeName(String n) {
        if (n == null) return false;
        return n.contains("like") || n.contains("zan") || n.contains("thumb") || n.contains("up");
    }

    private static boolean isDislikeName(String n) {
        if (n == null) return false;
        return n.contains("dislike") || n.contains("down");
    }

    private static boolean isLikeCountName(String n) {
        if (n == null) return false;
        return n.contains("count") || n.contains("num") || n.contains("cnt") || n.contains("total");
    }

    private static boolean isLikeStateName(String n) {
        if (n == null) return false;
        return n.contains("state") || n.contains("status");
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
        if (COMMENT_TIME_FIELD != null && COMMENT_TIME_FIELD_CLASS == item.getClass()) {
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
        if (COMMENT_TIME_FIELD != null && COMMENT_TIME_FIELD_CLASS == item.getClass()) {
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
        if (item == null) return;
        Class<?> cls = item.getClass();
        if (COMMENT_TIME_FIELD != null && COMMENT_TIME_FIELD_CLASS == cls) return;
        if (COMMENT_ITEM_CLASS != null && !COMMENT_ITEM_CLASS.isAssignableFrom(cls)) return;
        if (COMMENT_ITEM_CLASS == null && !looksLikeCommentItemInstance(item)) return;
        if (COMMENT_ITEM_CLASS == null) {
            setCommentItemClass(cls, "time-field");
        }
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
            COMMENT_TIME_FIELD_CLASS = cls;
        }
    }

    private static long getId(Object item) {
        if (item == null) return 0L;
        Class<?> cls = item.getClass();
        if (COMMENT_ITEM_CLASS != null && !COMMENT_ITEM_CLASS.isAssignableFrom(cls)) {
            return 0L;
        }
        try {
            return XposedHelpers.getLongField(item, "f55112a");
        } catch (Throwable ignored) {
        }
        try {
            Object v = XposedHelpers.callMethod(item, "getId");
            if (v instanceof Long) return (Long) v;
        } catch (Throwable ignored) {
        }
        if (COMMENT_ID_FIELD != null && COMMENT_ID_FIELD_CLASS == cls) {
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
        if (COMMENT_ID_FIELD != null && COMMENT_ID_FIELD_CLASS == cls) {
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
        if (item == null) return;
        Class<?> cls = item.getClass();
        if (COMMENT_ID_FIELD != null && COMMENT_ID_FIELD_CLASS == cls) return;
        if (COMMENT_ITEM_CLASS != null && !COMMENT_ITEM_CLASS.isAssignableFrom(cls)) return;
        if (COMMENT_ITEM_CLASS == null && !looksLikeCommentItemInstance(item)) return;
        if (COMMENT_ITEM_CLASS == null) {
            setCommentItemClass(cls, "id-field");
        }
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
            COMMENT_ID_FIELD_CLASS = cls;
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
            if (!resolveCommentTagClasses(cl)) return null;
            Class<?> tagCls = COMMENT_TAG_CLASS;
            Class<?> displayCls = COMMENT_TAG_DISPLAY_CLASS;
            Class<?> labelCls = COMMENT_TAG_LABEL_CLASS;
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
        if (item == null) return false;
        String cls = item.getClass().getName();
        String known = ZIP_CARD_CLASS;
        if (known != null && !known.isEmpty() && known.equals(cls)) {
            return looksLikeZipCard(item);
        }
        if (looksLikeZipCard(item)) {
            ZIP_CARD_CLASS = cls;
            ClassLoader cl = item.getClass().getClassLoader();
            if (cl == null) cl = APP_CL;
            hookZipCardViewByName(cls, cl, false);
            return true;
        }
        return false;
    }

    private static boolean looksLikeZipCard(Object item) {
        if (item == null) return false;
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

    private static boolean isZipCardClass(Object item) {
        if (item == null) return false;
        String cls = item.getClass().getName();
        String known = ZIP_CARD_CLASS;
        if (known != null && !known.isEmpty()) {
            return known.equals(cls);
        }
        return false;
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
            if (isZipCardClass(item)) {
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
        if (item == null) return false;
        String text = callStringMethod(item, "h");
        if (text == null) return false;
        String t = text.trim();
        boolean isFooter = t.contains("\u518d\u600e\u4e48\u627e\u4e5f\u6ca1\u6709")
                || t.contains("\u6ca1\u6709\u66f4\u591a\u8bc4\u8bba")
                || t.contains("\u6ca1\u6709\u66f4\u591a\u4e86")
                || t.contains("\u8fd9\u91cc\u662f\u8bc4\u8bba\u533a");
        if (!isFooter) return false;
        String cls = item.getClass().getName();
        String known = ZIP_CARD_CLASS;
        if (known == null || known.isEmpty()) {
            ZIP_CARD_CLASS = cls;
            ClassLoader cl = item.getClass().getClassLoader();
            if (cl == null) cl = APP_CL;
            hookZipCardViewByName(cls, cl, false);
            return true;
        }
        return known.equals(cls);
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
        String key = makeOffsetKey(offset, getCurrentScopeKey());
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
        String key = makeOffsetKey(offset, getCurrentScopeKey());
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


