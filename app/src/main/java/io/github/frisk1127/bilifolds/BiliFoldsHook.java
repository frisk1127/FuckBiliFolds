package io.github.frisk1127.bilifolds;

import java.util.Arrays;
import java.util.List;

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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }

        ClassLoader cl = lpparam.classLoader;
        hookReplyControl(cl);
        hookSubjectControl(cl);
        hookCommentItem(cl);
    }

    private static void hookReplyControl(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.ReplyControl",
                cl
        );
        if (c == null) return;

        XposedHelpers.findAndHookMethod(c, "getIsFoldedReply", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return false;
            }
        });

        XposedHelpers.findAndHookMethod(c, "getHasFoldedReply", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return false;
            }
        });
    }

    private static void hookSubjectControl(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(
                "com.bapis.bilibili.main.community.reply.v1.SubjectControl",
                cl
        );
        if (c == null) return;

        XposedHelpers.findAndHookMethod(c, "getHasFoldedReply", new XC_MethodReplacement() {
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
        if (c == null) return;

        XposedHelpers.findAndHookMethod(c, "D", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return false;
            }
        });

        XposedHelpers.findAndHookMethod(c, "V", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                Object list = null;
                try {
                    list = XposedHelpers.callMethod(param.thisObject, "w");
                } catch (Throwable ignored) {
                    try {
                        list = XposedHelpers.getObjectField(param.thisObject, "f55134w");
                    } catch (Throwable ignored2) {
                        XposedBridge.log(TAG + ": failed to fetch childItemList");
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
                        XposedBridge.log(TAG + ": failed to fetch foldChildItemList");
                    }
                }

                if (foldList instanceof java.util.List && list instanceof java.util.List) {
                    java.util.List<?> child = (java.util.List<?>) list;
                    java.util.List<?> fold = (java.util.List<?>) foldList;
                    if (!fold.isEmpty()) {
                        java.util.ArrayList<Object> merged = new java.util.ArrayList<>(child.size() + fold.size());
                        merged.addAll(child);
                        merged.addAll(fold);
                        return merged;
                    }
                }

                return list;
            }
        });
    }

}
