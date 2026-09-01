# Golden President — Android WebView + Myket Billing

این نسخه برای اجرای بازی HTML داخل WebView و پرداخت درون‌برنامه‌ای مایکت آماده شده است.

## پرداخت مایکت
SDK رسمی `myket-billing-client` از JitPack اضافه شده و کلید RSA مایکت در `BuildConfig.MYKET_PUBLIC_KEY` قرار دارد. خود SDK در مخزن رسمی مایکت منتشر شده است.

محصولات:
- `gp_capital_50k` — مصرفی
- `gp_capital_180k` — مصرفی
- `gp_capital_400k` — مصرفی
- `gp_premium` — دائمی
- `gp_no_ads` — دائمی

بعد از اتصال به مایکت، خریدهای دائمی به‌صورت خودکار بازیابی می‌شوند. محصولات مصرفی بعد از خرید `consume` می‌شوند تا دوباره قابل خرید باشند.

> شناسه محصولات باید دقیقاً با شناسه‌هایی که در پنل توسعه‌دهندگان مایکت می‌سازی یکسان باشند.

## اتصال HTML به Android
HTML از این Bridge استفاده می‌کند:
- `MyketBridge.purchase(productId)`
- `MyketBridge.restore()`

و Android نتیجه را با این callbackها به HTML برمی‌گرداند:
- `window.onMyketPurchaseSuccess(id)`
- `window.onMyketPurchaseFailed(message)`
- `window.onMyketPurchaseRestored(id)`

## تبلیغات
در این پروژه، تبلیغات از Yandex Mobile Ads جدا از SDK پرداخت مایکت پیاده شده است؛ چون مخزن عمومی رسمی مایکت که برای Android در دسترس است SDK پرداخت را ارائه می‌کند، نه یک SDK تبلیغات عمومی مستند. شناسه‌های تبلیغ فعلاً آزمایشی هستند و قبل از انتشار باید با شناسه واقعی سرویس تبلیغات جایگزین شوند.

## ساخت بدون لپ‌تاپ
پوشه `.github/workflows/main.yml` اضافه شده است. با Push کردن پروژه به GitHub، GitHub Actions پروژه را با JDK 17 و Gradle 8.9 می‌سازد و APK را به‌عنوان Artifact تحویل می‌دهد.
