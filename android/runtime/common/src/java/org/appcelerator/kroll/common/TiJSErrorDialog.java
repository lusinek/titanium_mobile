/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll.common;

import java.util.LinkedList;

import org.appcelerator.kroll.KrollApplication;
import org.appcelerator.kroll.KrollRuntime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A utility class for creating a dialog that displays Javascript errors
 */
public class TiJSErrorDialog implements Handler.Callback
{
	private static final String TAG = "TiJSError";
	private static LinkedList<ErrorMessage> errorMessages = new LinkedList<ErrorMessage>();
	private static boolean dialogShowing = false;
	private static final int MSG_OPEN_ERROR_DIALOG = 10011;
	private static Handler mainHandler;
	private static TiJSErrorDialog _instance;

	public static void printError(String title, String message, String sourceName, int line, String lineSource,
		int lineOffset)
	{
		Log.e(TAG, "----- Titanium Javascript " + title + " -----");
		Log.e(TAG, "- In " + sourceName + ":" + line + "," + lineOffset);
		Log.e(TAG, "- Message: " + message);
		Log.e(TAG, "- Source: " + lineSource);
	}

	private static class ErrorMessage
	{
		String title, message, sourceName, lineSource;
		int line, lineOffset;
	}

	private static Handler getMainHandler()
	{
		if (_instance == null) {
			_instance = new TiJSErrorDialog();
		}

		if (mainHandler == null) {
			mainHandler = new Handler(TiMessenger.getMainMessenger().getLooper(), _instance);
		}

		return mainHandler;
	}

	public static void openErrorDialog(final String title, final String message, final String sourceName, final int line,
		final String lineSource, final int lineOffset)
	{
		ErrorMessage error = new ErrorMessage();
		error.title = title;
		error.message = message;
		error.sourceName = sourceName;
		error.line = line;
		error.lineSource = lineSource;
		error.lineOffset = lineOffset;

		TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_OPEN_ERROR_DIALOG), error);
	}

	protected static void handleOpenErrorDialog(ErrorMessage error)
	{
		KrollApplication application = KrollRuntime.getInstance().getKrollApplication();
		if (application == null) {
			return;
		}

		Activity activity = application.getCurrentActivity();
		if (activity == null || activity.isFinishing()) {
			Log.w(TAG, "Activity is null or already finishing, skipping dialog.");
			return;
		}

		printError(error.title, error.message, error.sourceName, error.line, error.lineSource, error.lineOffset);

		if (!dialogShowing) {
			dialogShowing = true;
			final ErrorMessage fError = error;
			application.waitForCurrentActivity(new CurrentActivityListener()
			{
				// TODO @Override
				public void onCurrentActivityReady(Activity activity)
				{
					createDialog(fError);
				}
			});
		} else {
			errorMessages.add(error);
		}
	}

	protected static void createDialog(final ErrorMessage error)
	{
		KrollApplication application = KrollRuntime.getInstance().getKrollApplication();
		if (application == null) {
			return;
		}

		Context context = application.getCurrentActivity();
		FrameLayout layout = new FrameLayout(context);
		layout.setBackgroundColor(Color.rgb(128, 0, 0));

		LinearLayout vlayout = new LinearLayout(context);
		vlayout.setOrientation(LinearLayout.VERTICAL);
		vlayout.setPadding(10, 10, 10, 10);
		layout.addView(vlayout);

		TextView sourceInfoView = new TextView(context);
		sourceInfoView.setBackgroundColor(Color.WHITE);
		sourceInfoView.setTextColor(Color.BLACK);
		sourceInfoView.setPadding(4, 5, 4, 0);
		sourceInfoView.setText("[" + error.line + "," + error.lineOffset + "] " + error.sourceName);

		TextView messageView = new TextView(context);
		messageView.setBackgroundColor(Color.WHITE);
		messageView.setTextColor(Color.BLACK);
		messageView.setPadding(4, 5, 4, 0);
		messageView.setText(error.message);

		TextView sourceView = new TextView(context);
		sourceView.setBackgroundColor(Color.WHITE);
		sourceView.setTextColor(Color.BLACK);
		sourceView.setPadding(4, 5, 4, 0);
		sourceView.setText(error.lineSource);

		TextView infoLabel = new TextView(context);
		infoLabel.setText("Location: ");
		infoLabel.setTextColor(Color.WHITE);
		infoLabel.setTextScaleX(1.5f);

		TextView messageLabel = new TextView(context);
		messageLabel.setText("Message: ");
		messageLabel.setTextColor(Color.WHITE);
		messageLabel.setTextScaleX(1.5f);

		TextView sourceLabel = new TextView(context);
		sourceLabel.setText("Source: ");
		sourceLabel.setTextColor(Color.WHITE);
		sourceLabel.setTextScaleX(1.5f);

		vlayout.addView(infoLabel);
		vlayout.addView(sourceInfoView);
		vlayout.addView(messageLabel);
		vlayout.addView(messageView);
		vlayout.addView(sourceLabel);
		vlayout.addView(sourceView);

		OnClickListener clickListener = new OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				if (which == DialogInterface.BUTTON_POSITIVE) {
					// Kill Process
					Process.killProcess(Process.myPid());

				} else if (which == DialogInterface.BUTTON_NEUTRAL) {
					// Continue
				} else if (which == DialogInterface.BUTTON_NEGATIVE) {
					// TODO: Reload (Fastdev)
					// if (error.tiContext != null && error.tiContext.get() != null) {
					// reload(error.sourceName);
					// }

				}
				if (!errorMessages.isEmpty()) {
					createDialog(errorMessages.removeFirst());

				} else {
					dialogShowing = false;
				}
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(context)
			.setTitle(error.title).setView(layout)
			.setPositiveButton("Kill", clickListener)
			.setNeutralButton("Continue", clickListener)
			.setCancelable(false);

		// TODO: Enable when we have fastdev working
		// if (TiFastDev.isFastDevEnabled()) {
		// builder.setNegativeButton("Reload", clickListener);
		// }
		builder.create().show();
	}

	protected static void reload(String sourceName)
	{
		// try {
		// TODO: Enable this when we have fastdev
		// KrollContext.getKrollContext().evalFile(sourceName);
		/*
		 * } catch (IOException e) {
		 * Log.e(TAG, e.getMessage(), e);
		 * }
		 */
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_OPEN_ERROR_DIALOG:
				AsyncResult asyncResult = (AsyncResult) msg.obj;
				ErrorMessage errorMessage = (ErrorMessage) asyncResult.getArg();
				handleOpenErrorDialog(errorMessage);
				asyncResult.setResult(null);
				return true;
			default:
				break;
		}

		return false;
	}
}
