# Siderea release shrinking rules.
# Hilt, kotlinx.serialization, Navigation and Compose all ship consumer rules, so this file
# only needs to carry rules that are specific to this project.

# Keep line numbers so crash reports from real devices stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
