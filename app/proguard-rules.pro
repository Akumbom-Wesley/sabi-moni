# Ktor's OkHttp engine references optional Conscrypt/BouncyCastle providers reflectively.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Ktor resolves engines via ServiceLoader.
-keep class io.ktor.client.engine.okhttp.** { *; }
