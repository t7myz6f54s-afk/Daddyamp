package com.audify.music;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Poweramp-style Music Folders engine.
 *
 * The user grants one or more directory trees (SAF ACTION_OPEN_DOCUMENT_TREE with
 * persistable read permission). This engine recursively walks each tree, extracts
 * tags + embedded artwork, and keeps an incremental scan-state database so rescanning
 * only picks up new/changed/deleted files. The web layer owns the visible catalog
 * (IndexedDB); this engine owns the ground truth of "what is on disk + what changed",
 * plus durable roots and OS-level permission grants.
 */
public class FolderEngine extends SQLiteOpenHelper {

    private static final String TAG = "DaddyAmpFolders";
    private static final long IGNORE_SHORT_DEFAULT_MS = 10_000L;
    private static final String[] AUDIO_EXT = {"mp3","m4a","aac","flac","ogg","opus","wav","wma","ape","aiff","aif","mp4","mka","dsf"};

    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> scanning = new HashSet<>();

    /** Lightweight MediaStore row for the fast path (no file opens). */
    private static class MsMeta {
        String title, artist, album, mime, data;
        long id, albumId, durationMs;
        int year, track;
    }

    public FolderEngine(Activity activity, WebView webView) {
        super(activity, "daddyamp_folders.db", null, 1);
        this.activity = activity;
        this.webView = webView;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE roots (uri TEXT PRIMARY KEY, name TEXT, enabled INTEGER DEFAULT 1, last_scan INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE files (uri TEXT PRIMARY KEY, root_uri TEXT, mtime INTEGER, size INTEGER)");
        db.execSQL("CREATE INDEX idx_files_root ON files(root_uri)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /* ---------------------------------------------------------------- roots */

    /** Add (or re-enable) a root after the user picked it. */
    public synchronized void addRoot(String treeUri, String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("uri", treeUri);
        cv.put("name", name != null ? name : "Music");
        cv.put("enabled", 1);
        db.insertWithOnConflict("roots", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized JSONArray getRoots(boolean testAccess) {
        JSONArray out = new JSONArray();
        Cursor c = getReadableDatabase().query("roots", null, null, null, null, null, "name ASC");
        if (c == null) return out;
        try {
            int u = c.getColumnIndexOrThrow("uri"), n = c.getColumnIndexOrThrow("name"),
                e = c.getColumnIndexOrThrow("enabled"), l = c.getColumnIndexOrThrow("last_scan");
            while (c.moveToNext()) {
                try {
                    String uri = c.getString(u);
                    JSONObject o = new JSONObject();
                    o.put("uri", uri);
                    o.put("name", c.getString(n));
                    o.put("enabled", c.getInt(e) == 1);
                    o.put("lastScan", c.getLong(l));
                    o.put("accessLost", testAccess && !canAccess(uri));
                    out.put(o);
                } catch (Exception ignored) {}
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** True if the persisted tree permission still resolves to a readable directory. */
    private boolean canAccess(String treeUri) {
        try {
            Uri tree = Uri.parse(treeUri);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree));
            ContentResolver cr = activity.getContentResolver();
            Cursor c = cr.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    null, null, null);
            if (c != null) {
                boolean ok = c.moveToFirst() || true; // empty dir is still accessible
                c.close();
                return ok;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void setEnabled(String treeUri, boolean enabled) {
        ContentValues cv = new ContentValues();
        cv.put("enabled", enabled ? 1 : 0);
        getWritableDatabase().update("roots", cv, "uri=?", new String[]{treeUri});
    }

    /** Un-index a root. Returns number of tracks removed; files on disk untouched. */
    public synchronized int removeRoot(String treeUri) {
        SQLiteDatabase db = getWritableDatabase();
        int n = db.delete("files", "root_uri=?", new String[]{treeUri});
        db.delete("roots", "uri=?", new String[]{treeUri});
        return n;
    }

    public synchronized long lastScan(String treeUri) {
        Cursor c = getReadableDatabase().query("roots", new String[]{"last_scan"},
                "uri=?", new String[]{treeUri}, null, null, null);
        if (c == null) return 0;
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    /* --------------------------------------------------------------- scanner */

    public boolean isScanning(String treeUri) {
        synchronized (scanning) {
            return scanning.contains(treeUri);
        }
    }

    /**
     * Recursive scan. full=true re-reads everything; full=false is incremental on
     * mtime/size. Emits chunked progress to window.onFolderScanProgress.
     */
    public void scan(final String treeUri, final boolean full, final long ignoreShortMs) {
        synchronized (scanning) {
            if (scanning.contains(treeUri)) return;
            scanning.add(treeUri);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    scanSync(treeUri, full, ignoreShortMs > 0 ? ignoreShortMs : IGNORE_SHORT_DEFAULT_MS);
                } catch (Throwable t) {
                    Log.e(TAG, "scan failed", t);
                    emitProgress(treeUri, doneObj(t.getMessage()));
                } finally {
                    synchronized (scanning) { scanning.remove(treeUri); }
                }
            }
        }, "daddyamp-scan").start();
    }

    private JSONObject doneObj(String err) {
        JSONObject o = new JSONObject();
        try {
            o.put("phase", "done");
            if (err != null) o.put("error", err);
        } catch (Exception ignored) {}
        return o;
    }

    private void scanSync(String treeUri, boolean full, long ignoreShortMs) throws Exception {
        Uri tree = Uri.parse(treeUri);
        String rootName = tree.getLastPathSegment() != null ? tree.getLastPathSegment() : "Root";
        SQLiteDatabase db = getWritableDatabase();
        final long startMs = System.currentTimeMillis();

        // FAST PATH: one MediaStore query gives us tags/duration/albums for every
        // indexed file — no per-file MediaMetadataRetriever open (that was the
        // "500+ songs takes forever" killer for 10k-track libraries).
        String msPrefix = null;
        try {
            String treeDoc = DocumentsContract.getTreeDocumentId(tree);
            msPrefix = docIdToFsPath(treeDoc);
        } catch (Exception ignored) {}
        java.util.Map<String, MsMeta> msIndex = loadMediaStoreIndex(msPrefix);

        // Ultra-fast full scan for normal device folders: when the SAF tree maps
        // to a filesystem prefix and MediaStore already knows the audio, do not
        // recursively query every document and open retrievers. Build the folder
        // catalog from the single MediaStore query instead. This is the 10k+
        // library path.
        if (full && msPrefix != null && msIndex != null && !msIndex.isEmpty()) {
            scanSyncFromMediaStore(treeUri, tree, rootName, msPrefix, msIndex, db, ignoreShortMs, startMs);
            return;
        }

        // Known file index (uri -> "mtime|size")
        final java.util.Map<String, String> known = new java.util.HashMap<>();
        if (!full) {
            Cursor c = db.query("files", new String[]{"uri", "mtime", "size"},
                    "root_uri=?", new String[]{treeUri}, null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) known.put(c.getString(0), c.getLong(1) + "|" + c.getLong(2));
                } finally { c.close(); }
            }
        }

        final Set<String> seen = new HashSet<>();
        final java.util.Map<String, String> current = new java.util.HashMap<>(); // uri -> mtime|size
        final Set<String> artThisRun = new HashSet<>();
        final int[] stats = new int[]{0, 0, 0, 0};          // scanned, added, updated
        final long[] durAcc = new long[]{0};
        final JSONArray batch = new JSONArray();
        lastEmitMs = System.currentTimeMillis();
        walk(tree, tree, "", known, seen, current, artThisRun, stats, durAcc, batch, ignoreShortMs, msIndex, msPrefix);

        // Removed files: known but not seen this pass
        JSONArray removed = new JSONArray();
        for (String u : known.keySet()) {
            if (!seen.contains(u)) removed.put(u);
        }
        for (int i = 0; i < removed.length(); i++) {
            try { db.delete("files", "uri=?", new String[]{removed.getString(i)}); } catch (Exception ignored) {}
        }

        // Upsert fresh mtime/size for every current file (keeps incremental state accurate)
        db.beginTransaction();
        try {
            for (java.util.Map.Entry<String, String> en : current.entrySet()) {
                String[] parts = en.getValue().split("\\|");
                ContentValues cv = new ContentValues();
                cv.put("uri", en.getKey());
                cv.put("root_uri", treeUri);
                cv.put("mtime", parts.length > 0 ? Long.parseLong(parts[0]) : 0L);
                cv.put("size", parts.length > 1 ? Long.parseLong(parts[1]) : 0L);
                db.insertWithOnConflict("files", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        ContentValues rootCv = new ContentValues();
        rootCv.put("last_scan", System.currentTimeMillis());
        db.update("roots", rootCv, "uri=?", new String[]{treeUri});

        JSONObject done = new JSONObject();
        try {
            done.put("phase", "done");
            done.put("rootUri", treeUri);
            done.put("rootName", rootName);
            done.put("scanned", stats[0]);
            done.put("added", stats[1]);
            done.put("updated", stats[2]);
            done.put("removedCount", removed.length());
            done.put("removedUris", removed);
            done.put("elapsedMs", System.currentTimeMillis() - startMs);
            if (batch.length() > 0) done.put("batch", batch);
        } catch (Exception ignored) {}
        emitProgress(treeUri, done);
    }

    private void walk(Uri tree, Uri dir, String relPath,
                      java.util.Map<String, String> known, Set<String> seen,
                      java.util.Map<String, String> current, Set<String> artThisRun,
                      int[] stats, long[] durAcc, JSONArray batch, long ignoreShortMs,
                      java.util.Map<String, MsMeta> msIndex, String msPrefix) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(dir));
        Cursor c = null;
        try {
            c = activity.getContentResolver().query(childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_FLAGS
                    }, null, null, null);
        } catch (Exception e) {
            return;
        }
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                String docId = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long mtime = c.getLong(3);
                long size = c.getLong(4);
                long flags = c.getLong(5);
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId);

                if (name == null) continue;
                if (name.startsWith(".") || name.startsWith("._") || name.equals("Thumbs.db")) continue;

                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                if (isDir) {
                    walk(tree, childUri, relPath.isEmpty() ? name : relPath + "/" + name,
                            known, seen, current, artThisRun, stats, durAcc, batch, ignoreShortMs, msIndex, msPrefix);
                    continue;
                }
                if (!isAudio(mime, name)) continue;
                if (size > 0 && size < 1024) continue;

                String key = childUri.toString();
                seen.add(key);
                current.put(key, mtime + "|" + size);
                stats[0]++;
                maybeHeartbeat(tree.toString(), relPath, stats);

                String knownMeta = known.get(key);
                if (knownMeta != null && knownMeta.equals(mtime + "|" + size)) {
                    flushWalkBatch(tree.toString(), batch, stats, relPath);
                    continue; // unchanged
                }
                if (knownMeta != null) stats[2]++; else stats[1]++;

                JSONObject song = null;
                String fsPath = msPrefix != null ? docIdToFsPath(docId) : null;
                if (fsPath != null && msIndex != null && !msIndex.isEmpty()) {
                    MsMeta m = msIndex.get(fsPath.toLowerCase());
                    if (m != null) {
                        song = buildSongFromStore(tree, childUri, relPath, name, mime, mtime, size,
                                m, artThisRun, durAcc);
                    }
                }
                if (song == null) {
                    // Slow path: only files MediaStore does not know (rare).
                    song = buildSong(tree, childUri, relPath, name, mime, mtime, size,
                            artThisRun, ignoreShortMs, durAcc);
                }
                if (song == null) continue;

                if (durationIgnore(song, ignoreShortMs)) continue;

                batch.put(song);
                if (batch.length() >= 200) flushWalkBatch(tree.toString(), batch, stats, relPath);
            }
        } finally {
            c.close();
        }
    }

    private boolean durationIgnore(JSONObject song, long ignoreShortMs) {
        try {
            return ignoreShortMs > 0 && song.optLong("duration", 0) > 0 &&
                    song.optLong("duration", 0) * 1000 < ignoreShortMs;
        } catch (Exception e) {
            return false;
        }
    }

    private long lastEmitMs = 0;
    private void flushWalkBatch(String rootUri, JSONArray batch, int[] stats, String relPath) {
        if (batch.length() == 0) return;
        JSONObject o = new JSONObject();
        try {
            o.put("phase", "walking");
            o.put("scanned", stats[0]);
            o.put("added", stats[1]);
            o.put("updated", stats[2]);
            o.put("currentPath", relPath);
            o.put("batch", batch);
        } catch (Exception ignored) {}
        lastEmitMs = System.currentTimeMillis();
        emitProgress(rootUri, o);
        while (batch.length() > 0) batch.remove(0);
    }

    /** Emit a live heartbeat so the UI never looks frozen during long unchanged walks. */
    private void maybeHeartbeat(String rootUri, String relPath, int[] stats) {
        long now = System.currentTimeMillis();
        if (now - lastEmitMs < 1000) return;
        lastEmitMs = now;
        JSONObject o = new JSONObject();
        try {
            o.put("phase", "walking");
            o.put("scanned", stats[0]);
            o.put("added", stats[1]);
            o.put("updated", stats[2]);
            o.put("currentPath", relPath);
            o.put("heartbeat", true);
        } catch (Exception ignored) {}
        emitProgress(rootUri, o);
    }

    /** "primary:Music/a.mp3" -> "/storage/emulated/0/Music/a.mp3" (or null). */
    private String docIdToFsPath(String docId) {
        if (docId == null) return null;
        int i = docId.indexOf(':');
        if (i <= 0 || i == docId.length() - 1) return null;
        String vol = docId.substring(0, i);
        String rel = docId.substring(i + 1);
        String base;
        if (vol.equals("primary")) {
            base = Environment.getExternalStorageDirectory().getAbsolutePath();
        } else {
            base = "/storage/" + vol;
        }
        return base + "/" + rel;
    }

    private String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private java.util.Map<String, MsMeta> loadMediaStoreIndex(String treeFsPrefix) {
        java.util.Map<String, MsMeta> map = new java.util.HashMap<>();
        try {
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.TRACK
            };
            String selection = null;
            String[] selArgs = null;
            if (treeFsPrefix != null) {
                selection = MediaStore.Audio.Media.DATA + " LIKE ? ESCAPE '\\'";
                selArgs = new String[]{likeEscape(treeFsPrefix) + "/%"};
            }
            Cursor c = activity.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, selArgs, null);
            if (c == null) return map;
            try {
                while (c.moveToNext()) {
                    MsMeta m = new MsMeta();
                    m.id = c.getLong(0);
                    m.title = c.getString(1);
                    m.artist = c.getString(2);
                    m.album = c.getString(3);
                    m.albumId = c.getLong(4);
                    m.durationMs = c.getLong(5);
                    m.year = c.getInt(6);
                    m.mime = c.getString(7);
                    String data = c.getString(8);
                    m.data = data;
                    try { m.track = c.getInt(9); } catch (Exception ignored) { m.track = 0; }
                    if (data == null || data.isEmpty()) continue;
                    map.put(data.toLowerCase(), m);
                }
            } finally {
                c.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaStore index unavailable, falling back to retriever: " + e);
        }
        return map;
    }

    private void scanSyncFromMediaStore(String treeUri, Uri tree, String rootName, String msPrefix,
                                        java.util.Map<String, MsMeta> msIndex, SQLiteDatabase db,
                                        long ignoreShortMs, long startMs) throws Exception {
        final JSONArray batch = new JSONArray();
        final java.util.Map<String, String> current = new java.util.HashMap<>();
        int scanned = 0, added = 0;

        db.beginTransaction();
        try {
            db.delete("files", "root_uri=?", new String[]{treeUri});
            for (MsMeta m : msIndex.values()) {
                if (m == null || m.data == null || m.durationMs <= 0) continue;
                if (ignoreShortMs > 0 && m.durationMs < ignoreShortMs) continue;
                JSONObject song = buildSongFastFromStore(tree, rootName, msPrefix, m);
                if (song == null) continue;
                String uri = song.optString("url", "");
                if (uri.isEmpty()) continue;
                long size = 0L, mtime = 0L;
                try { File f = new File(m.data); if (f.exists()) { size = f.length(); mtime = f.lastModified(); } } catch (Exception ignored) {}
                ContentValues cv = new ContentValues();
                cv.put("uri", uri); cv.put("root_uri", treeUri); cv.put("mtime", mtime); cv.put("size", size);
                db.insertWithOnConflict("files", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                current.put(uri, mtime + "|" + size);
                batch.put(song); scanned++; added++;
                if (batch.length() >= 500) {
                    emitFastBatch(treeUri, batch, scanned, added, 0, rootName);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        ContentValues rootCv = new ContentValues();
        rootCv.put("last_scan", System.currentTimeMillis());
        db.update("roots", rootCv, "uri=?", new String[]{treeUri});

        JSONObject done = new JSONObject();
        try {
            done.put("phase", "done"); done.put("rootUri", treeUri); done.put("rootName", rootName);
            done.put("scanned", scanned); done.put("added", added); done.put("updated", 0);
            done.put("removedCount", 0); done.put("removedUris", new JSONArray());
            done.put("elapsedMs", System.currentTimeMillis() - startMs);
            if (batch.length() > 0) done.put("batch", batch);
        } catch (Exception ignored) {}
        emitProgress(treeUri, done);
    }

    private void emitFastBatch(String treeUri, JSONArray batch, int scanned, int added, int updated, String relPath) {
        JSONObject o = new JSONObject();
        try {
            o.put("phase", "walking"); o.put("rootUri", treeUri); o.put("scanned", scanned);
            o.put("added", added); o.put("updated", updated); o.put("currentPath", relPath); o.put("batch", batch);
        } catch (Exception ignored) {}
        emitProgress(treeUri, o);
        while (batch.length() > 0) batch.remove(0);
    }

    private JSONObject buildSongFastFromStore(Uri tree, String rootName, String msPrefix, MsMeta m) {
        try {
            Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, m.id);
            String rel = "";
            String name = "Audio";
            if (m.data != null) {
                if (m.data.startsWith(msPrefix)) {
                    rel = m.data.substring(msPrefix.length());
                    if (rel.startsWith("/")) rel = rel.substring(1);
                }
                int slash = rel.lastIndexOf('/');
                name = slash >= 0 ? rel.substring(slash + 1) : (rel.isEmpty() ? new File(m.data).getName() : rel);
                rel = slash >= 0 ? rel.substring(0, slash) : "";
            }
            JSONObject song = new JSONObject();
            song.put("title", m.title != null ? m.title : stripExt(name));
            song.put("artist", (m.artist != null && !m.artist.equals("<unknown>")) ? m.artist : "Unknown Artist");
            song.put("album", (m.album != null && !m.album.isEmpty()) ? m.album : (rootName != null ? rootName : "Folder Audio"));
            song.put("albumArtist", ""); song.put("genre", "Folder Audio");
            song.put("year", m.year > 0 && m.year < 3000 ? m.year : 0);
            song.put("duration", m.durationMs / 1000); song.put("trackNumber", normalizeTrackNumber(m.track)); song.put("mimeType", m.mime != null ? m.mime : "audio/mpeg");
            song.put("size", 0); song.put("mtime", 0);
            song.put("url", contentUri.toString()); song.put("path", contentUri.toString()); song.put("filePath", m.data != null ? m.data : "");
            song.put("rootUri", tree.toString()); song.put("folderPath", rel); song.put("source", "folder"); song.put("docName", name);
            song.put("favorite", 0); song.put("play_count", 0); song.put("lyrics", ""); song.put("imported", true);
            song.put("artwork", m.albumId > 0 ? ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), m.albumId).toString() : "");
            return song;
        } catch (Exception e) { return null; }
    }

    /** Build a song from MediaStore metadata — zero file opens (except album art). */
    private JSONObject buildSongFromStore(Uri tree, Uri fileUri, String relPath, String name, String mime,
                                          long mtime, long size, MsMeta m, Set<String> artThisRun,
                                          long[] durAcc) {
        try {
            if (m.durationMs <= 0) return null; // no reliable duration -> retriever fallback
            String title = m.title, artist = m.artist, album = m.album;
            String fallbackAlbum = relPath.isEmpty() ? name : relPath.replace('/', ' ');
            JSONObject song = new JSONObject();
            if (durAcc != null && m.durationMs > 0) durAcc[0] += m.durationMs;
            song.put("title", title != null ? title : stripExt(name));
            song.put("artist", (artist != null && !artist.equals("<unknown>")) ? artist : "Unknown Artist");
            song.put("album", (album != null && !album.isEmpty()) ? album : fallbackAlbum);
            song.put("albumArtist", "");
            song.put("genre", "Folder Audio");
            song.put("year", m.year > 0 && m.year < 3000 ? m.year : 0);
            song.put("duration", m.durationMs / 1000);
            song.put("trackNumber", normalizeTrackNumber(m.track));
            song.put("mimeType", m.mime != null ? m.mime : (mime != null ? mime : "audio/mpeg"));
            song.put("size", size);
            song.put("mtime", mtime);
            song.put("url", fileUri.toString());
            song.put("path", fileUri.toString());
            song.put("filePath", "");
            song.put("rootUri", tree.toString());
            song.put("folderPath", relPath);
            song.put("source", "folder");
            song.put("docName", name);
            song.put("favorite", 0);
            song.put("play_count", 0);
            song.put("lyrics", "");
            song.put("imported", true);
            song.put("artwork", extractArtById(tree, fileUri, name, relPath, m.albumId, artThisRun));
            return song;
        } catch (Exception e) {
            return null;
        }
    }

    /** Album art cached on disk per album id; at most one retriever open per album. */
    private String extractArtById(Uri tree, Uri fileUri, String name, String relPath,
                                  long albumId, Set<String> artThisRun) {
        try {
            String key = albumId > 0 ? ("aid" + albumId) : ("folder" + sha1(tree.toString() + "|" + relPath));
            String fileKey = sha1(tree.toString() + "|" + key);
            File dir = new File(activity.getCacheDir(), "art");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, fileKey + ".jpg");
            if (!out.exists() && !artThisRun.contains(key)) {
                artThisRun.add(key);
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                try {
                    ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(fileUri, "r");
                    if (pfd != null) {
                        try { mmr.setDataSource(pfd.getFileDescriptor()); } finally { pfd.close(); }
                    } else {
                        mmr.setDataSource(activity, fileUri);
                    }
                    byte[] pic = mmr.getEmbeddedPicture();
                    if (pic != null && pic.length > 512) {
                        FileOutputStream fos = new FileOutputStream(out);
                        fos.write(pic);
                        fos.close();
                    }
                } finally {
                    try { mmr.release(); } catch (Exception ignored) {}
                }
            }
            if (out.exists() && out.length() > 512) {
                return "file://" + out.getAbsolutePath();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private JSONObject buildSong(Uri tree, Uri fileUri, String relPath, String name, String mime,
                                 long mtime, long size, Set<String> artThisRun,
                                 long ignoreShortMs, long[] durAcc) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        JSONObject song = new JSONObject();
        try {
            ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(fileUri, "r");
            if (pfd != null) {
                try {
                    mmr.setDataSource(pfd.getFileDescriptor());
                } finally {
                    pfd.close();
                }
            } else {
                mmr.setDataSource(activity, fileUri);
            }
            String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            String genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            String durS = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String trackS = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            String yearS = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
            String mmrMime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);

            long durMs = 0;
            try { durMs = Long.parseLong(durS != null ? durS : "0"); } catch (Exception ignored) {}
            if (durMs > 0) durAcc[0] += durMs;

            String fallbackAlbum = relPath.isEmpty() ? name : relPath.replace('/', ' ');
            song.put("title", title != null ? title : stripExt(name));
            song.put("artist", (artist != null && !artist.equals("<unknown>")) ? artist : "Unknown Artist");
            song.put("album", (album != null && !album.isEmpty()) ? album : fallbackAlbum);
            song.put("albumArtist", (albumArtist != null && !albumArtist.equals("<unknown>")) ? albumArtist : "");
            song.put("genre", genre != null && !genre.equals("<unknown>") && !genre.isEmpty() ? genre : "Folder Audio");
            song.put("year", yearS != null ? parseIntSafe(yearS) : 0);
            song.put("duration", durMs / 1000);
            song.put("trackNumber", parseTrackNumber(trackS));
            song.put("mimeType", mmrMime != null ? mmrMime : (mime != null ? mime : "audio/mpeg"));
            song.put("size", size);
            song.put("mtime", mtime);
            song.put("url", fileUri.toString());
            song.put("path", fileUri.toString());
            song.put("filePath", "");
            song.put("rootUri", tree.toString());
            song.put("folderPath", relPath);
            song.put("source", "folder");
            song.put("docName", name);
            song.put("favorite", 0);
            song.put("play_count", 0);
            song.put("lyrics", "");
            song.put("imported", true);

            String artUrl = extractArt(tree, fileUri, song, mmr, artThisRun);
            song.put("artwork", artUrl);
        } catch (Exception e) {
            return null;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return song;
    }

    private int parseTrackNumber(String raw) {
        if (raw == null) return 0;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(raw);
            if (m.find()) return normalizeTrackNumber(Integer.parseInt(m.group()));
        } catch (Exception ignored) {}
        return 0;
    }

    private int normalizeTrackNumber(int raw) {
        int v = Math.abs(raw);
        if (v > 1000) v = v % 1000;
        return v > 0 && v < 1000 ? v : 0;
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private int parseIntSafe(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 && v < 3000 ? v : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Extract embedded artwork once per album key and cache it on disk. */
    private String extractArt(Uri tree, Uri fileUri, JSONObject song, MediaMetadataRetriever mmr,
                              Set<String> artThisRun) {
        try {
            String albumKey = song.optString("album", "") + "|" + song.optString("albumArtist", "")
                    + "|" + song.optString("folderPath", "");
            String key = sha1(tree.toString() + "|" + albumKey);
            File dir = new File(activity.getCacheDir(), "art");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, key + ".jpg");

            if (!out.exists() && !artThisRun.contains(key)) {
                byte[] pic = mmr.getEmbeddedPicture();
                artThisRun.add(key);
                if (pic != null && pic.length > 512) {
                    FileOutputStream fos = new FileOutputStream(out);
                    fos.write(pic);
                    fos.close();
                }
            }
            if (out.exists() && out.length() > 512) {
                return "file://" + out.getAbsolutePath();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private boolean isAudio(String mime, String name) {
        if (mime != null && mime.startsWith("audio/")) return true;
        int i = name.lastIndexOf('.');
        if (i < 0) return false;
        String ext = name.substring(i + 1).toLowerCase();
        for (String e : AUDIO_EXT) if (e.equals(ext)) return true;
        return false;
    }

    /* ------------------------------------------------------------- progress */

    private void emitProgress(final String treeUri, final JSONObject obj) {
        main.post(new Runnable() {
            @Override
            public void run() {
                String js = "if (typeof window.onFolderScanProgress === 'function') { window.onFolderScanProgress("
                        + obj.toString() + "); }";
                webView.evaluateJavascript(js, null);
            }
        });
    }
}
