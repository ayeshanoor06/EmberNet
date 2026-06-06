package com.ayesha.embernet;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.InputStream;

public class OfflineMapManager {

    private static final String TAG            = "OfflineMapManager";
    private static final String PREFS_NAME     = "embernet_map";
    private static final String KEY_MAP_SAVED  = "offline_map_saved";
    private static final String KEY_MAP_LAT    = "map_center_lat";
    private static final String KEY_MAP_LON    = "map_center_lon";

    public static final int    ZOOM_MIN        = 10;
    public static final int    ZOOM_MAX        = 16;
    private static final double DOWNLOAD_RADIUS = 0.08;

    private final Context           context;
    private final SharedPreferences prefs;

    public interface DownloadCallback {
        void onProgress(int percent, long done, long total);
        void onComplete(long totalTiles);
        void onError(String message);
    }

    public OfflineMapManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isMapSaved() {
        return prefs.getBoolean(KEY_MAP_SAVED, false);
    }

    public GeoPoint getSavedMapCenter() {
        double lat = Double.longBitsToDouble(
                prefs.getLong(KEY_MAP_LAT,
                        Double.doubleToLongBits(33.6938)));
        double lon = Double.longBitsToDouble(
                prefs.getLong(KEY_MAP_LON,
                        Double.doubleToLongBits(73.0651)));
        return new GeoPoint(lat, lon);
    }

    private void saveMapCenter(GeoPoint center) {
        prefs.edit()
                .putBoolean(KEY_MAP_SAVED, true)
                .putLong(KEY_MAP_LAT,
                        Double.doubleToLongBits(
                                center.getLatitude()))
                .putLong(KEY_MAP_LON,
                        Double.doubleToLongBits(
                                center.getLongitude()))
                .apply();
    }

    public File getOfflineTileFile() {
        return new File(context.getCacheDir(),
                "embernet_tiles.sqlite");
    }

    public void clearOfflineMap() {
        File f = getOfflineTileFile();
        if (f.exists()) f.delete();
        prefs.edit().putBoolean(KEY_MAP_SAVED, false).apply();
    }

    public BoundingBox buildBoundingBox(GeoPoint center) {
        return new BoundingBox(
                center.getLatitude()  + DOWNLOAD_RADIUS,
                center.getLongitude() + DOWNLOAD_RADIUS,
                center.getLatitude()  - DOWNLOAD_RADIUS,
                center.getLongitude() - DOWNLOAD_RADIUS);
    }

    public long estimateTileCount(BoundingBox bbox,
                                  int zoomMin, int zoomMax) {
        long total = 0;
        for (int z = zoomMin; z <= zoomMax; z++) {
            double t  = Math.pow(2, z);
            double xF = (bbox.getLonEast()
                    - bbox.getLonWest())  / 360.0;
            double yF = (bbox.getLatNorth()
                    - bbox.getLatSouth()) / 180.0;
            total += (long)(t * t * xF * yF) + 1;
        }
        return total;
    }

    public void downloadArea(GeoPoint center,
                             DownloadCallback callback) {
        BoundingBox bbox      = buildBoundingBox(center);
        long        estimated = estimateTileCount(
                bbox, ZOOM_MIN, ZOOM_MAX);

        Log.d(TAG, "Starting download — "
                + estimated + " estimated tiles");

        // ── KEY FIX: delete old file before starting ──────────
        // This prevents UNIQUE constraint SQLite errors when
        // the user taps download a second time
        File outputFile = getOfflineTileFile();
        if (outputFile.exists()) {
            outputFile.delete();
            Log.d(TAG, "Deleted old tile cache");
        }

        org.osmdroid.tileprovider.modules
                .SqliteArchiveTileWriter writer = null;

        try {
            writer =
                    new org.osmdroid.tileprovider.modules
                            .SqliteArchiveTileWriter(
                            outputFile.getAbsolutePath());

            long[] downloaded = {0};
            int    lastPct    = -1;

            for (int zoom = ZOOM_MIN;
                 zoom <= ZOOM_MAX; zoom++) {
                int xMin = lonToTileX(
                        bbox.getLonWest(),  zoom);
                int xMax = lonToTileX(
                        bbox.getLonEast(),  zoom);
                int yMin = latToTileY(
                        bbox.getLatNorth(), zoom);
                int yMax = latToTileY(
                        bbox.getLatSouth(), zoom);

                for (int x = xMin; x <= xMax; x++) {
                    for (int y = yMin; y <= yMax; y++) {
                        String url =
                                buildTileUrl(zoom, x, y);
                        downloadSingleTile(
                                url, zoom, x, y, writer);
                        downloaded[0]++;
                        int pct = (int)(
                                (downloaded[0] * 100L)
                                        / estimated);
                        if (pct != lastPct) {
                            lastPct = pct;
                            callback.onProgress(
                                    pct, downloaded[0],
                                    estimated);
                        }
                    }
                }
            }

            writer.onDetach();
            writer = null;
            saveMapCenter(center);
            callback.onComplete(downloaded[0]);
            Log.d(TAG, "Download complete — "
                    + downloaded[0] + " tiles");

        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + e.getMessage());
            callback.onError(e.getMessage());
            if (writer != null) {
                try { writer.onDetach(); }
                catch (Exception ignored) {}
            }
        }
    }

    private void downloadSingleTile(
            String url, int zoom, int x, int y,
            org.osmdroid.tileprovider.modules
                    .SqliteArchiveTileWriter writer) {
        try {
            java.net.URL tileUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection)
                            tileUrl.openConnection();
            conn.setRequestProperty("User-Agent",
                    context.getPackageName());
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.connect();

            if (conn.getResponseCode() == 200) {
                try (InputStream is =
                             conn.getInputStream()) {
                    long tileIndex =
                            org.osmdroid.util.MapTileIndex
                                    .getTileIndex(zoom, x, y);
                    writer.saveFile(
                            TileSourceFactory.MAPNIK,
                            tileIndex, is, null);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "Tile skip z=" + zoom
                    + " x=" + x + " y=" + y);
        }
    }

    private int lonToTileX(double lon, int zoom) {
        return (int) Math.floor(
                (lon + 180.0) / 360.0 * (1 << zoom));
    }

    private int latToTileY(double lat, int zoom) {
        double rad = Math.toRadians(lat);
        return (int) Math.floor(
                (1.0 - Math.log(
                        Math.tan(rad) + 1.0 / Math.cos(rad))
                        / Math.PI) / 2.0 * (1 << zoom));
    }

    private String buildTileUrl(int zoom, int x, int y) {
        String[] s = {"a", "b", "c"};
        return "https://" + s[(x + y) % 3]
                + ".tile.openstreetmap.org/"
                + zoom + "/" + x + "/" + y + ".png";
    }
}