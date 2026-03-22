# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#=========================================================================================
# AGGRESSIVE OBFUSCATION & OPTIMIZATION
# These rules make the code much harder to read after decompilation.
#=========================================================================================

# Obfuscate with a dictionary of common keywords to make it even more confusing
-obfuscationdictionary keywords.txt
-classobfuscationdictionary keywords.txt
-packageobfuscationdictionary keywords.txt

# Repackage all classes into a single, empty package. This flattens the package structure.
-repackageclasses ''

# Allow R8 to modify access levels (e.g., make a public method private) for more optimization.
-allowaccessmodification

# Apply more aggressive optimizations. The number of passes can be increased for more optimization.
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

#=========================================================================================
# KEEP RULES - PREVENT CRASHES
# You MUST keep certain classes and members from being removed or renamed.
#=========================================================================================

# Keep all application entry points (Activities, Services, etc.) referenced in the Manifest
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
#-keep public class com.android.vending.licensing.ILicensingService

# Keep custom Views and their constructors
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep classes that are used in XML layouts (e.g., Fragments)
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity

# Keep setters in custom data binding classes
-keepclassmembers class * {
    public void set*(*);
}

#=========================================================================================
# LIBRARY-SPECIFIC KEEP RULES
# Add rules for libraries like Room, Retrofit, Gson, etc.
#=========================================================================================

# --- Room Database ---
# Keep your @Entity, @Dao, and @Database classes.
# IMPORTANT: Replace 'com.costheta.cortexa.data.models.**' with your actual package name for models.
-keep class androidx.room.** { *; }
-keep public class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class com.costheta.cortexa.data.models.** { *; }

# --- Google Play Services & Maps ---
# These rules prevent issues with Google libraries.
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.libraries.places.** { *; }
-dontwarn com.google.android.libraries.places.**

# --- Coroutines ---
# Keep suspend functions from being removed or renamed incorrectly.
-keepclassmembers class kotlinx.coroutines.flow.internal.FlowCoroutine<**> {
    kotlin.jvm.internal.ContinuationImpl completion;
}

#=========================================================================================
# DO NOT CHANGE - Default Android Rules
#=========================================================================================
-dontwarn sun.misc.Unsafe
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class **.R$* {
    public static <fields>;
}