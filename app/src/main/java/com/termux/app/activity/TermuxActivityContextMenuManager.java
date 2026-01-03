package com.termux.app.activity;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

public class TermuxActivityContextMenuManager {

    private final TermuxActivity mActivity;

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    public TermuxActivityContextMenuManager(TermuxActivity activity) {
        this.mActivity = activity;
    }

    public void onCreateContextMenu(ContextMenu menu, TermuxActivityTerminalManager terminalManager) {
        TerminalSession currentSession = terminalManager.getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = terminalManager.getTerminalView().isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(terminalManager.getTerminalView().getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, mActivity.getResources().getString(R.string.action_kill_process, currentSession.getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mActivity.getPreferences().shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    public boolean onContextItemSelected(MenuItem item, TermuxActivityTerminalManager terminalManager) {
        TerminalSession session = terminalManager.getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                terminalManager.getTermuxTerminalViewClient().showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                terminalManager.getTermuxTerminalViewClient().shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                terminalManager.getTermuxTerminalViewClient().shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                terminalManager.getTerminalView().requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                terminalManager.getTerminalView().requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session, terminalManager);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn(terminalManager);
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(mActivity, new Intent(mActivity, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(mActivity, new Intent(mActivity, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                terminalManager.getTermuxTerminalViewClient().reportIssueFromTranscript();
                return true;
            default:
                return false;
        }
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(mActivity);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session, TermuxActivityTerminalManager terminalManager) {
        if (session != null) {
            session.reset();
            mActivity.showToast(mActivity.getResources().getString(R.string.msg_terminal_reset), true);

            if (terminalManager.getTermuxTerminalSessionClient() != null)
                terminalManager.getTermuxTerminalSessionClient().onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            mActivity.startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            new AlertDialog.Builder(mActivity).setMessage(mActivity.getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(mActivity, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void toggleKeepScreenOn(TermuxActivityTerminalManager terminalManager) {
        if (terminalManager.getTerminalView().getKeepScreenOn()) {
            terminalManager.getTerminalView().setKeepScreenOn(false);
            mActivity.getPreferences().setKeepScreenOn(false);
        } else {
            terminalManager.getTerminalView().setKeepScreenOn(true);
            mActivity.getPreferences().setKeepScreenOn(true);
        }
    }
}
