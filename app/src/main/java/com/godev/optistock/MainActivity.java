package com.godev.optistock;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    static class Product {
        String barcode, name, imageBase64, stockName, category;
        int quantity;
        double price;
    }

    private final List<Product> inventory = new ArrayList<>();
    private final List<Product> filteredInventory = new ArrayList<>();
    private final List<String> stocksList = new ArrayList<>();
    private final List<String> categoriesList = new ArrayList<>();

    private String currentStock = "";
    private String selectedCategoryFilter = "ALL";
    private InventoryAdapter adapter;
    private SharedPreferences prefs;

    private boolean isDarkMode = true;
    private boolean usePrices = false;
    private String langPref = "SYS";
    private String searchQuery = "";
    private double lastExitTotal = 0.0;
    private double sessionExitTotal = 0.0;

    private static final int REQ_CREATE_PDF = 1001;
    private static final int REQ_TAKE_PHOTO = 1002;
    private ImageView currentPhotoPreview;
    private String tempPhotoBase64 = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout rootLayout, headerLayout, subHeaderLayout, statsCard, exitTotalBanner;
    private TextView titleText, statsRefsText, statsUnitsText, emptyText, exitTotalText;
    private EditText searchInput;
    private Button btnEntry, btnExit, btnPdf, btnOptions, btnStockSelector, btnCatFilter;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("OptiStockPrefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("darkMode", true);
        usePrices = prefs.getBoolean("usePrices", false);
        langPref = prefs.getString("langPref", "SYS");
        loadData();
        showSplashScreen();
    }

    private void showSplashScreen() {
        LinearLayout splashRoot = new LinearLayout(this);
        splashRoot.setOrientation(LinearLayout.VERTICAL);
        splashRoot.setGravity(Gravity.CENTER_HORIZONTAL);
        splashRoot.setBackgroundColor(Color.parseColor("#040B1C"));
        splashRoot.setPadding(dp(24), dp(36), dp(24), dp(24));
        splashRoot.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.4f));

        ImageView logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int resId = getResources().getIdentifier("splash_logo", "drawable", getPackageName());
        if (resId != 0) {
            logoView.setImageResource(resId);
        } else {
            logoView.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(280), dp(280));
        logoLp.gravity = Gravity.CENTER;
        splashRoot.addView(logoView, logoLp);

        BarcodeLaserView laserView = new BarcodeLaserView(this);
        LinearLayout.LayoutParams laserLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(85));
        laserLp.setMargins(dp(16), dp(24), dp(16), dp(16));
        splashRoot.addView(laserView, laserLp);

        splashRoot.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.8f));

        TextView footerTv = new TextView(this);
        footerTv.setText("Développé par GoDev");
        footerTv.setTextColor(Color.parseColor("#60A5FA"));
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
            buildMainUI();
            applyThemeAndLang();
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
        dict.put("refs", new String[]{"Références", "Items", "Referencias"});
        dict.put("units", new String[]{"Total Unités", "Total Units", "Unidades Totales"});
        dict.put("btn_in", new String[]{"+ ENTRÉE", "+ STOCK IN", "+ ENTRADA"});
        dict.put("btn_out", new String[]{"- SORTIE", "- STOCK OUT", "- SALIDA"});
        dict.put("search", new String[]{"Rechercher un produit ou code...", "Search product or barcode...", "Buscar producto o código..."});
        dict.put("empty", new String[]{"Aucun produit dans ce stock.\nAppuyez sur + ENTRÉE pour scanner.", "No products in this stock.\nTap + STOCK IN to scan.", "Sin productos en este stock.\nToque + ENTRADA para escanear."});
        dict.put("fetching", new String[]{"Recherche du produit sur Internet...", "Searching product online...", "Buscando producto en línea..."});
        dict.put("entry_title", new String[]{"Entrée de stock", "Stock Entry", "Entrada de stock"});
        dict.put("exit_title", new String[]{"Sortie de stock", "Stock Exit", "Salida de stock"});
        dict.put("prod_name", new String[]{"Nom du produit", "Product Name", "Nombre del producto"});
        dict.put("qty_label", new String[]{"Quantité (Lot) :", "Quantity (Batch):", "Cantidad (Lote):"});
        dict.put("price_label", new String[]{"Prix unitaire (€ / $) :", "Unit Price (€ / $):", "Precio unitario (€ / $):"});
        dict.put("category_label", new String[]{"Catégorie :", "Category:", "Categoría:"});
        dict.put("current_stock", new String[]{"En stock : ", "In stock: ", "En stock: "});
        dict.put("save", new String[]{"VALIDER", "SAVE", "GUARDAR"});
        dict.put("cancel", new String[]{"Annuler", "Cancel", "Cancelar"});
        dict.put("delete", new String[]{"Supprimer", "Delete", "Eliminar"});
        dict.put("manual_code", new String[]{"Saisie manuelle du code", "Manual Barcode", "Código manual"});
        dict.put("photo_btn", new String[]{"📷 Photo", "📷 Photo", "📷 Foto"});
        dict.put("pdf_btn", new String[]{"📄 PDF", "📄 PDF", "📄 PDF"});
        dict.put("pdf_ok", new String[]{"PDF enregistré avec succès !", "PDF saved successfully!", "¡PDF guardado con éxito!"});
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
        dict.put("opt_dark", new String[]{"Mode Sombre", "Dark Mode", "Modo Oscuro"});
        dict.put("opt_prices", new String[]{"Activer la gestion des prix", "Enable product prices", "Activar precios de productos"});
        dict.put("exit_total_live", new String[]{"Prix total de cette sortie : ", "Total price for this exit: ", "Precio total de esta salida: "});
        dict.put("exit_banner", new String[]{"💰 Dernière sortie : %1$.2f €  |  Total : %2$.2f €", "💰 Last exit: %1$.2f  |  Total: %2$.2f", "💰 Última salida: %1$.2f €  |  Total: %2$.2f €"});

        String eff = langPref.equals("SYS") ? Locale.getDefault().getLanguage().toLowerCase() : langPref;
        int idx = eff.startsWith("en") ? 1 : (eff.startsWith("es") ? 2 : 0);
        return dict.containsKey(key) ? dict.get(key)[idx] : key;
    }

    private void buildMainUI() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(14), dp(14), dp(14), dp(10));

        headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        titleText = new TextView(this);
        titleText.setTextSize(22);
        titleText.setTypeface(null, Typeface.BOLD);
        headerLayout.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnPdf = new Button(this);
        btnPdf.setTextSize(12);
        btnPdf.setAllCaps(false);
        btnPdf.setTextColor(Color.WHITE);
        btnPdf.setBackground(createRoundedBg(Color.parseColor("#3B82F6"), dp(10)));
        btnPdf.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams pdfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        pdfLp.setMargins(0, 0, dp(8), 0);
        btnPdf.setOnClickListener(v -> startPdfExport());
        headerLayout.addView(btnPdf, pdfLp);

        btnOptions = new Button(this);
        btnOptions.setTextSize(12);
        btnOptions.setAllCaps(false);
        btnOptions.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnOptions.setOnClickListener(v -> showOptionsDialog());
        headerLayout.addView(btnOptions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        rootLayout.addView(headerLayout);

        subHeaderLayout = new LinearLayout(this);
        subHeaderLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(10), 0, dp(10));

        btnStockSelector = new Button(this);
        btnStockSelector.setAllCaps(false);
        btnStockSelector.setTextSize(13);
        btnStockSelector.setTypeface(null, Typeface.BOLD);
        btnStockSelector.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnStockSelector.setOnClickListener(v -> showStockPopupMenu(v));
        LinearLayout.LayoutParams stBtnLp = new LinearLayout.LayoutParams(0, dp(42), 1.1f);
        stBtnLp.setMargins(0, 0, dp(6), 0);
        subHeaderLayout.addView(btnStockSelector, stBtnLp);

        btnCatFilter = new Button(this);
        btnCatFilter.setAllCaps(false);
        btnCatFilter.setTextSize(12);
        btnCatFilter.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnCatFilter.setOnClickListener(v -> showCategoryFilterPopup(v));
        LinearLayout.LayoutParams catBtnLp = new LinearLayout.LayoutParams(0, dp(42), 0.9f);
        catBtnLp.setMargins(dp(6), 0, 0, 0);
        subHeaderLayout.addView(btnCatFilter, catBtnLp);

        rootLayout.addView(subHeaderLayout, subLp);

        statsCard = new LinearLayout(this);
        statsCard.setOrientation(LinearLayout.HORIZONTAL);
        statsCard.setGravity(Gravity.CENTER_VERTICAL);
        statsCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.setMargins(0, 0, 0, dp(10));

        statsRefsText = new TextView(this);
        statsRefsText.setTextSize(14);
        statsRefsText.setTypeface(null, Typeface.BOLD);
        statsCard.addView(statsRefsText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statsUnitsText = new TextView(this);
        statsUnitsText.setTextSize(14);
        statsUnitsText.setTypeface(null, Typeface.BOLD);
        statsUnitsText.setGravity(Gravity.END);
        statsCard.addView(statsUnitsText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rootLayout.addView(statsCard, statsParams);

        LinearLayout actionsLayout = new LinearLayout(this);
        actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actParams.setMargins(0, 0, 0, dp(10));

        btnEntry = new Button(this);
        btnEntry.setTextSize(15);
        btnEntry.setTypeface(null, Typeface.BOLD);
        btnEntry.setTextColor(Color.WHITE);
        btnEntry.setBackground(createRoundedBg(Color.parseColor("#10B981"), dp(14)));
        btnEntry.setPadding(0, dp(14), 0, dp(14));
        btnEntry.setOnClickListener(v -> startBarcodeScan(true));
        LinearLayout.LayoutParams btnInParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnInParams.setMargins(0, 0, dp(6), 0);
        actionsLayout.addView(btnEntry, btnInParams);

        btnExit = new Button(this);
        btnExit.setTextSize(15);
        btnExit.setTypeface(null, Typeface.BOLD);
        btnExit.setTextColor(Color.WHITE);
        btnExit.setBackground(createRoundedBg(Color.parseColor("#EF4444"), dp(14)));
        btnExit.setPadding(0, dp(14), 0, dp(14));
        btnExit.setOnClickListener(v -> startBarcodeScan(false));
        LinearLayout.LayoutParams btnOutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnOutParams.setMargins(dp(6), 0, 0, 0);
        actionsLayout.addView(btnExit, btnOutParams);

        rootLayout.addView(actionsLayout, actParams);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(14), dp(10), dp(14), dp(10));
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

        emptyText = new TextView(this);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(15);
        emptyText.setPadding(dp(20), dp(40), dp(20), dp(40));
        rootLayout.addView(emptyText);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(dp(8));
        adapter = new InventoryAdapter();
        listView.setAdapter(adapter);
        rootLayout.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        exitTotalBanner = new LinearLayout(this);
        exitTotalBanner.setOrientation(LinearLayout.HORIZONTAL);
        exitTotalBanner.setGravity(Gravity.CENTER_VERTICAL);
        exitTotalBanner.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerLp.setMargins(0, dp(6), 0, 0);

        exitTotalText = new TextView(this);
        exitTotalText.setTextColor(Color.WHITE);
        exitTotalText.setTextSize(13);
        exitTotalText.setTypeface(null, Typeface.BOLD);
        exitTotalBanner.addView(exitTotalText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnResetTotal = new Button(this);
        btnResetTotal.setText("↺");
        btnResetTotal.setTextColor(Color.WHITE);
        btnResetTotal.setBackground(createRoundedBg(Color.parseColor("#991B1B"), dp(8)));
        btnResetTotal.setOnClickListener(v -> {
            lastExitTotal = 0.0;
            sessionExitTotal = 0.0;
            updateStats();
        });
        exitTotalBanner.addView(btnResetTotal, new LinearLayout.LayoutParams(dp(36), dp(32)));

        rootLayout.addView(exitTotalBanner, bannerLp);
        setContentView(rootLayout);
    }

    private void showStockPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        for (int i = 0; i < stocksList.size(); i++) {
            String st = stocksList.get(i);
            popup.getMenu().add(0, i, i, st.equals(currentStock) ? "✅ " + st : "📦 " + st);
        }
        popup.getMenu().add(1, 998, 998, tr("new_stock_opt"));
        if (stocksList.size() > 1) popup.getMenu().add(1, 999, 999, tr("del_stock_opt"));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 998) showCreateStockDialog();
            else if (id == 999) deleteCurrentStock();
            else if (id >= 0 && id < stocksList.size()) {
                currentStock = stocksList.get(id);
                prefs.edit().putString("currentStock", currentStock).apply();
                applyThemeAndLang();
            }
            return true;
        });
        popup.show();
    }

    private void showCreateStockDialog() {
        EditText input = new EditText(this);
        input.setHint(tr("new_stock_hint"));
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle(tr("new_stock_title"))
                .setView(input)
                .setPositiveButton(tr("save"), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (!stocksList.contains(name)) stocksList.add(name);
                        currentStock = name;
                        saveData();
                        applyThemeAndLang();
                    }
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private void deleteCurrentStock() {
        if (stocksList.size() <= 1) return;
        for (int i = inventory.size() - 1; i >= 0; i--) {
            if (currentStock.equals(inventory.get(i).stockName)) {
                inventory.remove(i);
            }
        }
        stocksList.remove(currentStock);
        currentStock = stocksList.get(0);
        saveData();
        applyThemeAndLang();
    }

    private void showCategoryFilterPopup(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, -1, 0, "🏷️ " + tr("all_cats"));
        for (int i = 0; i < categoriesList.size(); i++) {
            popup.getMenu().add(0, i, i + 1, "📁 " + categoriesList.get(i));
        }
        popup.getMenu().add(1, 999, 999, tr("new_cat_opt"));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == -1) selectedCategoryFilter = "ALL";
            else if (id == 999) showCreateCategoryDialog();
            else if (id >= 0 && id < categoriesList.size()) selectedCategoryFilter = categoriesList.get(id);
            applyThemeAndLang();
            return true;
        });
        popup.show();
    }

    private void showCreateCategoryDialog() {
        EditText input = new EditText(this);
        input.setHint(tr("new_cat_hint"));
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("➕ " + tr("category_label"))
                .setView(input)
                .setPositiveButton(tr("save"), (d, w) -> {
                    String cat = input.getText().toString().trim();
                    if (!cat.isEmpty() && !categoriesList.contains(cat)) {
                        categoriesList.add(cat);
                        selectedCategoryFilter = cat;
                        saveData();
                        applyThemeAndLang();
                    }
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private void showOptionsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));

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
        darkSwitch.setPadding(0, dp(16), 0, dp(12));
        box.addView(darkSwitch);

        Switch priceSwitch = new Switch(this);
        priceSwitch.setText(tr("opt_prices"));
        priceSwitch.setChecked(usePrices);
        priceSwitch.setPadding(0, dp(8), 0, dp(16));
        box.addView(priceSwitch);

        new AlertDialog.Builder(this)
                .setTitle(tr("options_title"))
                .setView(box)
                .setPositiveButton(tr("save"), (d, w) -> {
                    langPref = langCodes[langSpinner.getSelectedItemPosition()];
                    isDarkMode = darkSwitch.isChecked();
                    usePrices = priceSwitch.isChecked();
                    prefs.edit().putString("langPref", langPref).putBoolean("darkMode", isDarkMode).putBoolean("usePrices", usePrices).apply();
                    applyThemeAndLang();
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private void applyThemeAndLang() {
        int bgColor = isDarkMode ? Color.parseColor("#0F172A") : Color.parseColor("#F1F5F9");
        int cardColor = isDarkMode ? Color.parseColor("#1E293B") : Color.parseColor("#FFFFFF");
        int accentCard = isDarkMode ? Color.parseColor("#1E3A8A") : Color.parseColor("#DBEAFE");
        int textColor = isDarkMode ? Color.parseColor("#F8FAFC") : Color.parseColor("#0F172A");
        int subTextColor = isDarkMode ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B");

        rootLayout.setBackgroundColor(bgColor);
        titleText.setText("📦 " + tr("app_title"));
        titleText.setTextColor(textColor);
        btnPdf.setText(tr("pdf_btn"));
        btnOptions.setText(tr("options_title"));
        btnOptions.setTextColor(textColor);
        btnOptions.setBackground(createRoundedBg(cardColor, dp(10)));
        btnStockSelector.setText("🏢 " + currentStock + " ▼");
        btnStockSelector.setTextColor(textColor);
        btnStockSelector.setBackground(createRoundedBg(accentCard, dp(10)));

        String catLabel = selectedCategoryFilter.equals("ALL") ? tr("all_cats") : selectedCategoryFilter;
        btnCatFilter.setText("🏷️ " + catLabel + " ▼");
        btnCatFilter.setTextColor(textColor);
        btnCatFilter.setBackground(createRoundedBg(cardColor, dp(10)));

        statsCard.setBackground(createRoundedBg(cardColor, dp(12)));
        statsRefsText.setTextColor(textColor);
        statsUnitsText.setTextColor(subTextColor);
        btnEntry.setText(tr("btn_in"));
        btnExit.setText(tr("btn_out"));
        searchInput.setHint(tr("search"));
        searchInput.setTextColor(textColor);
        searchInput.setHintTextColor(subTextColor);
        searchInput.setBackground(createRoundedBg(cardColor, dp(12)));
        emptyText.setText(tr("empty"));
        emptyText.setTextColor(subTextColor);
        exitTotalBanner.setBackground(createRoundedBg(Color.parseColor("#DC2626"), dp(12)));
        filterList();
    }

    private void updateStats() {
        int totalRefs = 0, totalUnits = 0;
        for (Product p : inventory) {
            if (currentStock.equals(p.stockName)) {
                totalRefs++;
                totalUnits += p.quantity;
            }
        }
        statsRefsText.setText(tr("refs") + " : " + totalRefs);
        statsUnitsText.setText(tr("units") + " : " + totalUnits);
        emptyText.setVisibility(filteredInventory.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(filteredInventory.isEmpty() ? View.GONE : View.VISIBLE);

        if (usePrices && (lastExitTotal > 0 || sessionExitTotal > 0)) {
            exitTotalBanner.setVisibility(View.VISIBLE);
            exitTotalText.setText(String.format(Locale.getDefault(), tr("exit_banner"), lastExitTotal, sessionExitTotal));
        } else {
            exitTotalBanner.setVisibility(View.GONE);
        }
    }

    private void startBarcodeScan(boolean isEntry) {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener(barcode -> {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null && !rawValue.isEmpty()) {
                        vibrate();
                        handleScannedBarcode(rawValue.trim(), isEntry);
                    }
                })
                .addOnFailureListener(e -> showManualBarcodeDialog(isEntry));
    }

    private void showManualBarcodeDialog(boolean isEntry) {
        EditText input = new EditText(this);
        input.setHint("Ex: 3017620422003");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(tr("manual_code"))
                .setView(input)
                .setPositiveButton(tr("save"), (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (!code.isEmpty()) handleScannedBarcode(code, isEntry);
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private void handleScannedBarcode(String barcode, boolean isEntry) {
        Product existingInStock = findProductInCurrentStock(barcode);
        if (existingInStock != null) {
            showProductTransactionDialog(existingInStock, isEntry, false);
        } else {
            if (!isEntry) {
                Toast.makeText(this, tr("not_in_stock"), Toast.LENGTH_LONG).show();
                return;
            }
            Product fromOtherStock = findProductAnywhere(barcode);
            if (fromOtherStock != null) {
                Product copy = new Product();
                copy.barcode = barcode;
                copy.name = fromOtherStock.name;
                copy.imageBase64 = fromOtherStock.imageBase64;
                copy.category = fromOtherStock.category;
                copy.price = fromOtherStock.price;
                copy.stockName = currentStock;
                copy.quantity = 0;
                showProductTransactionDialog(copy, true, true);
            } else {
                fetchProductOnline(barcode);
            }
        }
    }

    private void fetchProductOnline(String barcode) {
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage(tr("fetching") + "\n(" + barcode + ")")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        executor.execute(() -> {
            String foundName = "", foundImgBase64 = "";
            String[] apis = {
                "https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json",
                "https://world.openproductsfacts.org/api/v2/product/" + barcode + ".json"
            };
            for (String apiUrl : apis) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    if (conn.getResponseCode() == 200) {
                        InputStream is = conn.getInputStream();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int n;
                        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                        JSONObject root = new JSONObject(baos.toString("UTF-8"));
                        if (root.optInt("status", 0) == 1 && root.has("product")) {
                            JSONObject prod = root.getJSONObject("product");
                            foundName = prod.optString("product_name_fr", "");
                            if (foundName.isEmpty()) foundName = prod.optString("product_name", "");
                            String imgUrl = prod.optString("image_front_small_url", "");
                            if (!imgUrl.isEmpty()) foundImgBase64 = downloadImageAsBase64(imgUrl);
                            if (!foundName.isEmpty()) break;
                        }
                    }
                } catch (Exception ignored) {}
            }
            final String finalName = foundName, finalImg = foundImgBase64;
            mainHandler.post(() -> {
                loadingDialog.dismiss();
                Product newProd = new Product();
                newProd.barcode = barcode;
                newProd.name = finalName;
                newProd.quantity = 0;
                newProd.imageBase64 = finalImg;
                newProd.stockName = currentStock;
                newProd.category = categoriesList.isEmpty() ? tr("uncategorized") : categoriesList.get(0);
                newProd.price = 0.0;
                showProductTransactionDialog(newProd, true, true);
            });
        });
    }

    private String downloadImageAsBase64(String urlStr) {
        try {
            InputStream is = new URL(urlStr).openStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            return bmp != null ? bitmapToBase64(bmp) : "";
        } catch (Exception e) { return ""; }
    }

    private void showProductTransactionDialog(Product product, boolean isEntry, boolean isNew) {
        tempPhotoBase64 = product.imageBase64 != null ? product.imageBase64 : "";
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), dp(10));
        scroll.addView(box);

        LinearLayout photoRow = new LinearLayout(this);
        photoRow.setOrientation(LinearLayout.HORIZONTAL);
        photoRow.setGravity(Gravity.CENTER_VERTICAL);

        currentPhotoPreview = new ImageView(this);
        currentPhotoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        currentPhotoPreview.setBackground(createRoundedBg(Color.parseColor("#CBD5E1"), dp(10)));
        if (!tempPhotoBase64.isEmpty()) {
            Bitmap b = base64ToBitmap(tempPhotoBase64);
            if (b != null) currentPhotoPreview.setImageBitmap(b);
        }
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(76), dp(76));
        imgLp.setMargins(0, 0, dp(14), 0);
        photoRow.addView(currentPhotoPreview, imgLp);

        LinearLayout codeAndCam = new LinearLayout(this);
        codeAndCam.setOrientation(LinearLayout.VERTICAL);
        TextView codeTv = new TextView(this);
        codeTv.setText("🏷️ " + product.barcode + "\n📦 " + product.stockName + " (" + tr("current_stock") + product.quantity + ")");
        codeTv.setTextSize(12);
        codeAndCam.addView(codeTv);

        Button btnCam = new Button(this);
        btnCam.setText(tr("photo_btn"));
        btnCam.setAllCaps(false);
        btnCam.setOnClickListener(v -> {
            try { startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_TAKE_PHOTO); } catch (Exception ignored) {}
        });
        codeAndCam.addView(btnCam);
        photoRow.addView(codeAndCam);
        box.addView(photoRow);

        EditText nameInput = new EditText(this);
        nameInput.setHint(tr("prod_name"));
        nameInput.setText(product.name);
        box.addView(nameInput);

        TextView catTv = new TextView(this);
        catTv.setText(tr("category_label"));
        catTv.setTypeface(null, Typeface.BOLD);
        catTv.setPadding(0, dp(8), 0, dp(2));
        box.addView(catTv);

        Spinner catSpinner = new Spinner(this);
        List<String> spinnerCats = new ArrayList<>(categoriesList);
        spinnerCats.add(tr("new_cat_opt"));
        catSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerCats));
        int catIndex = categoriesList.indexOf(product.category);
        if (catIndex >= 0) catSpinner.setSelection(catIndex);

        EditText newCatInput = new EditText(this);
        newCatInput.setHint(tr("new_cat_hint"));
        newCatInput.setVisibility(View.GONE);

        catSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                newCatInput.setVisibility(position == spinnerCats.size() - 1 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        box.addView(catSpinner);
        box.addView(newCatInput);

        EditText priceInput = new EditText(this);
        priceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        priceInput.setText(product.price > 0 ? String.valueOf(product.price) : "");
        priceInput.setHint("0.00");
        if (usePrices) {
            TextView priceTv = new TextView(this);
            priceTv.setText(tr("price_label"));
            priceTv.setTypeface(null, Typeface.BOLD);
            priceTv.setPadding(0, dp(8), 0, dp(2));
            box.addView(priceTv);
            box.addView(priceInput);
        }

        TextView qtyLabel = new TextView(this);
        qtyLabel.setText(tr("qty_label"));
        qtyLabel.setTypeface(null, Typeface.BOLD);
        qtyLabel.setPadding(0, dp(10), 0, dp(2));
        box.addView(qtyLabel);

        EditText qtyInput = new EditText(this);
        qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyInput.setText("1");
        qtyInput.setTextSize(20);
        qtyInput.setTypeface(null, Typeface.BOLD);
        qtyInput.setGravity(Gravity.CENTER);
        qtyInput.selectAll();
        box.addView(qtyInput);

        TextView liveExitTotalTv = new TextView(this);
        liveExitTotalTv.setTextSize(16);
        liveExitTotalTv.setTypeface(null, Typeface.BOLD);
        liveExitTotalTv.setTextColor(Color.WHITE);
        liveExitTotalTv.setGravity(Gravity.CENTER);
        liveExitTotalTv.setPadding(dp(12), dp(10), dp(12), dp(10));
        liveExitTotalTv.setBackground(createRoundedBg(Color.parseColor("#DC2626"), dp(10)));
        LinearLayout.LayoutParams totalLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        totalLp.setMargins(0, dp(10), 0, dp(4));

        Runnable updateLiveTotal = () -> {
            if (!usePrices || isEntry) {
                liveExitTotalTv.setVisibility(View.GONE);
                return;
            }
            liveExitTotalTv.setVisibility(View.VISIBLE);
            int q = 1; double pr = product.price;
            try { q = Integer.parseInt(qtyInput.getText().toString().trim()); } catch (Exception ignored) {}
            try { pr = Double.parseDouble(priceInput.getText().toString().trim().replace(",", ".")); } catch (Exception ignored) {}
            liveExitTotalTv.setText(tr("exit_total_live") + String.format(Locale.getDefault(), "%.2f €", Math.max(0, q) * Math.max(0, pr)));
        };

        LinearLayout quickBtns = new LinearLayout(this);
        quickBtns.setOrientation(LinearLayout.HORIZONTAL);
        int[] steps = {-1, 1, 5, 10, 50};
        for (int i = 0; i < steps.length; i++) {
            final int step = steps[i];
            Button b = new Button(this);
            b.setText(step > 0 ? "+" + step : String.valueOf(step));
            b.setTextSize(12);
            b.setOnClickListener(v -> {
                int cur = 1;
                try { cur = Integer.parseInt(qtyInput.getText().toString().trim()); } catch (Exception ignored) {}
                qtyInput.setText(String.valueOf(Math.max(1, cur + step)));
                updateLiveTotal.run();
            });
            quickBtns.addView(b, new LinearLayout.LayoutParams(0, dp(38), 1f));
        }
        box.addView(quickBtns);
        box.addView(liveExitTotalTv, totalLp);

        qtyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateLiveTotal.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        priceInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateLiveTotal.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateLiveTotal.run();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isEntry ? "🟢 " + tr("entry_title") : "🔴 " + tr("exit_title"))
                .setView(scroll)
                .setPositiveButton(tr("save"), (d, w) -> {
                    String enteredName = nameInput.getText().toString().trim();
                    if (enteredName.isEmpty()) enteredName = tr("unknown_prod") + " (" + product.barcode + ")";

                    String chosenCat;
                    if (catSpinner.getSelectedItemPosition() == spinnerCats.size() - 1) {
                        chosenCat = newCatInput.getText().toString().trim();
                        if (chosenCat.isEmpty()) chosenCat = tr("uncategorized");
                        if (!categoriesList.contains(chosenCat)) categoriesList.add(chosenCat);
                    } else {
                        chosenCat = spinnerCats.get(catSpinner.getSelectedItemPosition());
                    }

                    int delta = 1;
                    try { delta = Integer.parseInt(qtyInput.getText().toString().trim()); } catch (Exception ignored) {}
                    if (usePrices) {
                        try { product.price = Double.parseDouble(priceInput.getText().toString().trim().replace(",", ".")); } catch (Exception ignored) {}
                    }

                    product.name = enteredName;
                    product.imageBase64 = tempPhotoBase64;
                    product.category = chosenCat;
                    product.stockName = currentStock;

                    if (isEntry) {
                        product.quantity += Math.max(0, delta);
                        if (isNew && findProductInCurrentStock(product.barcode) == null) inventory.add(0, product);
                    } else {
                        int actualRemoved = Math.min(product.quantity, Math.max(0, delta));
                        product.quantity = Math.max(0, product.quantity - delta);
                        if (usePrices) {
                            lastExitTotal = actualRemoved * product.price;
                            sessionExitTotal += lastExitTotal;
                        }
                    }
                    saveData();
                    applyThemeAndLang();
                })
                .setNegativeButton(tr("cancel"), null);

        if (!isNew) {
            builder.setNeutralButton(tr("delete"), (d, w) -> {
                inventory.remove(product);
                saveData();
                filterList();
            });
        }
        builder.show();
    }

    private class InventoryAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredInventory.size(); }
        @Override public Object getItem(int position) { return filteredInventory.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Product p = filteredInventory.get(position);
            int cardColor = isDarkMode ? Color.parseColor("#1E293B") : Color.parseColor("#FFFFFF");
            int textColor = isDarkMode ? Color.parseColor("#F8FAFC") : Color.parseColor("#0F172A");
            int subTextColor = isDarkMode ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B");

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackground(createRoundedBg(cardColor, dp(14)));

            ImageView img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(createRoundedBg(Color.parseColor("#334155"), dp(10)));
            if (p.imageBase64 != null && !p.imageBase64.isEmpty()) {
                Bitmap b = base64ToBitmap(p.imageBase64);
                if (b != null) img.setImageBitmap(b);
            }
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(54), dp(54));
            imgLp.setMargins(0, 0, dp(12), 0);
            card.addView(img, imgLp);

            LinearLayout info = new LinearLayout(MainActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView nameTv = new TextView(MainActivity.this);
            nameTv.setText(p.name);
            nameTv.setTextSize(15);
            nameTv.setTypeface(null, Typeface.BOLD);
            nameTv.setTextColor(textColor);

            String meta = "📁 " + (p.category != null ? p.category : tr("uncategorized")) + "  •  🏷️ " + p.barcode;
            if (usePrices && p.price > 0) meta += String.format(Locale.getDefault(), "\n💶 %.2f € / u", p.price);
            TextView codeTv = new TextView(MainActivity.this);
            codeTv.setText(meta);
            codeTv.setTextSize(12);
            codeTv.setTextColor(subTextColor);

            info.addView(nameTv);
            info.addView(codeTv);
            card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView badge = new TextView(MainActivity.this);
            badge.setText(String.valueOf(p.quantity));
            badge.setTextSize(16);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setTextColor(Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            int badgeColor = p.quantity == 0 ? Color.parseColor("#EF4444") : (p.quantity <= 3 ? Color.parseColor("#F59E0B") : Color.parseColor("#10B981"));
            badge.setBackground(createRoundedBg(badgeColor, dp(10)));
            badge.setPadding(dp(14), dp(8), dp(14), dp(8));
            card.addView(badge);

            card.setOnClickListener(v -> showProductTransactionDialog(p, true, false));
            return card;
        }
    }

    private void filterList() {
        filteredInventory.clear();
        for (Product p : inventory) {
            if (!currentStock.equals(p.stockName)) continue;
            if (!selectedCategoryFilter.equals("ALL") && !selectedCategoryFilter.equals(p.category)) continue;
            if (searchQuery.isEmpty()
                    || p.name.toLowerCase().contains(searchQuery)
                    || p.barcode.toLowerCase().contains(searchQuery)
                    || (p.category != null && p.category.toLowerCase().contains(searchQuery))) {
                filteredInventory.add(p);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        updateStats();
    }

    private Product findProductInCurrentStock(String barcode) {
        for (Product p : inventory) if (p.barcode.equals(barcode) && currentStock.equals(p.stockName)) return p;
        return null;
    }

    private Product findProductAnywhere(String barcode) {
        for (Product p : inventory) if (p.barcode.equals(barcode)) return p;
        return null;
    }

    private void startPdfExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "OptiStock_" + currentStock.replaceAll("\\s+", "_") + "_" + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + ".pdf");
        startActivityForResult(intent, REQ_CREATE_PDF);
    }

    private void generatePdfToUri(Uri uri) {
        try {
            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);
            linePaint.setStrokeWidth(1f);

            int pageWidth = 595, pageHeight = 842, pageNumber = 1, y = 55;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            paint.setTextSize(18f); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawText("OptiStock - " + currentStock, 40, y, paint);
            paint.setTextSize(10f); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText("Date : " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()) + "  |  Développé par GoDev", 40, y + 18, paint);
            y += 38; canvas.drawLine(40, y, 555, y, linePaint); y += 18;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Photo", 40, y, paint);
            canvas.drawText("Produit", 85, y, paint);
            canvas.drawText("Catégorie", 285, y, paint);
            if (usePrices) canvas.drawText("Prix", 420, y, paint);
            canvas.drawText("Qté", 510, y, paint);
            y += 8; canvas.drawLine(40, y, 555, y, linePaint); y += 14;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            double totalStockValue = 0.0;
            for (Product p : filteredInventory) {
                if (y > 760) {
                    pdf.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 50;
                }
                if (p.imageBase64 != null && !p.imageBase64.isEmpty()) {
                    Bitmap b = base64ToBitmap(p.imageBase64);
                    if (b != null) canvas.drawBitmap(Bitmap.createScaledBitmap(b, 32, 32, true), 40, y - 8, null);
                }
                canvas.drawText(p.name.length() > 30 ? p.name.substring(0, 27) + "..." : p.name, 85, y + 10, paint);
                String catStr = p.category != null ? p.category : "";
                canvas.drawText(catStr.length() > 18 ? catStr.substring(0, 15) + "..." : catStr, 285, y + 10, paint);
                if (usePrices) {
                    canvas.drawText(String.format(Locale.getDefault(), "%.2f €", p.price), 420, y + 10, paint);
                    totalStockValue += p.price * p.quantity;
                }
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(String.valueOf(p.quantity), 510, y + 10, paint);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                y += 34; canvas.drawLine(40, y - 6, 555, y - 6, linePaint);
            }
            if (usePrices) {
                y += 16;
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(String.format(Locale.getDefault(), "Valeur totale du stock : %.2f €", totalStockValue), 350, y, paint);
            }
            pdf.finishPage(page);
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) { pdf.writeTo(os); os.close(); }
            pdf.close();
            Toast.makeText(this, tr("pdf_ok"), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur PDF : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == REQ_CREATE_PDF && data.getData() != null) generatePdfToUri(data.getData());
        else if (requestCode == REQ_TAKE_PHOTO && data.getExtras() != null) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            if (photo != null) {
                tempPhotoBase64 = bitmapToBase64(photo);
                if (currentPhotoPreview != null) currentPhotoPreview.setImageBitmap(photo);
            }
        }
    }

    private void saveData() {
        try {
            JSONArray prodArr = new JSONArray();
            for (Product p : inventory) {
                JSONObject o = new JSONObject();
                o.put("barcode", p.barcode);
                o.put("name", p.name);
                o.put("quantity", p.quantity);
                o.put("imageBase64", p.imageBase64 != null ? p.imageBase64 : "");
                o.put("stockName", p.stockName != null ? p.stockName : currentStock);
                o.put("category", p.category != null ? p.category : tr("uncategorized"));
                o.put("price", p.price);
                prodArr.put(o);
            }
            JSONArray stArr = new JSONArray();
            for (String s : stocksList) stArr.put(s);
            JSONArray catArr = new JSONArray();
            for (String c : categoriesList) catArr.put(c);
            prefs.edit()
                    .putString("inventory_json", prodArr.toString())
                    .putString("stocks_json", stArr.toString())
                    .putString("cats_json", catArr.toString())
                    .putString("currentStock", currentStock)
                    .apply();
        } catch (Exception ignored) {}
    }

    private void loadData() {
        stocksList.clear();
        try {
            JSONArray stArr = new JSONArray(prefs.getString("stocks_json", "[]"));
            for (int i = 0; i < stArr.length(); i++) stocksList.add(stArr.getString(i));
        } catch (Exception ignored) {}
        if (stocksList.isEmpty()) stocksList.add("Stock Principal");
        currentStock = prefs.getString("currentStock", stocksList.get(0));
        if (!stocksList.contains(currentStock)) currentStock = stocksList.get(0);

        categoriesList.clear();
        try {
            JSONArray catArr = new JSONArray(prefs.getString("cats_json", "[]"));
            for (int i = 0; i < catArr.length(); i++) categoriesList.add(catArr.getString(i));
        } catch (Exception ignored) {}
        if (categoriesList.isEmpty()) {
            categoriesList.add(tr("uncategorized"));
            categoriesList.add("Alimentaire");
            categoriesList.add("Matériel");
        }

        inventory.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("inventory_json", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Product p = new Product();
                p.barcode = o.optString("barcode", "");
                p.name = o.optString("name", "");
                p.quantity = o.optInt("quantity", 0);
                p.imageBase64 = o.optString("imageBase64", "");
                p.stockName = o.optString("stockName", stocksList.get(0));
                p.category = o.optString("category", categoriesList.get(0));
                p.price = o.optDouble("price", 0.0);
                inventory.add(p);
            }
        } catch (Exception ignored) {}
    }

    private String bitmapToBase64(Bitmap bitmap) {
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 160, 160, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    private Bitmap base64ToBitmap(String b64) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { return null; }
    }

    private GradientDrawable createRoundedBg(int color, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(70L);
                }
            }
        } catch (Exception ignored) {}
    }
}
