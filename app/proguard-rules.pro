# Beeze release 混淆规则
# Compose / Media3 / Coil / 第三方库自带的 consumer rules 会自动合并，这里只补项目自身需要的

# Folia 歌词 SDK 的 JS 桥通过注解 @JavascriptInterface 反射调用，必须保留
-keepclassmembers class com.folialyric.FoliaLyricView$NativeEvents {
    @android.webkit.JavascriptInterface <methods>;
}
