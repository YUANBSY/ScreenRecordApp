// ProGuard配置文件
//
// ProGuard是什么？
// ProGuard是一个代码混淆工具，可以：
// 1. 删除未使用的代码，减小APK体积
// 2. 混淆类名、方法名，保护代码安全
// 3. 优化字节码，提高运行效率

// 添加项目特定的ProGuard规则
// 默认情况下，ProGuard会保留所有公共类和成员

// 保留应用的主Activity
// 主Activity不能被混淆，否则系统找不到入口
-keep public class com.example.screenrecord.MainActivity

// 保留录像服务
// 服务在AndroidManifest.xml中声明，不能混淆
-keep public class com.example.screenrecord.RecordService

// 保留所有继承自Service的类
// Android系统通过反射创建Service实例
-keep public class * extends android.app.Service

// 保留所有继承自Activity的类
-keep public class * extends android.app.Activity

// 保留Parcelable接口的实现类
// Parcelable用于进程间通信，序列化规则不能改变
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

// 保留Serializable接口的实现类
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

// 保留native方法
// JNI调用的方法名必须与C/C++代码中一致
-keepclasseswithmembernames class * {
    native <methods>;
}

// 保留自定义View的构造函数
// XML布局文件中使用的View需要这些构造函数
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

// 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
