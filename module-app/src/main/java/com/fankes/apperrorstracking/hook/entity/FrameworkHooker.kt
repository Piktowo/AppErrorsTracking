/*
 * AppErrorsTracking - Added more features to app's crash dialog, fixed custom rom deleted dialog, the best experience to Android developer.
 * Copyright (C) 2017 Fankes Studio(qzmmcn@163.com)
 * https://github.com/Piktowo/AppErrorsTracking
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 *
 * This file is created by fankes on 2022/5/7.
 */
@file:Suppress("ConstPropertyName")

package com.fankes.apperrorstracking.hook.entity

import android.app.ApplicationErrorReport
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.util.ArrayMap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.fankes.apperrorstracking.R
import com.fankes.apperrorstracking.bean.AppErrorsDisplayBean
import com.fankes.apperrorstracking.bean.AppErrorsInfoBean
import com.fankes.apperrorstracking.bean.AppInfoBean
import com.fankes.apperrorstracking.bean.MutedErrorsAppBean
import com.fankes.apperrorstracking.bean.enum.AppFiltersType
import com.fankes.apperrorstracking.data.AppErrorsConfigData
import com.fankes.apperrorstracking.data.AppErrorsRecordData
import com.fankes.apperrorstracking.data.ConfigData
import com.fankes.apperrorstracking.data.enum.AppErrorsConfigType
import com.fankes.apperrorstracking.locale.locale
import com.fankes.apperrorstracking.ui.activity.errors.AppErrorsDisplayActivity
import com.fankes.apperrorstracking.ui.activity.errors.AppErrorsRecordActivity
import com.fankes.apperrorstracking.utils.factory.appNameOf
import com.fankes.apperrorstracking.utils.factory.drawableOf
import com.fankes.apperrorstracking.utils.factory.isAppCanOpened
import com.fankes.apperrorstracking.utils.factory.listOfPackages
import com.fankes.apperrorstracking.utils.factory.openApp
import com.fankes.apperrorstracking.utils.factory.pushNotify
import com.fankes.apperrorstracking.utils.factory.toArrayList
import com.fankes.apperrorstracking.utils.factory.toast
import com.fankes.apperrorstracking.utils.tool.FrameworkTool
import com.fankes.apperrorstracking.wrapper.BuildConfigWrapper
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog

object FrameworkHooker : YukiBaseHooker() {

    private val UserControllerClass by lazyClass("com.android.server.am.UserController")
    private val AppErrorsClass by lazyClass("com.android.server.am.AppErrors")
    private val AppErrorDialogClass by lazyClass("com.android.server.am.AppErrorDialog")
    private val AppErrorDialog_DataClass by lazyClass("com.android.server.am.AppErrorDialog\$Data")
    private val ProcessRecordClass by lazyClass("com.android.server.am.ProcessRecord")
    private val ActivityManagerServiceClass by lazyClassOrNull("com.android.server.am.ActivityManagerService")
    private val ActivityTaskManagerService_LocalServiceClass by lazyClassOrNull("com.android.server.wm.ActivityTaskManagerService\$LocalService")

    private val PackageListClass by lazyClassOrNull(
        VariousClass(
            "com.android.server.am.ProcessRecord\$PackageList",
            "com.android.server.am.PackageList"
        )
    )
    private val ErrorDialogControllerClass by lazyClassOrNull(
        VariousClass(
            "com.android.server.am.ProcessRecord\$ErrorDialogController",
            "com.android.server.am.ErrorDialogController"
        )
    )

    /** 已忽略错误的 APP 数组 - 直到重新解锁 */
    private var mutedErrorsIfUnlockApps = mutableSetOf<String>()

    /** 已忽略错误的 APP 数组 - 直到重新启动 */
    private var mutedErrorsIfRestartApps = mutableSetOf<String>()

    /** 最近弹窗去重缓存 (避免多路径 Hook 触发重复弹窗) */
    private val recentUiEvents = LinkedHashMap<String, Long>()

    /** 通知小图标缓存，避免每次崩溃都重新 Bitmap 转换 */
    private var cachedNotifyIcon: IconCompat? = null

    /** UI 分发类型 */
    private enum class ErrorUiMode { DIALOG, NOTIFY, TOAST, NONE }

    /**
     * 当前崩溃事件是否可展示 UI
     * @param token 事件标识
     * @return [Boolean]
     */
    private fun shouldDispatchUiEvent(token: String): Boolean = synchronized(recentUiEvents) {
        val now = SystemClock.elapsedRealtime()
        recentUiEvents.entries.removeAll { now - it.value > 2500L }
        if (recentUiEvents.containsKey(token)) false else {
            recentUiEvents[token] = now
            true
        }
    }

    /** 仅在开启调试开关时打印分发日志 */
    private inline fun verboseLog(message: () -> String) {
        if (ConfigData.isEnableVerboseCrashDispatchLog) YLog.debug("[CrashDispatch] ${message()}")
    }

    /**
     * APP 进程异常数据定义类
     * @param errors [AppErrorsClass] 实例
     * @param proc [ProcessRecordClass] 实例
     * @param resultData [AppErrorDialog_DataClass] 实例 - 默认空
     */
    private class AppErrorsProcessData(errors: Any?, proc: Any?, resultData: Any? = null) {

        /**
         * 获取当前包列表实例
         * @return [Any] or null
         */
        private val pkgList by lazy {
            ProcessRecordClass.resolve().optional(silent = true)
                .firstMethodOrNull {
                    name = "getPkgList"
                    emptyParameters()
                }?.of(proc)?.invoke()
                ?: ProcessRecordClass.resolve().optional(silent = true)
                    .firstFieldOrNull {
                        name { it.endsWith("pkgList", true) }
                    }?.of(proc)?.get()
        }

        /**
         * 获取当前包列表数组大小
         * @return [Int]
         */
        private val pkgListSize by lazy {
            PackageListClass?.resolve()?.optional(silent = true)
                ?.firstMethodOrNull {
                    name = "size"
                    emptyParameters()
                }?.of(pkgList)?.invoke()
                ?: ProcessRecordClass.resolve().optional(silent = true)
                    .firstFieldOrNull { name = "pkgList" }
                    ?.of(proc)?.get<ArrayMap<*, *>>()?.size ?: -1
        }

        /**
         * 获取当前 pid 信息
         * @return [Int]
         */
        val pid by lazy {
            ProcessRecordClass.resolve().optional()
                .firstFieldOrNull {
                    name { it == "mPid" || it == "pid" }
                }?.of(proc)?.get<Int>() ?: 0
        }

        /**
         * 获取当前用户 ID 信息
         * @return [Int]
         */
        val userId by lazy {
            ProcessRecordClass.resolve().optional()
                .firstFieldOrNull { name = "userId" }
                ?.of(proc)?.get<Int>() ?: 0
        }

        /**
         * 获取当前 APP 信息
         * @return [ApplicationInfo] or null
         */
        val appInfo by lazy {
            ProcessRecordClass.resolve().optional()
                .firstFieldOrNull { name = "info" }
                ?.of(proc)?.get<ApplicationInfo>()
        }

        /**
         * 获取当前进程名称
         * @return [String]
         */
        val processName by lazy {
            ProcessRecordClass.resolve().optional()
                .firstFieldOrNull { name = "processName" }
                ?.of(proc)?.get<String>() ?: ""
        }

        /**
         * 获取当前 APP、进程 包名
         * @return [String]
         */
        val packageName = appInfo?.packageName ?: processName

        /**
         * 获取当前进程是否为可被启动的 APP - 非框架 APP
         * @return [Boolean]
         */
        val isActualApp = pkgListSize == 1 && appInfo != null

        /**
         * 获取当前进程是否为主进程
         * @return [Boolean]
         */
        val isMainProcess = packageName == processName

        /**
         * 获取当前进程是否为后台进程
         * @return [Boolean]
         */
        val isBackgroundProcess by lazy {
            UserControllerClass.resolve().optional()
                .firstMethodOrNull { name { it == "getCurrentProfileIds" || it == "getCurrentProfileIdsLocked" } }
                ?.of(ActivityManagerServiceClass?.resolve()?.optional()?.firstFieldOrNull { name = "mUserController" }
                    ?.of(AppErrorsClass.resolve().optional().firstFieldOrNull { name = "mService" }?.of(errors)?.get())?.getQuietly())
                ?.invokeQuietly<IntArray>()?.takeIf { it.isNotEmpty() }?.any { it != userId } ?: false
        }

        /**
         * 获取当前进程是否短时内重复崩溃
         * @return [Boolean]
         */
        val isRepeatingCrash by lazy {
            resultData?.let {
                AppErrorDialog_DataClass.resolve().optional()
                    .firstFieldOrNull { name = "repeating" }
                    ?.of(it)?.get<Boolean>() == true
            } ?: false
        }
    }

    /** 注册生命周期 */
    private fun registerLifecycle() {
        onAppLifecycle {
            /** 解锁后清空已记录的忽略错误 APP */
            registerReceiver(Intent.ACTION_USER_PRESENT) { _, _ -> mutedErrorsIfUnlockApps.clear() }
            /** 刷新模块 Resources 缓存 */
            registerReceiver(Intent.ACTION_LOCALE_CHANGED) { _, _ -> refreshModuleAppResources() }
            /** 启动时从本地获取异常记录数据 */
            onCreate { AppErrorsRecordData.init(context = this) }
        }
        FrameworkTool.Host.with(instance = this) {
            onRefreshFrameworkPrefsData {
                /** 必要的延迟防止 Sp 存储不刷新 */
                SystemClock.sleep(100)
                /** 刷新存储类 */
                AppErrorsConfigData.refresh()
                if (prefs.isPreferencesAvailable.not()) YLog.warn("Cannot refreshing app errors config data, preferences is not available")
            }
            onOpenAppUsedFramework {
                appContext?.openApp(it.first, it.second)
                YLog.info("Opened \"${it.first}\"${it.second.takeIf { e -> e > 0 }?.let { e -> " --user $e" } ?: ""}")
            }
            onPushAppErrorInfoData {
                AppErrorsRecordData.allData.firstOrNull { e -> e.pid == it } ?: run {
                    YLog.warn("Cannot received crash application data --pid $it")
                    AppErrorsInfoBean()
                }
            }
            onPushAppErrorsInfoData { AppErrorsRecordData.allData.toArrayList() }
            onRemoveAppErrorsInfoData {
                YLog.info("Removed app errors info data for package \"${it.packageName}\"")
                AppErrorsRecordData.remove(it)
            }
            onClearAppErrorsInfoData {
                YLog.info("Cleared all app errors info data, size ${AppErrorsRecordData.allData.size}")
                AppErrorsRecordData.clearAll()
            }
            onMutedErrorsIfUnlock {
                mutedErrorsIfUnlockApps.add(it)
                YLog.info("Muted \"$it\" until unlocks")
            }
            onMutedErrorsIfRestart {
                mutedErrorsIfRestartApps.add(it)
                YLog.info("Muted \"$it\" until restarts")
            }
            onPushMutedErrorsAppsData {
                arrayListOf<MutedErrorsAppBean>().apply {
                    mutedErrorsIfUnlockApps.takeIf { it.isNotEmpty() }
                        ?.forEach { add(MutedErrorsAppBean(MutedErrorsAppBean.MuteType.UNTIL_UNLOCKS, it)) }
                    mutedErrorsIfRestartApps.takeIf { it.isNotEmpty() }
                        ?.forEach { add(MutedErrorsAppBean(MutedErrorsAppBean.MuteType.UNTIL_REBOOTS, it)) }
                }
            }
            onUnmuteErrorsApp {
                when (it.type) {
                    MutedErrorsAppBean.MuteType.UNTIL_UNLOCKS -> {
                        YLog.info("Unmuted if unlocks errors app \"${it.packageName}\"")
                        mutedErrorsIfUnlockApps.remove(it.packageName)
                    }
                    MutedErrorsAppBean.MuteType.UNTIL_REBOOTS -> {
                        YLog.info("Unmuted if restarts errors app \"${it.packageName}\"")
                        mutedErrorsIfRestartApps.remove(it.packageName)
                    }
                }
            }
            onUnmuteAllErrorsApps {
                YLog.info("Unmute all errors apps --unlocks ${mutedErrorsIfUnlockApps.size} --restarts ${mutedErrorsIfRestartApps.size}")
                mutedErrorsIfUnlockApps.clear()
                mutedErrorsIfRestartApps.clear()
            }
            onPushAppListData { filters ->
                appContext?.let { context ->
                    context.listOfPackages()
                        .filter { it.packageName.let { e -> e != "android" && e != BuildConfigWrapper.APPLICATION_ID } }
                        .let { info ->
                            arrayListOf<AppInfoBean>().apply {
                                if (info.isNotEmpty())
                                    (if (filters.name.isNotBlank()) info.filter {
                                        it.packageName.contains(filters.name) || context.appNameOf(it.packageName).contains(filters.name)
                                    } else info).let { result ->
                                        /**
                                         * 是否为系统应用
                                         * @return [Boolean]
                                         */
                                        fun PackageInfo.isSystemApp() = applicationInfo?.let {
                                            (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                                        } ?: false
                                        when (filters.type) {
                                            AppFiltersType.USER -> result.filter { it.isSystemApp().not() }
                                            AppFiltersType.SYSTEM -> result.filter { it.isSystemApp() }
                                            AppFiltersType.ALL -> result
                                        }
                                    }.sortedByDescending { it.lastUpdateTime }
                                        .forEach { add(AppInfoBean(name = context.appNameOf(it.packageName), packageName = it.packageName)) }
                                else YLog.warn("Fetched installed packages but got empty list")
                            }
                        }
                } ?: arrayListOf()
            }
        }
    }

    /**
     * 处理 APP 进程异常信息展示
     * @param context 当前实例
     */
    private fun AppErrorsProcessData.handleShowAppErrorUi(context: Context) {
        val eventToken = "$pid|$userId|$processName"
        if (!shouldDispatchUiEvent(eventToken)) {
            verboseLog { "Skip duplicated event token=$eventToken package=$packageName process=$processName" }
            return
        }
        /** 当前 APP 名称 */
        val appName = appInfo?.let { context.appNameOf(it.packageName).ifBlank { it.packageName } } ?: packageName

        /** 当前 APP 名称 (包含用户 ID) */
        val appNameWithUserId = if (userId != 0) "$appName (${locale.userId(userId)})" else appName

        /** 崩溃标题 */
        val errorTitle = if (isRepeatingCrash) locale.aerrRepeatedTitle(appNameWithUserId) else locale.aerrTitle(appNameWithUserId)
        val canOpenApp = context.isAppCanOpened(packageName)
        verboseLog {
            "Received crash package=$packageName process=$processName pid=$pid user=$userId " +
                "actual=$isActualApp main=$isMainProcess bg=$isBackgroundProcess repeating=$isRepeatingCrash canOpen=$canOpenApp"
        }

        /** 使用通知推送异常信息 */
        fun showAppErrorsWithNotify() {
            verboseLog { "Dispatch UI mode=NOTIFY package=$packageName" }
            context.pushNotify(
                channelId = "APPS_ERRORS",
                channelName = locale.appName,
                title = errorTitle,
                content = locale.appErrorsTip,
                icon = cachedNotifyIcon ?: IconCompat.createWithBitmap(moduleAppResources.drawableOf(R.drawable.ic_notify).toBitmap()).also {
                    cachedNotifyIcon = it
                },
                color = 0xFFFF6200.toInt(),
                intent = AppErrorsRecordActivity.intent()
            )
        }

        /** 使用 Toast 展示异常信息 */
        fun showAppErrorsWithToast() {
            verboseLog { "Dispatch UI mode=TOAST package=$packageName" }
            context.toast(errorTitle)
        }

        /** 使用对话框展示异常信息 */
        fun showAppErrorsWithDialog() {
            verboseLog { "Dispatch UI mode=DIALOG package=$packageName" }
            val isLaunched = AppErrorsDisplayActivity.start(
                context, AppErrorsDisplayBean(
                    pid = pid,
                    userId = userId,
                    packageName = packageName,
                    processName = processName,
                    appName = appName,
                    title = errorTitle,
                    isShowAppInfoButton = isActualApp,
                    isShowReopenButton = isActualApp &&
                        (isRepeatingCrash.not() || ConfigData.isEnableAlwaysShowsReopenAppOptions) &&
                        canOpenApp &&
                        isMainProcess,
                    isShowCloseAppButton = isActualApp
                )
            )
            if (isLaunched.not()) {
                verboseLog { "Dialog launch failed, fallback to NOTIFY package=$packageName" }
                showAppErrorsWithNotify()
            }
        }

        fun resolveUiMode(): ErrorUiMode = when {
            packageName == BuildConfigWrapper.APPLICATION_ID -> ErrorUiMode.NONE
            ConfigData.isEnableAppConfigTemplate -> when {
                AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.GLOBAL, packageName) -> when {
                    AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.DIALOG) -> ErrorUiMode.DIALOG
                    AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY) -> ErrorUiMode.NOTIFY
                    AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST) -> ErrorUiMode.TOAST
                    else -> ErrorUiMode.NONE
                }
                AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.DIALOG, packageName) -> ErrorUiMode.DIALOG
                AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY, packageName) -> ErrorUiMode.NOTIFY
                AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST, packageName) -> ErrorUiMode.TOAST
                else -> ErrorUiMode.NONE
            }
            else -> ErrorUiMode.DIALOG
        /** 判断是否为已忽略的 APP */
        }
        if (mutedErrorsIfUnlockApps.contains(packageName) || mutedErrorsIfRestartApps.contains(packageName)) {
            verboseLog { "Skip UI due to muted package=$packageName" }
            return
        }
        /** 判断是否为后台进程 */
        if ((isBackgroundProcess || canOpenApp.not()) && ConfigData.isEnableOnlyShowErrorsInFront) {
            verboseLog { "Skip UI due to front-only policy package=$packageName bg=$isBackgroundProcess canOpen=$canOpenApp" }
            return
        }
        /** 判断是否为主进程 */
        if (isMainProcess.not() && ConfigData.isEnableOnlyShowErrorsInMain) {
            verboseLog { "Skip UI due to main-process-only policy package=$packageName process=$processName" }
            return
        }
        val mode = resolveUiMode()
        when (mode) {
            ErrorUiMode.DIALOG -> showAppErrorsWithDialog()
            ErrorUiMode.NOTIFY -> showAppErrorsWithNotify()
            ErrorUiMode.TOAST -> showAppErrorsWithToast()
            ErrorUiMode.NONE -> {
                if (packageName == BuildConfigWrapper.APPLICATION_ID) {
                    context.toast(msg = "AppErrorsTracking has crashed, please see the log in console")
                    YLog.error("AppErrorsTracking has crashed itself, please see the Android Runtime Exception in console")
                } else verboseLog { "Skip UI due to mode=NONE package=$packageName" }
            }
        }
        verboseLog { "Finish dispatch mode=${mode.name} package=$packageName" }
        /** 打印错误日志 */
        if (isActualApp) YLog.error(
            msg = "Application \"$packageName\" ${if (isRepeatingCrash) "keeps stopping" else "has stopped"}" +
                (if (packageName != processName) " --process \"$processName\"" else "") +
                "${if (userId != 0) " --user $userId" else ""} --pid $pid"
        ) else YLog.error("Process \"$processName\" ${if (isRepeatingCrash) "keeps stopping" else "has stopped"} --pid $pid")
    }

    /**
     * 处理 APP 进程异常数据
     * @param context 当前实例
     * @param info 系统错误报告数据实例
     */
    private fun AppErrorsProcessData.handleAppErrorsInfo(context: Context, info: ApplicationErrorReport.CrashInfo?) {
        AppErrorsRecordData.add(AppErrorsInfoBean.clone(context, pid, userId, appInfo?.packageName, info))
        YLog.info("Received crash application data${if (userId != 0) " --user $userId" else ""} --pid $pid")
    }

    /** 干掉系统原生弹窗并在必要时兜底转发到自定义弹窗 */
    private fun registerSystemDialogSuppressionHooks() {
        ErrorDialogControllerClass?.resolve()?.optional(silent = true)?.apply {
            val hasCrashDialogs = firstMethodOrNull {
                name = "hasCrashDialogs"
                emptyParameters()
            }?.hook()?.replaceToTrue() != null
            if (!hasCrashDialogs)
                firstConstructorOrNull {
                    parameterCount = 1
                }?.hook()?.after {
                    firstFieldOrNull { name = "mCrashDialogs" }?.of(instance)?.set(emptyList<Any>())
                }
            firstMethodOrNull {
                name = "showCrashDialogs"
                parameterCount = 1
            }?.hook()?.intercept()
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ActivityTaskManagerService_LocalServiceClass?.resolve()?.optional()?.firstMethodOrNull {
                name = "canShowErrorDialogs"
                emptyParameters()
            }?.hook()?.replaceToFalse()
            ActivityManagerServiceClass?.resolve()?.optional()?.firstMethodOrNull {
                name = "canShowErrorDialogs"
                emptyParameters()
            }?.hook()?.replaceToFalse()
        }
        AppErrorDialogClass.resolve().optional(silent = true).apply {
            firstMethodOrNull {
                name = "onCreate"
                parameters(Bundle::class)
            }?.hook()?.after {
                val dialog = instance<Dialog>()
                verboseLog { "Fallback intercept AppErrorDialog.onCreate" }
                val resultData = AppErrorDialogClass.resolve().optional(silent = true).firstFieldOrNull {
                    name { it.equals("mData", true) || it.endsWith("Data", true) }
                }?.of(dialog)?.get()
                val proc = resultData?.let {
                    AppErrorDialog_DataClass.resolve().optional().firstFieldOrNull { name = "proc" }?.of(it)?.get()
                }
                AppErrorsProcessData(errors = null, proc = proc, resultData = resultData).handleShowAppErrorUi(dialog.context)
                dialog.cancel()
            }
            firstMethodOrNull {
                name = "onStart"
                emptyParameters()
            }?.hook()?.after { instance<Dialog>().cancel() }
        }
    }

    /** 注入崩溃分发逻辑 */
    private fun registerCrashDispatchHooks() {
        AppErrorsClass.resolve().optional().apply {
            when {
                Build.VERSION.SDK_INT > Build.VERSION_CODES.R -> {
                    firstMethodOrNull {
                        name = "handleAppCrashLSPB"
                        parameterCount = 6
                    }?.hook()?.after {
                        if (args(index = 1).string() == "user-terminated") {
                            verboseLog { "Ignore user-terminated crash event" }
                            return@after
                        }
                        val context = appContext ?: firstFieldOrNull { name = "mContext" }?.of(instance)?.get<Context>() ?: return@after
                        val proc = args().first().any() ?: return@after YLog.warn("Received but got null ProcessRecord (Show UI failed)")
                        val resultData = args().last().any()
                        verboseLog { "Hit hook handleAppCrashLSPB" }
                        AppErrorsProcessData(instance, proc, resultData).handleShowAppErrorUi(context)
                    }
                }
                else -> {
                    firstMethodOrNull {
                        name = "handleShowAppErrorUi"
                        parameters(Message::class)
                    }?.hook()?.after {
                        val context = appContext ?: firstFieldOrNull { name = "mContext" }?.of(instance)?.get<Context>() ?: return@after
                        val resultData = args().first().cast<Message>()?.obj
                        val proc = AppErrorDialog_DataClass.resolve().optional().firstFieldOrNull { name = "proc" }?.of(resultData)?.get()
                        verboseLog { "Hit hook handleShowAppErrorUi" }
                        AppErrorsProcessData(instance, proc, resultData).handleShowAppErrorUi(context)
                    }
                }
            }
            firstMethodOrNull {
                name = "handleAppCrashInActivityController"
                returnType = Boolean::class
            }?.hook()?.after {
                val context = appContext ?: firstFieldOrNull { name = "mContext" }?.of(instance)?.get<Context>() ?: return@after
                val proc = args().first().any() ?: return@after YLog.warn("Received but got null ProcessRecord")
                verboseLog { "Hit hook handleAppCrashInActivityController" }
                AppErrorsProcessData(instance, proc).apply {
                    handleAppErrorsInfo(context, args(index = 1).cast())
                    handleShowAppErrorUi(context)
                }
            }
        }
    }

    override fun onHook() {
        registerLifecycle()
        registerSystemDialogSuppressionHooks()
        registerCrashDispatchHooks()
    }
}
