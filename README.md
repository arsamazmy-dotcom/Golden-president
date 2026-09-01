# Golden President — Myket WebView

نسخه آماده ساخت APK با WebView، پرداخت درون‌برنامه‌ای مایکت و تبلیغات بین بازی/جایزه‌ای.

## نکات مهم
- کلید عمومی مایکت داخل `app/build.gradle` قرار گرفته است.
- شناسه‌های خرید مایکت باید دقیقاً با پنل توسعه‌دهنده مایکت یکی باشند:
  - `gp_capital_50k`
  - `gp_capital_180k`
  - `gp_capital_400k`
  - `gp_premium`
  - `gp_no_ads`
- تبلیغات فعلاً از Yandex Mobile Ads SDK 8.2.0 با شناسه‌های Demo استفاده می‌کنند تا APK برای تست بدون نیاز به شناسه تبلیغ واقعی Build شود.
- قبل از انتشار، `INTERSTITIAL_AD_UNIT_ID` و `REWARDED_AD_UNIT_ID` را در `app/build.gradle` با شناسه‌های واقعی پنل تبلیغات جایگزین کنید.
- پاداش تبلیغ جایزه‌ای به ۱٬۰۰۰ بودجه کاهش داده شده است.
- تبلیغ بین بازی‌ها در یک نقطه طبیعی، به‌صورت گهگاهی و نه در هر نوبت نمایش داده می‌شود.
- خرید `gp_no_ads` و `gp_premium` تبلیغات را در Bridge غیرفعال می‌کند و وضعیت آن در SharedPreferences ذخیره می‌شود.

## GitHub Actions
Workflow با JDK 17 و Gradle 8.10.2 اجرا می‌شود و APK دیباگ را به‌صورت Artifact منتشر می‌کند.
