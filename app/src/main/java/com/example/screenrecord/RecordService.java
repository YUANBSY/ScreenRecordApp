package com.example.screenrecord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * RecordService - 录像后台服务
 * 
 * 这个服务是整个应用的核心，负责：
 * 1. 在后台持续录像（即使屏幕熄灭也能继续）
 * 2. 管理相机硬件
 * 3. 将视频保存到手机相册
 * 4. 显示通知让用户知道正在录像
 * 
 * 为什么需要Service而不是普通的Activity？
 * - Activity在熄屏后会被暂停，无法继续录像
 * - Service可以在后台运行，不受熄屏影响
 * - 前台Service会显示通知，告知用户应用正在工作
 */
public class RecordService extends Service {

    // 日志标签，用于在Logcat中识别日志来源
    private static final String TAG = "RecordService";
    
    // 通知渠道ID，Android 8.0+需要
    private static final String CHANNEL_ID = "record_channel";
    
    // 通知ID，用于更新或取消通知
    private static final int NOTIFICATION_ID = 1;

    // 相机相关对象
    private CameraManager cameraManager;        // 相机管理器，用于打开相机
    private CameraDevice cameraDevice;          // 相机设备对象
    private CameraCaptureSession captureSession; // 相机捕获会话
    private MediaRecorder mediaRecorder;        // 媒体录制器，用于录制视频
    private String cameraId;                    // 相机ID（后置摄像头）
    
    // 电源管理相关
    private PowerManager powerManager;          // 电源管理器
    private PowerManager.WakeLock wakeLock;     // 唤醒锁，防止CPU休眠
    
    // 视频文件相关
    private String currentVideoPath;            // 当前录像文件的保存路径
    private boolean isRecording = false;        // 是否正在录像
    
    // 处理录像状态变化的回调接口
    private static RecordStateListener recordStateListener;

    /**
     * 设置录像状态监听器
     * 
     * 这个方法让Activity可以知道录像状态的变化
     * 例如：录像开始、录像停止、保存成功等
     * 
     * @param listener 监听器对象
     */
    public static void setRecordStateListener(RecordStateListener listener) {
        recordStateListener = listener;
    }

    /**
     * onBind - Service绑定方法
     * 
     * 这个方法在Service被绑定时调用
     * 我们使用startService启动服务，不需要绑定，所以返回null
     * 
     * @param intent 绑定意图
     * @return null（不提供绑定接口）
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * onCreate - Service创建时调用
     * 
     * 这个方法只在Service第一次创建时调用一次
     * 用于初始化各种管理器和创建通知渠道
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务创建");
        
        // 获取相机管理器
        // CameraManager是系统服务，用于管理所有相机设备
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        
        // 获取电源管理器
        // PowerManager用于获取唤醒锁，防止CPU休眠
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        
        // 创建通知渠道
        // Android 8.0+必须为通知创建渠道，否则通知不会显示
        createNotificationChannel();
    }

    /**
     * onStartCommand - Service启动时调用
     * 
     * 每次调用startService都会执行这个方法
     * 根据Intent中的action决定是开始录像还是停止录像
     * 
     * @param intent 启动意图，包含操作指令
     * @param flags 启动标志
     * @param startId 启动ID
     * @return START_STICKY表示如果服务被杀死，系统会尝试重新创建
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "收到命令: " + action);
            
            if ("START_RECORD".equals(action)) {
                // 开始录像命令
                startRecording();
            } else if ("STOP_RECORD".equals(action)) {
                // 停止录像命令
                stopRecording();
            }
        }
        
        // START_STICKY：服务被杀死后会尝试重启
        // 这对于录像服务很重要，可以防止录像意外中断
        return START_STICKY;
    }

    /**
     * 创建通知渠道
     * 
     * Android 8.0（API 26）引入了通知渠道
     * 所有通知必须属于一个渠道，用户可以在设置中管理每个渠道的通知
     */
    private void createNotificationChannel() {
        // 只在Android 8.0+创建渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 创建通知渠道
            // 参数：渠道ID、渠道名称、重要性级别
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW  // 低重要性，不会发出声音
            );
            
            // 设置渠道描述
            channel.setDescription(getString(R.string.notification_channel_desc));
            
            // 注册渠道到系统
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建前台服务通知
     * 
     * 前台服务必须显示一个通知，告知用户应用正在后台运行
     * 这是Android系统的安全要求，防止应用偷偷在后台工作
     * 
     * @return 创建好的通知对象
     */
    private Notification createNotification() {
        // 创建点击通知时打开Activity的意图
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE  // Android 12+需要这个标志
        );

        // 使用NotificationCompat构建通知
        // NotificationCompat可以兼容不同Android版本
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))  // 通知标题
                .setContentText(getString(R.string.notification_content)) // 通知内容
                .setSmallIcon(android.R.drawable.ic_menu_camera)          // 小图标
                .setContentIntent(pendingIntent)                          // 点击意图
                .setOngoing(true)  // 持续通知，用户无法滑动删除
                .build();
    }

    /**
     * 开始录像
     * 
     * 这是整个录像流程的核心方法，执行以下步骤：
     * 1. 启动前台服务（显示通知）
     * 2. 获取唤醒锁（防止CPU休眠）
     * 3. 准备视频文件
     * 4. 打开相机
     * 5. 开始录制
     */
    private void startRecording() {
        if (isRecording) {
            Log.d(TAG, "已经在录像中，忽略开始请求");
            return;
        }
        
        Log.d(TAG, "开始录像流程");
        
        // 步骤1：启动前台服务
        // 这会让服务优先级提高，不容易被系统杀死
        // 同时显示通知告知用户
        startForeground(NOTIFICATION_ID, createNotification());
        
        // 步骤2：获取唤醒锁
        // PARTIAL_WAKE_LOCK：保持CPU运行，但允许屏幕和键盘关闭
        // 这样即使屏幕熄灭，录像也能继续
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ScreenRecord:RecordWakeLock"
        );
        wakeLock.acquire(10 * 60 * 60 * 1000L);  // 最长持有10小时
        
        // 步骤3：准备视频文件路径
        currentVideoPath = createVideoFilePath();
        
        // 步骤4：打开相机并开始录像
        openCameraAndRecord();
    }

    /**
     * 创建视频文件保存路径
     * 
     * 视频保存在公共的Movies目录下，这样：
     * 1. 用户可以在相册中看到视频
     * 2. 卸载应用后视频不会丢失
     * 
     * @return 视频文件的完整路径
     */
    private String createVideoFilePath() {
        // 创建文件名：使用时间戳，方便用户按时间查找
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String fileName = "VID_" + timeStamp + ".mp4";
        
        // 获取Movies目录
        File moviesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
        );
        
        // 创建应用专属目录
        File recordDir = new File(moviesDir, "ScreenRecord");
        if (!recordDir.exists()) {
            recordDir.mkdirs();  // 创建目录（包括父目录）
        }
        
        // 返回完整文件路径
        File videoFile = new File(recordDir, fileName);
        Log.d(TAG, "视频保存路径: " + videoFile.getAbsolutePath());
        return videoFile.getAbsolutePath();
    }

    /**
     * 打开相机并开始录像
     * 
     * 使用Camera2 API打开后置摄像头
     * Camera2是Android 5.0引入的新相机API，功能更强大
     */
    private void openCameraAndRecord() {
        try {
            // 查找后置摄像头
            // 大多数手机后置摄像头ID为"0"，前置为"1"
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = 
                        cameraManager.getCameraCharacteristics(id);
                
                // LENS_FACING_BACK表示后置摄像头
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            
            if (cameraId == null) {
                Log.e(TAG, "未找到后置摄像头");
                notifyError("未找到后置摄像头");
                return;
            }
            
            // 打开相机
            // 需要相机权限，否则会抛出异常
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                
                /**
                 * 相机打开成功回调
                 * 相机是硬件资源，打开是异步操作
                 */
                @Override
                public void onOpened(CameraDevice camera) {
                    Log.d(TAG, "相机打开成功");
                    cameraDevice = camera;
                    startPreviewAndRecord();
                }
                
                /**
                 * 相机断开连接回调
                 * 可能是其他应用占用了相机
                 */
                @Override
                public void onDisconnected(CameraDevice camera) {
                    Log.d(TAG, "相机断开连接");
                    camera.close();
                    cameraDevice = null;
                }
                
                /**
                 * 相机错误回调
                 */
                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "相机错误: " + error);
                    camera.close();
                    cameraDevice = null;
                    notifyError("相机错误: " + error);
                }
                
            }, null);  // null表示使用当前线程的Handler
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "无法访问相机", e);
            notifyError("无法访问相机: " + e.getMessage());
        } catch (SecurityException e) {
            Log.e(TAG, "相机权限未授予", e);
            notifyError("请授予相机权限");
        }
    }

    /**
     * 开始预览和录像
     * 
     * 相机打开后，需要创建捕获会话来开始预览和录像
     */
    private void startPreviewAndRecord() {
        try {
            // 创建MediaRecorder并配置
            setupMediaRecorder();
            
            // 创建捕获会话
            // 捕获会话定义了相机的输出目标（预览Surface和录像Surface）
            cameraDevice.createCaptureSession(
                    Arrays.asList(mediaRecorder.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        
                        /**
                         * 会话配置成功回调
                         */
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            Log.d(TAG, "捕获会话配置成功");
                            captureSession = session;
                            
                            try {
                                // 创建捕获请求
                                // TEMPLATE_RECORD是专门用于录像的模板
                                CaptureRequest.Builder builder = 
                                        cameraDevice.createCaptureRequest(
                                                CameraDevice.TEMPLATE_RECORD
                                        );
                                
                                // 添加录像Surface作为输出目标
                                builder.addTarget(mediaRecorder.getSurface());
                                
                                // 设置自动对焦模式
                                builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                
                                // 开始重复请求（持续预览）
                                captureSession.setRepeatingRequest(
                                        builder.build(), null, null
                                );
                                
                                // 开始录像
                                mediaRecorder.start();
                                isRecording = true;
                                
                                Log.d(TAG, "录像已开始");
                                
                                // 通知Activity录像已开始
                                if (recordStateListener != null) {
                                    recordStateListener.onRecordStarted();
                                }
                                
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "创建捕获请求失败", e);
                                notifyError("录像启动失败");
                            }
                        }
                        
                        /**
                         * 会话配置失败回调
                         */
                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "捕获会话配置失败");
                            notifyError("相机配置失败");
                        }
                    },
                    null  // 使用当前线程的Handler
            );
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建捕获会话失败", e);
            notifyError("录像启动失败");
        }
    }

    /**
     * 配置MediaRecorder
     * 
     * MediaRecorder是Android提供的媒体录制器
     * 用于将相机画面和麦克风声音录制为视频文件
     */
    private void setupMediaRecorder() throws CameraAccessException {
        mediaRecorder = new MediaRecorder();
        
        // 设置音频源：麦克风
        // 需要RECORD_AUDIO权限
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        
        // 设置视频源：相机Surface
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        
        // 设置输出格式：MP4
        // MPEG_4是最常用的视频格式，兼容性好
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        
        // 设置输出文件路径
        mediaRecorder.setOutputFile(currentVideoPath);
        
        // 设置视频编码器：H.264
        // H.264是目前最主流的视频编码，压缩率高、兼容性好
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        
        // 设置音频编码器：AAC
        // AAC是目前最主流的音频编码
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        
        // 获取相机支持的分辨率
        CameraCharacteristics characteristics = 
                cameraManager.getCameraCharacteristics(cameraId);
        
        // 设置视频尺寸：1920x1080（Full HD）
        // 这是大多数手机支持的标准分辨率
        mediaRecorder.setVideoSize(1920, 1080);
        
        // 设置视频帧率：30fps
        // 30帧每秒是标准视频帧率，流畅度足够
        mediaRecorder.setVideoFrameRate(30);
        
        // 设置视频比特率：8Mbps
        // 比特率越高画质越好，但文件越大
        // 8Mbps对于1080p视频是较好的平衡点
        mediaRecorder.setVideoEncodingBitRate(8 * 1024 * 1024);
        
        // 设置视频方向：竖屏
        // 华为nova7竖屏录像需要设置90度旋转
        mediaRecorder.setOrientationHint(90);
        
        // 准备MediaRecorder
        // 必须在start()之前调用
        mediaRecorder.prepare();
    }

    /**
     * 停止录像
     * 
     * 停止录像并保存视频文件
     * 执行以下步骤：
     * 1. 停止MediaRecorder
     * 2. 关闭相机
     * 3. 释放唤醒锁
     * 4. 将视频添加到相册
     * 5. 停止前台服务
     */
    private void stopRecording() {
        if (!isRecording) {
            Log.d(TAG, "未在录像中，忽略停止请求");
            return;
        }
        
        Log.d(TAG, "停止录像");
        isRecording = false;
        
        // 步骤1：停止MediaRecorder
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();  // 停止录像并保存文件
                mediaRecorder.release();  // 释放资源
                mediaRecorder = null;
            } catch (Exception e) {
                Log.e(TAG, "停止MediaRecorder失败", e);
            }
        }
        
        // 步骤2：关闭相机会话和相机
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        // 步骤3：释放唤醒锁
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        
        // 步骤4：将视频添加到相册
        addVideoToGallery();
        
        // 步骤5：停止前台服务
        stopForeground(true);
        
        // 通知Activity录像已停止
        if (recordStateListener != null) {
            recordStateListener.onRecordStopped(currentVideoPath);
        }
        
        // 停止服务
        stopSelf();
    }

    /**
     * 将视频添加到系统相册
     * 
     * 直接写入文件到目录后，相册应用可能不会立即识别
     * 需要通过MediaStore通知系统有新视频
     */
    private void addVideoToGallery() {
        if (currentVideoPath == null) return;
        
        File videoFile = new File(currentVideoPath);
        if (!videoFile.exists()) {
            Log.e(TAG, "视频文件不存在");
            return;
        }
        
        // 使用MediaStore添加视频到相册
        // 这是Android推荐的方式，兼容性好
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, videoFile.getName());
            values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATA, videoFile.getAbsolutePath());
            values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            
            Uri uri = getContentResolver().insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            );
            
            Log.d(TAG, "视频已添加到相册: " + uri);
            
        } catch (Exception e) {
            Log.e(TAG, "添加视频到相册失败", e);
        }
    }

    /**
     * 通知错误
     * 
     * 当发生错误时，通知Activity显示错误信息
     * 
     * @param message 错误信息
     */
    private void notifyError(String message) {
        if (recordStateListener != null) {
            recordStateListener.onError(message);
        }
        stopForeground(true);
        stopSelf();
    }

    /**
     * onDestroy - Service销毁时调用
     * 
     * 确保所有资源都被正确释放
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "服务销毁");
        
        // 确保录像已停止
        if (isRecording) {
            stopRecording();
        }
        
        // 释放唤醒锁
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        super.onDestroy();
    }

    /**
     * RecordStateListener - 录像状态监听接口
     * 
     * 定义了录像状态变化的回调方法
     * Activity实现这个接口来响应录像状态变化
     */
    public interface RecordStateListener {
        /**
         * 录像开始回调
         */
        void onRecordStarted();
        
        /**
         * 录像停止回调
         * @param videoPath 保存的视频文件路径
         */
        void onRecordStopped(String videoPath);
        
        /**
         * 错误回调
         * @param message 错误信息
         */
        void onError(String message);
    }
}
