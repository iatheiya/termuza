package com.termux.shared.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.termux.shared.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.Error;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.shared.theme.NightMode;

import org.commonmark.node.FencedCodeBlock;
import org.jetbrains.annotations.NotNull;

import io.noties.markwon.Markwon;
import io.noties.markwon.recycler.MarkwonAdapter;
import io.noties.markwon.recycler.SimpleEntry;

public class ReportActivity extends AppCompatActivity {

    private static final String CLASS_NAME = ReportActivity.class.getCanonicalName();
    private static final String ACTION_DELETE_REPORT_INFO_OBJECT_FILE = CLASS_NAME + ".ACTION_DELETE_REPORT_INFO_OBJECT_FILE";

    private static final String EXTRA_REPORT_INFO_OBJECT = CLASS_NAME + ".EXTRA_REPORT_INFO_OBJECT";
    private static final String EXTRA_REPORT_INFO_OBJECT_FILE_PATH = CLASS_NAME + ".EXTRA_REPORT_INFO_OBJECT_FILE_PATH";

    private static final String CACHE_DIR_BASENAME = "report_activity";
    private static final String CACHE_FILE_BASENAME_PREFIX = "report_info_";

    public static final int REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE = 1000;

    public static final int ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES = 1000 * 1024;

    private ReportInfo mReportInfo;
    private String mReportInfoFilePath;
    private String mReportActivityMarkdownString;
    private Bundle mBundle;

    private static final String LOG_TAG = "ReportActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.logVerbose(LOG_TAG, "onCreate");

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_report);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        mBundle = null;
        Intent intent = getIntent();
        if (intent != null)
            mBundle = intent.getExtras();
        else if (savedInstanceState != null)
            mBundle = savedInstanceState;

        updateUI();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Logger.logVerbose(LOG_TAG, "onNewIntent");

        setIntent(intent);

        if (intent != null) {
            deleteReportInfoFile(this, mReportInfoFilePath);
            mBundle = intent.getExtras();
            updateUI();
        }
    }

    private void updateUI() {
        if (mBundle == null) {
            finish(); return;
        }

        mReportInfo = null;
        mReportInfoFilePath = null;

        if (mBundle.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) {
            mReportInfoFilePath = mBundle.getString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH);
            if (mReportInfoFilePath != null) {
                try {
                    FileUtils.ReadSerializableObjectResult result = FileUtils.readSerializableObjectFromFile(ReportInfo.class.getSimpleName(), mReportInfoFilePath, ReportInfo.class, false);
                    if (result.error != null) {
                        Logger.logErrorExtended(LOG_TAG, result.error.toString());
                        Logger.showToast(this, Error.getMinimalErrorString(result.error), true);
                        finish(); return;
                    } else {
                        if (result.serializableObject != null)
                            mReportInfo = (ReportInfo) result.serializableObject;
                    }
                } catch (Exception e) {
                    Logger.logErrorAndShowToast(this, LOG_TAG, e.getMessage());
                }
            }
        } else {
            mReportInfo = (ReportInfo) mBundle.getSerializable(EXTRA_REPORT_INFO_OBJECT);
        }

        if (mReportInfo == null) {
            finish(); return;
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (mReportInfo.reportTitle != null)
                actionBar.setTitle(mReportInfo.reportTitle);
            else
                actionBar.setTitle(TermuxConstants.TERMUX_APP_NAME + " App Report");
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view);

        final Markwon markwon = MarkdownUtils.getRecyclerMarkwonBuilder(this);

        final MarkwonAdapter adapter = MarkwonAdapter.builderTextViewIsRoot(R.layout.markdown_adapter_node_default)
            .include(FencedCodeBlock.class, SimpleEntry.create(R.layout.markdown_adapter_node_code_block, R.id.code_text_view))
            .build();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        generateReportActivityMarkdownString();
        adapter.setMarkdown(markwon, mReportActivityMarkdownString);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mBundle != null) {
            if (mBundle.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) {
                outState.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, mReportInfoFilePath);
            } else {
                outState.putSerializable(EXTRA_REPORT_INFO_OBJECT, mReportInfo);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.logVerbose(LOG_TAG, "onDestroy");
        deleteReportInfoFile(this, mReportInfoFilePath);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_report, menu);

        if (mReportInfo != null && mReportInfo.reportSaveFilePath == null) {
            MenuItem item = menu.findItem(R.id.menu_item_save_report_to_file);
            if (item != null)
                item.setEnabled(false);
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAndRemoveTask();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_item_share_report) {
            ShareUtils.shareText(this, getString(R.string.title_report_text), ReportInfo.getReportInfoMarkdownString(mReportInfo));
        } else if (id == R.id.menu_item_copy_report) {
            ShareUtils.copyTextToClipboard(this, ReportInfo.getReportInfoMarkdownString(mReportInfo), null);
        } else if (id == R.id.menu_item_save_report_to_file) {
            ShareUtils.saveTextToFile(this, mReportInfo.reportSaveFileLabel,
                mReportInfo.reportSaveFilePath, ReportInfo.getReportInfoMarkdownString(mReportInfo),
                true, REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE);
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE) {
                ShareUtils.saveTextToFile(this, mReportInfo.reportSaveFileLabel,
                    mReportInfo.reportSaveFilePath, ReportInfo.getReportInfoMarkdownString(mReportInfo),
                    true, -1);
            }
        }
    }

    private void generateReportActivityMarkdownString() {
        StringBuilder reportString = new StringBuilder();

        if (mReportInfo.reportStringPrefix != null)
            reportString.append(mReportInfo.reportStringPrefix);

        String reportMarkdownString = ReportInfo.getReportInfoMarkdownString(mReportInfo);
        int reportMarkdownStringSize = reportMarkdownString.getBytes().length;
        boolean truncated = false;
        if (reportMarkdownStringSize > ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES) {
            reportString.append(DataUtils.getTruncatedCommandOutput(reportMarkdownString, ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES, true, false, true));
            truncated = true;
        } else {
            reportString.append(reportMarkdownString);
        }

        reportMarkdownString = null;

        if (mReportInfo.reportStringSuffix != null)
            reportString.append(mReportInfo.reportStringSuffix);

        int reportStringSize = reportString.length();
        if (reportStringSize > ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES) {
            mReportActivityMarkdownString = this.getString(R.string.msg_report_truncated) +
                DataUtils.getTruncatedCommandOutput(reportString.toString(), ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES, true, false, false);
        } else if (truncated) {
            mReportActivityMarkdownString = this.getString(R.string.msg_report_truncated) + reportString.toString();
        } else {
            mReportActivityMarkdownString = reportString.toString();
        }
    }

    public static class NewInstanceResult {
        public Intent contentIntent;
        public Intent deleteIntent;

        NewInstanceResult(Intent contentIntent, Intent deleteIntent) {
            this.contentIntent = contentIntent;
            this.deleteIntent = deleteIntent;
        }
    }

    public static void startReportActivity(@NonNull final Context context, @NonNull ReportInfo reportInfo) {
        NewInstanceResult result = newInstance(context, reportInfo);
        if (result.contentIntent == null) return;
        context.startActivity(result.contentIntent);
    }

    @NonNull
    public static NewInstanceResult newInstance(@NonNull final Context context, @NonNull final ReportInfo reportInfo) {
        long size = DataUtils.getSerializedSize(reportInfo);
        if (size > DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES) {
            String reportInfoDirectoryPath = getReportInfoDirectoryPath(context);
            String reportInfoFilePath = reportInfoDirectoryPath + "/" + CACHE_FILE_BASENAME_PREFIX + reportInfo.reportTimestamp;
            Error error = FileUtils.writeSerializableObjectToFile(ReportInfo.class.getSimpleName(), reportInfoFilePath, reportInfo);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, error.toString());
                Logger.showToast(context, Error.getMinimalErrorString(error), true);
                return new NewInstanceResult(null, null);
            }

            return new NewInstanceResult(createContentIntent(context, null, reportInfoFilePath),
                createDeleteIntent(context, reportInfoFilePath));
        } else {
            return new NewInstanceResult(createContentIntent(context, reportInfo, null),
                null);
        }
    }

    private static Intent createContentIntent(@NonNull final Context context, final ReportInfo reportInfo, final String reportInfoFilePath) {
        Intent intent = new Intent(context, ReportActivity.class);
        Bundle bundle = new Bundle();

        if (reportInfoFilePath != null) {
            bundle.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, reportInfoFilePath);
        } else {
            bundle.putSerializable(EXTRA_REPORT_INFO_OBJECT, reportInfo);
        }

        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        return intent;
    }

    private static Intent createDeleteIntent(@NonNull final Context context, final String reportInfoFilePath) {
        if (reportInfoFilePath == null) return null;

        Intent intent = new Intent(context, ReportActivityBroadcastReceiver.class);
        intent.setAction(ACTION_DELETE_REPORT_INFO_OBJECT_FILE);

        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, reportInfoFilePath);
        intent.putExtras(bundle);

        return intent;
    }

    @NotNull
    private static String getReportInfoDirectoryPath(Context context) {
        return FileUtils.getCanonicalPath(context.getCacheDir().getAbsolutePath(), null) + "/" + CACHE_DIR_BASENAME;
    }

    private static void deleteReportInfoFile(Context context, String reportInfoFilePath) {
        if (context == null || reportInfoFilePath == null) return;

        String reportInfoDirectoryPath = getReportInfoDirectoryPath(context);
        reportInfoFilePath = FileUtils.getCanonicalPath(reportInfoFilePath, null);
        if(!reportInfoFilePath.equals(reportInfoDirectoryPath) && reportInfoFilePath.startsWith(reportInfoDirectoryPath + "/")) {
            Error error = FileUtils.deleteRegularFile(ReportInfo.class.getSimpleName(), reportInfoFilePath, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, error.toString());
            }
        }
    }

    public static Error deleteReportInfoFilesOlderThanXDays(@NonNull final Context context, int days, final boolean isSynchronous) {
        if (isSynchronous) {
            return deleteReportInfoFilesOlderThanXDaysInner(context, days);
        } else {
            new Thread() { public void run() {
                deleteReportInfoFilesOlderThanXDaysInner(context, days);
            }}.start();
            return null;
        }
    }

    private static Error deleteReportInfoFilesOlderThanXDaysInner(@NonNull final Context context, int days) {
        String reportInfoDirectoryPath = getReportInfoDirectoryPath(context);
        return FileUtils.deleteFilesOlderThanXDays(ReportInfo.class.getSimpleName(), reportInfoDirectoryPath, null, days, true, FileType.REGULAR.getValue());
    }

    public static class ReportActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String action = intent.getAction();
            if (ACTION_DELETE_REPORT_INFO_OBJECT_FILE.equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle == null) return;
                if (bundle.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) {
                    deleteReportInfoFile(context, bundle.getString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH));
                }
            }
        }
    }
}
