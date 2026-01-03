package com.termux.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;

public class TermuxActivityUIManager {

    private static final String LOG_TAG = "TermuxActivityUIManager";
    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private final TermuxActivity mActivity;
    private TermuxActivityRootView mTermuxActivityRootView;
    private View mTermuxActivityBottomSpaceView;
    private ExtraKeysView mExtraKeysView;
    private TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;
    private int mNavBarHeight;
    private float mTerminalToolbarDefaultHeight;

    public TermuxActivityUIManager(TermuxActivity activity) {
        this.mActivity = activity;
    }

    public void setActivityTheme() {
        TermuxThemeUtils.setAppNightMode(mActivity.getProperties().getNightMode());
        AppCompatActivityUtils.setNightMode(mActivity, NightMode.getAppNightMode().getName(), true);
    }

    public void onCreate(Bundle savedInstanceState, TermuxActivityTerminalManager terminalManager) {
        mActivity.setContentView(R.layout.activity_termux);

        setMargins();

        mTermuxActivityRootView = mActivity.findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(mActivity);
        mTermuxActivityBottomSpaceView = mActivity.findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = mActivity.findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mActivity.getProperties().isUsingFullScreen()) {
            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTerminalToolbarView(savedInstanceState, terminalManager);
        setSettingsButtonView();
        setNewSessionButtonView(terminalManager);
        setToggleKeyboardView(terminalManager);
    }

    public void setMargins() {
        RelativeLayout relativeLayout = mActivity.findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mActivity.getProperties().getTerminalMarginHorizontal();
        int marginVertical = mActivity.getProperties().getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    public void onStart() {
        if (mActivity.getPreferences().isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();
    }

    public void onStop() {
        removeTermuxActivityRootViewGlobalLayoutListener();
        getDrawer().closeDrawers();
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        final EditText textInputView = mActivity.findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    public void reloadActivityStyling(float terminalToolbarDefaultHeight) {
        if (mExtraKeysView != null) {
            mExtraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
            mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), terminalToolbarDefaultHeight);
        }
        TermuxThemeUtils.setAppNightMode(mActivity.getProperties().getNightMode());
        setMargins();
        setTerminalToolbarHeight();
    }

    private void setTerminalToolbarView(Bundle savedInstanceState, TermuxActivityTerminalManager terminalManager) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(mActivity, terminalManager.getTerminalView(),
            terminalManager.getTermuxTerminalViewClient(), terminalManager.getTermuxTerminalSessionClient());

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mActivity.getPreferences().shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(mActivity, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(mActivity, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mActivity.getProperties().getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mActivity.getPreferences().toogleShowTerminalToolbar();
        Logger.showToast(mActivity, (showNow ? mActivity.getString(R.string.msg_enabling_terminal_toolbar) : mActivity.getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            mActivity.findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void setSettingsButtonView() {
        ImageButton settingsButton = mActivity.findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(mActivity, new Intent(mActivity, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView(TermuxActivityTerminalManager terminalManager) {
        View newSessionButton = mActivity.findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> terminalManager.getTermuxTerminalSessionClient().addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(mActivity, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> terminalManager.getTermuxTerminalSessionClient().addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> terminalManager.getTermuxTerminalSessionClient().addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView(TermuxActivityTerminalManager terminalManager) {
        mActivity.findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            terminalManager.getTermuxTerminalViewClient().onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        mActivity.findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    public void addTermuxActivityRootViewGlobalLayoutListener() {
        if (mTermuxActivityRootView != null)
            mTermuxActivityRootView.getViewTreeObserver().addOnGlobalLayoutListener(mTermuxActivityRootView);
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (mTermuxActivityRootView != null)
            mTermuxActivityRootView.getViewTreeObserver().removeOnGlobalLayoutListener(mTermuxActivityRootView);
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) mActivity.findViewById(R.id.drawer_layout);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) mActivity.findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }
}
