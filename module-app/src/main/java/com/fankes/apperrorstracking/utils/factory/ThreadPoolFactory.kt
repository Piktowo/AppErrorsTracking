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
 * This file is created by fankes on 2022/10/3.
 */
package com.fankes.apperrorstracking.utils.factory

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 全局共享的线程池，按需创建线程并自动回收空闲线程
 */
private val sharedThreadPool: ExecutorService = Executors.newCachedThreadPool()

/**
 * 创建并启动新的临时线程池
 *
 * 等待 [block] 执行完成并自动释放
 * @param block 方法块
 */
fun newThread(block: () -> Unit) {
    sharedThreadPool.execute { block() }
}