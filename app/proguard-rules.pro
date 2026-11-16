# Bouncy Castle is used only for Argon2id, through direct calls, so R8 can
# strip the rest. These optional classes it mentions do not exist on Android.
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.bouncycastle.mail.**
