# Tink（EncryptedSharedPreferences 的加密实现）在编译期引用了 Error Prone 注解，
# 运行时并不需要，R8 只做 dontwarn 即可。
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** { *; }
-dontwarn javax.annotation.**
