# YDL GUI for Android

یک رابط کاربری اندرویدی (Kotlin + Jetpack Compose) برای **yt-dlp + FFmpeg**، الهام‌گرفته از
[imsyy/yt-dlp-gui](https://github.com/imsyy/yt-dlp-gui). موتور دانلود با کتابخانه
[youtubedl-android](https://github.com/yausername/youtubedl-android) داخل APK قرار می‌گیرد
(Python + yt-dlp + FFmpeg + aria2c)، پس نیازی به نصب چیز دیگری نیست.

## ساخت APK

### روش ۱: GitHub Actions (بدون نیاز به نصب چیزی)
1. پوشه را در یک ریپوی GitHub آپلود کنید.
2. تب **Actions ← Build APK ← Run workflow**.
3. بعد از چند دقیقه فایل APK را از بخش Artifacts دانلود کنید.

### روش ۲: Android Studio
`File ▸ Open` را روی همین پوشه بزنید، صبر کنید Sync تمام شود و `Run ▸ Run 'app'`.
یا در ترمینال: `./gradlew assembleDebug` (خروجی: `app/build/outputs/apk/debug/`).

## قابلیت‌ها
- تحلیل لینک (ویدیو و پلی‌لیست) و نمایش فرمت‌ها، زیرنویس‌ها و فصل‌ها
- پریست‌های کیفیت + انتخاب دستی استریم ویدیو/صدا
- حالت‌های ویدیو+صدا / فقط ویدیو / فقط صدا (mp3, m4a, opus, flac, wav…)
- زیرنویس (آپلودشده و خودکار)، برش بازه زمانی، پخش زنده از ابتدا
- جاسازی متادیتا/تصویر/زیرنویس/فصل‌ها، SponsorBlock، Recode/Remux با FFmpeg
- آرگومان‌های دلخواه yt-dlp و FFmpeg، محدودیت سرعت، پروکسی، cookies.txt
- aria2c و دانلود موازی تکه‌ها، صف دانلود با چند دانلود همزمان
- سرویس پیش‌زمینه با نوتیفیکیشن پیشرفت
- **Share به برنامه** از مرورگر/یوتیوب و …
- تب Tools: اجرای هر دستور yt-dlp + پریست‌های سریع (فقط زیرنویس، کامنت، چت زنده و …)
- به‌روزرسانی yt-dlp داخل برنامه (stable / nightly / master)
- تم تیره/روشن، فارسی و انگلیسی (RTL خودکار)

فایل‌ها در `Downloads/YDL GUI` ذخیره می‌شوند.

## ساختار
- `core/ArgsBuilder.kt` ← پورت منطق ساخت آرگومان‌های نسخه دسکتاپ
- `core/DownloadManager.kt` ← صف، پیشرفت، خروجی گرفتن به MediaStore
- `core/InfoParser.kt` ← تحلیل خروجی `yt-dlp -J`
- `ui/*` ← صفحه‌های Compose

## نکات
- حداقل اندروید ۱۰ (API 29).
- اگر سایتی خطا داد، ابتدا از Settings ← Update yt-dlp را بزنید.
- Cookie از مرورگر به‌صورت خودکار در اندروید ممکن نیست؛ فایل `cookies.txt` را import کنید.
