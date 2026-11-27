package com.lichader.hipdanext.utils;

import android.os.Looper;
import android.widget.Toast;

import com.lichader.hipdanext.ui.HiApplication;

public class SimpleExceptionHandler implements Thread.UncaughtExceptionHandler {

    final private Thread.UncaughtExceptionHandler mDefaultHandler;

    public SimpleExceptionHandler() {
        this.mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Utils.saveCrashLog(e);
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(HiApplication.getAppContext(),
                        "抱歉，程序发生错误，日志已保存",
                        Toast.LENGTH_LONG).show();
                Looper.loop();
            }
        }.start();
        try {
            Thread.sleep(2000);
        } catch (Exception ignored) {
        }
        mDefaultHandler.uncaughtException(t, e);
    }
}