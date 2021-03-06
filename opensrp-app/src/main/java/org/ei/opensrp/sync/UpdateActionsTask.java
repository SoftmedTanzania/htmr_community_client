package org.ei.opensrp.sync;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import org.ei.opensrp.domain.DownloadStatus;
import org.ei.opensrp.domain.FetchStatus;
import org.ei.opensrp.service.ActionService;
import org.ei.opensrp.service.AllFormVersionSyncService;
import org.ei.opensrp.service.FormSubmissionSyncService;
import org.ei.opensrp.view.BackgroundAction;
import org.ei.opensrp.view.LockingBackgroundTask;
import org.ei.opensrp.view.ProgressIndicator;

import static org.ei.opensrp.domain.FetchStatus.fetched;
import static org.ei.opensrp.domain.FetchStatus.nothingFetched;
import static org.ei.opensrp.util.Log.logInfo;

public class UpdateActionsTask {
    private final LockingBackgroundTask task;
    private ActionService actionService;
    private Context context;
    private FormSubmissionSyncService formSubmissionSyncService;
    private AllFormVersionSyncService allFormVersionSyncService;
    private AdditionalSyncService additionalSyncService;

    private static final String TAG = UpdateActionsTask.class.getSimpleName();
    public UpdateActionsTask(Context context, ActionService actionService, FormSubmissionSyncService formSubmissionSyncService, ProgressIndicator progressIndicator,
                             AllFormVersionSyncService allFormVersionSyncService) {
        this.actionService = actionService;
        this.context = context;
        this.formSubmissionSyncService = formSubmissionSyncService;
        this.allFormVersionSyncService = allFormVersionSyncService;
        this.additionalSyncService = null;
        task = new LockingBackgroundTask(progressIndicator);
    }

    public void setAdditionalSyncService(AdditionalSyncService additionalSyncService) {
        this.additionalSyncService = additionalSyncService;
    }

    public void updateFromServer(final AfterFetchListener afterFetchListener) {
        if (org.ei.opensrp.Context.getInstance().IsUserLoggedOut()) {
            logInfo("Not updating from server as user is not logged in.");
            return;
        }

        task.doActionInBackground(new BackgroundAction<FetchStatus>() {
            public FetchStatus actionToDoInBackgroundThread() {

                FetchStatus fetchStatusForForms = formSubmissionSyncService.sync();
                Log.d(TAG,"fetched Status "+new Gson().toJson(fetchStatusForForms));
                FetchStatus fetchStatusForActions = actionService.fetchNewActions();

                Log.d(TAG,"fetchStatusForActions "+new Gson().toJson(fetchStatusForActions));
                FetchStatus fetchStatusAdditional = additionalSyncService == null ? nothingFetched : additionalSyncService.sync();

                Log.d(TAG,"fetchStatusAdditional "+new Gson().toJson(fetchStatusAdditional));
                if(org.ei.opensrp.Context.getInstance().configuration().shouldSyncForm()) {

                    allFormVersionSyncService.verifyFormsInFolder();
                    FetchStatus fetchVersionStatus = allFormVersionSyncService.pullFormDefinitionFromServer();
                    DownloadStatus downloadStatus = allFormVersionSyncService.downloadAllPendingFormFromServer();

                    if(downloadStatus == DownloadStatus.downloaded) {
                        allFormVersionSyncService.unzipAllDownloadedFormFile();
                    }

                    if(fetchVersionStatus == fetched || downloadStatus == DownloadStatus.downloaded) {
                        Log.d(TAG,"am in fetched ");
                        return fetched;
                    }
                }


                if(fetchStatusForActions == fetched || fetchStatusForForms == fetched || fetchStatusAdditional == fetched)
                    return fetched;

                return fetchStatusForForms;
            }

            public void postExecuteInUIThread(FetchStatus result) {
                if (result != null && context != null && result != nothingFetched) {
                    Toast.makeText(context, result.displayValue(), Toast.LENGTH_SHORT).show();
                }
                afterFetchListener.afterFetch(result);
            }
        });
    }
}