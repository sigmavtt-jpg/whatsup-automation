# Proguard / R8 Rules for WhatsUp Automation Engine

# 1. Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void clearAllTables();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# 2. Hilt / Dagger
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.ComponentManager { *; }
-keepclassmembers class * {
    @javax.inject.Inject *;
    @dagger.Provides *;
    @dagger.Binds *;
}
-dontwarn dagger.hilt.**

# 3. Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 4. Domain Models & JSON Serialization
-keep class com.whatsup.automation.domain.model.** { *; }
-keep class com.whatsup.automation.data.local.entity.** { *; }

# 5. AndroidX Security / EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# 6. Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# 7. NodeRunner & JNI Native Bridge
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.whatsup.automation.data.engine.NodeRunner { *; }
