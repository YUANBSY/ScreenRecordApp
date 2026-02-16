package com.example.screenrecord;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * MainActivity - 应用主界面
 * 
 * 这是用户打开应用后看到的第一个界面
 * 主要功能：
 * 1. 显示录像按钮和状态信息
 * 2. 请求必要的权限（相机、录音、存储）
 * 3. 启动/停止录像服务
 * 4. 显示录像时长计时器
 * 
 * AppCompatActivity是什么？
 * - 它是Activity的子类，提供了对旧版Android的兼容支持
 * - 让我们可以使用新版本的特性，同时兼容旧版本
 */
public class MainActivity extends AppCompatActivity implements RecordService.RecordStateListener {

    // 权限请求码
    // 当请求权限时，我们需要一个数字来识别这个请求
    // 在回调中通过这个数字判断是哪个权限请求的结果
    private static final int PERMISSION_REQUEST_CODE = 100;

    // 需要请求的权限数组
    // 这些权限必须在AndroidManifest.xml中声明，运行时还需要动态请求
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,           // 相机权限
            Manifest.permission.RECORD_AUDIO,     // 录音权限
            Manifest.permission.WRITE_EXTERNAL_STORAGE  // 存储权限
    };

    // 界面元素引用
    private ImageButton recordButton;    // 录像按钮
    private TextView statusText;         // 状态文本
    private TextView timerText;          // 计时器文本
    private TextView hintText;           // 提示文本

    // 录像状态
    private boolean isRecording = false;  // 是否正在录像
    
    // 计时器相关
    private Handler timerHandler;         // 用于定时更新UI
    private long startTime;               // 录像开始时间
    private Runnable timerRunnable;       // 计时器任务

    /**
     * onCreate - Activity创建时调用
     * 
     * 这是Activity生命周期的第一个方法
     * 用于初始化界面和检查权限
     * 
     * @param savedInstanceState 保存的状态，用于恢复Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置布局文件
        // 这会将activity_main.xml中的界面定义加载到内存中
        setContentView(R.layout.activity_main);
        
        Log.d("MainActivity", "Activity创建");
        
        // 初始化界面元素
        initViews();
        
        // 初始化计时器
        initTimer();
        
        // 检查并请求权限
        checkAndRequestPermissions();
    }

    /**
     * 初始化界面元素
     * 
     * 获取布局文件中定义的各个界面元素的引用
     * 以便在代码中操作它们
     */
    private void initViews() {
        // findViewById通过ID找到布局中对应的视图元素
        // R.id.xxx是自动生成的资源ID
        recordButton = findViewById(R.id.recordButton);
        statusText = findViewById(R.id.statusText);
        timerText = findViewById(R.id.timerText);
        hintText = findViewById(R.id.hintText);
        
        // 设置录像按钮点击事件
        // 当用户点击按钮时，会执行onClick方法中的代码
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRecordButtonClick();
            }
        });
    }

    /**
     * 初始化计时器
     * 
     * 创建一个定时任务，每秒更新一次录像时长显示
     */
    private void initTimer() {
        timerHandler = new Handler(Looper.getMainLooper());
        
        // 定义计时器任务
        // 这个任务会计算已经录像的时长并更新界面
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    // 计算已经录像的秒数
                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    
                    // 更新计时器显示
                    timerText.setText(formatTime(elapsedSeconds));
                    
                    // 每秒执行一次
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
    }

    /**
     * 格式化时间显示
     * 
     * 将秒数转换为"时:分:秒"格式
     * 例如：3665秒 -> "01:01:05"
     * 
     * @param totalSeconds 总秒数
     * @return 格式化的时间字符串
     */
    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        // 使用String.format格式化，%02d表示两位数字，不足补0
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 检查并请求权限
     * 
     * Android 6.0（API 23）引入了运行时权限
     * 危险权限（如相机、录音）必须在运行时请求用户授权
     */
    private void checkAndRequestPermissions() {
        // 检查是否所有权限都已授予
        boolean allGranted = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        
        if (allGranted) {
            // 所有权限已授予，可以开始使用
            Log.d("MainActivity", "所有权限已授予");
        } else {
            // 请求权限
            // ActivityCompat.requestPermissions会显示权限请求对话框
            // 用户选择后，会回调onRequestPermissionsResult方法
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 权限请求结果回调
     * 
     * 用户在权限对话框中做出选择后，这个方法会被调用
     * 我们需要检查用户是否授予了所有权限
     * 
     * @param requestCode 请求码，用于识别是哪个权限请求
     * @param permissions 请求的权限数组
     * @param grantResults 授权结果数组
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 检查所有权限是否都被授予
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                // 用户拒绝了某些权限
                Toast.makeText(this, R.string.error_permission, Toast.LENGTH_LONG).show();
                
                // 检查是否应该显示权限说明
                // 如果用户选择了"不再询问"，shouldShowRequestPermissionRationale返回false
                boolean shouldShowRationale = false;
                for (String permission : REQUIRED_PERMISSIONS) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        shouldShowRationale = true;
                        break;
                    }
                }
                
                if (!shouldShowRationale && !allGranted) {
                    // 用户选择了"不再询问"，引导用户到设置页面
                    Toast.makeText(this, "请在设置中手动授予权限", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * 录像按钮点击事件处理
     * 
     * 根据当前录像状态，决定是开始录像还是停止录像
     */
    private void onRecordButtonClick() {
        if (isRecording) {
            // 当前正在录像，停止录像
            stopRecording();
        } else {
            // 当前未录像，开始录像
            startRecording();
        }
    }

    /**
     * 开始录像
     * 
     * 首先检查权限，然后启动录像服务
     */
    private void startRecording() {
        // 检查权限
        if (!checkPermissions()) {
            Toast.makeText(this, R.string.error_permission, Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions();
            return;
        }
        
        // 设置录像状态监听器
        // 这样当录像状态变化时，Activity会收到通知
        RecordService.setRecordStateListener(this);
        
        // 创建启动服务的Intent
        // Intent是Android中用于启动组件的消息对象
        Intent intent = new Intent(this, RecordService.class);
        intent.setAction("START_RECORD");
        
        // 启动前台服务
        // Android 8.0+需要使用startForegroundService
        // 前台服务会在通知栏显示通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    /**
     * 停止录像
     * 
     * 发送停止命令给录像服务
     */
    private void stopRecording() {
        Intent intent = new Intent(this, RecordService.class);
        intent.setAction("STOP_RECORD");
        startService(intent);
    }

    /**
     * 检查所有必要权限是否已授予
     * 
     * @return true表示所有权限已授予
     */
    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 更新界面为录像中状态
     */
    private void updateUIRecording() {
        isRecording = true;
        
        // 更改按钮图标为停止图标
        recordButton.setImageResource(R.drawable.stop_icon);
        
        // 更新状态文本
        statusText.setText(R.string.status_recording);
        statusText.setTextColor(getResources().getColor(R.color.colorAccent));
        
        // 显示计时器
        timerText.setVisibility(View.VISIBLE);
        timerText.setText("00:00:00");
        
        // 更新提示文本
        hintText.setText(R.string.hint_stop);
        
        // 开始计时
        startTime = System.currentTimeMillis();
        timerHandler.post(timerRunnable);
    }

    /**
     * 更新界面为停止录像状态
     * 
     * @param videoPath 保存的视频路径
     */
    private void updateUIStopped(String videoPath) {
        isRecording = false;
        
        // 停止计时
        timerHandler.removeCallbacks(timerRunnable);
        
        // 更改按钮图标为录像图标
        recordButton.setImageResource(R.drawable.record_icon);
        
        // 更新状态文本
        statusText.setText(R.string.status_saved);
        statusText.setTextColor(getResources().getColor(R.color.textSecondary));
        
        // 隐藏计时器
        timerText.setVisibility(View.INVISIBLE);
        
        // 更新提示文本
        hintText.setText(R.string.hint_start);
        
        // 显示保存成功提示
        Toast.makeText(this, "视频已保存到相册", Toast.LENGTH_LONG).show();
    }

    /**
     * 显示错误信息
     * 
     * @param message 错误信息
     */
    private void showError(String message) {
        isRecording = false;
        timerHandler.removeCallbacks(timerRunnable);
        
        recordButton.setImageResource(R.drawable.record_icon);
        statusText.setText(message);
        statusText.setTextColor(getResources().getColor(R.color.colorAccent));
        timerText.setVisibility(View.INVISIBLE);
        hintText.setText(R.string.hint_start);
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ========== RecordStateListener 接口实现 ==========

    /**
     * 录像开始回调
     * 
     * 当录像服务成功开始录像时调用
     */
    @Override
    public void onRecordStarted() {
        // 在主线程更新UI
        // Android要求所有UI操作都在主线程执行
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUIRecording();
            }
        });
    }

    /**
     * 录像停止回调
     * 
     * 当录像服务停止录像时调用
     * 
     * @param videoPath 保存的视频文件路径
     */
    @Override
    public void onRecordStopped(String videoPath) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUIStopped(videoPath);
            }
        });
    }

    /**
     * 错误回调
     * 
     * 当录像过程中发生错误时调用
     * 
     * @param message 错误信息
     */
    @Override
    public void onError(String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showError(message);
            }
        });
    }

    /**
     * onResume - Activity恢复时调用
     * 
     * 当Activity从后台回到前台时调用
     * 用于恢复界面状态
     */
    @Override
    protected void onResume() {
        super.onResume();
        
        // 重新设置录像状态监听器
        // 以便接收录像服务的状态更新
        RecordService.setRecordStateListener(this);
    }

    /**
     * onDestroy - Activity销毁时调用
     * 
     * 清理资源，防止内存泄漏
     */
    @Override
    protected void onDestroy() {
        // 移除计时器回调
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        
        super.onDestroy();
    }
}
