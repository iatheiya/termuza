package com.termux.shared.termux.shell.command.runner.terminal;

import android.content.Context;
import android.system.OsConstants;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Joiner;
import com.termux.shared.R;
import com.termux.app.service.ServiceExecutionManager;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.UnixShellEnvironment;
import com.termux.shared.shell.command.result.ResultData;
import com.termux.shared.errors.Errno;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.environment.IShellEnvironment;
import com.termux.shared.shell.ShellUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class TermuxSession {
    private final TerminalSession mTerminalSession;
    private final ExecutionCommand mExecutionCommand;
    private final TermuxSessionClient mTermuxSessionClient;
    private final boolean mSetStdoutOnExit;
    private static final String LOG_TAG = "TermuxSession";

    private TermuxSession(@NonNull final TerminalSession terminalSession, @NonNull final ExecutionCommand executionCommand,
                         final TermuxSessionClient termuxSessionClient, final boolean setStdoutOnExit) {
        this.mTerminalSession = terminalSession;
        this.mExecutionCommand = executionCommand;
        this.mTermuxSessionClient = termuxSessionClient;
        this.mSetStdoutOnExit = setStdoutOnExit;
    }

    public static TermuxSession execute(@NonNull final Context currentPackageContext, @NonNull ExecutionCommand executionCommand,
                                       @NonNull final TerminalSessionClient terminalSessionClient, final TermuxSessionClient termuxSessionClient,
                                       @NonNull final IShellEnvironment shellEnvironmentClient,
                                       @Nullable HashMap<String, String> additionalEnvironment,
                                       final boolean setStdoutOnExit) {
        if (executionCommand.executable != null && executionCommand.executable.isEmpty())
            executionCommand.executable = null;
        if (executionCommand.workingDirectory == null || executionCommand.workingDirectory.isEmpty())
            executionCommand.workingDirectory = shellEnvironmentClient.getDefaultWorkingDirectoryPath();
        if (executionCommand.workingDirectory.isEmpty())
            executionCommand.workingDirectory = "/";
        String defaultBinPath = shellEnvironmentClient.getDefaultBinPath();
        if (defaultBinPath.isEmpty())
            defaultBinPath = "/system/bin";
        boolean isLoginShell = false;
        if (executionCommand.executable == null) {
            if (!executionCommand.isFailsafe) {
                for (String shellBinary : UnixShellEnvironment.LOGIN_SHELL_BINARIES) {
                    File shellFile = new File(defaultBinPath, shellBinary);
                    if (shellFile.canExecute()) {
                        executionCommand.executable = shellFile.getAbsolutePath();
                        break;
                    }
                }
            }
            if (executionCommand.executable == null) {
                executionCommand.executable = "/system/bin/sh";
            } else {
                isLoginShell = true;
            }
        }

        String[] commandArgs = shellEnvironmentClient.setupShellCommandArguments(executionCommand.executable, executionCommand.arguments);
        executionCommand.executable = commandArgs[0];
        String processName = (isLoginShell ? "-" : "") + ShellUtils.getExecutableBasename(executionCommand.executable);
        String[] arguments = new String[commandArgs.length];
        arguments[0] = processName;
        if (commandArgs.length > 1) System.arraycopy(commandArgs, 1, arguments, 1, commandArgs.length - 1);
        executionCommand.arguments = arguments;
        if (executionCommand.commandLabel == null) executionCommand.commandLabel = processName;

        HashMap<String, String> environment = shellEnvironmentClient.setupShellCommandEnvironment(currentPackageContext, executionCommand);
        if (additionalEnvironment != null) environment.putAll(additionalEnvironment);
        List<String> environmentList = ShellEnvironmentUtils.convertEnvironmentToEnviron(environment);
        Collections.sort(environmentList);
        String[] environmentArray = environmentList.toArray(new String[0]);

        if (!executionCommand.setState(ExecutionCommand.ExecutionState.EXECUTING)) {
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), currentPackageContext.getString(R.string.error_failed_to_execute_termux_session_command, executionCommand.getCommandIdAndLabelLogString()));
            TermuxSession.processTermuxSessionResult(null, executionCommand);
            return null;
        }

        Logger.logDebugExtended(LOG_TAG, executionCommand.toString());
        Logger.logVerboseExtended(LOG_TAG, executionCommand.getCommandIdAndLabelLogString() + " TermuxSession Environment:\n" + Joiner.on("\n").join(environmentArray));
        Logger.logDebug(LOG_TAG, "Running " + executionCommand.getCommandIdAndLabelLogString() + " TermuxSession via Native Loader");

        int[] result = ServiceExecutionManager.nativeStartSession(executionCommand.executable, arguments, environmentArray);
        if (result == null || result.length < 2 || result[0] <= 0) {
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), "Failed to start native session via loader");
            TermuxSession.processTermuxSessionResult(null, executionCommand);
            return null;
        }
        int pid = result[0];
        int fd = result[1];

        TerminalSession terminalSession = new TerminalSession(fd, pid, executionCommand.workingDirectory, executionCommand.terminalTranscriptRows, terminalSessionClient);
        if (executionCommand.shellName != null) {
            terminalSession.mSessionName = executionCommand.shellName;
        }
        return new TermuxSession(terminalSession, executionCommand, termuxSessionClient, setStdoutOnExit);
    }

    public void finish() {
        if (mTerminalSession.isRunning()) return;
        int exitCode = mTerminalSession.getExitStatus();
        if (exitCode == 0)
            Logger.logDebug(LOG_TAG, "The " + mExecutionCommand.getCommandIdAndLabelLogString() + " TermuxSession exited normally");
        else
            Logger.logDebug(LOG_TAG, "The " + mExecutionCommand.getCommandIdAndLabelLogString() + " TermuxSession exited with code: " + exitCode);
        if (mExecutionCommand.isStateFailed()) {
            Logger.logDebug(LOG_TAG, "Ignoring setting " + mExecutionCommand.getCommandIdAndLabelLogString() + " TermuxSession state to ExecutionState.EXECUTED and processing results since it has already failed");
            return;
        }
        mExecutionCommand.resultData.exitCode = exitCode;
        if (this.mSetStdoutOnExit)
            mExecutionCommand.resultData.stdout.append(ShellUtils.getTerminalSessionTranscriptText(mTerminalSession, true, false));
        if (!mExecutionCommand.setState(ExecutionCommand.ExecutionState.EXECUTED))
            return;
        TermuxSession.processTermuxSessionResult(this, null);
    }

    public void killIfExecuting(@NonNull final Context context, boolean processResult) {
        if (mExecutionCommand.hasExecuted()) {
            Logger.logDebug(LOG_TAG, "Ignoring sending SIGKILL to " + mExecutionCommand.getCommandIdAndLabelLogString() + " TermuxSession since it has already finished executing");
            return;
        }
        Logger.logDebug(LOG_TAG, "Send SIGKILL to \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession");
        if (mExecutionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), context.getString(R.string.error_sending_sigkill_to_process))) {
            if (processResult) {
                mExecutionCommand.resultData.exitCode = 137;
                if (this.mSetStdoutOnExit)
                    mExecutionCommand.resultData.stdout.append(ShellUtils.getTerminalSessionTranscriptText(mTerminalSession, true, false));
                TermuxSession.processTermuxSessionResult(this, null);
            }
        }
        mTerminalSession.finishIfRunning();
    }

    private static void processTermuxSessionResult(final TermuxSession termuxSession, ExecutionCommand executionCommand) {
        if (termuxSession != null) executionCommand = termuxSession.mExecutionCommand;
        if (executionCommand == null) return;

        if (executionCommand.shouldNotProcessResults()) {
            Logger.logDebug(LOG_TAG, "Ignoring duplicate call to process \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession result");
            return;
        }

        Logger.logDebug(LOG_TAG, "Processing \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession result");

        if (termuxSession != null && termuxSession.mTermuxSessionClient != null) {
            termuxSession.mTermuxSessionClient.onTermuxSessionExited(termuxSession);
        } else {
            if (!executionCommand.isStateFailed())
                executionCommand.setState(ExecutionCommand.ExecutionState.SUCCESS);
        }
    }

    public TerminalSession getTerminalSession() {
        return mTerminalSession;
    }

    public ExecutionCommand getExecutionCommand() {
        return mExecutionCommand;
    }

    public interface TermuxSessionClient {
        void onTermuxSessionExited(TermuxSession termuxSession);
    }
}