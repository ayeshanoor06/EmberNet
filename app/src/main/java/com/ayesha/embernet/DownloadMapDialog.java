package com.ayesha.embernet;

import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.util.GeoPoint;

public class DownloadMapDialog {

    public interface OnDownloadComplete {
        void onComplete();
        void onCancelled();
    }

    private final Context context;
    private final GeoPoint center;
    private final OnDownloadComplete listener;
    private Dialog dialog;
    private DownloadTask task;

    public DownloadMapDialog(Context context, GeoPoint center,
                             OnDownloadComplete listener) {
        this.context  = context;
        this.center   = center;
        this.listener = listener;
    }

    public void show() {
        // Build dialog view manually
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_download_map, null);

        ProgressBar progressBar = view.findViewById(R.id.download_progress);
        TextView progressText   = view.findViewById(R.id.download_progress_text);
        TextView tilesText      = view.findViewById(R.id.download_tiles_text);
        MaterialButton cancelBtn = view.findViewById(R.id.btn_cancel_download);

        dialog = new MaterialAlertDialogBuilder(context,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setView(view)
                .setCancelable(false)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(R.color.ash);
        dialog.show();

        cancelBtn.setOnClickListener(v -> {
            if (task != null) task.cancel(true);
            dialog.dismiss();
            listener.onCancelled();
        });

        // Start download on background thread
        task = new DownloadTask(progressBar, progressText, tilesText);
        task.execute(center);
    }

    // AsyncTask runs the download off the UI thread
    private class DownloadTask extends AsyncTask<GeoPoint, int[], Long> {

        private final ProgressBar progressBar;
        private final TextView progressText;
        private final TextView tilesText;

        DownloadTask(ProgressBar pb, TextView pt, TextView tt) {
            this.progressBar  = pb;
            this.progressText = pt;
            this.tilesText    = tt;
        }

        @Override
        protected Long doInBackground(GeoPoint... points) {
            OfflineMapManager manager = new OfflineMapManager(context);
            final long[] result = {0};

            manager.downloadArea(points[0], new OfflineMapManager.DownloadCallback() {
                @Override
                public void onProgress(int percent, long done, long total) {
                    if (!isCancelled()) {
                        publishProgress(new int[]{percent, (int) done, (int) total});
                    }
                }

                @Override
                public void onComplete(long totalTiles) {
                    result[0] = totalTiles;
                }

                @Override
                public void onError(String message) {
                    result[0] = -1;
                }
            });

            return result[0];
        }

        @Override
        protected void onProgressUpdate(int[]... values) {
            int percent = values[0][0];
            int done    = values[0][1];
            int total   = values[0][2];

            progressBar.setProgress(percent);
            progressText.setText(percent + "%");
            tilesText.setText(done + " / " + total + " tiles");
        }

        @Override
        protected void onPostExecute(Long totalTiles) {
            if (dialog.isShowing()) dialog.dismiss();
            if (totalTiles >= 0) {
                listener.onComplete();
            }
        }

        @Override
        protected void onCancelled() {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        }
    }
}