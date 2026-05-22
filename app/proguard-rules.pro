# --- JavaScript Interface 桥接（必须保留，否则 R8 混淆后 WebView JS 调用失败） ---
-keepclassmembers class com.example.coffeediary.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Room 数据库实体（必须保留字段名，否则 Room 反射失败） ---
-keep class com.example.coffeediary.CoffeeRecord { *; }

# --- 自定义 View（XML layout 中引用，需保留构造函数） ---
-keep class com.example.coffeediary.SplashOverlayView {
    public <init>(...);
}

# --- TFLite 抠图分割 ---
-keep class com.example.coffeediary.CoffeeSegmenter { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# --- 保留行号用于调试堆栈 ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
