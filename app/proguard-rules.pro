# Add project specific ProGuard rules here.
-keep class com.couple.expensetracker.data.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
