package com.godev.optistock;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    static class Product {
        String barcode, name, imagePath, stockName, category;
        int quantity;
        double price;
    }

    static class ReceiptItem {
        String barcode, name;
        int quantity;
        double unitPrice;
    }

    static class Movement {
        long timestamp;
        String type;
        String barcode;
        String productName;
        String category;
        int quantity;
        String stockName;
        double unitPrice;
    }

    static class SavedReceipt {
        long timestamp;
        String stockName;
        double totalAmount;
        double discountAmount;
        String receiptText;
        List<ReceiptItem> items = new ArrayList<>();
    }

    private final List<Product> inventory = new ArrayList<>();
    private final List<Product> filteredInventory = new ArrayList<>();
    private final List<String> stocksList = new ArrayList<>();
    private final List<String> categoriesList = new ArrayList<>();
    private final List<ReceiptItem> currentReceipt = new ArrayList<>();
    private final List<SavedReceipt> savedReceiptsHistory = new ArrayList<>();
    private final List<String> clientEmails = new ArrayList<>();
    private final List<Movement> movementsHistory = new ArrayList<>();
    private final Map<String, Bitmap> bitmapCache = new HashMap<>();

    private final List<String> pendingExportStocks = new ArrayList<>();
    private final List<String> pendingExportCats = new ArrayList<>();
    private int pendingReceiptsPeriod = 0;

    private String currentStock = "";
    private String selectedCategoryFilter = "ALL";
    private boolean onlyLowStockFilter = false;
    private int lowStockThreshold = 3;
    private InventoryAdapter adapter;
    private SharedPreferences prefs;

    private boolean isDarkMode = true;
    private boolean usePrices = false;
    private boolean useScanner = true;
    private String langPref = "SYS";
    private String searchQuery = "";
    private String smtpHost = "smtp.gmail.com";
    private String smtpPort = "587";
    private String senderEmail = "";
    private String senderAppPass = "";
    private String shopName = "";
    private String customLogoPath = "";
    private double lastExitTotal = 0.0;
    private double sessionExitTotal = 0.0;

    private static final String SKU_PRO_LIFETIME = "optistock_lifetime_pro";
    private static final long TRIAL_DURATION_MS = 7L * 24L * 60L * 60L * 1000L;
    private BillingClient billingClient;
    private ProductDetails proProductDetails;
    private String formattedLaunchPrice = "4,99 €";

    private static final int REQ_EXPORT_STOCKS_PDF = 1001;
    private static final int REQ_TAKE_PHOTO = 1002;
    private static final int REQ_EXPORT_BACKUP = 1003;
    private static final int REQ_IMPORT_BACKUP = 1004;
    private static final int REQ_CREATE_SINGLE_RECEIPT_PDF = 1005;
    private static final int REQ_EXPORT_HISTORY_CSV = 1006;
    private static final int REQ_PICK_CUSTOM_LOGO = 1007;
    private static final int REQ_EXPORT_EXITS_PDF = 1008;
    private static final int REQ_EXPORT_RECEIPTS_PERIOD_PDF = 1009;

    private ImageView currentPhotoPreview, optionsLogoPreview;
    private String tempPhotoPath = "";
    private String tempCustomLogoPath = "";
    private double pendingReceiptDiscountVal = 0.0;
    private boolean pendingReceiptDiscountPct = true;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout rootLayout, headerLayout, actionRowLayout, subHeaderLayout, statsRefsCard, statsUnitsCard, statsLowCard, emptyContainer, exitTotalBanner, bottomBarLayout;
    private TextView titleText, trialBannerText, statsRefsValue, statsRefsLabel, statsUnitsValue, statsUnitsLabel, statsLowValue, statsLowLabel, emptyText, exitTotalText;
    private ImageView headerLogo, emptyLogo;
    private EditText searchInput;
    private Button btnTestSim, btnEntry, btnExit, btnHistory, btnExport, btnOptions, btnStockSelector, btnCatFilter, btnOpenReceipt;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("OptiStockPrefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("darkMode", true);
        usePrices = prefs.getBoolean("usePrices", false);
        useScanner = prefs.getBoolean("useScanner", true);
        lowStockThreshold = prefs.getInt("lowStockThreshold", 3);
        langPref = prefs.getString("langPref", "SYS");
        smtpHost = prefs.getString("smtpHost", "smtp.gmail.com");
        smtpPort = prefs.getString("smtpPort", "587");
        senderEmail = prefs.getString("senderEmail", "");
        senderAppPass = prefs.getString("senderAppPass", "");
        shopName = prefs.getString("shopName", "");
        customLogoPath = prefs.getString("customLogoPath", "");

        initTrialTimestamp();
        initBilling();
        loadData();
        showSplashScreen();
    }

    private void initTrialTimestamp() {
        long start = prefs.getLong("trialStartMs", 0L);
        if (start == 0L) {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                start = pi.firstInstallTime > 0 ? pi.firstInstallTime : System.currentTimeMillis();
            } catch (Exception e) {
                start = System.currentTimeMillis();
            }
            prefs.edit().putLong("trialStartMs", start).apply();
        }
    }

    private boolean isProUnlocked() {
        return prefs.getBoolean("isProUnlocked", false) || prefs.getBoolean("devBypassPro", false);
    }

    private int getRemainingTrialDays() {
        long start = prefs.getLong("trialStartMs", System.currentTimeMillis());
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= TRIAL_DURATION_MS) return 0;
        long remaining = TRIAL_DURATION_MS - elapsed;
        return (int) Math.ceil(remaining / (24.0 * 60.0 * 60.0 * 1000.0));
    }

    private boolean isAccessAllowed() {
        return isProUnlocked() || getRemainingTrialDays() > 0;
    }

    private void initBilling() {
        PurchasesUpdatedListener listener = (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (Purchase purchase : purchases) {
                    handlePurchase(purchase);
                }
            }
        };

        billingClient = BillingClient.newBuilder(this)
                .setListener(listener)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProProductDetails();
                    checkExistingPurchases();
                }
            }
            @Override
            public void onBillingServiceDisconnected() {}
        });
    }

    private void queryProProductDetails() {
        if (billingClient == null || !billingClient.isReady()) return;
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_PRO_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();
        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                proProductDetails = productDetailsList.get(0);
                if (proProductDetails.getOneTimePurchaseOfferDetails() != null) {
                    formattedLaunchPrice = proProductDetails.getOneTimePurchaseOfferDetails().getFormattedPrice();
                }
            }
        });
    }

    private void checkExistingPurchases() {
        if (billingClient == null || !billingClient.isReady()) return;
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (Purchase p : purchases) {
                    handlePurchase(p);
                }
            }
        });
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getProducts().contains(SKU_PRO_LIFETIME) && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                billingClient.acknowledgePurchase(ackParams, result -> {});
            }
            prefs.edit().putBoolean("isProUnlocked", true).apply();
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, tr("pro_unlocked_toast"), Toast.LENGTH_LONG).show();
                buildMainUI();
                applyThemeAndLang();
            });
        }
    }

    private void openPlayStoreOrBilling() {
        if (isProUnlocked()) {
            Toast.makeText(this, "⭐ " + tr("pro_version_badge"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (billingClient != null && billingClient.isReady() && proProductDetails != null) {
            BillingFlowParams.ProductDetailsParams pdParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(proProductDetails)
                    .build();
            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(pdParams))
                    .build();
            billingClient.launchBillingFlow(this, flowParams);
        } else {
            try {
                Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()));
                startActivity(marketIntent);
            } catch (Exception e) {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()));
                startActivity(webIntent);
            }
        }
    }

    private void showPaywallUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#040B1C"));
        scroll.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(dp(24), dp(36), dp(24), dp(24));
        scroll.addView(container);

        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadAppLogoInto(logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(140), dp(140));
        logoLp.setMargins(0, 0, 0, dp(16));
        container.addView(logo, logoLp);

        TextView title = new TextView(this);
        title.setText(tr("trial_expired_title"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        container.addView(title);

        TextView sub = new TextView(this);
        sub.setText(tr("trial_expired_sub"));
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(20));
        container.addView(sub);

        LinearLayout offerCard = new LinearLayout(this);
        offerCard.setOrientation(LinearLayout.VERTICAL);
        offerCard.setGravity(Gravity.CENTER);
        offerCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        offerCard.setBackground(createRoundedBg(Color.parseColor("#0F172A"), dp(16), dp(2), Color.parseColor("#38BDF8")));

        TextView badge = new TextView(this);
        badge.setText(tr("launch_offer_badge"));
        badge.setTextColor(Color.parseColor("#040B1C"));
        badge.setTextSize(12);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(12), dp(4), dp(12), dp(4));
        badge.setBackground(createRoundedBg(Color.parseColor("#38BDF8"), dp(8)));
        offerCard.addView(badge);

        TextView priceTv = new TextView(this);
        priceTv.setText(formattedLaunchPrice);
        priceTv.setTextColor(Color.WHITE);
        priceTv.setTextSize(36);
        priceTv.setTypeface(null, Typeface.BOLD);
        priceTv.setPadding(0, dp(12), 0, dp(4));
        offerCard.addView(priceTv);

        TextView oneTimeTv = new TextView(this);
        oneTimeTv.setText(tr("one_time_label"));
        oneTimeTv.setTextColor(Color.parseColor("#34D399"));
        oneTimeTv.setTextSize(13);
        oneTimeTv.setTypeface(null, Typeface.BOLD);
        oneTimeTv.setPadding(0, 0, 0, dp(14));
        offerCard.addView(oneTimeTv);

        TextView perksTv = new TextView(this);
        perksTv.setText(tr("pro_perks_list"));
        perksTv.setTextColor(Color.parseColor("#E2E8F0"));
        perksTv.setTextSize(13);
        perksTv.setLineSpacing(dp(4), 1f);
        offerCard.addView(perksTv);

        container.addView(offerCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnUnlock = new Button(this);
        btnUnlock.setText(String.format(Locale.getDefault(), tr("unlock_btn_fmt"), formattedLaunchPrice));
        btnUnlock.setTextSize(16);
        btnUnlock.setTypeface(null, Typeface.BOLD);
        btnUnlock.setTextColor(Color.WHITE);
        btnUnlock.setBackground(createRoundedBg(Color.parseColor("#059669"), dp(14), dp(2), Color.parseColor("#34D399")));
        btnUnlock.setOnClickListener(v -> openPlayStoreOrBilling());
        LinearLayout.LayoutParams unlockLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        unlockLp.setMargins(0, dp(22), 0, dp(10));
        container.addView(btnUnlock, unlockLp);

        Button btnSimPaywall = new Button(this);
        btnSimPaywall.setText("🧪 TEST : Simuler l'achat Pro");
        btnSimPaywall.setAllCaps(false);
        btnSimPaywall.setTextColor(Color.WHITE);
        btnSimPaywall.setBackground(createRoundedBg(Color.parseColor("#7C3AED"), dp(12), dp(1), Color.parseColor("#A78BFA")));
        btnSimPaywall.setOnClickListener(v -> {
            prefs.edit().putBoolean("devBypassPro", true).apply();
            buildMainUI();
            applyThemeAndLang();
        });
        container.addView(btnSimPaywall, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        setContentView(scroll);
    }

    private void loadAppLogoInto(ImageView imgView) {
        int resId = getResources().getIdentifier("splash_logo", "drawable", getPackageName());
        if (resId != 0) {
            imgView.setImageResource(resId);
        } else {
            imgView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    private Bitmap getUserCompanyLogoBitmap() {
        if (customLogoPath != null && !customLogoPath.isEmpty()) {
            return loadBitmapFromInternalStorage(customLogoPath);
        }
        return null;
    }

    private void showSplashScreen() {
        LinearLayout splashRoot = new LinearLayout(this);
        splashRoot.setOrientation(LinearLayout.VERTICAL);
        splashRoot.setGravity(Gravity.CENTER_HORIZONTAL);
        splashRoot.setBackgroundColor(Color.parseColor("#040B1C"));
        splashRoot.setPadding(dp(20), dp(32), dp(20), dp(24));
        splashRoot.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.3f));

        ImageView logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadAppLogoInto(logoView);

        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(300), dp(300));
        logoLp.gravity = Gravity.CENTER;
        splashRoot.addView(logoView, logoLp);

        BarcodeLaserView laserView = new BarcodeLaserView(this);
        LinearLayout.LayoutParams laserLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(85));
        laserLp.setMargins(dp(16), dp(24), dp(16), dp(16));
        splashRoot.addView(laserView, laserLp);

        splashRoot.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.7f));

        TextView footerTv = new TextView(this);
        footerTv.setText("Développé par GoDev");
        footerTv.setTextColor(Color.parseColor("#38BDF8"));
        footerTv.setTextSize(15);
        footerTv.setTypeface(null, Typeface.BOLD);
        footerTv.setGravity(Gravity.CENTER);
        splashRoot.addView(footerTv);

        setContentView(splashRoot);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(3500);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(animation -> laserView.setProgress((float) animation.getAnimatedValue()));
        anim.start();

        mainHandler.postDelayed(() -> {
            if (isAccessAllowed()) {
                buildMainUI();
                applyThemeAndLang();
            } else {
                showPaywallUI();
            }
        }, 3650);
    }

    private class BarcodeLaserView extends View {
        private float progress = 0f;
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint whiteDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] barPattern = {3,2,5,2,2,4,6,2,3,3,2,5,2,2,4,3,6,2,2,4,3,2,5,3,2,4,2,6,3,2,4,2,5,2,3,4,2,3,5,2,4,2,3,5,2,2,6,3,2,4,3,2};

        public BarcodeLaserView(Context context) {
            super(context);
            barPaint.setColor(Color.WHITE);
            bgBarPaint.setColor(Color.parseColor("#1E293B"));
            laserPaint.setColor(Color.parseColor("#FF1E1E"));
            laserPaint.setStrokeWidth(dp(3));
            glowPaint.setColor(Color.parseColor("#66FF0000"));
            glowPaint.setStrokeWidth(dp(12));
            framePaint.setColor(Color.parseColor("#0EA5E9"));
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(dp(2));
            textPaint.setColor(Color.parseColor("#94A3B8"));
            textPaint.setTextSize(dp(12));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.MONOSPACE);
            whiteDot.setColor(Color.WHITE);
        }

        public void setProgress(float p) {
            this.progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight(), padX = dp(20), padY = dp(10), barBottom = h - dp(24);
            canvas.drawRoundRect(new RectF(padX - dp(8), padY - dp(4), w - padX + dp(8), barBottom + dp(6)), dp(10), dp(10), framePaint);
            float usableWidth = w - 2f * padX, laserX = padX + usableWidth * progress;
            int totalUnits = 0;
            for (int u : barPattern) totalUnits += u;
            float unitW = usableWidth / totalUnits, curX = padX;
            for (int i = 0; i < barPattern.length; i++) {
                float bw = barPattern[i] * unitW;
                if (i % 2 == 0) {
                    canvas.drawRect(curX, padY + dp(4), curX + bw, barBottom - dp(2), bgBarPaint);
                    if (curX < laserX) canvas.drawRect(curX, padY + dp(4), Math.min(curX + bw, laserX), barBottom - dp(2), barPaint);
                }
                curX += bw;
            }
            canvas.drawLine(laserX, padY - dp(6), laserX, barBottom + dp(8), glowPaint);
            canvas.drawLine(laserX, padY - dp(6), laserX, barBottom + dp(8), laserPaint);
            canvas.drawCircle(laserX, (padY + barBottom) / 2f, dp(4), glowPaint);
            canvas.drawCircle(laserX, (padY + barBottom) / 2f, dp(2), whiteDot);
            canvas.drawText("SCANNING... " + (int)(progress * 100) + "%", w / 2f, h - dp(4), textPaint);
        }
    }

    private String tr(String key) {
        Map<String, String[]> dict = new HashMap<>();
        dict.put("app_title", new String[]{"OptiStock", "OptiStock", "OptiStock"});
        dict.put("refs", new String[]{"RÉFÉRENCES", "ITEMS", "REFERENCIAS"});
        dict.put("units", new String[]{"TOTAL UNITÉS", "TOTAL UNITS", "UNIDADES TOTALES"});
        dict.put("low_stock", new String[]{"⚠️ STOCK BAS", "⚠️ LOW STOCK", "⚠️ STOCK BAJO"});
        dict.put("btn_in", new String[]{"+ ENTRÉE", "+ STOCK IN", "+ ENTRADA"});
        dict.put("btn_out", new String[]{"- SORTIE", "- STOCK OUT", "- SALIDA"});
        dict.put("search", new String[]{"🔍 Rechercher un produit ou code...", "🔍 Search product or barcode...", "🔍 Buscar producto o código..."});
        dict.put("empty", new String[]{"Aucun produit affiché.\nAppuyez sur + ENTRÉE en bas pour ajouter un article.", "No products shown.\nTap + STOCK IN below to add an item.", "Sin productos mostrados.\nToque + ENTRADA abajo para añadir."});
        dict.put("fetching", new String[]{"Recherche du produit sur Internet...", "Searching product online...", "Buscando producto en línea..."});
        dict.put("entry_title", new String[]{"Entrée de stock", "Stock Entry", "Entrada de stock"});
        dict.put("exit_title", new String[]{"Sortie de stock", "Stock Exit", "Salida de stock"});
        dict.put("prod_name", new String[]{"Nom du produit", "Product Name", "Nombre del producto"});
        dict.put("code_opt_hint", new String[]{"Code ou Référence (optionnel, auto si vide)", "Barcode or Ref (optional, auto if empty)", "Código o Ref (opcional, auto si vacío)"});
        dict.put("qty_label", new String[]{"Quantité :", "Quantity:", "Cantidad:"});
        dict.put("price_label", new String[]{"Prix unitaire (€ / $) :", "Unit Price (€ / $):", "Precio unitario (€ / $):"});
        dict.put("category_label", new String[]{"Catégorie :", "Category:", "Categoría:"});
        dict.put("current_stock", new String[]{"En stock : ", "In stock: ", "En stock: "});
        dict.put("save", new String[]{"VALIDER", "SAVE", "GUARDAR"});
        dict.put("action_exit", new String[]{"🔴 SORTIE", "🔴 STOCK OUT", "🔴 SALIDA"});
        dict.put("action_entry", new String[]{"🟢 + ENTRÉE", "🟢 + STOCK IN", "🟢 + ENTRADA"});
        dict.put("cancel", new String[]{"Annuler", "Cancel", "Cancelar"});
        dict.put("delete", new String[]{"🗑️ Supprimer", "🗑️ Delete", "🗑️ Eliminar"});
        dict.put("manual_code", new String[]{"Saisie manuelle du code", "Manual Barcode", "Código manual"});
        dict.put("photo_btn", new String[]{"📷 Photo", "📷 Photo", "📷 Foto"});
        dict.put("hist_btn", new String[]{"📜 Historique", "📜 History", "📜 Historial"});
        dict.put("export_btn", new String[]{"📤 Exporter", "📤 Export", "📤 Exportar"});
        dict.put("pdf_ok", new String[]{"Document PDF généré avec succès !", "PDF document saved successfully!", "¡Documento PDF guardado con éxito!"});
        dict.put("unknown_prod", new String[]{"Nouveau produit", "New Product", "Nuevo producto"});
        dict.put("not_in_stock", new String[]{"Ce produit n'est pas dans ce stock !", "Product not in this stock!", "¡Producto no está en este stock!"});
        dict.put("all_cats", new String[]{"Toutes catégories", "All Categories", "Todas las categorías"});
        dict.put("uncategorized", new String[]{"Général", "General", "General"});
        dict.put("new_cat_opt", new String[]{"➕ Créer une nouvelle catégorie...", "➕ Create new category...", "➕ Crear nueva categoría..."});
        dict.put("new_cat_hint", new String[]{"Nom de la nouvelle catégorie", "New category name", "Nombre de la nueva categoría"});
        dict.put("new_stock_opt", new String[]{"➕ Créer un nouveau stock...", "➕ Create new stock...", "➕ Crear nuevo stock..."});
        dict.put("del_stock_opt", new String[]{"🗑️ Supprimer le stock actuel", "🗑️ Delete current stock", "🗑️ Eliminar stock actual"});
        dict.put("new_stock_title", new String[]{"Nouveau Stock", "New Stock", "Nuevo Stock"});
        dict.put("new_stock_hint", new String[]{"Ex: Réserve B, Camion...", "Ex: Warehouse B...", "Ej: Almacén B..."});
        dict.put("options_title", new String[]{"⚙️ Options", "⚙️ Settings", "⚙️ Opciones"});
        dict.put("opt_lang", new String[]{"Langue de l'application :", "App Language:", "Idioma de la aplicación:"});
        dict.put("lang_sys", new String[]{"📱 Langue du téléphone", "📱 Phone Language", "📱 Idioma del teléfono"});
        dict.put("opt_dark", new String[]{"Mode Sombre (Cyber-Logistique)", "Dark Mode (Cyber-Logistics)", "Modo Oscuro (Ciber-Logística)"});
        dict.put("opt_scanner", new String[]{"📷 Activer le scanner de code-barres", "📷 Enable barcode scanner", "📷 Activar escáner de código de barras"});
        dict.put("opt_prices", new String[]{"💶 Activer la gestion des prix & Ticket", "💶 Enable prices & Receipt", "💶 Activar precios y Ticket"});
        dict.put("opt_low_thresh", new String[]{"Seuil d'alerte stock bas (unités) :", "Low stock alert threshold (units):", "Umbral de alerta stock bajo (unidades):"});
        dict.put("exit_total_live", new String[]{"Prix total de cette sortie : ", "Total price for this exit: ", "Precio total de esta salida: "});
        dict.put("exit_banner", new String[]{"🧾 Ticket (%1$d art.) : %2$.2f €", "🧾 Receipt (%1$d it.) : %2$.2f", "🧾 Ticket (%1$d art.) : %2$.2f €"});
        dict.put("btn_receipt_view", new String[]{"📧 Ticket / PDF", "📧 Receipt / PDF", "📧 Ticket / PDF"});
        dict.put("receipt_title", new String[]{"🧾 Ticket de Caisse", "🧾 Sales Receipt", "🧾 Ticket de Caja"});
        dict.put("discount_label", new String[]{"Remise :", "Discount:", "Descuento:"});
        dict.put("receipt_pdf_btn", new String[]{"📄 Ticket PDF", "📄 Receipt PDF", "📄 Ticket PDF"});
        dict.put("client_search_hint", new String[]{"🔍 Chercher ou saisir l'e-mail du client...", "🔍 Search or type customer email...", "🔍 Buscar o escribir email del cliente..."});
        dict.put("send_email_btn", new String[]{"📧 Envoyer par mail", "📧 Send by email", "📧 Enviar por correo"});
        dict.put("clear_receipt_btn", new String[]{"🗑️ Vider", "🗑️ Clear", "🗑️ Vaciar"});
        dict.put("opt_shop_name", new String[]{"Nom de votre entreprise (Tickets & PDF) :", "Your Company Name (Receipts & PDFs):", "Nombre de su empresa (Tickets y PDFs):"});
        dict.put("opt_custom_logo", new String[]{"Logo de votre entreprise (Tickets & PDF) :", "Your Company Logo (Receipts & PDFs):", "Logo de su empresa (Tickets y PDFs):"});
        dict.put("pick_logo_btn", new String[]{"🖼️ Choisir un logo", "🖼️ Choose a logo", "🖼️ Elegir un logo"});
        dict.put("opt_smtp_host", new String[]{"Serveur SMTP :", "SMTP Server:", "Servidor SMTP:"});
        dict.put("opt_smtp_port", new String[]{"Port SMTP :", "SMTP Port:", "Puerto SMTP:"});
        dict.put("opt_sender_email", new String[]{"Mon adresse e-mail liée (Expéditeur) :", "My linked email address (Sender):", "Mi correo vinculado (Remitente):"});
        dict.put("opt_sender_pass", new String[]{"Mot de passe d'application SMTP :", "SMTP App Password:", "Contraseña de aplicación SMTP:"});
        dict.put("email_sent_ok", new String[]{"✅ Ticket envoyé automatiquement au client !", "✅ Receipt automatically sent to customer!", "✅ ¡Ticket enviado automáticamente al cliente!"});
        dict.put("email_invalid", new String[]{"Veuillez saisir une adresse e-mail client valide.", "Please enter a valid customer email.", "Ingrese un correo electrónico válido."});
        dict.put("saved_clients_label", new String[]{"👥 Registre clients (touchez pour sélectionner) :", "👥 Customer registry (tap to select):", "👥 Registro de clientes (toque para elegir):"});
        dict.put("tap_product_for_exit", new String[]{"👆 Cliquez sur un produit de la liste pour choisir la quantité et valider la sortie.", "👆 Tap a product in the list to choose quantity and confirm stock out.", "👆 Toque un producto en la lista para elegir la cantidad y validar la salida."});
        dict.put("backup_section", new String[]{"💾 Sauvegarde & Restauration des données :", "💾 Data Backup & Restore:", "💾 Copia de seguridad y Restauración:"});
        dict.put("backup_export_btn", new String[]{"📤 Exporter sauvegarde (.json)", "📤 Export backup (.json)", "📤 Exportar copia (.json)"});
        dict.put("backup_import_btn", new String[]{"📥 Restaurer sauvegarde (.json)", "📥 Restore backup (.json)", "📥 Restaurar copia (.json)"});
        dict.put("backup_ok", new String[]{"✅ Sauvegarde exportée avec succès !", "✅ Backup exported successfully!", "✅ ¡Copia exportada con éxito!"});
        dict.put("restore_ok", new String[]{"✅ Données restaurées avec succès !", "✅ Data restored successfully!", "✅ ¡Datos restaurados con éxito!"});
        dict.put("history_title", new String[]{"📜 Historique des mouvements", "📜 Movement History", "📜 Historial de movimientos"});
        dict.put("history_empty", new String[]{"Aucun mouvement enregistré pour le moment.", "No movements recorded yet.", "Sin movimientos registrados aún."});
        dict.put("history_csv_btn", new String[]{"📊 Exporter CSV", "📊 Export CSV", "📊 Exportar CSV"});
        dict.put("history_clear_btn", new String[]{"🗑️ Effacer", "🗑️ Clear", "🗑️ Borrar"});
        dict.put("csv_ok", new String[]{"✅ Historique CSV exporté !", "✅ History CSV exported!", "✅ ¡Historial CSV exportado!"});
        dict.put("trial_banner_fmt", new String[]{"🎁 Essai gratuit (%1$d j) - Cliquez ici pour la version Pro", "🎁 Free trial (%1$d d) - Click here for Pro version", "🎁 Prueba gratis (%1$d d) - Clic aquí para versión Pro"});
        dict.put("pro_version_badge", new String[]{"⭐ Version Pro", "⭐ Pro Version", "⭐ Versión Pro"});
        dict.put("trial_expired_title", new String[]{"Essai gratuit de 7 jours terminé", "7-Day Free Trial Ended", "Prueba gratuita de 7 días finalizada"});
        dict.put("trial_expired_sub", new String[]{"Débloquez OptiStock définitivement pour continuer à gérer vos stocks et vos tickets sans limite.", "Unlock OptiStock permanently to keep managing your inventory and receipts without limits.", "Desbloquea OptiStock para siempre para seguir gestionando tus stocks y tickets."});
        dict.put("launch_offer_badge", new String[]{"🚀 OFFRE DE LANCEMENT • LICENCE À VIE", "🚀 LAUNCH OFFER • LIFETIME LICENSE", "🚀 OFERTA DE LANZAMIENTO • DE POR VIDA"});
        dict.put("one_time_label", new String[]{"Achat unique • Aucun abonnement", "One-time purchase • No subscription", "Compra única • Sin suscripción"});
        dict.put("pro_perks_list", new String[]{"✓ Multi-stocks & catégories illimités\n✓ Scan code-barres + saisie rapide sans scan\n✓ Ticket de caisse PDF, remises & envoi e-mail\n✓ Historique CSV & sauvegarde complète JSON\n✓ Mises à jour futures incluses", "✓ Unlimited multi-stocks & categories\n✓ Barcode scanner + fast manual mode\n✓ PDF receipts, discounts & client emails\n✓ CSV history & full JSON backup\n✓ All future updates included", "✓ Multi-stocks y categorías ilimitados\n✓ Escáner y modo manual rápido\n✓ Tickets PDF, descuentos y envío email\n✓ Historial CSV y copia de seguridad JSON"});
        dict.put("unlock_btn_fmt", new String[]{"🔓 Débloquer à vie (%1$s)", "🔓 Unlock Lifetime (%1$s)", "🔓 Desbloquear de por vida (%1$s)"});
        dict.put("pro_unlocked_toast", new String[]{"⭐ Merci ! OptiStock Version Pro est activé.", "⭐ Thank you! OptiStock Pro Version is active.", "⭐ ¡Gracias! OptiStock Versión Pro activada."});

        String eff = langPref.equals("SYS") ? Locale.getDefault().getLanguage().toLowerCase() : langPref;
        int idx = eff.startsWith("en") ? 1 : (eff.startsWith("es") ? 2 : 0);
        return dict.containsKey(key) ? dict.get(key)[idx] : key;
    }

    private void buildMainUI() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(12), dp(10), dp(12), dp(10));

        trialBannerText = new TextView(this);
        trialBannerText.setTextSize(12);
        trialBannerText.setTypeface(null, Typeface.BOLD);
        trialBannerText.setGravity(Gravity.CENTER);
        trialBannerText.setPadding(dp(12), dp(7), dp(12), dp(7));
        trialBannerText.setOnClickListener(v -> openPlayStoreOrBilling());
        LinearLayout.LayoutParams trialLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trialLp.setMargins(0, 0, 0, dp(8));
        rootLayout.addView(trialBannerText, trialLp);

        headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        headerLogo = new ImageView(this);
        headerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadAppLogoInto(headerLogo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoLp.setMargins(0, 0, dp(10), 0);
        headerLayout.addView(headerLogo, logoLp);

        titleText = new TextView(this);
        titleText.setTextSize(23);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setSingleLine(true);
        headerLayout.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rootLayout.addView(headerLayout);

        actionRowLayout = new LinearLayout(this);
        actionRowLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actRowLp.setMargins(0, dp(8), 0, dp(6));

        btnHistory = new Button(this);
        btnHistory.setTextSize(12);
        btnHistory.setAllCaps(false);
        btnHistory.setTypeface(null, Typeface.BOLD);
        btnHistory.setPadding(dp(6), dp(4), dp(6), dp(4));
        btnHistory.setOnClickListener(v -> showHistoryDialog());
        LinearLayout.LayoutParams histLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        histLp.setMargins(0, 0, dp(4), 0);
        actionRowLayout.addView(btnHistory, histLp);

        btnExport = new Button(this);
        btnExport.setTextSize(12);
        btnExport.setAllCaps(false);
        btnExport.setTypeface(null, Typeface.BOLD);
        btnExport.setPadding(dp(6), dp(4), dp(6), dp(4));
        btnExport.setOnClickListener(v -> showExportMenuDialog());
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        expLp.setMargins(dp(4), 0, dp(4), 0);
        actionRowLayout.addView(btnExport, expLp);

        btnOptions = new Button(this);
        btnOptions.setTextSize(12);
        btnOptions.setAllCaps(false);
        btnOptions.setTypeface(null, Typeface.BOLD);
        btnOptions.setPadding(dp(6), dp(4), dp(6), dp(4));
        btnOptions.setOnClickListener(v -> showOptionsDialog());
        LinearLayout.LayoutParams optLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        optLp.setMargins(dp(4), 0, 0, 0);
        actionRowLayout.addView(btnOptions, optLp);

        rootLayout.addView(actionRowLayout, actRowLp);

        subHeaderLayout = new LinearLayout(this);
        subHeaderLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(2), 0, dp(8));

        btnStockSelector = new Button(this);
        btnStockSelector.setAllCaps(false);
        btnStockSelector.setTextSize(13);
        btnStockSelector.setTypeface(null, Typeface.BOLD);
        btnStockSelector.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnStockSelector.setOnClickListener(v -> showStockPopupMenu(v));
        LinearLayout.LayoutParams stBtnLp = new LinearLayout.LayoutParams(0, dp(40), 1.05f);
        stBtnLp.setMargins(0, 0, dp(5), 0);
        subHeaderLayout.addView(btnStockSelector, stBtnLp);

        btnCatFilter = new Button(this);
        btnCatFilter.setAllCaps(false);
        btnCatFilter.setTextSize(12);
        btnCatFilter.setTypeface(null, Typeface.BOLD);
        btnCatFilter.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnCatFilter.setOnClickListener(v -> showCategoryFilterPopup(v));
        LinearLayout.LayoutParams catBtnLp = new LinearLayout.LayoutParams(0, dp(40), 0.95f);
        catBtnLp.setMargins(dp(5), 0, 0, 0);
        subHeaderLayout.addView(btnCatFilter, catBtnLp);

        rootLayout.addView(subHeaderLayout, subLp);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsRowLp.setMargins(0, 0, 0, dp(8));

        statsRefsCard = new LinearLayout(this);
        statsRefsCard.setOrientation(LinearLayout.VERTICAL);
        statsRefsCard.setGravity(Gravity.CENTER);
        statsRefsCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        statsRefsValue = new TextView(this);
        statsRefsValue.setTextSize(20);
        statsRefsValue.setTypeface(null, Typeface.BOLD);
        statsRefsLabel = new TextView(this);
        statsRefsLabel.setTextSize(10);
        statsRefsLabel.setTypeface(null, Typeface.BOLD);
        statsRefsCard.addView(statsRefsValue);
        statsRefsCard.addView(statsRefsLabel);
        LinearLayout.LayoutParams refCardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        refCardLp.setMargins(0, 0, dp(4), 0);
        statsRow.addView(statsRefsCard, refCardLp);

        statsUnitsCard = new LinearLayout(this);
        statsUnitsCard.setOrientation(LinearLayout.VERTICAL);
        statsUnitsCard.setGravity(Gravity.CENTER);
        statsUnitsCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        statsUnitsValue = new TextView(this);
        statsUnitsValue.setTextSize(20);
        statsUnitsValue.setTypeface(null, Typeface.BOLD);
        statsUnitsLabel = new TextView(this);
        statsUnitsLabel.setTextSize(10);
        statsUnitsLabel.setTypeface(null, Typeface.BOLD);
        statsUnitsCard.addView(statsUnitsValue);
        statsUnitsCard.addView(statsUnitsLabel);
        LinearLayout.LayoutParams unitCardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        unitCardLp.setMargins(dp(4), 0, dp(4), 0);
        statsRow.addView(statsUnitsCard, unitCardLp);

        statsLowCard = new LinearLayout(this);
        statsLowCard.setOrientation(LinearLayout.VERTICAL);
        statsLowCard.setGravity(Gravity.CENTER);
        statsLowCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        statsLowValue = new TextView(this);
        statsLowValue.setTextSize(20);
        statsLowValue.setTypeface(null, Typeface.BOLD);
        statsLowLabel = new TextView(this);
        statsLowLabel.setTextSize(10);
        statsLowLabel.setTypeface(null, Typeface.BOLD);
        statsLowCard.addView(statsLowValue);
        statsLowCard.addView(statsLowLabel);
        statsLowCard.setOnClickListener(v -> {
            onlyLowStockFilter = !onlyLowStockFilter;
            applyThemeAndLang();
        });
        LinearLayout.LayoutParams lowCardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lowCardLp.setMargins(dp(4), 0, 0, 0);
        statsRow.addView(statsLowCard, lowCardLp);

        rootLayout.addView(statsRow, statsRowLp);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                filterList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, 0, 0, dp(8));
        rootLayout.addView(searchInput, searchParams);

        emptyContainer = new LinearLayout(this);
        emptyContainer.setOrientation(LinearLayout.VERTICAL);
        emptyContainer.setGravity(Gravity.CENTER);
        emptyLogo = new ImageView(this);
        emptyLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadAppLogoInto(emptyLogo);
        emptyLogo.setAlpha(0.22f);
        LinearLayout.LayoutParams emptyLogoLp = new LinearLayout.LayoutParams(dp(110), dp(110));
        emptyLogoLp.setMargins(0, 0, 0, dp(10));
        emptyContainer.addView(emptyLogo, emptyLogoLp);

        emptyText = new TextView(this);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(14);
        emptyText.setPadding(dp(20), 0, dp(20), 0);
        emptyContainer.addView(emptyText);
        rootLayout.addView(emptyContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(dp(8));
        adapter = new InventoryAdapter();
        listView.setAdapter(adapter);
        rootLayout.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        exitTotalBanner = new LinearLayout(this);
        exitTotalBanner.setOrientation(LinearLayout.HORIZONTAL);
        exitTotalBanner.setGravity(Gravity.CENTER_VERTICAL);
        exitTotalBanner.setPadding(dp(12), dp(7), dp(10), dp(7));
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerLp.setMargins(0, dp(6), 0, dp(4));

        exitTotalText = new TextView(this);
        exitTotalText.setTextColor(Color.WHITE);
        exitTotalText.setTextSize(13);
        exitTotalText.setTypeface(null, Typeface.BOLD);
        exitTotalText.setOnClickListener(v -> showReceiptDialog());
        exitTotalBanner.addView(exitTotalText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnOpenReceipt = new Button(this);
        btnOpenReceipt.setAllCaps(false);
        btnOpenReceipt.setTextSize(12);
        btnOpenReceipt.setTypeface(null, Typeface.BOLD);
        btnOpenReceipt.setTextColor(Color.WHITE);
        btnOpenReceipt.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnOpenReceipt.setBackground(createRoundedBg(Color.parseColor("#0284C7"), dp(8), dp(1), Color.parseColor("#38BDF8")));
        btnOpenReceipt.setOnClickListener(v -> showReceiptDialog());
        LinearLayout.LayoutParams openRecLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        openRecLp.setMargins(dp(6), 0, dp(6), 0);
        exitTotalBanner.addView(btnOpenReceipt, openRecLp);

        Button btnResetTotal = new Button(this);
        btnResetTotal.setText("↺");
        btnResetTotal.setTextColor(Color.WHITE);
        btnResetTotal.setBackground(createRoundedBg(Color.parseColor("#7F1D1D"), dp(8), dp(1), Color.parseColor("#F87171")));
        btnResetTotal.setOnClickListener(v -> {
            archiveCurrentReceiptIfNotEmpty(0.0, true);
            currentReceipt.clear();
            lastExitTotal = 0.0;
            sessionExitTotal = 0.0;
            saveData();
            updateStats();
        });
        exitTotalBanner.addView(btnResetTotal, new LinearLayout.LayoutParams(dp(34), dp(32)));
        rootLayout.addView(exitTotalBanner, bannerLp);

        bottomBarLayout = new LinearLayout(this);
        bottomBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        bottomBarLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actParams.setMargins(0, dp(6), 0, dp(2));

        btnTestSim = new Button(this);
        btnTestSim.setText("🧪 TEST");
        btnTestSim.setTextSize(11);
        btnTestSim.setAllCaps(false);
        btnTestSim.setTypeface(null, Typeface.BOLD);
        btnTestSim.setTextColor(Color.WHITE);
        btnTestSim.setBackground(createRoundedBg(Color.parseColor("#7C3AED"), dp(12), dp(1), Color.parseColor("#C4B5FD")));
        btnTestSim.setOnClickListener(v -> {
            boolean cur = prefs.getBoolean("devBypassPro", false);
            prefs.edit().putBoolean("devBypassPro", !cur).apply();
            Toast.makeText(this, !cur ? "⭐ Simulation : Version Pro ACTIVÉE" : "🎁 Simulation : Retour en Essai gratuit", Toast.LENGTH_SHORT).show();
            applyThemeAndLang();
        });
        btnTestSim.setOnLongClickListener(v -> {
            prefs.edit().putBoolean("devBypassPro", false).putBoolean("isProUnlocked", false).apply();
            showPaywallUI();
            return true;
        });
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(dp(68), dp(52));
        testLp.setMargins(0, 0, dp(6), 0);
        bottomBarLayout.addView(btnTestSim, testLp);

        btnEntry = new Button(this);
        btnEntry.setTextSize(15);
        btnEntry.setTypeface(null, Typeface.BOLD);
        btnEntry.setTextColor(Color.WHITE);
        btnEntry.setBackground(createRoundedBg(Color.parseColor("#059669"), dp(14), dp(2), Color.parseColor("#34D399")));
        btnEntry.setOnClickListener(v -> handleEntryAction());
        LinearLayout.LayoutParams btnInParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        btnInParams.setMargins(0, 0, dp(5), 0);
        bottomBarLayout.addView(btnEntry, btnInParams);

        btnExit = new Button(this);
        btnExit.setTextSize(15);
        btnExit.setTypeface(null, Typeface.BOLD);
        btnExit.setTextColor(Color.WHITE);
        btnExit.setBackground(createRoundedBg(Color.parseColor("#DC2626"), dp(14), dp(2), Color.parseColor("#F87171")));
        btnExit.setOnClickListener(v -> handleExitAction());
        LinearLayout.LayoutParams btnOutParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        btnOutParams.setMargins(dp(5), 0, 0, 0);
        bottomBarLayout.addView(btnExit, btnOutParams);

        rootLayout.addView(bottomBarLayout, actParams);
        setContentView(rootLayout);
    }

    private void applyThemeAndLang() {
        int bgColor = isDarkMode ? Color.parseColor("#040B1C") : Color.parseColor("#F1F5F9");
        int cardColor = isDarkMode ? Color.parseColor("#0F172A") : Color.parseColor("#FFFFFF");
        int accentCard = isDarkMode ? Color.parseColor("#112240") : Color.parseColor("#DBEAFE");
        int borderCyan = isDarkMode ? Color.parseColor("#0EA5E9") : Color.parseColor("#38BDF8");
        int borderSubtle = isDarkMode ? Color.parseColor("#1E3A8A") : Color.parseColor("#CBD5E1");
        int textColor = isDarkMode ? Color.parseColor("#F8FAFC") : Color.parseColor("#0F172A");
        int subTextColor = isDarkMode ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B");
        int cyanHighlight = isDarkMode ? Color.parseColor("#38BDF8") : Color.parseColor("#0284C7");

        rootLayout.setBackgroundColor(bgColor);

        trialBannerText.setVisibility(View.VISIBLE);
        if (isProUnlocked()) {
            trialBannerText.setText(tr("pro_version_badge"));
            trialBannerText.setTextColor(Color.parseColor("#34D399"));
            trialBannerText.setBackground(createRoundedBg(Color.parseColor("#064E3B"), dp(8), dp(1), Color.parseColor("#10B981")));
        } else {
            trialBannerText.setText(String.format(Locale.getDefault(), tr("trial_banner_fmt"), getRemainingTrialDays()));
            trialBannerText.setTextColor(Color.parseColor("#38BDF8"));
            trialBannerText.setBackground(createRoundedBg(Color.parseColor("#0F172A"), dp(8), dp(1), Color.parseColor("#0284C7")));
        }

        SpannableString styledTitle = new SpannableString("OptiStock");
        styledTitle.setSpan(new ForegroundColorSpan(textColor), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styledTitle.setSpan(new ForegroundColorSpan(cyanHighlight), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleText.setText(styledTitle);

        btnHistory.setText(tr("hist_btn"));
        btnHistory.setTextColor(textColor);
        btnHistory.setBackground(createRoundedBg(cardColor, dp(10), dp(1), borderSubtle));

        btnExport.setText(tr("export_btn"));
        btnExport.setTextColor(Color.WHITE);
        btnExport.setBackground(createRoundedBg(Color.parseColor("#0284C7"), dp(10), dp(1), Color.parseColor("#38BDF8")));

        btnOptions.setText(tr("options_title"));
        btnOptions.setTextColor(textColor);
        btnOptions.setBackground(createRoundedBg(cardColor, dp(10), dp(1), borderSubtle));

        btnStockSelector.setText("🏢 " + currentStock + " ▼");
        btnStockSelector.setTextColor(textColor);
        btnStockSelector.setBackground(createRoundedBg(accentCard, dp(12), dp(1), borderCyan));

        String catLabel = selectedCategoryFilter.equals("ALL") ? tr("all_cats") : selectedCategoryFilter;
        btnCatFilter.setText("🏷️ " + catLabel + " ▼");
        btnCatFilter.setTextColor(textColor);
        btnCatFilter.setBackground(createRoundedBg(cardColor, dp(12), dp(1), borderSubtle));

        statsRefsCard.setBackground(createRoundedBg(cardColor, dp(12), dp(1), borderSubtle));
        statsRefsValue.setTextColor(cyanHighlight);
        statsRefsLabel.setTextColor(subTextColor);
        statsRefsLabel.setText(tr("refs"));

        statsUnitsCard.setBackground(createRoundedBg(cardColor, dp(12), dp(1), borderSubtle));
        statsUnitsValue.setTextColor(Color.parseColor("#10B981"));
        statsUnitsLabel.setTextColor(subTextColor);
        statsUnitsLabel.setText(tr("units"));

        if (onlyLowStockFilter) {
            statsLowCard.setBackground(createRoundedBg(Color.parseColor("#451A03"), dp(12), dp(2), Color.parseColor("#F59E0B")));
        } else {
            statsLowCard.setBackground(createRoundedBg(cardColor, dp(12), dp(1), borderSubtle));
        }
        statsLowValue.setTextColor(Color.parseColor("#F59E0B"));
        statsLowLabel.setTextColor(onlyLowStockFilter ? Color.parseColor("#FBBF24") : subTextColor);
        statsLowLabel.setText(tr("low_stock"));

        btnEntry.setText(tr("btn_in"));
        btnExit.setText(tr("btn_out"));
        btnOpenReceipt.setText(tr("btn_receipt_view"));

        searchInput.setHint(tr("search"));
        searchInput.setTextColor(textColor);
        searchInput.setHintTextColor(subTextColor);
        searchInput.setBackground(createRoundedBg(cardColor, dp(12), dp(1), borderSubtle));

        emptyText.setText(tr("empty"));
        emptyText.setTextColor(subTextColor);

        exitTotalBanner.setBackground(createRoundedBg(Color.parseColor("#1E293B"), dp(12), dp(1), Color.parseColor("#38BDF8")));
        filterList();
    }

    private void showExportMenuDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(16));

        AlertDialog[] holder = new AlertDialog[1];

        Button bStocks = new Button(this);
        bStocks.setText("📦 Exporter les stocks (PDF)");
        bStocks.setAllCaps(false);
        bStocks.setTextSize(14);
        bStocks.setTypeface(null, Typeface.BOLD);
        bStocks.setTextColor(Color.WHITE);
        bStocks.setBackground(createRoundedBg(Color.parseColor("#0284C7"), dp(12), dp(1), Color.parseColor("#38BDF8")));
        bStocks.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showExportStocksOrExitsDialog(true);
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp1.setMargins(0, 0, 0, dp(10));
        box.addView(bStocks, lp1);

        Button bExits = new Button(this);
        bExits.setText("🔴 Exporter les sorties (PDF)");
        bExits.setAllCaps(false);
        bExits.setTextSize(14);
        bExits.setTypeface(null, Typeface.BOLD);
        bExits.setTextColor(Color.WHITE);
        bExits.setBackground(createRoundedBg(Color.parseColor("#DC2626"), dp(12), dp(1), Color.parseColor("#F87171")));
        bExits.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showExportStocksOrExitsDialog(false);
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp2.setMargins(0, 0, 0, dp(10));
        box.addView(bExits, lp2);

        Button bReceipts = new Button(this);
        bReceipts.setText("🧾 Exporter les tickets de caisse (PDF)");
        bReceipts.setAllCaps(false);
        bReceipts.setTextSize(14);
        bReceipts.setTypeface(null, Typeface.BOLD);
        bReceipts.setTextColor(Color.WHITE);
        bReceipts.setBackground(createRoundedBg(Color.parseColor("#059669"), dp(12), dp(1), Color.parseColor("#34D399")));
        bReceipts.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showExportReceiptsPeriodDialog();
        });
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        box.addView(bReceipts, lp3);

        holder[0] = new AlertDialog.Builder(this)
                .setTitle("📤 Que souhaitez-vous exporter ?")
                .setView(box)
                .setNegativeButton(tr("cancel"), null)
                .create();
        holder[0].show();
    }

    private void showExportStocksOrExitsDialog(boolean isStocksExport) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), dp(10));
        scroll.addView(box);

        TextView stTitle = new TextView(this);
        stTitle.setText("🏢 Sélectionnez le(s) stock(s) :");
        stTitle.setTypeface(null, Typeface.BOLD);
        stTitle.setPadding(0, 0, 0, dp(6));
        box.addView(stTitle);

        final List<CheckBox> stockBoxes = new ArrayList<>();
        for (String st : stocksList) {
            CheckBox cb = new CheckBox(this);
            cb.setText(st);
            cb.setChecked(st.equals(currentStock));
            stockBoxes.add(cb);
            box.addView(cb);
        }

        TextView catTitle = new TextView(this);
        catTitle.setText("🏷️ Sélectionnez la ou les catégorie(s) :");
        catTitle.setTypeface(null, Typeface.BOLD);
        catTitle.setPadding(0, dp(14), 0, dp(6));
        box.addView(catTitle);

        final List<CheckBox> catBoxes = new ArrayList<>();
        for (String cat : categoriesList) {
            CheckBox cb = new CheckBox(this);
            cb.setText(cat);
            cb.setChecked(true);
            catBoxes.add(cb);
            box.addView(cb);
        }

        new AlertDialog.Builder(this)
                .setTitle(isStocksExport ? "📦 Exporter les stocks" : "🔴 Exporter les sorties")
                .setView(scroll)
                .setPositiveButton("📄 Générer PDF", (d, w) -> {
                    pendingExportStocks.clear();
                    for (CheckBox cb : stockBoxes) {
                        if (cb.isChecked()) pendingExportStocks.add(cb.getText().toString());
                    }
                    pendingExportCats.clear();
                    for (CheckBox cb : catBoxes) {
                        if (cb.isChecked()) pendingExportCats.add(cb.getText().toString());
                    }
                    if (pendingExportStocks.isEmpty() || pendingExportCats.isEmpty()) {
                        Toast.makeText(this, "Veuillez cocher au moins un stock et une catégorie.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/pdf");
                    String prefix = isStocksExport ? "Stocks_" : "Sorties_";
                    intent.putExtra(Intent.EXTRA_TITLE, prefix + new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(new Date()) + ".pdf");
                    startActivityForResult(intent, isStocksExport ? REQ_EXPORT_STOCKS_PDF : REQ_EXPORT_EXITS_PDF);
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private void showExportReceiptsPeriodDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));

        TextView label = new TextView(this);
        label.setText("📅 Choisissez la période des tickets de caisse :");
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, 0, 0, dp(10));
        box.addView(label);

        RadioGroup rg = new RadioGroup(this);
        RadioButton rbDay = new RadioButton(this);
        rbDay.setId(101);
        rbDay.setText("Aujourd'hui (Tickets de la journée)");
        rbDay.setChecked(true);
        rg.addView(rbDay);

        RadioButton rbWeek = new RadioButton(this);
        rbWeek.setId(102);
        rbWeek.setText("Cette semaine (7 derniers jours)");
        rg.addView(rbWeek);

        RadioButton rbMonth = new RadioButton(this);
        rbMonth.setId(103);
        rbMonth.setText("Ce mois-ci (30 derniers jours)");
        rg.addView(rg.)]),
        rg.addView(rbMonth);

        box.addView(rg);

        new AlertDialog.Builder(this)
                .setTitle("🧾 Exporter les tickets")
                .setView(box)
                .setPositiveButton("📄 Générer PDF", (d, w) -> {
                    int checkedId = rg.getCheckedRadioButtonId();
                    pendingReceiptsPeriod = (checkedId == 102) ? 1 : ((checkedId == 103) ? 2 : 0);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/pdf");
                    String pName = pendingReceiptsPeriod == 0 ? "Jour_" : (pendingReceiptsPeriod == 1 ? "Semaine_" : "Mois_");
                    intent.putExtra(Intent.EXTRA_TITLE, "Tickets_" + pName + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + ".pdf");
                    startActivityForResult(intent, REQ_EXPORT_RECEIPTS_PERIOD_PDF);
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private int drawCompanyPdfHeader(Canvas canvas, Paint paint, Paint linePaint, String docTitle, int pageWidth) {
        int y = 45;
        Bitmap userLogo = getUserCompanyLogoBitmap();
        String compName = (shopName != null && !shopName.trim().isEmpty()) ? shopName.trim() : "OptiStock";

        if (userLogo != null) {
            canvas.drawBitmap(Bitmap.createScaledBitmap(userLogo, 44, 44, true), 40, y - 10, null);
            paint.setTextSize(17f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawText(compName, 94, y + 8, paint);
            paint.setTextSize(12f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText(docTitle, 94, y + 25, paint);
            y += 44;
        } else {
            paint.setTextSize(18f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawText(compName + " — " + docTitle, 40, y, paint);
            y += 22;
        }

        paint.setTextSize(9.5f);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setColor(Color.rgb(71, 85, 105));
        String dateLine = "Date : " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        if (senderEmail != null && !senderEmail.isEmpty()) dateLine += "  |  " + senderEmail;
        canvas.drawText(dateLine, 40, y, paint);
        y += 12;
        canvas.drawLine(40, y, pageWidth - 40, y, linePaint);
        return y + 18;
    }

    private void generateFilteredStocksPdfToUri(Uri uri) {
        try {
            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);
            linePaint.setStrokeWidth(1f);

            int pageWidth = 595, pageHeight = 842, pageNumber = 1;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int y = drawCompanyPdfHeader(canvas, paint, linePaint, "État des Stocks", pageWidth);

            paint.setColor(Color.BLACK);
            paint.setTextSize(10f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Photo", 40, y, paint);
            canvas.drawText("Produit", 82, y, paint);
            canvas.drawText("Stock", 250, y, paint);
            canvas.drawText("Catégorie", 350, y, paint);
            if (usePrices) canvas.drawText("Prix", 455, y, paint);
            canvas.drawText("Qté", 515, y, paint);
            y += 8; canvas.drawLine(40, y, 555, y, linePaint); y += 14;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            double totalStockValue = 0.0;
            int totalUnits = 0;

            for (Product p : inventory) {
                if (!pendingExportStocks.contains(p.stockName)) continue;
                String pCat = p.category != null ? p.category : tr("uncategorized");
                if (!pendingExportCats.contains(pCat)) continue;

                if (y > 760) {
                    pdf.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 50;
                }
                if (p.imagePath != null && !p.imagePath.isEmpty()) {
                    Bitmap b = loadBitmapFromInternalStorage(p.imagePath);
                    if (b != null) canvas.drawBitmap(Bitmap.createScaledBitmap(b, 28, 28, true), 40, y - 6, null);
                }
                canvas.drawText(p.name.length() > 26 ? p.name.substring(0, 24) + ".." : p.name, 82, y + 10, paint);
                canvas.drawText(p.stockName.length() > 14 ? p.stockName.substring(0, 12) + ".." : p.stockName, 250, y + 10, paint);
                canvas.drawText(pCat.length() > 14 ? pCat.substring(0, 12) + ".." : pCat, 350, y + 10, paint);
                if (usePrices) {
                    canvas.drawText(String.format(Locale.getDefault(), "%.2f €", p.price), 455, y + 10, paint);
                    totalStockValue += p.price * p.quantity;
                }
                totalUnits += p.quantity;
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(String.valueOf(p.quantity), 515, y + 10, paint);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                y += 32; canvas.drawLine(40, y - 6, 555, y - 6, linePaint);
            }

            y += 14;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            String summary = "Total unités : " + totalUnits;
            if (usePrices) summary += String.format(Locale.getDefault(), "   |   Valeur totale : %.2f €", totalStockValue);
            canvas.drawText(summary, 40, y, paint);

            pdf.finishPage(page);
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) { pdf.writeTo(os); os.close(); }
            pdf.close();
            Toast.makeText(this, tr("pdf_ok"), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur PDF : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void generateFilteredExitsPdfToUri(Uri uri) {
        try {
            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);
            linePaint.setStrokeWidth(1f);

            int pageWidth = 595, pageHeight = 842, pageNumber = 1;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int y = drawCompanyPdfHeader(canvas, paint, linePaint, "Relevé des Sorties de Stock", pageWidth);

            paint.setColor(Color.BLACK);
            paint.setTextSize(10f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Date", 40, y, paint);
            canvas.drawText("Produit", 130, y, paint);
            canvas.drawText("Stock", 295, y, paint);
            canvas.drawText("Catégorie", 390, y, paint);
            canvas.drawText("Qté", 475, y, paint);
            canvas.drawText("Total", 515, y, paint);
            y += 8; canvas.drawLine(40, y, 555, y, linePaint); y += 14;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault());
            int totalOutUnits = 0;
            double totalOutVal = 0.0;

            for (Movement m : movementsHistory) {
                if (!"OUT".equals(m.type)) continue;
                if (!pendingExportStocks.contains(m.stockName)) continue;
                String mCat = (m.category != null && !m.category.isEmpty()) ? m.category : findCategoryForBarcode(m.barcode);
                if (!pendingExportCats.contains(mCat)) continue;

                if (y > 760) {
                    pdf.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 50;
                }
                canvas.drawText(sdf.format(new Date(m.timestamp)), 40, y, paint);
                String pName = m.productName != null ? m.productName : m.barcode;
                canvas.drawText(pName.length() > 24 ? pName.substring(0, 22) + ".." : pName, 130, y, paint);
                canvas.drawText(m.stockName.length() > 14 ? m.stockName.substring(0, 12) + ".." : m.stockName, 295, y, paint);
                canvas.drawText(mCat.length() > 14 ? mCat.substring(0, 12) + ".." : mCat, 390, y, paint);
                canvas.drawText("-" + m.quantity, 475, y, paint);
                double lineVal = m.quantity * m.unitPrice;
                canvas.drawText(String.format(Locale.getDefault(), "%.2f €", lineVal), 515, y, paint);

                totalOutUnits += m.quantity;
                totalOutVal += lineVal;
                y += 20; canvas.drawLine(40, y - 6, 555, y - 6, linePaint);
            }

            y += 14;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(String.format(Locale.getDefault(), "Total articles sortis : %d   |   Montant total des sorties : %.2f €", totalOutUnits, totalOutVal), 40, y, paint);

            pdf.finishPage(page);
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) { pdf.writeTo(os); os.close(); }
            pdf.close();
            Toast.makeText(this, tr("pdf_ok"), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur PDF Sorties : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void generateReceiptsPeriodPdfToUri(Uri uri) {
        try {
            long now = System.currentTimeMillis();
            long cutoffMs;
            String periodLabel;
            if (pendingReceiptsPeriod == 0) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cutoffMs = cal.getTimeInMillis();
                periodLabel = "Tickets de la journée";
            } else if (pendingReceiptsPeriod == 1) {
                cutoffMs = now - (7L * 24L * 60L * 60L * 1000L);
                periodLabel = "Tickets de la semaine (7 derniers jours)";
            } else {
                cutoffMs = now - (30L * 24L * 60L * 60L * 1000L);
                periodLabel = "Tickets du mois (30 derniers jours)";
            }

            List<SavedReceipt> listToExport = new ArrayList<>();
            if (!currentReceipt.isEmpty()) {
                SavedReceipt live = createSavedReceiptObject(0.0, true);
                listToExport.add(live);
            }
            for (SavedReceipt sr : savedReceiptsHistory) {
                if (sr.timestamp >= cutoffMs) {
                    listToExport.add(sr);
                }
            }

            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);
            linePaint.setStrokeWidth(1f);

            int pageWidth = 595, pageHeight = 842, pageNumber = 1;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int y = drawCompanyPdfHeader(canvas, paint, linePaint, periodLabel, pageWidth);
            paint.setColor(Color.BLACK);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            double grandPeriodTotal = 0.0;

            if (listToExport.isEmpty()) {
                paint.setTextSize(12f);
                canvas.drawText("Aucun ticket de caisse enregistré sur cette période.", 40, y + 20, paint);
            } else {
                for (int idx = 0; idx < listToExport.size(); idx++) {
                    SavedReceipt sr = listToExport.get(idx);
                    if (y > 700) {
                        pdf.finishPage(page);
                        pageNumber++;
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                        page = pdf.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = 50;
                    }
                    paint.setTextSize(11f);
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    canvas.drawText("🧾 Ticket #" + (listToExport.size() - idx) + " — " + sdf.format(new Date(sr.timestamp)) + " (" + sr.stockName + ")", 40, y, paint);
                    y += 16;

                    paint.setTextSize(10f);
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                    for (ReceiptItem ri : sr.items) {
                        if (y > 770) {
                            pdf.finishPage(page);
                            pageNumber++;
                            pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                            page = pdf.startPage(pageInfo);
                            canvas = page.getCanvas();
                            y = 50;
                        }
                        canvas.drawText("• " + ri.name, 55, y, paint);
                        canvas.drawText(String.format(Locale.getDefault(), "%d x %.2f € = %.2f €", ri.quantity, ri.unitPrice, ri.quantity * ri.unitPrice), 360, y, paint);
                        y += 15;
                    }
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    canvas.drawText(String.format(Locale.getDefault(), "Total Ticket : %.2f €", sr.totalAmount), 360, y, paint);
                    grandPeriodTotal += sr.totalAmount;
                    y += 12;
                    canvas.drawLine(40, y, 555, y, linePaint);
                    y += 18;
                }

                y += 10;
                paint.setTextSize(13f);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(String.format(Locale.getDefault(), "CHIFFRE D'AFFAIRES TOTAL (%d tickets) : %.2f €", listToExport.size(), grandPeriodTotal), 40, y, paint);
            }

            pdf.finishPage(page);
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) { pdf.writeTo(os); os.close(); }
            pdf.close();
            Toast.makeText(this, tr("pdf_ok"), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur PDF Tickets : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String findCategoryForBarcode(String barcode) {
        if (barcode != null) {
            for (Product p : inventory) {
                if (barcode.equalsIgnoreCase(p.barcode) && p.category != null) return p.category;
            }
        }
        return categoriesList.isEmpty() ? tr("uncategorized") : categoriesList.get(0);
    }

    private SavedReceipt createSavedReceiptObject(double discountVal, boolean isPercent) {
        SavedReceipt sr = new SavedReceipt();
        sr.timestamp = System.currentTimeMillis();
        sr.stockName = currentStock;
        double sub = 0.0;
        for (ReceiptItem item : currentReceipt) {
            ReceiptItem copy = new ReceiptItem();
            copy.barcode = item.barcode;
            copy.name = item.name;
            copy.quantity = item.quantity;
            copy.unitPrice = item.unitPrice;
            sr.items.add(copy);
            sub += item.quantity * item.unitPrice;
        }
        double disc = isPercent ? sub * (discountVal / 100.0) : discountVal;
        disc = Math.max(0.0, Math.min(sub, disc));
        sr.discountAmount = disc;
        sr.totalAmount = sub - disc;
        sr.receiptText = buildReceiptText(discountVal, isPercent);
        return sr;
    }

    private void archiveCurrentReceiptIfNotEmpty(double discountVal, boolean isPercent) {
        if (currentReceipt.isEmpty()) return;
        SavedReceipt sr = createSavedReceiptObject(discountVal, isPercent);
        savedReceiptsHistory.add(0, sr);
        while (savedReceiptsHistory.size() > 300) {
            savedReceiptsHistory.remove(savedReceiptsHistory.size() - 1);
        }
    }

    // ==================== OPTIONS & SMTP ====================

    private void showOptionsDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));
        scroll.addView(box);

        TextView langLabel = new TextView(this);
        langLabel.setText(tr("opt_lang"));
        langLabel.setTypeface(null, Typeface.BOLD);
        box.addView(langLabel);

        Spinner langSpinner = new Spinner(this);
        String[] langLabels = {tr("lang_sys"), "🇫🇷 Français", "🇬🇧 English", "🇪🇸 Español"};
        String[] langCodes = {"SYS", "FR", "EN", "ES"};
        langSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, langLabels));
        int curIdx = 0;
        for (int i = 0; i < langCodes.length; i++) if (langCodes[i].equals(langPref)) curIdx = i;
        langSpinner.setSelection(curIdx);
        box.addView(langSpinner);

        Switch darkSwitch = new Switch(this);
        darkSwitch.setText(tr("opt_dark"));
        darkSwitch.setChecked(isDarkMode);
        darkSwitch.setPadding(0, dp(14), 0, dp(8));
        box.addView(darkSwitch);

        Switch scannerSwitch = new Switch(this);
        scannerSwitch.setText(tr("opt_scanner"));
        scannerSwitch.setChecked(useScanner);
        scannerSwitch.setPadding(0, dp(6), 0, dp(8));
        box.addView(scannerSwitch);

        Switch priceSwitch = new Switch(this);
        priceSwitch.setText(tr("opt_prices"));
        priceSwitch.setChecked(usePrices);
        priceSwitch.setPadding(0, dp(6), 0, dp(12));
        box.addView(priceSwitch);

        TextView threshTv = new TextView(this);
        threshTv.setText(tr("opt_low_thresh"));
        threshTv.setTypeface(null, Typeface.BOLD);
        box.addView(threshTv);

        EditText threshInput = new EditText(this);
        threshInput.setText(String.valueOf(lowStockThreshold));
        threshInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        threshInput.setSingleLine(true);
        box.addView(threshInput);

        TextView shopTv = new TextView(this);
        shopTv.setText(tr("opt_shop_name"));
        shopTv.setTypeface(null, Typeface.BOLD);
        shopTv.setPadding(0, dp(10), 0, 0);
        box.addView(shopTv);

        EditText shopInput = new EditText(this);
        shopInput.setText(shopName);
        shopInput.setHint("Ex: Mon Entreprise");
        shopInput.setSingleLine(true);
        box.addView(shopInput);

        TextView logoSecTv = new TextView(this);
        logoSecTv.setText(tr("opt_custom_logo"));
        logoSecTv.setTypeface(null, Typeface.BOLD);
        logoSecTv.setPadding(0, dp(12), 0, dp(6));
        box.addView(logoSecTv);

        LinearLayout logoPickRow = new LinearLayout(this);
        logoPickRow.setOrientation(LinearLayout.HORIZONTAL);
        logoPickRow.setGravity(Gravity.CENTER_VERTICAL);

        tempCustomLogoPath = customLogoPath;
        optionsLogoPreview = new ImageView(this);
        optionsLogoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        optionsLogoPreview.setBackground(createRoundedBg(Color.parseColor("#1E293B"), dp(10), dp(1), Color.parseColor("#38BDF8")));
        if (!tempCustomLogoPath.isEmpty()) {
            Bitmap b = loadBitmapFromInternalStorage(tempCustomLogoPath);
            if (b != null) optionsLogoPreview.setImageBitmap(b);
            else optionsLogoPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            optionsLogoPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        LinearLayout.LayoutParams oImgLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        oImgLp.setMargins(0, 0, dp(12), 0);
        logoPickRow.addView(optionsLogoPreview, oImgLp);

        Button btnPickLogo = new Button(this);
        btnPickLogo.setText(tr("pick_logo_btn"));
        btnPickLogo.setAllCaps(false);
        btnPickLogo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQ_PICK_CUSTOM_LOGO);
        });
        logoPickRow.addView(btnPickLogo, new LinearLayout.LayoutParams(0, dp(42), 1f));
        box.addView(logoPickRow);

        // Section SMTP complète (comme sur FlashQuote)
        TextView smtpSecTv = new TextView(this);
        smtpSecTv.setText("Configuration d'envoi E-mail direct (SMTP)");
        smtpSecTv.setTypeface(null, Typeface.BOLD);
        smtpSecTv.setTextColor(Color.parseColor("#38BDF8"));
        smtpSecTv.setPadding(0, dp(16), 0, dp(6));
        box.addView(smtpSecTv);

        TextView hostTv = new TextView(this);
        hostTv.setText(tr("opt_smtp_host"));
        box.addView(hostTv);
        EditText hostInput = new EditText(this);
        hostInput.setText(smtpHost);
        hostInput.setSingleLine(true);
        box.addView(hostInput);

        TextView portTv = new TextView(this);
        portTv.setText(tr("opt_smtp_port"));
        box.addView(portTv);
        EditText portInput = new EditText(this);
        portInput.setText(smtpPort);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setSingleLine(true);
        box.addView(portInput);

        TextView senderTv = new TextView(this);
        senderTv.setText(tr("opt_sender_email"));
        box.addView(senderTv);
        EditText senderInput = new EditText(this);
        senderInput.setText(senderEmail);
        senderInput.setHint("exemple@gmail.com");
        senderInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        senderInput.setSingleLine(true);
        box.addView(senderInput);

        TextView passTv = new TextView(this);
        passTv.setText(tr("opt_sender_pass"));
        box.addView(passTv);
        EditText passInput = new EditText(this);
        passInput.setText(senderAppPass);
        passInput.setHint("xxxx xxxx xxxx xxxx");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setSingleLine(true);
        box.addView(passInput);

        TextView backupTv = new TextView(this);
        backupTv.setText(tr("backup_section"));
        backupTv.setTypeface(null, Typeface.BOLD);
        backupTv.setPadding(0, dp(16), 0, dp(6));
        box.addView(backupTv);

        Button btnExportBackup = new Button(this);
        btnExportBackup.setText(tr("backup_export_btn"));
        btnExportBackup.setAllCaps(false);
        btnExportBackup.setOnClickListener(v -> startBackupExport());
        box.addView(btnExportBackup);

        Button btnImportBackup = new Button(this);
        btnImportBackup.setText(tr("backup_import_btn"));
        btnImportBackup.setAllCaps(false);
        btnImportBackup.setOnClickListener(v -> startBackupImport());
        box.addView(btnImportBackup);

        new AlertDialog.Builder(this)
                .setTitle(tr("options_title"))
                .setView(scroll)
                .setPositiveButton(tr("save"), (d, w) -> {
                    langPref = langCodes[langSpinner.getSelectedItemPosition()];
                    isDarkMode = darkSwitch.isChecked();
                    useScanner = scannerSwitch.isChecked();
                    usePrices = priceSwitch.isChecked();
                    try {
                        lowStockThreshold = Math.max(0, Integer.parseInt(threshInput.getText().toString().trim()));
                    } catch (Exception ignored) {}
                    shopName = shopInput.getText().toString().trim();
                    customLogoPath = tempCustomLogoPath;
                    smtpHost = hostInput.getText().toString().trim();
                    if (smtpHost.isEmpty()) smtpHost = "smtp.gmail.com";
                    smtpPort = portInput.getText().toString().trim();
                    if (smtpPort.isEmpty()) smtpPort = "587";
                    senderEmail = senderInput.getText().toString().trim();
                    senderAppPass = passInput.getText().toString().trim();
                    prefs.edit()
                            .putString("langPref", langPref)
                            .putBoolean("darkMode", isDarkMode)
                            .putBoolean("useScanner", useScanner)
                            .putBoolean("usePrices", usePrices)
                            .putInt("lowStockThreshold", lowStockThreshold)
                            .putString("shopName", shopName)
                            .putString("customLogoPath", customLogoPath)
                            .putString("smtpHost", smtpHost)
                            .putString("smtpPort", smtpPort)
                            .putString("senderEmail", senderEmail)
                            .putString("senderAppPass", senderAppPass)
                            .apply();
                    applyThemeAndLang();
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private void startBackupExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "OptiStock_Sauvegarde_" + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + ".json");
        startActivityForResult(intent, REQ_EXPORT_BACKUP);
    }

    private void generateBackupToUri(Uri uri) {
        try {
            saveData();
            JSONObject backup = new JSONObject();
            backup.put("version", 4);
            backup.put("exportedAt", System.currentTimeMillis());
            backup.put("inventory_json", prefs.getString("inventory_json", "[]"));
            backup.put("stocks_json", prefs.getString("stocks_json", "[]"));
            backup.put("cats_json", prefs.getString("cats_json", "[]"));
            backup.put("clients_json", prefs.getString("clients_json", "[]"));
            backup.put("history_json", prefs.getString("history_json", "[]"));
            backup.put("saved_receipts_json", prefs.getString("saved_receipts_json", "[]"));
            backup.put("currentStock", currentStock);
            backup.put("shopName", shopName);
            backup.put("customLogoPath", customLogoPath);
            backup.put("smtpHost", smtpHost);
            backup.put("smtpPort", smtpPort);
            backup.put("senderEmail", senderEmail);
            backup.put("lowStockThreshold", lowStockThreshold);

            JSONObject photosMap = new JSONObject();
            for (Product p : inventory) {
                if (p.imagePath != null && !p.imagePath.isEmpty()) {
                    String b64 = fileToBase64(p.imagePath);
                    if (!b64.isEmpty()) photosMap.put(p.imagePath, b64);
                }
            }
            if (customLogoPath != null && !customLogoPath.isEmpty()) {
                String b64 = fileToBase64(customLogoPath);
                if (!b64.isEmpty()) photosMap.put(customLogoPath, b64);
            }
            backup.put("photos_map", photosMap);

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(backup.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
            }
            Toast.makeText(this, tr("backup_ok"), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur sauvegarde : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startBackupImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_BACKUP);
    }

    private void restoreBackupFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();

            JSONObject backup = new JSONObject(baos.toString("UTF-8"));
            if (backup.has("photos_map")) {
                JSONObject photosMap = backup.getJSONObject("photos_map");
                Iterator<String> keys = photosMap.keys();
                while (keys.hasNext()) {
                    String fileName = keys.next();
                    String b64 = photosMap.optString(fileName, "");
                    if (!b64.isEmpty()) base64ToFile(b64, fileName);
                }
            }

            SharedPreferences.Editor ed = prefs.edit();
            if (backup.has("inventory_json")) ed.putString("inventory_json", backup.getString("inventory_json"));
            if (backup.has("stocks_json")) ed.putString("stocks_json", backup.getString("stocks_json"));
            if (backup.has("cats_json")) ed.putString("cats_json", backup.getString("cats_json"));
            if (backup.has("clients_json")) ed.putString("clients_json", backup.getString("clients_json"));
            if (backup.has("history_json")) ed.putString("history_json", backup.getString("history_json"));
            if (backup.has("saved_receipts_json")) ed.putString("saved_receipts_json", backup.getString("saved_receipts_json"));
            if (backup.has("currentStock")) ed.putString("currentStock", backup.getString("currentStock"));
            if (backup.has("shopName")) {
                shopName = backup.getString("shopName");
                ed.putString("shopName", shopName);
            }
            if (backup.has("customLogoPath")) {
                customLogoPath = backup.getString("customLogoPath");
                ed.putString("customLogoPath", customLogoPath);
            }
            if (backup.has("smtpHost")) {
                smtpHost = backup.getString("smtpHost");
                ed.putString("smtpHost", smtpHost);
            }
            if (backup.has("smtpPort")) {
                smtpPort = backup.getString("smtpPort");
                ed.putString("smtpPort", smtpPort);
            }
            if (backup.has("senderEmail")) {
                senderEmail = backup.getString("senderEmail");
                ed.putString("senderEmail", senderEmail);
            }
            if (backup.has("lowStockThreshold")) {
                lowStockThreshold = backup.getInt("lowStockThreshold");
                ed.putInt("lowStockThreshold", lowStockThreshold);
            }
            ed.apply();

            bitmapCache.clear();
            loadData();
            applyThemeAndLang();
            Toast.makeText(this, tr("restore_ok"), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur restauration : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void dispatchReceiptEmail(String toEmail, String subject, String bodyText) {
        if (!senderEmail.isEmpty() && !senderAppPass.isEmpty()) {
            AlertDialog progress = new AlertDialog.Builder(this)
                    .setMessage("📤 Envoi automatique du ticket à " + toEmail + "...")
                    .setCancelable(false)
                    .create();
            progress.show();

            executor.execute(() -> {
                boolean ok = sendSmtpSslEmail(smtpHost, smtpPort, senderEmail.trim(), senderAppPass.trim(), toEmail, subject, bodyText);
                mainHandler.post(() -> {
                    progress.dismiss();
                    if (ok) {
                        Toast.makeText(MainActivity.this, tr("email_sent_ok"), Toast.LENGTH_LONG).show();
                    } else {
                        launchEmailClientFallback(toEmail, subject, bodyText);
                    }
                });
            });
        } else {
            launchEmailClientFallback(toEmail, subject, bodyText);
        }
    }

    private boolean sendSmtpSslEmail(String host, String portStr, String fromUser, String appPass, String toMail, String subject, String body) {
        try {
            int port = 465;
            try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
            socket.setSoTimeout(8000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            reader.readLine();
            writer.println("EHLO optistock");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("250 ")) break;
            }
            writer.println("AUTH LOGIN");
            reader.readLine();
            writer.println(Base64.encodeToString(fromUser.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            reader.readLine();
            writer.println(Base64.encodeToString(appPass.replace(" ", "").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            String authResp = reader.readLine();
            if (authResp == null || !authResp.startsWith("235")) {
                socket.close();
                return false;
            }

            writer.println("MAIL FROM:<" + fromUser + ">");
            reader.readLine();
            writer.println("RCPT TO:<" + toMail + ">");
            reader.readLine();
            writer.println("DATA");
            reader.readLine();

            writer.println("From: " + (shopName.isEmpty() ? "OptiStock" : shopName) + " <" + fromUser + ">");
            writer.println("To: <" + toMail + ">");
            writer.println("Subject: " + subject);
            writer.println("Content-Type: text/plain; charset=UTF-8");
            writer.println();
            writer.println(body);
            writer.println(".");
            reader.readLine();
            writer.println("QUIT");
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void presentationPdfToUriCommon(Uri uri, boolean isStocks) {
        // Ignoré ici car géré par les fonctions dédiées
    }
}
