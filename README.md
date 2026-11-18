# StickerLab — Minimal Android Sticker Maker (Kotlin + Jetpack Compose)

A clean, minimal Android app that converts **Photos, GIFs, and Videos** into `512×512` WebP stickers (`< 100 KB`) and adds them directly into **WhatsApp** using WhatsApp's official `StickerContentProvider` + `ENABLE_STICKER_PACK` Intent API (plus direct PNG sharing to **Telegram**).

## How to Run & Build APK (Android Studio on Mac)
1. Unzip `StickerLab_Android.zip`.
2. Open **Android Studio** → **Open** → select the `StickerLab_Android` folder.
3. Wait for Gradle Sync to finish (uses Kotlin 2.0 + Jetpack Compose Material 3).
4. Connect your Android phone (with USB Debugging ON) and click **Run ▶** (or **Build → Build Bundle(s) / APK(s) → Build APK(s)** to generate an installable APK).

## How 1-Click "Add to WhatsApp" Works
- WhatsApp requires a minimum of **3 stickers** per pack before enabling the **Add to WhatsApp** button.
- Create 3+ stickers inside a pack → tap **Add to WhatsApp** → WhatsApp opens its native *"Would you like to add this pack?"* popup → tap **Add**.
- Your stickers appear directly inside WhatsApp's sticker tray!
