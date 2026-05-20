package com.termux.app.ghostex;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class GhostexFileLogger {

    private static final String LOG_FOLDER_NAME = "ghostex";
    private static final String LOG_FILE_NAME = "ghostex-android.log";
    private static final String LOG_FILE_PREFIX = "ghostex-android";
    private static final String PREVIOUS_LOG_FILE_NAME = "ghostex-android.previous.log";
    private static final Object LOCK = new Object();
    private static boolean legacyLogsCleaned;

    private GhostexFileLogger() {}

    /*
    CDXC:AndroidRemoteAttach 2026-05-18-04:38:
    SSHJ-backed attach failures can close the terminal with only
    `[Process completed (code 1)]` visible. Persist a small app-owned diagnostic
    log in `Downloads/ghostex/ghostex-android.log` so phone-side SSH lifecycle
    evidence can be shared without requiring adb logcat or leaking saved SSH
    passwords.

    CDXC:AndroidRemoteAttach 2026-05-18-05:02:
    Keep all Ghostex attach diagnostics in one append-only file and include a
    timestamp plus ZMX session tag on every line. This makes repeated attach
    failures comparable without asking the user to gather multiple files.

    CDXC:AndroidRemoteAttach 2026-05-18-05:12:
    Earlier debug builds wrote fallback copies and rotated files in multiple
    locations. Clean those old artifacts before appending new diagnostics so
    the user's phone has one shareable Ghostex log file, not a mix of current
    and legacy files.

    CDXC:AndroidRemoteAttach 2026-05-18-05:31:
    Android's Downloads MediaStore can rewrite `ghostex-android.log` as
    `ghostex-android.log.txt` or `ghostex-android.log (N).txt` when the MIME
    type looks textual. Treat every `ghostex-android*` row in the Ghostex
    Downloads folder as the same log family, keep one row, and append to that
    row so each log write cannot create another numbered file.
    */
    public static void log(@NonNull Context context,
                           @NonNull String area,
                           @NonNull String message) {
        log(context, area, null, message, null);
    }

    public static void cleanup(@NonNull Context context) {
        synchronized (LOCK) {
            cleanupLegacyLogs(context.getApplicationContext());
        }
    }

    public static void log(@NonNull Context context,
                           @NonNull String area,
                           @Nullable String sessionTag,
                           @NonNull String message) {
        log(context, area, sessionTag, message, null);
    }

    public static void log(@NonNull Context context,
                           @NonNull String area,
                           @NonNull String message,
                           @Nullable Throwable throwable) {
        log(context, area, null, message, throwable);
    }

    public static void log(@NonNull Context context,
                           @NonNull String area,
                           @Nullable String sessionTag,
                           @NonNull String message,
                           @Nullable Throwable throwable) {
        String cleanSessionTag = sessionTag == null || sessionTag.trim().isEmpty() ? "zmx=none" : sessionTag.trim();
        String line = timestamp() + " [" + area + "] [" + clean(cleanSessionTag) + "] " + clean(message);
        if (throwable != null) line += "\n" + stackTrace(throwable);
        synchronized (LOCK) {
            cleanupLegacyLogs(context.getApplicationContext());
            writeDownloadsLine(context.getApplicationContext(), line);
        }
    }

    @NonNull
    public static String primaryLogPath(@NonNull Context context) {
        return shareableLogPath(context);
    }

    @NonNull
    public static String shareableLogPath(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Environment.DIRECTORY_DOWNLOADS + "/" + LOG_FOLDER_NAME + "/" + LOG_FILE_NAME;
        }
        return new File(legacyDownloadsDirectory(), LOG_FILE_NAME).getAbsolutePath();
    }

    @NonNull
    private static File legacyDownloadsDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOG_FOLDER_NAME);
    }

    private static void writeLegacyDownloadsLine(@NonNull String line) {
        try {
            File directory = legacyDownloadsDirectory();
            if (!directory.exists() && !directory.mkdirs()) return;
            File logFile = new File(directory, LOG_FILE_NAME);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(logFile, true), StandardCharsets.UTF_8)) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (Exception ignored) {
            // Diagnostics must never affect the terminal session they are observing.
        }
    }

    private static void writeDownloadsLine(@NonNull Context context, @NonNull String line) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            cleanupLegacyDownloadsDirectory();
            writeLegacyDownloadsLine(line);
            return;
        }
        try {
            Uri logUri = findOrCreateDownloadsLog(context);
            if (logUri == null) return;
            ContentResolver resolver = context.getContentResolver();
            try (OutputStream outputStream = resolver.openOutputStream(logUri, "wa")) {
                if (outputStream == null) return;
                outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                outputStream.write('\n');
            }
        } catch (Exception ignored) {
            // Diagnostics must never affect the terminal session they are observing.
        }
    }

    @Nullable
    @TargetApi(Build.VERSION_CODES.Q)
    private static Uri findOrCreateDownloadsLog(@NonNull Context context) {
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + LOG_FOLDER_NAME + "/";
        ContentResolver resolver = context.getContentResolver();
        Long keptId = keepSingleDownloadsLog(resolver, collection, relativePath);
        if (keptId != null) return ContentUris.withAppendedId(collection, keptId);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_FILE_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        return resolver.insert(collection, values);
    }

    private static void cleanupLegacyLogs(@NonNull Context context) {
        if (legacyLogsCleaned) return;
        legacyLogsCleaned = true;
        cleanupDirectory(new File(context.getFilesDir(), LOG_FOLDER_NAME));
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles != null) cleanupDirectory(new File(externalFiles, LOG_FOLDER_NAME));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cleanupDownloadsMediaStore(context);
        } else {
            cleanupLegacyDownloadsDirectory();
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private static void cleanupDownloadsMediaStore(@NonNull Context context) {
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + LOG_FOLDER_NAME + "/";
        keepSingleDownloadsLog(context.getContentResolver(), collection, relativePath);
    }

    @Nullable
    private static Long keepSingleDownloadsLog(@NonNull ContentResolver resolver,
                                               @NonNull Uri collection,
                                               @NonNull String relativePath) {
        ArrayList<Long> idsToDelete = new ArrayList<>();
        Long exactId = null;
        Long firstFamilyId = null;
        try (Cursor cursor = resolver.query(collection,
            new String[] { BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME },
            MediaStore.MediaColumns.RELATIVE_PATH + "=?",
            new String[] { relativePath },
            null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String displayName = cursor.getString(1);
                    if (displayName == null || !displayName.startsWith(LOG_FILE_PREFIX)) continue;
                    if (firstFamilyId == null) firstFamilyId = id;
                    if (LOG_FILE_NAME.equals(displayName) && exactId == null) exactId = id;
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        Long keepId = exactId != null ? exactId : firstFamilyId;
        if (keepId == null) return null;

        try (Cursor cursor = resolver.query(collection,
            new String[] { BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME },
            MediaStore.MediaColumns.RELATIVE_PATH + "=?",
            new String[] { relativePath },
            null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String displayName = cursor.getString(1);
                    if (displayName != null && displayName.startsWith(LOG_FILE_PREFIX) && id != keepId) {
                        idsToDelete.add(id);
                    }
                }
            }
        } catch (Exception ignored) {
            return keepId;
        }

        for (long id : idsToDelete) {
            try {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null);
            } catch (Exception ignored) {
                // Diagnostics cleanup must not affect terminal sessions.
            }
        }
        normalizeLogRow(resolver, ContentUris.withAppendedId(collection, keepId));
        return keepId;
    }

    private static void normalizeLogRow(@NonNull ContentResolver resolver, @NonNull Uri logUri) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_FILE_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            resolver.update(logUri, values, null, null);
        } catch (Exception ignored) {
            // Diagnostics cleanup must not affect terminal sessions.
        }
    }

    private static void cleanupLegacyDownloadsDirectory() {
        File directory = legacyDownloadsDirectory();
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (!LOG_FILE_NAME.equals(child.getName())) deleteRecursively(child);
        }
    }

    private static void cleanupDirectory(@NonNull File directory) {
        if (!directory.exists()) return;
        deleteRecursively(directory);
    }

    private static void deleteRecursively(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    @NonNull
    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date());
    }

    @NonNull
    private static String clean(@NonNull String value) {
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    @NonNull
    private static String stackTrace(@NonNull Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }
}
