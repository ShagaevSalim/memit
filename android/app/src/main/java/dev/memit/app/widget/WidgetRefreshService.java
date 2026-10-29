package dev.memit.app.widget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.atomic.AtomicBoolean;

/** One manual refresh, without toggling package components as WorkManager does. */
public final class WidgetRefreshService extends JobService {
    public static final int JOB_ID = 701;
    private AtomicBoolean cancelled;

    @Override public boolean onStartJob(JobParameters parameters) {
        AtomicBoolean token = new AtomicBoolean(false);
        cancelled = token;
        new Thread(() -> {
            try { new WidgetRefreshTask(getApplicationContext(), token::get).run(); }
            finally { if (!token.get()) jobFinished(parameters, false); }
        }, "memit-widget-refresh").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        if (cancelled != null) cancelled.set(true);
        return false;
    }
}
