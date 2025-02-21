package com.example.actionrecoder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.gesture.GestureLibraries;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.Toast;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity"; // 定义 TAG
    private static final int REQUEST_PERMISSION = 1;

    private Button recordButton;
    private Button accessibilityButton;
    private Button openDirButton;
    private ScheduledExecutorService executorService;
    private boolean isRecording = false;
    private List<String> recordDataList = new ArrayList<>();
    private File recordFile;
    private BufferedWriter csvWriter;
    private Handler handler = new Handler();
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 权限按钮
        accessibilityButton = findViewById(R.id.accessibilityButton);
        // 初始检查服务状态
        updateServiceStatus();
        // checkAccessibilityPermission();
        // 定期检查服务状态（每 1 秒检查一次）
        startPeriodicCheck();

        // 录制按钮
        recordButton = findViewById(R.id.recordButton);
        recordButton.setOnClickListener(v -> toggleRecording());

        // 打开目录按钮
        openDirButton = findViewById(R.id.openDirButton);
        openDirButton.setOnClickListener(v -> openDirectory());

        // 动态申请权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }


    }

    // 检查服务是否正在运行
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请授予存储权限，否则无法记录数据！", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 检查授权
    private void updateServiceStatus() {
        // 检查授权
        boolean isServiceRunning = isServiceON(this, OperationRecorderService.class.getName());
        Log.d("ServiceStatus", "Service is running: " + isServiceRunning);
        accessibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isServiceRunning) {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else {
                    Toast.makeText(MainActivity.this, "无障碍服务已开启", Toast.LENGTH_SHORT).show();
                }
            }
        });
        if (isServiceRunning) {
            accessibilityButton.setText("无障碍服务已开启");
            accessibilityButton.setBackgroundColor(Color.GREEN);
            // 如果服务已开启，停止定期检查
            if (handler != null && runnable != null) {
                handler.removeCallbacks(runnable);
            }
        } else {
            accessibilityButton.setText("去开启无障碍服务");
            accessibilityButton.setBackgroundColor(Color.RED);
        }
    }

    // 持续循环，更新服务状态
    private void startPeriodicCheck() {
        runnable = new Runnable() {
            @Override
            public void run() {
                updateServiceStatus();
                // 持续循环，直到服务处于开启状态
                boolean isServiceRunningAlready = accessibilityButton.getText().equals("无障碍服务已开启");
                if (!isServiceRunningAlready) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(runnable, 1000);
    }

    // 检查无障碍服务权限
    public static boolean isServiceON(Context context,String className){
        // 检查无障碍服务权限
        ActivityManager activityManager = (ActivityManager)context.getSystemService(context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo>
                runningServices = activityManager.getRunningServices(100);
        if (runningServices.size() < 0 ){
            return false;
        }
        for (int i = 0;i<runningServices.size();i++){
            ComponentName service = runningServices.get(i).service;
            if (service.getClassName().contains(className)){
                return true;
            }
        }
        return false;
    }

    private boolean isServiceON2() {
        // OperationRecorderService.class.getName()
        String service = getPackageName() + "/.OperationRecorderService";
        int enabled = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
        );
        if (enabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return settingValue != null && settingValue.contains(service);
        }
        return false;
    }

    private void checkAccessibilityPermission() {
        if (!isServiceON2()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要无障碍权限")
                    .setMessage("请前往设置开启本应用的无障碍服务权限")
                    .setPositiveButton("去设置", (d, w) -> startActivity(
                            new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
        }
    }

    // 启动/停止记录
    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    // 启动记录
    private void startRecording() {
        isRecording = true;
        recordButton.setText("停止记录");
        // 显示 “启动记录”
        Toast.makeText(this, "启动记录", Toast.LENGTH_SHORT).show();

        // 准备存储路径,将文件路径共享给无障碍服务
        prepareStorage();

        // 启动服务
        //startService(new Intent(this, OperationRecorderService.class));
        OperationRecorderService.setRecordingEnabled(true); // 启用记录
    }


    private void stopRecording() {
        isRecording = false;
        recordButton.setText("启动记录");

        // 显示 “停止记录”
        Toast.makeText(this, "停止记录", Toast.LENGTH_SHORT).show();

        OperationRecorderService.setRecordingEnabled(false); // 启用记录
        //stopService(new Intent(this, OperationRecorderService.class));

    }

    // 记录设备信息
    private void saveDeviceInfo(File dir) {
        String fileName = "DeviceInfo.txt";
        File file = new File(dir, fileName);
        String deviceInfo = android.os.Build.ID + "," + android.os.Build.BRAND + "," + android.os.Build.MODEL + "," + android.os.Build.VERSION.SDK_INT;
        try {
            if (file.exists()) {
                file.delete();
            }
            // 创建文件
            file.createNewFile();
            FileWriter writer = new FileWriter(file, true);
            // 写入设备信息
            writer.write("Build ID, Brand, Model, SDK Version\n");
            writer.write(deviceInfo);
            writer.flush(); // 刷新缓冲区
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private void prepareStorage() {
        // 创建文件目录
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File subDir = new File(downloadsDir, "ActionRecorderData");
        subDir.mkdirs();


        // 记录设备信息
        saveDeviceInfo(subDir);

        // 生成文件名
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = "ActionRecorderData_" + sdf.format(new Date()) + ".csv";
        recordFile = new File(subDir, fileName);


        // 创建对应的截图保存目录
        String screenshotDirName = "Screenshots_" + sdf.format(new Date());
        File screenshotDir = new File(subDir, screenshotDirName);
        if (!screenshotDir.exists()) {
            screenshotDir.mkdir();
        } else {
            screenshotDir.delete();
            screenshotDir.mkdir();
        }

        try {
            if (recordFile.exists()) {
                recordFile.delete();
            }
            // 创建CSV文件
            recordFile.createNewFile();

            // 时间戳,时间, 操作, 包名， 截图路径
            // timestamp, Time, operation, packageName, screenshotPath
            csvWriter = new BufferedWriter(new FileWriter(recordFile, true));
            csvWriter.write("Timestamp, Time, Operation, PackageName, Image\n");
            csvWriter.flush();
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "文件创建失败！", Toast.LENGTH_SHORT).show();
        }

        // 将文件路径共享给无障碍服务
        OperationRecorderService.setTargetFile(recordFile);
        OperationRecorderService.setScreenshotDir(screenshotDir);
    }

    private void openDirectory(){
        if (true){
            Toast.makeText(this, "Download/ActionRecorderData", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取 Download/ActionRecorderData 文件夹
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File subDir = new File(downloadsDir, "ActionRecorderData");

        // 使用 FileProvider 生成安全的 URI
        Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FActionRecorderData");

        // 创建一个打开文件夹的 Intent
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // 授予读取权限
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);


        // 检查 Intent 是否可启动
        startActivity(intent);

    }
}