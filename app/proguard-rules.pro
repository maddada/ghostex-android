# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

# CDXC:AndroidSshTransport 2026-06-30-03:27:
# SSHJ resolves JCE algorithms through the BouncyCastle provider at runtime, and BouncyCastle registers algorithm implementations by provider metadata and string class names. Release shrinking must keep those provider classes so macOS Ed25519 host keys do not fall through to AndroidKeyStore and crash reconnect with KeyGenParameterSpec errors.
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider$* { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProviderConfiguration { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
