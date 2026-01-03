package com.termux.app.activity;

import android.content.Intent;
import android.view.ContextMenu;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxInstaller;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

public class TermuxActivityTerminalManager {

    private final TermuxActivity mActivity;
    private TerminalView mTerminalView;
    private TermuxTerminalViewClient mTermuxTerminalViewClient;
    private TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    private TermuxSessionsListViewController mTermuxSessionListViewController;

    public TermuxActivityTerminalManager(TermuxActivity activity) {
        this.mActivity = activity;
    }

    public void onCreate() {
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(mActivity);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(mActivity, mTermuxTerminalSessionActivityClient);

        mTerminalView = mActivity.findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
        
        mActivity.registerForContextMenu(mTerminalView);
    }

    public void onStart() {
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();
    }

    public void onResume() {
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();
    }

    public void onStop() {
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();
    }

    public void onReloadProperties() {
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }

    public void onReloadActivityStyling() {
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();
    }

    public void setupSessionsListView(TermuxService service) {
        ListView termuxSessionsListView = mActivity.findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(mActivity, service.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    public void handleOnServiceConnected(TermuxService service) {
        setupSessionsListView(service);

        final Intent intent = mActivity.getIntent();
        mActivity.setIntent(null);

        if (service.isTermuxSessionsEmpty()) {
            if (mActivity.isVisible()) {
                TermuxInstaller.setupBootstrapIfNeeded(mActivity, () -> {
                    if (service == null) return;
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                    }
                });
            } else {
                mActivity.finishActivityIfNotFinishing();
            }
        } else {
            if (!mActivity.isActivityRecreated() && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                boolean isFailSafe = intent.getBooleanExtra(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        service.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    public void notifySessionListUpdated() {
        if (mTermuxSessionListViewController != null) {
            mTermuxSessionListViewController.notifyDataSetChanged();
        }
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }
}
