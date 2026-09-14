-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities and generated implementations are referenced by generated code.
-keep class dev.hexora.app.data.local.** { *; }
