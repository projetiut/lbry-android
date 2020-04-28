package io.lbry.browser.tasks;

import android.os.AsyncTask;
import android.view.View;

import java.util.Arrays;
import java.util.List;

import io.lbry.browser.exceptions.ApiCallException;
import io.lbry.browser.model.Claim;
import io.lbry.browser.utils.Helper;
import io.lbry.browser.utils.Lbry;

public class ResolveTask extends AsyncTask<Void, Void, List<Claim>> {
    private List<String> urls;
    private String connectionString;
    private ResolveResultHandler handler;
    private View progressView;
    private ApiCallException error;

    public ResolveTask(String url, String connectionString, View progressView, ResolveResultHandler handler) {
        this(Arrays.asList(url), connectionString, progressView, handler);
    }

    public ResolveTask(List<String> urls, String connectionString, View progressView, ResolveResultHandler handler) {
        this.urls = urls;
        this.connectionString = connectionString;
        this.progressView = progressView;
        this.handler = handler;
    }
    protected void onPreExecute() {
        Helper.setViewVisibility(progressView, View.VISIBLE);
    }
    protected List<Claim> doInBackground(Void... params) {
        try {
            return Lbry.resolve(urls, connectionString);
        } catch (ApiCallException ex) {
            error = ex;
            return null;
        }
    }
    protected void onPostExecute(List<Claim> claims) {
        Helper.setViewVisibility(progressView, View.GONE);
        if (handler != null) {
            if (claims != null) {
                handler.onSuccess(claims);
            } else {
                handler.onError(error);
            }
        }
    }

    public interface ResolveResultHandler {
        void onSuccess(List<Claim> claims);
        void onError(Exception error);
    }
}
