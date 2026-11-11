package com.godev.optistock;

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
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
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

public class MainActivity extends AppCompatActivity {

    static class Product {
        String barcode;
        String name;
        int quantity;
        String imageBase64;
    }

    private List<Product> inventory = new ArrayList<>();
    private List<Product> filteredInventory = new ArrayList<>();
    private InventoryAdapter adapter;
    private SharedPreferences prefs;

    private boolean isDarkMode = true;
    private String currentLang = "FR";
    private String searchQuery = "";

    private static final int REQ_CREATE_PDF = 1001;
    private static final int REQ_TAKE_PHOTO = 1002;
    private ImageView currentPhotoPreview;
    private String tempPhotoBase64 = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout rootLayout, headerLayout, statsCard;
    private TextView titleText, statsRefsText, statsUnitsText, emptyText;
    private EditText searchInput;
    private Button btnEntry, btnExit, btnPdf, btnLang, btnTheme;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("OptiStockPrefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("darkMode", true);
        currentLang = prefs.getString("lang", "FR");

        loadInventory();
        buildUI();
        applyThemeAndLang();
    }

    private String tr(String key) {
        Map<String, String[]> dict = new HashMap<>();
        dict.put("app_title", new String[]{"OptiStock", "OptiStock", "OptiStock"});
        dict.put("refs", new String[]{"Références", "Items", "Referencias"});
        dict.put("units", new String[]{"Total Unités", "Total Units", "Unidades Totales"});
        dict.put("btn_in", new String[]{"+ ENTRÉE", "+ STOCK IN", "+ ENTRADA"});
        dict.put("btn_out", new String[]{"- SORTIE", "- STOCK OUT", "- SALIDA"});
        dict.put("search", new String[]{"Rechercher un produit ou code...", "Search product or barcode...", "Buscar producto o código..."});
        dict.put("empty", new String[]{"Aucun produit dans l'inventaire.\nAppuyez sur + ENTRÉE pour scanner.", "No products in inventory.\nTap + STOCK IN to scan.", "Sin productos en el inventario.\nToque + ENTRADA para escanear."});
        dict.put("fetching", new String[]{"Recherche du produit sur Internet...", "Searching product online...", "Buscando producto en línea..."});
        dict.put("entry_title", new String[]{"Entrée de stock", "Stock Entry", "Entrada de stock"});
        dict.put("exit_title", new String[]{"Sortie de stock", "Stock Exit", "Salida de stock"});
        dict.put("edit_title", new String[]{"Modifier le produit", "Edit Product", "Editar producto"});
        dict.put("prod_name", new String[]{"Nom du produit", "Product Name", "Nombre del producto"});
        dict.put("qty_label", new String[]{"Quantité (Lot) :", "Quantity (Batch):", "Cantidad (Lote):"});
        dict.put("current_stock", new String[]{"Stock actuel : ", "Current stock: ", "Stock actual: "});
        dict.put("save", new String[]{"VALIDER", "SAVE", "GUARDAR"});
        dict.put("cancel", new String[]{"Annuler", "Cancel", "Cancelar"});
        dict.put("delete", new String[]{"Supprimer", "Delete", "Eliminar"});
        dict.put("manual_code", new String[]{"Saisie manuelle", "Manual Barcode", "Código manual"});
        dict.put("photo_btn", new String[]{"📷 Photo", "📷 Photo", "📷 Foto"});
        dict.put("pdf_btn", new String[]{"📄 Exporter PDF", "📄 Export PDF", "📄 Exportar PDF"});
        dict.put("pdf_ok", new String[]{"PDF enregistré avec succès !", "PDF saved successfully!", "¡PDF guardado con éxito!"});
        dict.put("unknown_prod", new String[]{"Nouveau produit", "New Product", "Nuevo producto"});
        dict.put("not_in_stock", new String[]{"Ce produit n'est pas encore en stock !", "Product not in stock yet!", "¡Producto aún no en stock!"});

        int idx = currentLang.equals("EN") ? 1 : (currentLang.equals("ES") ? 2 : 0);
        return dict.containsKey(key) ? dict.get(key)[idx] : key;
    }

    private void buildUI() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(16), dp(16), dp(16), dp(16));

        headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        titleText = new TextView(this);
        titleText.setTextSize(24);
        titleText.setTypeface(null, Typeface.BOLD);
        headerLayout.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnLang = createTopButton(currentLang);
        btnLang.setOnClickListener(v -> {
            if (currentLang.equals("FR")) currentLang = "EN";
            else if (currentLang.equals("EN")) currentLang = "ES";
            else currentLang = "FR";
            prefs.edit().putString("lang", currentLang).apply();
            applyThemeAndLang();
        });
        headerLayout.addView(btnLang);

        btnTheme = createTopButton(isDarkMode ? "☀️" : "🌙");
        btnTheme.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            prefs.edit().putBoolean("darkMode", isDarkMode).apply();
            applyThemeAndLang();
        });
        headerLayout.addView(btnTheme);

        rootLayout.addView(headerLayout);

        statsCard = new LinearLayout(this);
        statsCard.setOrientation(LinearLayout.HORIZONTAL);
        statsCard.setGravity(Gravity.CENTER_VERTICAL);
        statsCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.setMargins(0, dp(12), 0, dp(12));

        LinearLayout leftStats = new LinearLayout(this);
        leftStats.setOrientation(LinearLayout.VERTICAL);
        statsRefsText = new TextView(this);
        statsRefsText.setTextSize(15);
        statsRefsText.setTypeface(null, Typeface.BOLD);
        statsUnitsText = new TextView(this);
        statsUnitsText.setTextSize(13);
        leftStats.addView(statsRefsText);
        leftStats.addView(statsUnitsText);

        statsCard.addView(leftStats, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnPdf = new Button(this);
        btnPdf.setTextSize(12);
        btnPdf.setAllCaps(false);
        btnPdf.setTextColor(Color.WHITE);
        btnPdf.setBackground(createRoundedBg(Color.parseColor("#3B82F6"), dp(10)));
        btnPdf.setPadding(dp(14), dp(8), dp(14), dp(8));
        btnPdf.setOnClickListener(v -> startPdfExport());
        statsCard.addView(btnPdf);

        rootLayout.addView(statsCard, statsParams);

        LinearLayout actionsLayout = new LinearLayout(this);
        actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actParams.setMargins(0, 0, 0, dp(12));

        btnEntry = new Button(this);
        btnEntry.setTextSize(16);
        btnEntry.setTypeface(null, Typeface.BOLD);
        btnEntry.setTextColor(Color.WHITE);
        btnEntry.setBackground(createRoundedBg(Color.parseColor("#10B981"), dp(14)));
        btnEntry.setPadding(0, dp(16), 0, dp(16));
        btnEntry.setOnClickListener(v -> startBarcodeScan(true));
        LinearLayout.LayoutParams btnInParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnInParams.setMargins(0, 0, dp(8), 0);
        actionsLayout.addView(btnEntry, btnInParams);

        btnExit = new Button(this);
        btnExit.setTextSize(16);
        btnExit.setTypeface(null, Typeface.BOLD);
        btnExit.setTextColor(Color.WHITE);
        btnExit.setBackground(createRoundedBg(Color.parseColor("#EF4444"), dp(14)));
        btnExit.setPadding(0, dp(16), 0, dp(16));
        btnExit.setOnClickListener(v -> startBarcodeScan(false));
        LinearLayout.LayoutParams btnOutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnOutParams.setMargins(dp(8), 0, 0, 0);
        actionsLayout.addView(btnExit, btnOutParams);

        rootLayout.addView(actionsLayout, actParams);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                filterList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, 0, 0, dp(10));
        rootLayout.addView(searchInput, searchParams);

        emptyText = new TextView(this);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(15);
        emptyText.setPadding(dp(20), dp(40), dp(20), dp(40));
        rootLayout.addView(emptyText);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(dp(10));
        adapter = new InventoryAdapter();
        listView.setAdapter(adapter);
        rootLayout.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(rootLayout);
    }

    private Button createTopButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(40));
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void applyThemeAndLang() {
        int bgColor = isDarkMode ? Color.parseColor("#0F172A") : Color.parseColor("#F1F5F9");
        int cardColor = isDarkMode ? Color.parseColor("#1E293B") : Color.parseColor("#FFFFFF");
        int textColor = isDarkMode ? Color.parseColor("#F8FAFC") : Color.parseColor("#0F172A");
        int subTextColor = isDarkMode ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B");

        rootLayout.setBackgroundColor(bgColor);
        titleText.setText("📦 " + tr("app_title"));
        titleText.setTextColor(textColor);

        btnLang.setText(currentLang);
        btnLang.setTextColor(textColor);
        btnLang.setBackground(createRoundedBg(cardColor, dp(10)));

        btnTheme.setText(isDarkMode ? "☀️" : "🌙");
        btnTheme.setTextColor(textColor);
        btnTheme.setBackground(createRoundedBg(cardColor, dp(10)));

        statsCard.setBackground(createRoundedBg(cardColor, dp(14)));
        statsRefsText.setTextColor(textColor);
        statsUnitsText.setTextColor(subTextColor);

        btnPdf.setText(tr("pdf_btn"));
        btnEntry.setText(tr("btn_in"));
        btnExit.setText(tr("btn_out"));

        searchInput.setHint(tr("search"));
        searchInput.setTextColor(textColor);
        searchInput.setHintTextColor(subTextColor);
        searchInput.setBackground(createRoundedBg(cardColor, dp(12)));

        emptyText.setText(tr("empty"));
        emptyText.setTextColor(subTextColor);

        filterList();
    }

    private void updateStats() {
        int totalUnits = 0;
        for (Product p : inventory) totalUnits += p.quantity;
        statsRefsText.setText(tr("refs") + " : " + inventory.size());
        statsUnitsText.setText(tr("units") + " : " + totalUnits);
        emptyText.setVisibility(filteredInventory.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(filteredInventory.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void startBarcodeScan(boolean isEntry) {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .enableAutoZoom()
                .build();

        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
        scanner.startScan()
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
        Product existing = findProductByBarcode(barcode);
        if (existing != null) {
            showQuantityBatchDialog(existing, isEntry, false);
        } else {
            if (!isEntry) {
                Toast.makeText(this, tr("not_in_stock"), Toast.LENGTH_LONG).show();
                return;
            }
            fetchProductOnline(barcode);
        }
    }

    private void fetchProductOnline(String barcode) {
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage(tr("fetching") + "\n(" + barcode + ")")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        executor.execute(() -> {
            String foundName = "";
            String foundImgBase64 = "";

            String[] apis = new String[]{
                    "https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json",
                    "https://world.openproductsfacts.org/api/v2/product/" + barcode + ".json",
                    "https://world.openbeautyfacts.org/api/v2/product/" + barcode + ".json"
            };

            for (String apiUrl : apis) {
                try {
                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    conn.setRequestProperty("User-Agent", "OptiStock - Android - Version 1.0");

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
                            if (foundName.isEmpty()) foundName = prod.optString("generic_name", "");

                            String brand = prod.optString("brands", "");
                            if (!brand.isEmpty() && !foundName.toLowerCase().contains(brand.toLowerCase())) {
                                foundName = foundName.isEmpty() ? brand : foundName + " - " + brand;
                            }

                            String imgUrl = prod.optString("image_front_small_url", "");
                            if (imgUrl.isEmpty()) imgUrl = prod.optString("image_small_url", "");
                            if (imgUrl.isEmpty()) imgUrl = prod.optString("image_url", "");

                            if (!imgUrl.isEmpty()) {
                                foundImgBase64 = downloadImageAsBase64(imgUrl);
                            }
                            if (!foundName.isEmpty()) break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            final String finalName = foundName;
            final String finalImg = foundImgBase64;

            mainHandler.post(() -> {
                loadingDialog.dismiss();
                Product newProd = new Product();
                newProd.barcode = barcode;
                newProd.name = finalName.isEmpty() ? "" : finalName;
                newProd.quantity = 0;
                newProd.imageBase64 = finalImg;
                showQuantityBatchDialog(newProd, true, true);
            });
        });
    }

    private String downloadImageAsBase64(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            InputStream is = conn.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null) return bitmapToBase64(bmp);
        } catch (Exception ignored) {}
        return "";
    }

    private void showQuantityBatchDialog(Product product, boolean isEntry, boolean isNew) {
        tempPhotoBase64 = product.imageBase64 != null ? product.imageBase64 : "";

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));
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
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dp(80), dp(80));
        imgParams.setMargins(0, 0, dp(16), 0);
        photoRow.addView(currentPhotoPreview, imgParams);

        LinearLayout codeAndCam = new LinearLayout(this);
        codeAndCam.setOrientation(LinearLayout.VERTICAL);
        TextView codeTv = new TextView(this);
        codeTv.setText("Code : " + product.barcode + "\n" + tr("current_stock") + product.quantity);
        codeTv.setTextSize(13);
        codeAndCam.addView(codeTv);

        Button btnCam = new Button(this);
        btnCam.setText(tr("photo_btn"));
        btnCam.setAllCaps(false);
        btnCam.setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            try { startActivityForResult(takePictureIntent, REQ_TAKE_PHOTO); } catch (Exception ignored) {}
        });
        codeAndCam.addView(btnCam);
        photoRow.addView(codeAndCam);
        box.addView(photoRow);

        EditText nameInput = new EditText(this);
        nameInput.setHint(tr("prod_name"));
        nameInput.setText(product.name);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.setMargins(0, dp(12), 0, dp(12));
        box.addView(nameInput, nameLp);

        TextView qtyLabel = new TextView(this);
        qtyLabel.setText(tr("qty_label"));
        qtyLabel.setTypeface(null, Typeface.BOLD);
        box.addView(qtyLabel);

        EditText qtyInput = new EditText(this);
        qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyInput.setText("1");
        qtyInput.setTextSize(22);
        qtyInput.setTypeface(null, Typeface.BOLD);
        qtyInput.setGravity(Gravity.CENTER);
        qtyInput.selectAll();
        box.addView(qtyInput);

        LinearLayout quickBtns = new LinearLayout(this);
        quickBtns.setOrientation(LinearLayout.HORIZONTAL);
        int[] steps = new int[]{-1, 1, 5, 10, 50};
        for (int step : steps) {
            Button b = new Button(this);
            b.setText(step > 0 ? "+" + step : String.valueOf(step));
            b.setTextSize(12);
            b.setPadding(0, 0, 0, 0);
            b.setOnClickListener(v -> {
                int cur = 1;
                try { cur = Integer.parseInt(qtyInput.getText().toString().trim()); } catch (Exception ignored) {}
                int next = Math.max(1, cur + step);
                qtyInput.setText(String.valueOf(next));
            });
            quickBtns.addView(b, new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        box.addView(quickBtns);

        String dialogTitle = isEntry ? "🟢 " + tr("entry_title") : "🔴 " + tr("exit_title");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(scroll)
                .setPositiveButton(tr("save"), (d, w) -> {
                    String enteredName = nameInput.getText().toString().trim();
                    if (enteredName.isEmpty()) enteredName = tr("unknown_prod") + " (" + product.barcode + ")";

                    int delta = 1;
                    try { delta = Integer.parseInt(qtyInput.getText().toString().trim()); } catch (Exception ignored) {}
                    if (delta < 0) delta = 0;

                    product.name = enteredName;
                    product.imageBase64 = tempPhotoBase64;

                    if (isEntry) {
                        product.quantity += delta;
                        if (isNew && findProductByBarcode(product.barcode) == null) {
                            inventory.add(0, product);
                        }
                    } else {
                        product.quantity = Math.max(0, product.quantity - delta);
                    }

                    saveInventory();
                    filterList();
                })
                .setNegativeButton(tr("cancel"), null);

        if (!isNew) {
            builder.setNeutralButton(tr("delete"), (d, w) -> {
                inventory.remove(product);
                saveInventory();
                filterList();
            });
        }

        builder.show();
    }

    private void showEditProductDialog(Product product) {
        tempPhotoBase64 = product.imageBase64 != null ? product.imageBase64 : "";

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));

        currentPhotoPreview = new ImageView(this);
        currentPhotoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        currentPhotoPreview.setBackground(createRoundedBg(Color.parseColor("#CBD5E1"), dp(10)));
        if (!tempPhotoBase64.isEmpty()) {
            Bitmap b = base64ToBitmap(tempPhotoBase64);
            if (b != null) currentPhotoPreview.setImageBitmap(b);
        }
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dp(90), dp(90));
        imgParams.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(currentPhotoPreview, imgParams);

        Button btnCam = new Button(this);
        btnCam.setText(tr("photo_btn"));
        btnCam.setAllCaps(false);
        btnCam.setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            try { startActivityForResult(takePictureIntent, REQ_TAKE_PHOTO); } catch (Exception ignored) {}
        });
        box.addView(btnCam);

        EditText nameInput = new EditText(this);
        nameInput.setHint(tr("prod_name"));
        nameInput.setText(product.name);
        box.addView(nameInput);

        EditText qtyInput = new EditText(this);
        qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyInput.setText(String.valueOf(product.quantity));
        box.addView(qtyInput);

        new AlertDialog.Builder(this)
                .setTitle(tr("edit_title") + " (" + product.barcode + ")")
                .setView(box)
                .setPositiveButton(tr("save"), (d, w) -> {
                    product.name = nameInput.getText().toString().trim();
                    try { product.quantity = Integer.parseInt(qtyInput.getText().toString().trim()); } catch (Exception ignored) {}
                    product.imageBase64 = tempPhotoBase64;
                    saveInventory();
                    filterList();
                })
                .setNeutralButton(tr("delete"), (d, w) -> {
                    inventory.remove(product);
                    saveInventory();
                    filterList();
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
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
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setBackground(createRoundedBg(cardColor, dp(14)));

            ImageView img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(createRoundedBg(Color.parseColor("#334155"), dp(10)));
            if (p.imageBase64 != null && !p.imageBase64.isEmpty()) {
                Bitmap b = base64ToBitmap(p.imageBase64);
                if (b != null) img.setImageBitmap(b);
            }
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(56), dp(56));
            imgLp.setMargins(0, 0, dp(12), 0);
            card.addView(img, imgLp);

            LinearLayout info = new LinearLayout(MainActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView nameTv = new TextView(MainActivity.this);
            nameTv.setText(p.name);
            nameTv.setTextSize(16);
            nameTv.setTypeface(null, Typeface.BOLD);
            nameTv.setTextColor(textColor);

            TextView codeTv = new TextView(MainActivity.this);
            codeTv.setText("🏷️ " + p.barcode);
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
            int badgeColor = p.quantity == 0 ? Color.parseColor("#EF4444")
                    : (p.quantity <= 3 ? Color.parseColor("#F59E0B") : Color.parseColor("#10B981"));
            badge.setBackground(createRoundedBg(badgeColor, dp(10)));
            badge.setPadding(dp(14), dp(8), dp(14), dp(8));
            card.addView(badge);

            card.setOnClickListener(v -> showEditProductDialog(p));
            return card;
        }
    }

    private void filterList() {
        filteredInventory.clear();
        for (Product p : inventory) {
            if (searchQuery.isEmpty()
                    || p.name.toLowerCase().contains(searchQuery)
                    || p.barcode.toLowerCase().contains(searchQuery)) {
                filteredInventory.add(p);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        updateStats();
    }

    private Product findProductByBarcode(String barcode) {
        for (Product p : inventory) {
            if (p.barcode.equals(barcode)) return p;
        }
        return null;
    }

    private void startPdfExport() {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "Inventaire_OptiStock_" + dateStr + ".pdf");
        startActivityForResult(intent, REQ_CREATE_PDF);
    }

    private void generatePdfToUri(Uri uri) {
        try {
            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);
            linePaint.setStrokeWidth(1f);

            int pageWidth = 595;
            int pageHeight = 842;
            int pageNumber = 1;
            int y = 60;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            paint.setTextSize(20f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawText("Inventaire OptiStock", 40, y, paint);

            paint.setTextSize(11f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            String dateFull = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
            canvas.drawText("Date : " + dateFull + "  |  " + tr("refs") + " : " + inventory.size(), 40, y + 20, paint);
            y += 45;

            canvas.drawLine(40, y, 555, y, linePaint);
            y += 22;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Photo", 40, y, paint);
            canvas.drawText("Produit", 95, y, paint);
            canvas.drawText("Code-barres", 370, y, paint);
            canvas.drawText("Qté", 510, y, paint);
            y += 10;
            canvas.drawLine(40, y, 555, y, linePaint);
            y += 15;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

            for (Product p : inventory) {
                if (y > 770) {
                    pdf.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 50;
                }

                if (p.imageBase64 != null && !p.imageBase64.isEmpty()) {
                    Bitmap b = base64ToBitmap(p.imageBase64);
                    if (b != null) {
                        Bitmap scaled = Bitmap.createScaledBitmap(b, 36, 36, true);
                        canvas.drawBitmap(scaled, 40, y - 10, null);
                    }
                }

                String displayName = p.name.length() > 38 ? p.name.substring(0, 35) + "..." : p.name;
                paint.setColor(Color.BLACK);
                canvas.drawText(displayName, 95, y + 12, paint);
                canvas.drawText(p.barcode, 370, y + 12, paint);

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(String.valueOf(p.quantity), 510, y + 12, paint);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

                y += 38;
                canvas.drawLine(40, y - 6, 555, y - 6, linePaint);
            }

            pdf.finishPage(page);

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                pdf.writeTo(os);
                os.close();
            }
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

        if (requestCode == REQ_CREATE_PDF && data.getData() != null) {
            generatePdfToUri(data.getData());
        } else if (requestCode == REQ_TAKE_PHOTO && data.getExtras() != null) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            if (photo != null) {
                tempPhotoBase64 = bitmapToBase64(photo);
                if (currentPhotoPreview != null) {
                    currentPhotoPreview.setImageBitmap(photo);
                }
            }
        }
    }

    private void saveInventory() {
        try {
            JSONArray arr = new JSONArray();
            for (Product p : inventory) {
                JSONObject o = new JSONObject();
                o.put("barcode", p.barcode);
                o.put("name", p.name);
                o.put("quantity", p.quantity);
                o.put("imageBase64", p.imageBase64 != null ? p.imageBase64 : "");
                arr.put(o);
            }
            prefs.edit().putString("inventory_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadInventory() {
        inventory.clear();
        String json = prefs.getString("inventory_json", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Product p = new Product();
                p.barcode = o.optString("barcode", "");
                p.name = o.optString("name", "");
                p.quantity = o.optInt("quantity", 0);
                p.imageBase64 = o.optString("imageBase64", "");
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
        } catch (Exception e) {
            return "";
        }
    }

    private Bitmap base64ToBitmap(String b64) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
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
                    v.vibrate(70);
                }
            }
        } catch (Exception ignored) {}
    }
}
