# Moshi — 保留 JSON 字段和 @JsonClass 注解的类
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# 保留本项目数据类字段（供 Moshi 反射使用）
-keepclassmembers class com.yunplayer.stockdashboard.* {
    <fields>;
}

# OkHttp & Okio（库自带 consumer rules，这里补充）
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin 协程（库自带 consumer rules）
-dontwarn kotlinx.coroutines.**

# R8 全模式下允许访问修饰符优化
-allowaccessmodification
