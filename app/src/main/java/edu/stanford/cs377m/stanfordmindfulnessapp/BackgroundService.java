package edu.stanford.cs377m.stanfordmindfulnessapp;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;


/**
 * Created by peterwashington on 2/27/16.
 */
public class BackgroundService extends IntentService {

    public static final String PARAM_IN_MSG = "imsg";
    public static final String PARAM_OUT_MSG = "omsg";

    public static final String TAG = "BackgroundService";

    private static final int WAIT_SECONDS = 10;

    String fwVersion = null;
    String hwVersion = null;

    public BackgroundService() {
        super("");

    }

    @Override
    protected void onHandleIntent(Intent workIntent) {
        // Gets data from the incoming Intent
        String dataString = workIntent.getDataString();
        // Do work here, based on the contents of dataString

        int i = 0;

        while (true) {
            if (i % 100000000 == 0) {
                Log.i(TAG, "User:" + UserStatus.currUser);
                Log.i(TAG, "Heart Rate:" + UserStatus.heartRate);


//                Intent broadcastIntent = new Intent();
//                broadcastIntent.setAction("RESPONSE");
//                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
//                broadcastIntent.putExtra(PARAM_OUT_MSG, "ayeee");
//                sendBroadcast(broadcastIntent);

            }
            i++;
            if (i > 2000000000) {
                i = 0;
            }
        }

    }

}
