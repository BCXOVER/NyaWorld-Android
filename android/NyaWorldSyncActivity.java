package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NyaWorld synchronized updater preview.
 * This is an experimental pack-file updater, NOT an installer for Minecraft/NeoForge
 * and NOT proof that every NeoForge mod works on Android.
 */
public final class NyaWorldSyncActivity extends Activity {
    private static final String BASE = "https://serve.nyaworld.site/client/";
    private static final String MANIFEST = BASE + "manifest.json";
    private static final String FOLDER = "profiles/NyaWorld";
    private static final int MAX_MANIFEST = 8 * 1024 * 1024;
    private static final long MAX_FILE = 500L * 1024 * 1024;
    private static final Set<String> ROOTS = new HashSet<>();
    static {
        ROOTS.add("mods");
        ROOTS.add("config");
        ROOTS.add("kubejs");
        ROOTS.add("defaultconfigs");
        ROOTS.add("resourcepacks");
        // GPU-dependent shaderpacks and platform-local files are deliberately excluded.
    }

    private TextView status;
    private ProgressBar progress;
    private Button checkButton;
    private Button syncButton;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean busy;
    private volatile boolean syncedThisSession;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout ui = new LinearLayout(this);
        ui.setOrientation(LinearLayout.VERTICAL);
        ui.setPadding(dp(22), dp(30), dp(22), dp(24));
        ui.setBackgroundColor(0xFF211E23);
        ui.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView heading = new TextView(this);
        heading.setText("NYAWORLD");
        heading.setTextColor(0xFFE2B184);
        heading.setTextSize(32);
        heading.setGravity(Gravity.CENTER);
        ui.addView(heading, new LinearLayout.LayoutParams(-1, dp(70)));

        status = new TextView(this);
        status.setText("Android Sync Preview\nПроверка обновлений ПК-сборки");
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(15);
        ui.addView(status, new LinearLayout.LayoutParams(-1, dp(105)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        ui.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));

        checkButton = button(ui, "ПРОВЕРИТЬ ОБНОВЛЕНИЯ");
        checkButton.setOnClickListener(v -> submit(false));

        syncButton = button(ui, "СКАЧАТЬ СБОРКУ (ТЕСТ)");
        syncButton.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Экспериментальная установка")
                .setMessage("Полная NyaWorld содержит сотни модов. Совместимость с Android не проверена. Скачивание может занять более 1 ГБ и не гарантирует запуск игры. Продолжить?")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Скачать", (dialog, which) -> submit(true))
                .show());

        Button launcherButton = button(ui, "ОТКРЫТЬ ЛАУНЧЕР");
        launcherButton.setOnClickListener(v -> {
            // Keep vanilla launcher usable even if modpack sync fails or is not wanted.
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
        });

        TextView note = new TextView(this);
        note.setText("Версия NyaWorld обновляется из того же manifest.json, что и ПК.\n"
                + "Файлы загружаются в отдельную папку profiles/NyaWorld.\n"
                + "Minecraft 1.21.1 и NeoForge 21.1.250 пока устанавливаются в обычном лаунчере.");
        note.setTextColor(0xFFB8ACAB);
        note.setTextSize(12);
        ui.addView(note);
        setContentView(ui);
        submit(false);
    }

    private Button button(LinearLayout parent, String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(60));
        p.topMargin = dp(14);
        parent.addView(b, p);
        return b;
    }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density * n); }
    private void show(String s, int percentage) {
        runOnUiThread(() -> {
            status.setText(s);
            progress.setProgress(Math.min(100, Math.max(0,percentage)));
        });
    }
    private void submit(boolean download) {
        if (busy) return;
        busy = true;
        checkButton.setEnabled(false);
        syncButton.setEnabled(false);
        executor.execute(() -> {
            try {
                JSONObject manifest = loadManifest();
                JSONArray entries = manifest.getJSONArray("files");
                String version = manifest.optString("version", "?");
                String loader = manifest.optString("loader", "?");
                String loaderVersion = manifest.optString("loaderVersion", "?");
                if (!"NeoForge".equalsIgnoreCase(loader)) throw new IOException("Сервер использует другой загрузчик: " + loader);
                if (!"21.1.250".equals(loaderVersion)) throw new IOException("Обновился NeoForge до " + loaderVersion + ". Нужна новая версия Android-лаунчера.");
                if (!download) {
                    show("NyaWorld " + version + "\nNeoForge " + loaderVersion + "\n"
                            + "Файлов в манифесте: " + entries.length()
                            + "\nНажмите «Скачать сборку», если хотите установить тестовый клиент.", 100);
                    return;
                }
                updatePack(entries, version);
                syncedThisSession = true;
                show("NyaWorld " + version + " обновлён.\n"
                        + "Далее установите Minecraft/NeoForge и выберите профиль NyaWorld.\n"
                        + "Моды на Android ещё не проверены.", 100);
            } catch (Exception ex) {
                show("Ошибка NyaWorld: " + ex.getMessage()
                        + "\nОбычный лаунчер остаётся доступен.", 0);
            } finally {
                busy = false;
                runOnUiThread(() -> {
                    checkButton.setEnabled(true);
                    syncButton.setEnabled(true);
                });
            }
        });
    }

    private JSONObject loadManifest() throws Exception {
        byte[] bytes = loadBytes(new URL(MANIFEST),MAX_MANIFEST);
        return new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
    }

    private void updatePack(JSONArray entries,String version) throws Exception {
        File game = new File(Tools.DIR_GAME_HOME, FOLDER);
        if (!game.isDirectory() && !game.mkdirs()) throw new IOException("Нет доступа к папке игры");
        String basePath = game.getCanonicalPath() + File.separator;
        File managedFile = new File(getFilesDir(), "nyaworld_managed.json");
        JSONObject previous = readManaged(managedFile);
        JSONObject next = new JSONObject();
        int total = entries.length();
        int downloaded = 0;
        int present = 0;
        for(int i = 0; i < total; i++) {
            JSONObject item = entries.getJSONObject(i);
            String rel = item.getString("path");
            if(!allowed(rel)) continue;
            long size = item.getLong("size");
            String digest = item.getString("sha256").toLowerCase(Locale.ROOT);
            if(size < 0 || size > MAX_FILE || !digest.matches("[0-9a-f]{64}"))
                throw new IOException("Некорректный файл: " + rel);
            File target = new File(game,rel);
            if (!target.getCanonicalPath().startsWith(basePath))
                throw new IOException("Небезопасный путь: " + rel);
            next.put(rel, digest);
            if (!matches(target,size,digest)) {
                File dir = target.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Нет папки для " + rel);
                File temp = new File(dir, target.getName() + ".nyaworld-part");
                downloadFile(new URL(BASE + urlEncodePath(rel)), temp, size, digest);
                File backup = new File(dir, target.getName() + ".nyaworld-backup");
                if (backup.exists() && !backup.delete())
                    throw new IOException("Не удалось удалить старый backup");
                if (target.exists() && !target.renameTo(backup))
                    throw new IOException("Не удалось сохранить старую версию: " + rel);
                if (!temp.renameTo(target)) {
                    if (backup.exists()) backup.renameTo(target);
                    throw new IOException("Не удалось обновить " + rel);
                }
                if (backup.exists()) backup.delete();
                downloaded++;
            } else {
                present++;
            }
            if ((i % 7) == 0 || i == total - 1)
                show("NyaWorld " + version + "\nПроверено: " + (i+1) + "/" + total
                        + "\nСкачано: " + downloaded + " / без изменений: " + present,
                        (i+1)*100/total);
        }
        // Remove ONLY files that this updater wrote in a previous successful install.
        // Keep game saves, controls, screenshots and user-added files.
        JSONArray oldPaths = previous.names();
        if (oldPaths != null) for(int i=0;i<oldPaths.length();i++) {
            String rel = oldPaths.getString(i);
            if (next.has(rel) || !allowed(rel)) continue;
            File stale = new File(game,rel);
            if (!stale.getCanonicalPath().startsWith(basePath))
                throw new IOException("Invalid old managed path");
            String oldSha = previous.optString(rel, "");
            // Do not delete user-edited files.
            if (oldSha.matches("[0-9a-f]{64}") && stale.isFile()
                    && matches(stale, stale.length(),oldSha)
                    && !stale.delete())
                throw new IOException("Не удалось удалить старый мод: " + rel);
        }
        writeManaged(managedFile,next);
    }

    private JSONObject readManaged(File file) {
        if(!file.isFile()) return new JSONObject();
        try(FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n=in.read(buffer))!=-1) {
                if(out.size()+n > 1024*1024) return new JSONObject();
                out.write(buffer,0,n);
            }
            return new JSONObject(out.toString("UTF-8"));
        } catch(Exception ex) { return new JSONObject(); }
    }

    private void writeManaged(File file,JSONObject next) throws Exception {
        File temp = new File(file.getParentFile(),file.getName()+".tmp");
        try(FileOutputStream out = new FileOutputStream(temp)) {
            out.write(next.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!temp.renameTo(file)) {
            if (file.exists() && !file.delete()) throw new IOException("Не сохранить список обновлений");
            if (!temp.renameTo(file)) throw new IOException("Не сохранить список обновлений");
        }
    }

    private static boolean allowed(String path) {
        if(path == null || path.isEmpty() || path.startsWith("/") || path.contains("\\")
                || path.indexOf('\u0000')>=0 || path.indexOf(':')>=0) return false;
        String[] components = path.split("/",-1);
        if(components.length < 2 || !ROOTS.contains(components[0])) return false;
        for(String segment: components)
            if(segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
        return true;
    }

    private static String urlEncodePath(String rel) throws Exception {
        StringBuilder b = new StringBuilder();
        for(String piece: rel.split("/")) {
            if(b.length()>0) b.append('/');
            b.append(URLEncoder.encode(piece,"UTF-8").replace("+","%20"));
        }
        return b.toString();
    }

    private static boolean matches(File file,long size,String sha) throws Exception {
        if (!file.isFile() || (size >= 0 && file.length()!=size)) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64*1024];
        try (InputStream in=new BufferedInputStream(new FileInputStream(file))) {
            int n;
            while((n=in.read(buffer))!=-1) digest.update(buffer,0,n);
        }
        return sha.equals(hex(digest.digest()));
    }

    private static void downloadFile(URL url, File output,long expected, String sha) throws Exception {
        HttpURLConnection c = connection(url);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try(InputStream in = new BufferedInputStream(c.getInputStream());
            FileOutputStream fos=new FileOutputStream(output);
            BufferedOutputStream out=new BufferedOutputStream(fos)) {
            byte[] buffer = new byte[64*1024];
            int n;
            while((n=in.read(buffer))!=-1) {
                total += n;
                if(total > MAX_FILE || total > expected)
                    throw new IOException("Некорректный размер загрузки");
                digest.update(buffer,0,n);
                out.write(buffer,0,n);
            }
            out.flush();
            fos.getFD().sync();
        } finally { c.disconnect(); }
        if(total != expected || !sha.equals(hex(digest.digest()))) {
            output.delete();
            throw new IOException("Проверка SHA-256 не пройдена");
        }
    }

    private static byte[] loadBytes(URL url,int max) throws Exception {
        HttpURLConnection c = connection(url);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while((n=in.read(b))!=-1) {
                if(out.size()+n > max) throw new IOException("Манифест слишком большой");
                out.write(b,0,n);
            }
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection connection(URL url) throws Exception {
        if(!"https".equalsIgnoreCase(url.getProtocol()) || !"serve.nyaworld.site".equalsIgnoreCase(url.getHost()))
            throw new IOException("Запрещённый сервер загрузки");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent","NyaWorld-Android/0.2");
        c.setInstanceFollowRedirects(false);
        int response=c.getResponseCode();
        if(response!=200) {
            c.disconnect();
            throw new IOException("HTTP " + response);
        }
        return c;
    }
    private static String hex(byte[] bytes) {
        StringBuilder b=new StringBuilder(bytes.length*2);
        for(byte x:bytes) b.append(String.format(Locale.ROOT,"%02x",x&255));
        return b.toString();
    }
}