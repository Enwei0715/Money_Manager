# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.enwei.moneymanager.**$$serializer { *; }
-keepclassmembers class com.enwei.moneymanager.** {
    *** Companion;
}
-keepclasseswithmembers class com.enwei.moneymanager.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 閃退紀錄保留原始檔名與行號，配合每版保存的 mapping 檔即可還原成可讀的堆疊
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
