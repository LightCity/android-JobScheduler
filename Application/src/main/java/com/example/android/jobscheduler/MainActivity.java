/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.jobscheduler;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.jobscheduler.service.TestJobService;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    public static final int MSG_UNCOLOUR_START = 0;
    public static final int MSG_UNCOLOUR_STOP = 1;
    public static final int MSG_SERVICE_OBJ = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);

        mHandler = new MyHandler(this);

        Resources res = getResources();
        defaultColor = res.getColor(R.color.none_received);
        startJobColor = res.getColor(R.color.start_received);
        stopJobColor = res.getColor(R.color.stop_received);

        // Set up UI.
        mShowStartView = (TextView) findViewById(R.id.onstart_textview);
        mShowStopView = (TextView) findViewById(R.id.onstop_textview);
        mParamsTextView = (TextView) findViewById(R.id.task_params);
        mDelayEditText = (EditText) findViewById(R.id.delay_time);
        mDeadlineEditText = (EditText) findViewById(R.id.deadline_time);
        mWiFiConnectivityRadioButton = (RadioButton) findViewById(R.id.checkbox_unmetered);
        mAnyConnectivityRadioButton = (RadioButton) findViewById(R.id.checkbox_any);
        mRequiresChargingCheckBox = (CheckBox) findViewById(R.id.checkbox_charging);
        mRequiresIdleCheckbox = (CheckBox) findViewById(R.id.checkbox_idle);
        mServiceComponent = new ComponentName(this, TestJobService.class);
        // Start service and provide it a way to communicate with us.
        Intent startServiceIntent = new Intent(this, TestJobService.class);
        startServiceIntent.putExtra("messenger", new Messenger(mHandler)); // 启动TestJobService的时候，TestJobService里头会把“自己”放到这里的“mTestService”对象，以保持通信。
        startService(startServiceIntent);
    }
    // UI fields.
    int defaultColor;
    int startJobColor;
    int stopJobColor;

    private TextView mShowStartView;
    private TextView mShowStopView;
    private TextView mParamsTextView;
    private EditText mDelayEditText;
    private EditText mDeadlineEditText;
    private RadioButton mWiFiConnectivityRadioButton;
    private RadioButton mAnyConnectivityRadioButton;
    private CheckBox mRequiresChargingCheckBox;
    private CheckBox mRequiresIdleCheckbox;

    ComponentName mServiceComponent;
    /** Service object to interact scheduled jobs. */
    TestJobService mTestService;

    private static int kJobId = 0;

    static class MyHandler extends Handler {
        WeakReference<MainActivity> ref;

        MyHandler(MainActivity activity) {
            ref = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            if (ref.get() == null) {
                return;
            }
            MainActivity activity = ref.get();
            switch (msg.what) {
                case MSG_UNCOLOUR_START:
                    activity.mShowStartView.setBackgroundColor(activity.defaultColor);
                    break;
                case MSG_UNCOLOUR_STOP:
                    activity.mShowStopView.setBackgroundColor(activity.defaultColor);
                    break;
                case MSG_SERVICE_OBJ:
                    activity.mTestService = (TestJobService) msg.obj;
                    activity.mTestService.setUiCallback(activity);
            }
        }
    }

    static Handler  mHandler;

    private boolean ensureTestService() {
        if (mTestService == null) {
            Toast.makeText(MainActivity.this, "Service null, never got callback?", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * UI onclick listener to schedule a job. What this job is is defined in
     * TestJobService#scheduleJob().
     */
    public void scheduleJob(View v) {
        if (!ensureTestService()) {
            return;
        }

        JobInfo.Builder builder = new JobInfo.Builder(kJobId++, mServiceComponent);

        String delay = mDelayEditText.getText().toString();
        if (!TextUtils.isEmpty(delay)) {
            builder.setMinimumLatency(Long.valueOf(delay) * 1000);
        }
        String deadline = mDeadlineEditText.getText().toString();
        if (!TextUtils.isEmpty(deadline)) {
            builder.setOverrideDeadline(Long.valueOf(deadline) * 1000);
        }
        boolean requiresUnmetered = mWiFiConnectivityRadioButton.isChecked();
        boolean requiresAnyConnectivity = mAnyConnectivityRadioButton.isChecked();
        if (requiresUnmetered) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        } else if (requiresAnyConnectivity) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        }
        builder.setRequiresDeviceIdle(mRequiresIdleCheckbox.isChecked());
        builder.setRequiresCharging(mRequiresChargingCheckBox.isChecked());

        mTestService.scheduleJob(builder.build());

    }

    public void cancelAllJobs(View v) {
        JobScheduler tm =
                (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        tm.cancelAll();
    }

    /**
     * UI onclick listener to call jobFinished() in our service.
     */
    public void finishJob(View v) {
        if (!ensureTestService()) {
            return;
        }
        mTestService.callJobFinished();
        mParamsTextView.setText("");
    }

    /**
     * Receives callback from the service when a job has landed
     * on the app. Colours the UI and post a message to
     * uncolour it after a second.
     */
    public void onReceivedStartJob(JobParameters params) {
        mShowStartView.setBackgroundColor(startJobColor);
        Message m = Message.obtain(mHandler, MSG_UNCOLOUR_START);
        mHandler.sendMessageDelayed(m, 1000L); // uncolour in 1 second.
        mParamsTextView.setText("Executing: " + params.getJobId() + " " + params.getExtras());
    }

    /**
     * Receives callback from the service when a job that
     * previously landed on the app must stop executing.
     * Colours the UI and post a message to uncolour it after a
     * second.
     */
    public void onReceivedStopJob() {
        mShowStopView.setBackgroundColor(stopJobColor);
        Message m = Message.obtain(mHandler, MSG_UNCOLOUR_STOP);
        mHandler.sendMessageDelayed(m, 2000L); // uncolour in 1 second.
        mParamsTextView.setText("");
    }
}
