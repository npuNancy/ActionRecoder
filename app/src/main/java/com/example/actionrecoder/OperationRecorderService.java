package com.example.actionrecoder;



import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import static android.view.accessibility.AccessibilityEvent.eventTypeToString;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat; // 导入SimpleDateFormat类
import java.util.Date;           // 导入Date类
import java.util.concurrent.Executors;

/* 无障碍服务教程
 * https://blog.csdn.net/Jason_Lee155/article/details/115966816
 * https://developer.android.com/guide/topics/ui/accessibility/service?hl=zh-cn#java
 */

public class OperationRecorderService extends AccessibilityService{
    private static final String TAG = "OperationRecorderService"; // 定义 TAG
    private static final String CHANNEL_ID = "recording_channel"; // 定义通知渠道 ID
    private static File targetFile; // 保存文件路径的静态变量
    private static File screenshotDir; // 保存截图存储路径的静态变量
    private static volatile boolean isRecording = false; // 静态标志位控制记录状态

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //这个方法是我们用的最多的方法，我们会在这个方法里写大量的逻辑操作。
        //通过对event的判断执行不同的操作
        //当窗口发生的事件是我们配置监听的事件时,会回调此方法.会被调用多次

        if (!isRecording) return; // 仅在启用时记录

        // 获取包名
        String packageName = event.getPackageName() != null ?
                event.getPackageName().toString() : "unknown";

        // 获取事件
        String eventTypeString = eventTypeToString(event.getEventType());
        String operation = parseEventType(event);

        if(operation.equals("UNKNOWN")) return; // 忽略未知事件

        // 定义日期格式
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String time = dateFormat.format(new Date()); // 格式化当前时间并存储为字符串
        long timeStamp = System.currentTimeMillis(); // 获取时间戳，精确到毫秒

        // 截图保存
        String relativePath = ""; // 相对路径, 默认空
        if (eventTypeString.equals("TYPE_VIEW_CLICKED")){
            takeScreenshot(timeStamp);
            // 截图文件路径:  xxxx/xxx/0/Download/ActionRecoder/xxx
            File screenshotFile = new File(screenshotDir, timeStamp + ".jpg"); // 截图文件路径
            // 相对路径
            relativePath = screenshotFile.toString().split("ActionRecoder/")[1];
        }


        Log.d(TAG, "Record: " + time + " | " + operation + " | " + packageName);
        saveToFile(timeStamp, time, operation, packageName, relativePath);
    }

    @Override
    public void onInterrupt() {
        //当服务要被中断时调用.会被调用多次
        Log.d(TAG, "服务被中断");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.d("MyKeyEvent", "按键事件发生: " + event.getKeyCode());
        // 监听按键事件（如 Back 键）
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            Log.d("MyKeyEvent", "返回键按下");
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            Log.d("MyKeyEvent", "Home 键按下");
        }
        return super.onKeyEvent(event);
    }

    @Override
    protected void onServiceConnected() {
        //服务开启时，调用
        super.onServiceConnected();
        Log.d(TAG, "开启服务");

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "操作记录",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
        // 配置前台服务
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("操作记录中")
                .setContentText("正在记录用户操作...")
                .build();
        startForeground(1, notification);
    }


    /*
     * 有用的：
     * TYPE_VIEW_CLICKED: 表示点击
     * TYPE_VIEW_LONG_CLICKED: 表示长按
     * TYPE_WINDOW_STATE_CHANGED:
     * TYPE_VIEW_TEXT_CHANGED:              表示更改 EditText 文本的事件。
     * TYPE_VIEW_TEXT_SELECTION_CHANGED:    表示在 EditText 中更改选择的事件。
     *
     *
     * 无用的：
     * TYPE_VIEW_SCROLLED: 滚动视图
     * TYPE_WINDOW_CONTENT_CHANGED
     * TYPE_VIEW_FOCUSED:表示聚焦于 View 的事件。
     * TYPE_WINDOWS_CHANGED:            表示屏幕上显示的系统窗口的事件更改
     */
    private String parseEventType(AccessibilityEvent event) {
        int eventType = event.getEventType();
        String eventTypeString = eventTypeToString(eventType);
        String coordinates = "";
        CharSequence eventDescription = "";

        eventDescription = event.getContentDescription(); // 获取该节点的内容描述
        if (eventDescription != null) {
            eventDescription = " Event Description: " + eventDescription + ";";
            Log.d("eventDescription", eventDescription.toString());
        } else {
            eventDescription = " ";
        }


        // 获取事件来源 eventSource
        AccessibilityNodeInfo eventSource = event.getSource();
        if (eventSource != null) {
            coordinates = getFormattedCoordinates(eventSource);
        }

        // TODO: 获取 x, y 坐标
        // TODO: 筛选有用的
        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                return "CLICK" + coordinates + eventDescription; // 点击
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
                return "LONG_CLICK" + coordinates + eventDescription; // 长按
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                return "UNKNOWN"; // 滚动
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "WINDOW_CHANGE";
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                return eventTypeString;
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                return eventTypeString;
            // 添加更多事件类型...
            default:
                return "UNKNOWN";
                // return eventTypeString;
        }
    }
    private String getFormattedCoordinates(AccessibilityNodeInfo node) {
        // 获取视图在屏幕上的矩形区域
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        // 计算点击的坐标（中心点）
        int x = rect.centerX();
        int y = rect.centerY();
        return String.format("(%d, %d);", x, y); // 格式化坐标的输出
    }

    // 截屏方法
    public void takeScreenshot(long timeStamp) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels; // 获取屏幕宽度
        int screenHeight = getResources().getDisplayMetrics().heightPixels; // 获取屏幕高度
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(0, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull AccessibilityService.ScreenshotResult screenshotResult) {
                    // 截屏成功
                    Bitmap bitmap = Bitmap.createBitmap(
                            Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace()),
                            0, 0, screenWidth, screenHeight
                    );
                    saveBitmap(bitmap, timeStamp); // 保存截图
                    Log.d(TAG, "截图成功");
                }

                @Override
                public void onFailure(int error) {
                    // 截屏失败
                    Log.e(TAG, "截图失败: " + error);
                }
            });
        }
    }

    private void saveBitmap(Bitmap bitmap, long timeStamp) {
        // 创建截图文件路径
        File screenshotFile = new File(screenshotDir, timeStamp + ".jpg");

        try {
            // 保存截图
            FileOutputStream fos = new FileOutputStream(screenshotFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos); // 90 是质量参数（0-100）
            fos.flush();
            fos.close();

            // 提示用户
            Log.d(TAG, "Screenshot saved to: " + screenshotFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to save screenshot: " + e.getMessage());
        }
    }
    // 写入文件
    private void saveToFile(long timeStamp, String time, String operation, String packageName, String imagePath) {
        operation = operation.replace(",", "，"); // 替换逗号为中文逗号
        operation = operation.trim(); // 去除首尾空格
        String log = timeStamp + "," + time + "," + operation + "," + packageName + "," + imagePath + "\n";
        try {
            if (targetFile != null && targetFile.exists()) {
                FileWriter writer = new FileWriter(targetFile, true);
                writer.append(log);
                writer.flush();
                writer.close();
            } else {
                Log.e(TAG, "目标文件不存在！");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // 用来接收 MainActivity 设置的目标文件路径
    public static void setTargetFile(File file) {
        targetFile = file;
    }

    // 用来接收 MainActivity 设置的图像保存路径
    public static void setScreenshotDir(File file) {
        screenshotDir = file;
    }

    // 用来接收 MainActivity 设置的是否 开始/停止 记录
    public static void setRecordingEnabled(boolean enabled) {
        isRecording = enabled;
    }

    public long convertEventTimeToTimestamp(long eventTime) {
        // eventTime: 1740020923192 | timeStamp: 1740038552179
        long currentTime = System.currentTimeMillis(); // 当前时间戳
        long elapsedRealtime = SystemClock.elapsedRealtime(); // 系统启动时间
        return currentTime - elapsedRealtime + eventTime;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //关闭服务时,调用
        Log.d(TAG, "关闭服务");
        //如果有资源记得释放
        return super.onUnbind(intent);
    }
}
