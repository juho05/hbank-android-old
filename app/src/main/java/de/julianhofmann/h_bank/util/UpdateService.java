package de.julianhofmann.h_bank.util;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.jetbrains.annotations.NotNull;

import java.io.File;

import de.julianhofmann.h_bank.BuildConfig;
import de.julianhofmann.h_bank.R;
import de.julianhofmann.h_bank.api.RetrofitService;
import de.julianhofmann.h_bank.api.models.VersionModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UpdateService {

    public static boolean askedForUpdate = false;

    public static void update(Context context, boolean autoUpdate) {
        if (RetrofitService.getHbankApi() != null) {
            Call<VersionModel> call = RetrofitService.getHbankApi().getVersion();
            call.enqueue(new Callback<VersionModel>() {
                @Override
                public void onResponse(@NotNull Call<VersionModel> call, @NotNull Response<VersionModel> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().getVersion() > BuildConfig.VERSION_CODE) {
                            DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                                if (which == dialog.BUTTON_POSITIVE) {
                                    installUpdate(context);
                                } else if (which == dialog.BUTTON_NEUTRAL) {
                                    SettingsService.setCheckForUpdates(false);
                                }
                                askedForUpdate = true;
                            };
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            if (autoUpdate)
                                builder.setTitle(R.string.update_available).setMessage(R.string.update_question).setPositiveButton(R.string.yes, dialogClickListener).setNegativeButton(R.string.no, dialogClickListener).setNeutralButton(R.string.dont_ask, dialogClickListener).show();
                            else
                                builder.setTitle(R.string.update_available).setMessage(R.string.update_question).setPositiveButton(R.string.yes, dialogClickListener).setNegativeButton(R.string.no, dialogClickListener).show();

                        } else if (!autoUpdate) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTitle(R.string.update).setMessage(R.string.up_to_date).setNeutralButton(context.getString(R.string.ok), null).show();
                        }
                    }
                }

                @Override
                public void onFailure(@NotNull Call<VersionModel> call, @NotNull Throwable t) {
                    if (!autoUpdate) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.update).setMessage(R.string.cannot_reach_server).setNeutralButton(context.getString(R.string.ok), null).show();
                    }
                }
            });
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void installUpdate(Context context) {
        String destination = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/apk/h-bank.apk";

        Uri uri = Uri.parse("file://" + destination);

        File file = new File(destination);
        if (file.exists()) file.delete();

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        Uri downloadUri = Uri.parse(RetrofitService.getUrl() + "apk");

        DownloadManager.Request request = new DownloadManager.Request(downloadUri);
        request.setMimeType("application/vnd.android.package-archive");
        request.setTitle(context.getString(R.string.update));
        request.setDescription(context.getString(R.string.downloading));
        request.setDestinationUri(uri);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Uri fileUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                i.setDataAndType(fileUri, "application/vnd.android.package-archive");
                context.startActivity(i);
                context.unregisterReceiver(this);
            }
        };
        context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        Toast.makeText(context, R.string.downloading, Toast.LENGTH_SHORT).show();
        downloadManager.enqueue(request);

    }
}
