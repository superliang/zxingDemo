/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liangyn.zxing;

import android.app.Activity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 检测 Activity 条码扫描超时的类
 */
public final class ScanTimoutTimer {

    /**
     * 扫描超时时间，即多少秒后停止拍照预览扫描条码
     */
    private static final int TIMOUT_DELAY_SECONDS = 180;

    private final ScheduledExecutorService scanTimer = Executors
            .newSingleThreadScheduledExecutor(new DaemonThreadFactory());
    private final Activity activity;
    private ScheduledFuture<?> scanFuture = null;

    public ScanTimoutTimer(Activity activity) {
        this.activity = activity;
        onActivity();
    }

    public void onActivity() {
        cancel();
        scanFuture = scanTimer.schedule(
                new FinishListener(activity), TIMOUT_DELAY_SECONDS,
                TimeUnit.SECONDS);
    }

    private void cancel() {
        if (scanFuture != null) {
            scanFuture.cancel(true);
            scanFuture = null;
        }
    }

    public void shutdown() {
        cancel();
        scanTimer.shutdown();
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }
    }

}
