package com.godev.optistock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

public class MigrationProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"backup_payload"});
        Context ctx = getContext();
        if (ctx == null) return cursor;

        // Vérification stricte de la signature numérique entre l'application appelante (Pro) et cette application (Trial)
        try {
            PackageManager pm = ctx.getPackageManager();
            int callingUid = Binder.getCallingUid();
            int myUid = Process.myUid();
            if (callingUid != myUid && pm.checkSignatures(myUid, callingUid) != PackageManager.SIGNATURE_MATCH) {
                return cursor;
            }
        } catch (Exception e) {
            return cursor;
        }

        try {
            SharedPreferences prefs = ctx.getSharedPreferences("OptiStockPrefs", Context.MODE_PRIVATE);
            JSONObject backup = new JSONObject();
            backup.put("version", 7);
            backup.put("inventory_json", prefs.getString("inventory_json", "[]"));
            backup.put("stocks_json", prefs.getString("stocks_json", "[]"));
            backup.put("cats_json", prefs.getString("cats_json", "[]"));
            backup.put("clients_json", prefs.getString("clients_json", "[]"));
            backup.put("receipt_json", prefs.getString("receipt_json", "[]"));
            backup.put("saved_receipts_json", prefs.getString("saved_receipts_json", "[]"));
            backup.put("history_json", prefs.getString("history_json", "[]"));
            backup.put("currentStock", prefs.getString("currentStock", "Stock Principal"));
            backup.put("shopName", prefs.getString("shopName", ""));
            backup.put("customLogoPath", prefs.getString("customLogoPath", ""));
            backup.put("smtpHost", prefs.getString("smtpHost", "smtp.gmail.com"));
            backup.put("smtpPort", prefs.getString("smtpPort", "587"));
            backup.put("senderEmail", prefs.getString("senderEmail", ""));
            backup.put("senderAppPass", prefs.getString("senderAppPass", ""));
            backup.put("lowStockThreshold", prefs.getInt("lowStockThreshold", 3));
            backup.put("darkMode", prefs.getBoolean("darkMode", true));
            backup.put("usePrices", prefs.getBoolean("usePrices", false));
            backup.put("useScanner", prefs.getBoolean("useScanner", true));
            backup.put("langPref", prefs.getString("langPref", "SYS"));

            JSONObject photosMap = new JSONObject();
            File photosDir = new File(ctx.getFilesDir(), "product_photos");
            if (photosDir.exists() && photosDir.isDirectory()) {
                File[] files = photosDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            String b64 = readFileAsBase64(f);
                            if (!b64.isEmpty()) {
                                photosMap.put(f.getName(), b64);
                            }
                        }
                    }
                }
            }
            backup.put("photos_map", photosMap);
            cursor.addRow(new Object[]{backup.toString()});
        } catch (Exception ignored) {}

        return cursor;
    }

    private String readFileAsBase64(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) baos.write(buf, 0, n);
            fis.close();
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.godev.optistock.backup"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
