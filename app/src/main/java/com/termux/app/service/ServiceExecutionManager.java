package com.termux.app.service;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.errors.Errno;
import com.termux.shared.logger.Logger;
import com.termux.shared.net.uri.UriUtils;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.shell.command.ExecutionCommand.ShellCreateMode;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;
import com.termux.shared.termux.plugins.TermuxPluginUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

public class ServiceExecutionManager {

    private static final String LOG_TAG = "ServiceExecutionManager";
    private final TermuxService mService;
    private final TermuxShellManager mShellManager;

    public ServiceExecutionManager(TermuxService service, TermuxShellManager shellManager) {
        this.mService = service;
        this.mShellManager = shellManager;
    }

    public void actionServiceExecute(Intent intent) {
        if (intent == null) {
            Logger.logError(LOG_TAG, "Ignoring null intent to actionServiceExecute");
            return;
        }
        ExecutionCommand executionCommand = new ExecutionCommand(TermuxShellManager.getNextShellId());
        executionCommand.executableUri = intent.getData();
        executionCommand.isPluginExecutionCommand = true;
        executionCommand.runner = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RUNNER, (intent.getBooleanExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, false) ? Runner.APP_SHELL.getName() : Runner.TERMINAL_SESSION.getName()));
        if (Runner.runnerOf(executionCommand.runner) == null) {
            String errmsg = mService.getString(R.string.error_termux_service_invalid_execution_command_runner, executionCommand.runner);
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg);
            TermuxPluginUtils.processPluginExecutionCommandError(mService, LOG_TAG, executionCommand, false);
            return;
        }
        if (executionCommand.executableUri != null) {
            Logger.logVerbose(LOG_TAG, "uri: \"" + executionCommand.executableUri + "\", path: \"" + executionCommand.executableUri.getPath() + "\", fragment: \"" + executionCommand.executableUri.getFragment() + "\"");
            executionCommand.executable = UriUtils.getUriFilePathWithFragment(executionCommand.executableUri);
            executionCommand.arguments = IntentUtils.getStringArrayExtraIfSet(intent, TERMUX_SERVICE.EXTRA_ARGUMENTS, null);
            if (Runner.APP_SHELL.equalsRunner(executionCommand.runner)) executionCommand.stdin = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_STDIN, null);
            executionCommand.backgroundCustomLogLevel = IntentUtils.getIntegerExtraIfSet(intent, TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, null);
        }
        executionCommand.workingDirectory = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_WORKDIR, null);
        executionCommand.isFailsafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
        executionCommand.sessionAction = intent.getStringExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION);
        executionCommand.shellName = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_SHELL_NAME, null);
        executionCommand.shellCreateMode = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, null);
        executionCommand.commandLabel = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Execution Intent Command");
        executionCommand.commandDescription = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION, null);
        executionCommand.commandHelp = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_HELP, null);
        executionCommand.pluginAPIHelp = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_PLUGIN_API_HELP, null);
        executionCommand.resultConfig.resultPendingIntent = intent.getParcelableExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT);
        executionCommand.resultConfig.resultDirectoryPath = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_DIRECTORY, null);
        if (executionCommand.resultConfig.resultDirectoryPath != null) {
            executionCommand.resultConfig.resultSingleFile = intent.getBooleanExtra(TERMUX_SERVICE.EXTRA_RESULT_SINGLE_FILE, false);
            executionCommand.resultConfig.resultFileBasename = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_BASENAME, null);
            executionCommand.resultConfig.resultFileOutputFormat = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_OUTPUT_FORMAT, null);
            executionCommand.resultConfig.resultFileErrorFormat = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, null);
            executionCommand.resultConfig.resultFilesSuffix = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILES_SUFFIX, null);
        }
        if (executionCommand.shellCreateMode == null) executionCommand.shellCreateMode = ShellCreateMode.ALWAYS.getMode();
        mShellManager.mPendingPluginExecutionCommands.add(executionCommand);
        if (Runner.APP_SHELL.equalsRunner(executionCommand.runner)) executeTermuxTaskCommand(executionCommand);
        else if (Runner.TERMINAL_SESSION.equalsRunner(executionCommand.runner)) executeTermuxSessionCommand(executionCommand);
        else {
            String errmsg = mService.getString(R.string.error_termux_service_unsupported_execution_command_runner, executionCommand.runner);
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg);
            TermuxPluginUtils.processPluginExecutionCommandError(mService, LOG_TAG, executionCommand, false);
        }
    }

    private void executeTermuxTaskCommand(ExecutionCommand executionCommand) {
        if (executionCommand == null) return;
        Logger.logDebug(LOG_TAG, "Executing background \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxTask command");
        if (executionCommand.shellName == null && executionCommand.executable != null) executionCommand.shellName = ShellUtils.getExecutableBasename(executionCommand.executable);
        AppShell newTermuxTask = null;
        ShellCreateMode shellCreateMode = processShellCreateMode(executionCommand);
        if (shellCreateMode == null) return;
        if (ShellCreateMode.NO_SHELL_WITH_NAME == shellCreateMode) {
            newTermuxTask = mService.getTermuxTaskForShellName(executionCommand.shellName);
            if (newTermuxTask != null) Logger.logVerbose(LOG_TAG, "Existing TermuxTask with \"" + executionCommand.shellName + "\" shell name found for shell create mode \"" + shellCreateMode.getMode() + "\"");
            else Logger.logVerbose(LOG_TAG, "No existing TermuxTask with \"" + executionCommand.shellName + "\" shell name found for shell create mode \"" + shellCreateMode.getMode() + "\"");
        }
        if (newTermuxTask == null) newTermuxTask = createTermuxTask(executionCommand);
    }

    @Nullable
    public synchronized AppShell createTermuxTask(ExecutionCommand executionCommand) {
        if (executionCommand == null) return null;
        Logger.logDebug(LOG_TAG, "Creating \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxTask");
        if (!Runner.APP_SHELL.equalsRunner(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring wrong runner \"" + executionCommand.runner + "\" command passed to createTermuxTask()");
            return null;
        }
        executionCommand.setShellCommandShellEnvironment = true;
        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE) Logger.logVerboseExtended(LOG_TAG, executionCommand.toString());
        AppShell newTermuxTask = AppShell.execute(mService, executionCommand, mService, new TermuxShellEnvironment(), null, false);
        if (newTermuxTask == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxTask command for:\n" + executionCommand.getCommandIdAndLabelLogString());
            if (executionCommand.isPluginExecutionCommand) TermuxPluginUtils.processPluginExecutionCommandError(mService, LOG_TAG, executionCommand, false);
            else {
                Logger.logError(LOG_TAG, "Set log level to debug or higher to see error in logs");
                Logger.logErrorPrivateExtended(LOG_TAG, executionCommand.toString());
            }
            return null;
        }
        mShellManager.mTermuxTasks.add(newTermuxTask);
        if (executionCommand.isPluginExecutionCommand) mShellManager.mPendingPluginExecutionCommands.remove(executionCommand);
        mService.updateNotification();
        return newTermuxTask;
    }

    private void executeTermuxSessionCommand(ExecutionCommand executionCommand) {
        if (executionCommand == null) return;
        Logger.logDebug(LOG_TAG, "Executing foreground \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession command");
        if (executionCommand.shellName == null && executionCommand.executable != null) executionCommand.shellName = ShellUtils.getExecutableBasename(executionCommand.executable);
        TermuxSession newTermuxSession = null;
        ShellCreateMode shellCreateMode = processShellCreateMode(executionCommand);
        if (shellCreateMode == null) return;
        if (ShellCreateMode.NO_SHELL_WITH_NAME == shellCreateMode) {
            newTermuxSession = mService.getTermuxSessionForShellName(executionCommand.shellName);
            if (newTermuxSession != null) Logger.logVerbose(LOG_TAG, "Existing TermuxSession with \"" + executionCommand.shellName + "\" shell name found for shell create mode \"" + shellCreateMode.getMode() + "\"");
            else Logger.logVerbose(LOG_TAG, "No existing TermuxSession with \"" + executionCommand.shellName + "\" shell name found for shell create mode \"" + shellCreateMode.getMode() + "\"");
        }
        if (newTermuxSession == null) newTermuxSession = createTermuxSession(executionCommand);
        if (newTermuxSession == null) return;
        handleSessionAction(DataUtils.getIntFromString(executionCommand.sessionAction, TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY), newTermuxSession.getTerminalSession());
    }

    @Nullable
    public synchronized TermuxSession createTermuxSession(ExecutionCommand executionCommand) {
        if (executionCommand == null) return null;
        Logger.logDebug(LOG_TAG, "Creating \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession");
        if (!Runner.TERMINAL_SESSION.equalsRunner(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring wrong runner \"" + executionCommand.runner + "\" command passed to createTermuxSession()");
            return null;
        }
        executionCommand.setShellCommandShellEnvironment = true;
        executionCommand.terminalTranscriptRows = mService.getProperties().getTerminalTranscriptRows();
        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE) Logger.logVerboseExtended(LOG_TAG, executionCommand.toString());
        TermuxSession newTermuxSession = TermuxSession.execute(mService, executionCommand, mService.getTermuxTerminalSessionClient(), mService, new TermuxShellEnvironment(), null, executionCommand.isPluginExecutionCommand);
        if (newTermuxSession == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxSession command for:\n" + executionCommand.getCommandIdAndLabelLogString());
            if (executionCommand.isPluginExecutionCommand) TermuxPluginUtils.processPluginExecutionCommandError(mService, LOG_TAG, executionCommand, false);
            else {
                Logger.logError(LOG_TAG, "Set log level to debug or higher to see error in logs");
                Logger.logErrorPrivateExtended(LOG_TAG, executionCommand.toString());
            }
            return null;
        }
        mShellManager.mTermuxSessions.add(newTermuxSession);
        if (executionCommand.isPluginExecutionCommand) mShellManager.mPendingPluginExecutionCommands.remove(executionCommand);
        if (mService.getTermuxTerminalSessionActivityClient() != null) mService.getTermuxTerminalSessionActivityClient().termuxSessionListNotifyUpdated();
        mService.updateNotification();
        TermuxActivity.updateTermuxActivityStyling(mService, false);
        return newTermuxSession;
    }

    public synchronized void killAllTermuxExecutionCommands() {
        boolean processResult;
        Logger.logDebug(LOG_TAG, "Killing TermuxSessions=" + mShellManager.mTermuxSessions.size() + ", TermuxTasks=" + mShellManager.mTermuxTasks.size() + ", PendingPluginExecutionCommands=" + mShellManager.mPendingPluginExecutionCommands.size());
        List<TermuxSession> termuxSessions = new ArrayList<>(mShellManager.mTermuxSessions);
        List<AppShell> termuxTasks = new ArrayList<>(mShellManager.mTermuxTasks);
        List<ExecutionCommand> pendingPluginExecutionCommands = new ArrayList<>(mShellManager.mPendingPluginExecutionCommands);
        for (int i = 0; i < termuxSessions.size(); i++) {
            ExecutionCommand executionCommand = termuxSessions.get(i).getExecutionCommand();
            processResult = mService.wantsToStop() || executionCommand.isPluginExecutionCommandWithPendingResult();
            termuxSessions.get(i).killIfExecuting(mService, processResult);
            if (!processResult) mShellManager.mTermuxSessions.remove(termuxSessions.get(i));
        }
        for (int i = 0; i < termuxTasks.size(); i++) {
            ExecutionCommand executionCommand = termuxTasks.get(i).getExecutionCommand();
            if (executionCommand.isPluginExecutionCommandWithPendingResult()) termuxTasks.get(i).killIfExecuting(mService, true);
            else mShellManager.mTermuxTasks.remove(termuxTasks.get(i));
        }
        for (int i = 0; i < pendingPluginExecutionCommands.size(); i++) {
            ExecutionCommand executionCommand = pendingPluginExecutionCommands.get(i);
            if (!executionCommand.shouldNotProcessResults() && executionCommand.isPluginExecutionCommandWithPendingResult()) {
                if (executionCommand.setStateFailed(Errno.ERRNO_CANCELLED.getCode(), mService.getString(com.termux.shared.R.string.error_execution_cancelled))) {
                    TermuxPluginUtils.processPluginExecutionCommandResult(mService, LOG_TAG, executionCommand);
                }
            }
        }
    }

    private ShellCreateMode processShellCreateMode(@NonNull ExecutionCommand executionCommand) {
        if (ShellCreateMode.ALWAYS.equalsMode(executionCommand.shellCreateMode)) return ShellCreateMode.ALWAYS;
        else if (ShellCreateMode.NO_SHELL_WITH_NAME.equalsMode(executionCommand.shellCreateMode)) {
            if (DataUtils.isNullOrEmpty(executionCommand.shellName)) {
                TermuxPluginUtils.setAndProcessPluginExecutionCommandError(mService, LOG_TAG, executionCommand, false, mService.getString(R.string.error_termux_service_execution_command_shell_name_unset, executionCommand.shellCreateMode));
                return null;
            } else {
                return ShellCreateMode.NO_SHELL_WITH_NAME;
            }
        } else {
            TermuxPluginUtils.setAndProcessPluginExecutionCommandError(mService, LOG_TAG, executionCommand, false, mService.getString(R.string.error_termux_service_unsupported_execution_command_shell_create_mode, executionCommand.shellCreateMode));
            return null;
        }
    }

    private void handleSessionAction(int sessionAction, TerminalSession newTerminalSession) {
        Logger.logDebug(LOG_TAG, "Processing sessionAction \"" + sessionAction + "\" for session \"" + newTerminalSession.mSessionName + "\"");
        switch (sessionAction) {
            case TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY:
                mService.setCurrentStoredTerminalSession(newTerminalSession);
                if (mService.getTermuxTerminalSessionActivityClient() != null) mService.getTermuxTerminalSessionActivityClient().setCurrentSession(newTerminalSession);
                startTermuxActivity();
                break;
            case TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY:
                if (mService.getTermuxSessionsSize() == 1) mService.setCurrentStoredTerminalSession(newTerminalSession);
                startTermuxActivity();
                break;
            case TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY:
                mService.setCurrentStoredTerminalSession(newTerminalSession);
                if (mService.getTermuxTerminalSessionActivityClient() != null) mService.getTermuxTerminalSessionActivityClient().setCurrentSession(newTerminalSession);
                break;
            case TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY:
                if (mService.getTermuxSessionsSize() == 1) mService.setCurrentStoredTerminalSession(newTerminalSession);
                break;
            default:
                Logger.logError(LOG_TAG, "Invalid sessionAction: \"" + sessionAction + "\". Force using default sessionAction.");
                handleSessionAction(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY, newTerminalSession);
                break;
        }
    }

    private void startTermuxActivity() {
        if (PermissionUtils.validateDisplayOverOtherAppsPermissionForPostAndroid10(mService, true)) {
            TermuxActivity.startTermuxActivity(mService);
        } else {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mService);
            if (preferences == null) return;
            if (preferences.arePluginErrorNotificationsEnabled(false)) Logger.showToast(mService, mService.getString(R.string.error_display_over_other_apps_permission_not_granted_to_start_terminal), true);
        }
    }
}
