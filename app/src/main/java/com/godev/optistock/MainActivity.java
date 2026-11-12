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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
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

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    static class Product {
        String barcode, name, imageBase64, stockName, category;
        int quantity;
        double price;
    }

    static class ReceiptItem {
        String barcode, name;
        int quantity;
        double unitPrice;
    }

    private final List<Product> inventory = new ArrayList<>();
    private final List<Product> filteredInventory = new ArrayList<>();
    private final List<String> stocksList = new ArrayList<>();
    private final List<String> categoriesList = new ArrayList<>();
    private final List<ReceiptItem> currentReceipt = new ArrayList<>();
    private final List<String> clientEmails = new ArrayList<>();

    private String currentStock = "";
    private String selectedCategoryFilter = "ALL";
    private InventoryAdapter adapter;
    private SharedPreferences prefs;

    private boolean isDarkMode = true;
    private boolean usePrices = false;
    private String langPref = "SYS";
    private String searchQuery = "";
    private String senderEmail = "";
    private String senderAppPass = "";
    private String shopName = "OptiStock";
    private double lastExitTotal = 0.0;
    private double sessionExitTotal = 0.0;

    private static final int REQ_CREATE_PDF = 1001;
    private static final int REQ_TAKE_PHOTO = 1002;
    private ImageView currentPhotoPreview;
    private String tempPhotoBase64 = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout rootLayout, headerLayout, subHeaderLayout, statsRefsCard, statsUnitsCard, emptyContainer, exitTotalBanner, bottomBarLayout;
    private TextView titleText, statsRefsValue, statsRefsLabel, statsUnitsValue, statsUnitsLabel, emptyText, exitTotalText;
    private ImageView headerLogo, emptyLogo;
    private EditText searchInput;
    private Button btnEntry, btnExit, btnPdf, btnOptions, btnStockSelector, btnCatFilter, btnOpenReceipt;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("OptiStockPrefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("darkMode", true);
        usePrices = prefs.getBoolean("usePrices", false);
        langPref = prefs.getString("langPref", "SYS");
        senderEmail = prefs.getString("senderEmail", "");
        senderAppPass = prefs.getString("senderAppPass", "");
        shopName = prefs.getString("shopName", "OptiStock");
        loadData();
        showSplashScreen();
    }

    private void loadLogoInto(ImageView imgView) {
        int resId = getResources().getIdentifier("splash_logo", "drawable", getPackageName());
        if (resId != 0) {
            imgView.setImageResource(resId);
        } else {
            imgView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
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
        loadLogoInto(logoView);

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
        dict.put("refs", new String[]{"RÉFÉRENCES", "ITEMS", "REFERENCIAS"});
        dict.put("units", new String[]{"TOTAL UNITÉS", "TOTAL UNITS", "UNIDADES TOTALES"});
        dict.put("btn_in", new String[]{"+ ENTRÉE", "+ STOCK IN", "+ ENTRADA"});
        dict.put("btn_out", new String[]{"- SORTIE", "- STOCK OUT", "- SALIDA"});
        dict.put("search", new String[]{"🔍 Rechercher un produit ou code...", "🔍 Search product or barcode...", "🔍 Buscar producto o código..."});
        dict.put("empty", new String[]{"Aucun produit dans ce stock.\nAppuyez sur + ENTRÉE en bas pour scanner.", "No products in this stock.\nTap + STOCK IN below to scan.", "Sin productos en este stock.\nToque + ENTRADA abajo para escanear."});
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
        dict.put("opt_dark", new String[]{"Mode Sombre (Cyber-Logistique)", "Dark Mode (Cyber-Logistics)", "Modo Oscuro (Ciber-Logística)"});
        dict.put("opt_prices", new String[]{"Activer la gestion des prix & Ticket", "Enable prices & Receipt", "Activar precios y Ticket"});
        dict.put("exit_total_live", new String[]{"Prix total de cette sortie : ", "Total price for this exit: ", "Precio total de esta salida: "});
        dict.put("exit_banner", new String[]{"🧾 Ticket (%1$d art.) : %2$.2f €", "🧾 Receipt (%1$d it.) : %2$.2f", "🧾 Ticket (%1$d art.) : %2$.2f €"});
        dict.put("btn_receipt_view", new String[]{"📧 Ticket / Mail", "📧 Receipt / Mail", "📧 Ticket / Mail"});
        dict.put("receipt_title", new String[]{"🧾 Ticket de Caisse", "🧾 Sales Receipt", "🧾 Ticket de Caja"});
        dict.put("client_search_hint", new String[]{"🔍 Chercher ou saisir l'e-mail du client...", "🔍 Search or type customer email...", "🔍 Buscar o escribir email del cliente..."});
        dict.put("send_email_btn", new String[]{"📧 Envoyer le ticket au client", "📧 Send receipt to customer", "📧 Enviar ticket al cliente"});
        dict.put("clear_receipt_btn", new String[]{"🗑️ Nouveau ticket (Vider)", "🗑️ New receipt (Clear)", "🗑️ Nuevo ticket (Vaciar)"});
        dict.put("opt_shop_name", new String[]{"Nom de la boutique / Entreprise :", "Shop / Company Name:", "Nombre de la tienda / Empresa:"});
        dict.put("opt_sender_email", new String[]{"Mon adresse e-mail liée (Expéditeur) :", "My linked email address (Sender):", "Mi correo vinculado (Remitente):"});
        dict.put("opt_sender_pass", new String[]{"Clé / Mot de passe d'appli SMTP (Optionnel, pour envoi direct sans ouvrir l'appli mail) :", "SMTP App Password (Optional, for silent background sending):", "Contraseña de app SMTP (Opcional, para envío directo):"});
        dict.put("email_sent_ok", new String[]{"✅ Ticket envoyé automatiquement au client !", "✅ Receipt automatically sent to customer!", "✅ ¡Ticket enviado automáticamente al cliente!"});
        dict.put("email_invalid", new String[]{"Veuillez saisir une adresse e-mail client valide.", "Please enter a valid customer email.", "Ingrese un correo electrónico válido."});
        dict.put("saved_clients_label", new String[]{"👥 Registre clients (touchez pour sélectionner) :", "👥 Customer registry (tap to select):", "👥 Registro de clientes (toque para elegir):"});

        String eff = langPref.equals("SYS") ? Locale.getDefault().getLanguage().toLowerCase() : langPref;
        int idx = eff.startsWith("en") ? 1 : (eff.startsWith("es") ? 2 : 0);
        return dict.containsKey(key) ? dict.get(key)[idx] : key;
    }

    private void buildMainUI() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(14), dp(12), dp(14), dp(12));

        headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        headerLogo = new ImageView(this);
        headerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadLogoInto(headerLogo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoLp.setMargins(0, 0, dp(10), 0);
        headerLayout.addView(headerLogo, logoLp);

        titleText = new TextView(this);
        titleText.setTextSize(23);
        titleText.setTypeface(null, Typeface.BOLD);
        headerLayout.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnPdf = new Button(this);
        btnPdf.setTextSize(12);
        btnPdf.setAllCaps(false);
        btnPdf.setTypeface(null, Typeface.BOLD);
        btnPdf.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams pdfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        pdfLp.setMargins(0, 0, dp(8), 0);
        btnPdf.setOnClickListener(v -> startPdfExport());
        headerLayout.addView(btnPdf, pdfLp);

        btnOptions = new Button(this);
        btnOptions.setTextSize(12);
        btnOptions.setAllCaps(false);
        btnOptions.setTypeface(null, Typeface.BOLD);
        btnOptions.setPadding(dp(12), dp(4), dp(12), dp(4));
        btnOptions.setOnClickListener(v -> showOptionsDialog());
        headerLayout.addView(btnOptions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        rootLayout.addView(headerLayout);

        subHeaderLayout = new LinearLayout(this);
        subHeaderLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(12), 0, dp(10));

        btnStockSelector = new Button(this);
        btnStockSelector.setAllCaps(false);
        btnStockSelector.setTextSize(13);
        btnStockSelector.setTypeface(null, Typeface.BOLD);
        btnStockSelector.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnStockSelector.setOnClickListener(v -> showStockPopupMenu(v));
        LinearLayout.LayoutParams stBtnLp = new LinearLayout.LayoutParams(0, dp(42), 1.08f);
        stBtnLp.setMargins(0, 0, dp(6), 0);
        subHeaderLayout.addView(btnStockSelector, stBtnLp);

        btnCatFilter = new Button(this);
        btnCatFilter.setAllCaps(false);
        btnCatFilter.setTextSize(12);
        btnCatFilter.setTypeface(null, Typeface.BOLD);
        btnCatFilter.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnCatFilter.setOnClickListener(v -> showCategoryFilterPopup(v));
        LinearLayout.LayoutParams catBtnLp = new LinearLayout.LayoutParams(0, dp(42), 0.92f);
        catBtnLp.setMargins(dp(6), 0, 0, 0);
        subHeaderLayout.addView(btnCatFilter, catBtnLp);

        rootLayout.addView(subHeaderLayout, subLp);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsRowLp.setMargins(0, 0, 0, dp(10));

        statsRefsCard = new LinearLayout(this);
        statsRefsCard.setOrientation(LinearLayout.VERTICAL);
        statsRefsCard.setGravity(Gravity.CENTER);
        statsRefsCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        statsRefsValue = new TextView(this);
        statsRefsValue.setTextSize(22);
        statsRefsValue.setTypeface(null, Typeface.BOLD);
        statsRefsLabel = new TextView(this);
        statsRefsLabel.setTextSize(11);
        statsRefsLabel.setTypeface(null, Typeface.BOLD);
        statsRefsCard.addView(statsRefsValue);
        statsRefsCard.addView(statsRefsLabel);
        LinearLayout.LayoutParams refCardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        refCardLp.setMargins(0, 0, dp(6), 0);
        statsRow.addView(statsRefsCard, refCardLp);

        statsUnitsCard = new LinearLayout(this);
        statsUnitsCard.setOrientation(LinearLayout.VERTICAL);
        statsUnitsCard.setGravity(Gravity.CENTER);
        statsUnitsCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        statsUnitsValue = new TextView(this);
        statsUnitsValue.setTextSize(22);
        statsUnitsValue.setTypeface(null, Typeface.BOLD);
        statsUnitsLabel = new TextView(this);
        statsUnitsLabel.setTextSize(11);
        statsUnitsLabel.setTypeface(null, Typeface.BOLD);
        statsUnitsCard.addView(statsUnitsValue);
        statsUnitsCard.addView(statsUnitsLabel);
        LinearLayout.LayoutParams unitCardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        unitCardLp.setMargins(dp(6), 0, 0, 0);
        statsRow.addView(statsUnitsCard, unitCardLp);

        rootLayout.addView(statsRow, statsRowLp);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
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

        emptyContainer = new LinearLayout(this);
        emptyContainer.setOrientation(LinearLayout.VERTICAL);
        emptyContainer.setGravity(Gravity.CENTER);
        emptyLogo = new ImageView(this);
        emptyLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadLogoInto(emptyLogo);
        emptyLogo.setAlpha(0.22f);
        LinearLayout.LayoutParams emptyLogoLp = new LinearLayout.LayoutParams(dp(120), dp(120));
        emptyLogoLp.setMargins(0, 0, 0, dp(12));
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

        // BANDEAU DU TICKET DE CAISSE EN COURS
        exitTotalBanner = new LinearLayout(this);
        exitTotalBanner.setOrientation(LinearLayout.HORIZONTAL);
        exitTotalBanner.setGravity(Gravity.CENTER_VERTICAL);
        exitTotalBanner.setPadding(dp(12), dp(8), dp(10), dp(8));
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
            currentReceipt.clear();
            lastExitTotal = 0.0;
            sessionExitTotal = 0.0;
            saveData();
            updateStats();
        });
        exitTotalBanner.addView(btnResetTotal, new LinearLayout.LayoutParams(dp(34), dp(32)));
        rootLayout.addView(exitTotalBanner, bannerLp);

        // BARRE D'ACTIONS EN BAS SOUS LE POUCE
        bottomBarLayout = new LinearLayout(this);
        bottomBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actParams.setMargins(0, dp(8), 0, dp(2));

        btnEntry = new Button(this);
        btnEntry.setTextSize(16);
        btnEntry.setTypeface(null, Typeface.BOLD);
        btnEntry.setTextColor(Color.WHITE);
        btnEntry.setBackground(createRoundedBg(Color.parseColor("#059669"), dp(14), dp(2), Color.parseColor("#34D399")));
        btnEntry.setOnClickListener(v -> startBarcodeScan(true));
        LinearLayout.LayoutParams btnInParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        btnInParams.setMargins(0, 0, dp(6), 0);
        bottomBarLayout.addView(btnEntry, btnInParams);

        btnExit = new Button(this);
        btnExit.setTextSize(16);
        btnExit.setTypeface(null, Typeface.BOLD);
        btnExit.setTextColor(Color.WHITE);
        btnExit.setBackground(createRoundedBg(Color.parseColor("#DC2626"), dp(14), dp(2), Color.parseColor("#F87171")));
        btnExit.setOnClickListener(v -> startBarcodeScan(false));
        LinearLayout.LayoutParams btnOutParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        btnOutParams.setMargins(dp(6), 0, 0, 0);
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

        SpannableString styledTitle = new SpannableString("OptiStock");
        styledTitle.setSpan(new ForegroundColorSpan(textColor), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styledTitle.setSpan(new ForegroundColorSpan(cyanHighlight), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleText.setText(styledTitle);

        btnPdf.setText(tr("pdf_btn"));
        btnPdf.setTextColor(Color.WHITE);
        btnPdf.setBackground(createRoundedBg(Color.parseColor("#0284C7"), dp(10), dp(1), Color.parseColor("#38BDF8")));

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

    private void updateStats() {
        int totalRefs = 0, totalUnits = 0;
        for (Product p : inventory) {
            if (currentStock.equals(p.stockName)) {
                totalRefs++;
                totalUnits += p.quantity;
            }
        }
        statsRefsValue.setText(String.valueOf(totalRefs));
        statsUnitsValue.setText(String.valueOf(totalUnits));

        emptyContainer.setVisibility(filteredInventory.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(filteredInventory.isEmpty() ? View.GONE : View.VISIBLE);

        int receiptCount = 0;
        double receiptSum = 0.0;
        for (ReceiptItem item : currentReceipt) {
            receiptCount += item.quantity;
            receiptSum += item.quantity * item.unitPrice;
        }
        sessionExitTotal = receiptSum;

        if (usePrices && (!currentReceipt.isEmpty() || sessionExitTotal > 0)) {
            exitTotalBanner.setVisibility(View.VISIBLE);
            exitTotalText.setText(String.format(Locale.getDefault(), tr("exit_banner"), receiptCount, sessionExitTotal));
        } else {
            exitTotalBanner.setVisibility(View.GONE);
        }
    }

    private String buildReceiptText() {
        StringBuilder sb = new StringBuilder();
        String title = (shopName != null && !shopName.trim().isEmpty()) ? shopName.trim().toUpperCase() : "OPTISTOCK";
        sb.append("================================\n");
        sb.append("      ").append(title).append("\n");
        if (senderEmail != null && !senderEmail.isEmpty()) {
            sb.append("  ").append(senderEmail).append("\n");
        }
        sb.append("  Date : ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date())).append("\n");
        sb.append("  Stock : ").append(currentStock).append("\n");
        sb.append("================================\n\n");

        double grandTotal = 0.0;
        int totalQty = 0;
        for (ReceiptItem item : currentReceipt) {
            double lineTotal = item.quantity * item.unitPrice;
            grandTotal += lineTotal;
            totalQty += item.quantity;
            String pName = item.name != null ? item.name : item.barcode;
            if (pName.length() > 22) pName = pName.substring(0, 20) + "..";
            sb.append(String.format(Locale.getDefault(), "• %s\n", pName));
            sb.append(String.format(Locale.getDefault(), "  %d x %.2f €  =  %.2f €\n", item.quantity, item.unitPrice, lineTotal));
        }

        sb.append("\n--------------------------------\n");
        sb.append(String.format(Locale.getDefault(), "ARTICLES SORTIS : %d\n", totalQty));
        sb.append(String.format(Locale.getDefault(), "TOTAL À PAYER   : %.2f €\n", grandTotal));
        sb.append("================================\n");
        sb.append("   Merci de votre confiance !\n");
        return sb.toString();
    }

    private void showReceiptDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        scroll.addView(box);

        // Affichage visuel façon Ticket de caisse papier
        TextView ticketView = new TextView(this);
        ticketView.setText(buildReceiptText());
        ticketView.setTypeface(Typeface.MONOSPACE);
        ticketView.setTextSize(12);
        ticketView.setTextColor(Color.parseColor("#0F172A"));
        ticketView.setBackground(createRoundedBg(Color.parseColor("#F8FAFC"), dp(10), dp(1), Color.parseColor("#CBD5E1")));
        ticketView.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.addView(ticketView);

        // Info compte expéditeur lié
        TextView senderInfoTv = new TextView(this);
        String senderStatus = senderEmail.isEmpty()
                ? "⚠️ Aucun e-mail expéditeur lié (configurable dans ⚙️ Options)"
                : "📤 Expéditeur lié : " + senderEmail + (!senderAppPass.isEmpty() ? " (Envoi auto activé)" : "");
        senderInfoTv.setText(senderStatus);
        senderInfoTv.setTextSize(11);
        senderInfoTv.setTextColor(Color.parseColor("#38BDF8"));
        senderInfoTv.setPadding(0, dp(10), 0, dp(6));
        box.addView(senderInfoTv);

        // Barre de saisie / Recherche rapide dans le registre clients
        EditText emailInput = new EditText(this);
        emailInput.setHint(tr("client_search_hint"));
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setSingleLine(true);
        emailInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(emailInput);

        TextView savedLabel = new TextView(this);
        savedLabel.setText(tr("saved_clients_label"));
        savedLabel.setTextSize(12);
        savedLabel.setTypeface(null, Typeface.BOLD);
        savedLabel.setPadding(0, dp(8), 0, dp(4));
        box.addView(savedLabel);

        LinearLayout clientsListContainer = new LinearLayout(this);
        clientsListContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(clientsListContainer);

        Runnable refreshClientSuggestions = new Runnable() {
            @Override
            public void run() {
                clientsListContainer.removeAllViews();
                String q = emailInput.getText().toString().trim().toLowerCase();
                int shown = 0;
                for (int i = 0; i < clientEmails.size(); i++) {
                    final String savedMail = clientEmails.get(i);
                    if (q.isEmpty() || savedMail.toLowerCase().contains(q)) {
                        LinearLayout row = new LinearLayout(MainActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        rowLp.setMargins(0, dp(2), 0, dp(2));

                        Button selectBtn = new Button(MainActivity.this);
                        selectBtn.setText("👤 " + savedMail);
                        selectBtn.setAllCaps(false);
                        selectBtn.setTextSize(12);
                        selectBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        selectBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
                        selectBtn.setOnClickListener(v -> {
                            emailInput.setText(savedMail);
                            emailInput.setSelection(savedMail.length());
                        });
                        row.addView(selectBtn, new LinearLayout.LayoutParams(0, dp(36), 1f));

                        Button delBtn = new Button(MainActivity.this);
                        delBtn.setText("🗑️");
                        delBtn.setTextSize(11);
                        delBtn.setOnClickListener(v -> {
                            clientEmails.remove(savedMail);
                            saveData();
                            this.run();
                        });
                        row.addView(delBtn, new LinearLayout.LayoutParams(dp(42), dp(36)));

                        clientsListContainer.addView(row, rowLp);
                        shown++;
                        if (shown >= 5) break;
                    }
                }
                savedLabel.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
            }
        };

        emailInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshClientSuggestions.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        refreshClientSuggestions.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(tr("receipt_title"))
                .setView(scroll)
                .setPositiveButton(tr("send_email_btn"), null)
                .setNeutralButton(tr("clear_receipt_btn"), (d, w) -> {
                    currentReceipt.clear();
                    lastExitTotal = 0.0;
                    sessionExitTotal = 0.0;
                    saveData();
                    updateStats();
                })
                .setNegativeButton(tr("cancel"), null)
                .create();

        dialog.setOnShowListener(d -> {
            Button sendBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            sendBtn.setOnClickListener(v -> {
                String targetEmail = emailInput.getText().toString().trim();
                if (targetEmail.isEmpty() || !targetEmail.contains("@")) {
                    Toast.makeText(MainActivity.this, tr("email_invalid"), Toast.LENGTH_SHORT).show();
                    return;
                }
                // Enregistrement automatique dans le registre clients
                if (!clientEmails.contains(targetEmail)) {
                    clientEmails.add(0, targetEmail);
                    saveData();
                }
                String subject = "Ticket de caisse - " + (shopName.isEmpty() ? "OptiStock" : shopName);
                String body = buildReceiptText();
                dialog.dismiss();
                dispatchReceiptEmail(targetEmail, subject, body);
            });
        });

        dialog.show();
    }

    private void dispatchReceiptEmail(String toEmail, String subject, String bodyText) {
        if (!senderEmail.isEmpty() && !senderAppPass.isEmpty()) {
            AlertDialog progress = new AlertDialog.Builder(this)
                    .setMessage("📤 Envoi automatique du ticket à " + toEmail + "...")
                    .setCancelable(false)
                    .create();
            progress.show();

            executor.execute(() -> {
                boolean ok = sendSmtpSslEmail(senderEmail.trim(), senderAppPass.trim(), toEmail, subject, bodyText);
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

    private void launchEmailClientFallback(String toEmail, String subject, String bodyText) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{toEmail});
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, bodyText);
            startActivity(Intent.createChooser(intent, "Envoyer le ticket"));
        } catch (Exception e) {
            Toast.makeText(this, "Erreur ouverture mail : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean sendSmtpSslEmail(String fromUser, String appPass, String toMail, String subject, String body) {
        try {
            String host = "smtp.gmail.com";
            if (fromUser.endsWith("@outlook.com") || fromUser.endsWith("@hotmail.com") || fromUser.endsWith("@live.fr")) {
                host = "smtp.office365.com";
            } else if (fromUser.endsWith("@yahoo.com") || fromUser.endsWith("@yahoo.fr")) {
                host = "smtp.mail.yahoo.com";
            }
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, 465);
            socket.setSoTimeout(8000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            reader.readLine();
            writer.println("EHLO optistock");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("250 ")) break;
            }
            writer.println("AUTH LOGIN");
            reader.readLine();
            writer.println(Base64.encodeToString(fromUser.getBytes("UTF-8"), Base64.NO_WRAP));
            reader.readLine();
            writer.println(Base64.encodeToString(appPass.replace(" ", "").getBytes("UTF-8"), Base64.NO_WRAP));
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

            writer.println("From: " + shopName + " <" + fromUser + ">");
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
        darkSwitch.setPadding(0, dp(14), 0, dp(10));
        box.addView(darkSwitch);

        Switch priceSwitch = new Switch(this);
        priceSwitch.setText(tr("opt_prices"));
        priceSwitch.setChecked(usePrices);
        priceSwitch.setPadding(0, dp(6), 0, dp(14));
        box.addView(priceSwitch);

        TextView shopTv = new TextView(this);
        shopTv.setText(tr("opt_shop_name"));
        shopTv.setTypeface(null, Typeface.BOLD);
        box.addView(shopTv);

        EditText shopInput = new EditText(this);
        shopInput.setText(shopName);
        shopInput.setHint("Ex: OptiStock");
        shopInput.setSingleLine(true);
        box.addView(shopInput);

        TextView senderTv = new TextView(this);
        senderTv.setText(tr("opt_sender_email"));
        senderTv.setTypeface(null, Typeface.BOLD);
        senderTv.setPadding(0, dp(10), 0, 0);
        box.addView(senderTv);

        EditText senderInput = new EditText(this);
        senderInput.setText(senderEmail);
        senderInput.setHint("exemple@gmail.com");
        senderInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        senderInput.setSingleLine(true);
        box.addView(senderInput);

        TextView passTv = new TextView(this);
        passTv.setText(tr("opt_sender_pass"));
        passTv.setTextSize(12);
        passTv.setPadding(0, dp(10), 0, 0);
        box.addView(passTv);

        EditText passInput = new EditText(this);
        passInput.setText(senderAppPass);
        passInput.setHint("xxxx xxxx xxxx xxxx");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setSingleLine(true);
        box.addView(passInput);

        new AlertDialog.Builder(this)
                .setTitle(tr("options_title"))
                .setView(scroll)
                .setPositiveButton(tr("save"), (d, w) -> {
                    langPref = langCodes[langSpinner.getSelectedItemPosition()];
                    isDarkMode = darkSwitch.isChecked();
                    usePrices = priceSwitch.isChecked();
                    shopName = shopInput.getText().toString().trim();
                    if (shopName.isEmpty()) shopName = "OptiStock";
                    senderEmail = senderInput.getText().toString().trim();
                    senderAppPass = passInput.getText().toString().trim();
                    prefs.edit()
                            .putString("langPref", langPref)
                            .putBoolean("darkMode", isDarkMode)
                            .putBoolean("usePrices", usePrices)
                            .putString("shopName", shopName)
                            .putString("senderEmail", senderEmail)
                            .putString("senderAppPass", senderAppPass)
                            .apply();
                    applyThemeAndLang();
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    private void startBarcodeScan(boolean isEntry) {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
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

    private void addOrUpdateReceiptItem(String barcode, String name, int qtyRemoved, double unitPrice) {
        if (qtyRemoved <= 0) return;
        for (ReceiptItem item : currentReceipt) {
            if (item.barcode.equals(barcode) && Math.abs(item.unitPrice - unitPrice) < 0.001) {
                item.quantity += qtyRemoved;
                item.name = name;
                return;
            }
        }
        ReceiptItem newItem = new ReceiptItem();
        newItem.barcode = barcode;
        newItem.name = name;
        newItem.quantity = qtyRemoved;
        newItem.unitPrice = unitPrice;
        currentReceipt.add(newItem);
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
                        if (usePrices && actualRemoved > 0) {
                            lastExitTotal = actualRemoved * product.price;
                            addOrUpdateReceiptItem(product.barcode, product.name, actualRemoved, product.price);
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
            int cardColor = isDarkMode ? Color.parseColor("#0F172A") : Color.parseColor("#FFFFFF");
            int borderColor = isDarkMode ? Color.parseColor("#1E3A8A") : Color.parseColor("#CBD5E1");
            int textColor = isDarkMode ? Color.parseColor("#F8FAFC") : Color.parseColor("#0F172A");
            int subTextColor = isDarkMode ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B");

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackground(createRoundedBg(cardColor, dp(14), dp(1), borderColor));

            ImageView img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(createRoundedBg(Color.parseColor("#1E293B"), dp(10)));
            if (p.imageBase64 != null && !p.imageBase64.isEmpty()) {
                Bitmap b = base64ToBitmap(p.imageBase64);
                if (b != null) img.setImageBitmap(b);
            } else {
                loadLogoInto(img);
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
            JSONArray clientArr = new JSONArray();
            for (String em : clientEmails) clientArr.put(em);
            JSONArray recArr = new JSONArray();
            for (ReceiptItem ri : currentReceipt) {
                JSONObject ro = new JSONObject();
                ro.put("barcode", ri.barcode);
                ro.put("name", ri.name);
                ro.put("quantity", ri.quantity);
                ro.put("unitPrice", ri.unitPrice);
                recArr.put(ro);
            }
            prefs.edit()
                    .putString("inventory_json", prodArr.toString())
                    .putString("stocks_json", stArr.toString())
                    .putString("cats_json", catArr.toString())
                    .putString("clients_json", clientArr.toString())
                    .putString("receipt_json", recArr.toString())
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

        clientEmails.clear();
        try {
            JSONArray clientArr = new JSONArray(prefs.getString("clients_json", "[]"));
            for (int i = 0; i < clientArr.length(); i++) clientEmails.add(clientArr.getString(i));
        } catch (Exception ignored) {}

        currentReceipt.clear();
        try {
            JSONArray recArr = new JSONArray(prefs.getString("receipt_json", "[]"));
            for (int i = 0; i < recArr.length(); i++) {
                JSONObject ro = recArr.getJSONObject(i);
                ReceiptItem ri = new ReceiptItem();
                ri.barcode = ro.optString("barcode", "");
                ri.name = ro.optString("name", "");
                ri.quantity = ro.optInt("quantity", 1);
                ri.unitPrice = ro.optDouble("unitPrice", 0.0);
                currentReceipt.add(ri);
            }
        } catch (Exception ignored) {}

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

    private GradientDrawable createRoundedBg(int color, int radiusPx, int strokeWidthPx, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radiusPx);
        gd.setStroke(strokeWidthPx, strokeColor);
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
