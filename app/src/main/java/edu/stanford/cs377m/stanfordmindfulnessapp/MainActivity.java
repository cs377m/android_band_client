package edu.stanford.cs377m.stanfordmindfulnessapp;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandPendingResult;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.notifications.VibrationType;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.tiles.BandIcon;
import com.microsoft.band.tiles.BandTile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements HeartRateConsentListener {

    private static final String TAG = "MainActivity";

    private static BandInfo[] pairedBands;

    private static BandClient bandClient;

    private BandIcon tileIcon;

    private Activity activity;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private HeartRateConsentListener mHeartRateConsentListener = new HeartRateConsentListener() {
        @Override
        public void userAccepted(boolean b) {
            // handle user's heart rate consent decision
            if (b == true) {
                // Consent has been given, start HR sensor event listener
                startHRListener();
            } else {
                // Consent hasn't been given
                Log.i(TAG, "NO CONSENT YET");
            }
        }
    };

    private BandHeartRateEventListener heartRateListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                Log.i(TAG, "HEART RATE EVENT");
                UserStatus.heartRate = event.getHeartRate();


                try {
                    URL url = new URL("http://ec2-54-165-135-172.compute-1.amazonaws.com/post/" + UserStatus.heartRate);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    writeToFile(UserStatus.heartRate + "\n", UserStatus.currUser + ".txt");
                } catch (Exception e) {}
            }
        }

    };

    public void startHRListener() {
        try {
            // register HR sensor event listener
            bandClient.getSensorManager().registerHeartRateEventListener(heartRateListener);
        } catch (BandIOException ex) {
        } catch (BandException e) {
            String exceptionMessage="";
            switch (e.getErrorType()) {
                case UNSUPPORTED_SDK_VERSION_ERROR:
                    exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.";
                    break;
                case SERVICE_ERROR:
                    exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.";
                    break;
                default:
                    exceptionMessage = "Unknown error occurred: " + e.getMessage();
                    break;
            }
        } catch (Exception e) {
        }
    }

    private void setupBand() {

        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        pairedBands = BandClientManager.getInstance().getPairedBands();
        if (pairedBands.length == 0 ) {
            Log.i(TAG, "No paired Bands found!");
            return;
        } else {
            bandClient = BandClientManager.getInstance().create(this, pairedBands[0]);
        }

        final Activity act = this;

        // Connect to the Band and create main tile.
        final BandPendingResult<ConnectionState> pendingResult = MainActivity.bandClient.connect();

        if (UserStatus.tileUUID == null) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        if (!UserStatus.bandStatus) {
                            ConnectionState state = pendingResult.await();
                            if (state == ConnectionState.CONNECTED) {
                                Log.i(TAG, "Connected to Band!");
                            } else {
                                Log.i(TAG, "Failed to connect.");
                            }
                            int tileCapacity = MainActivity.bandClient.getTileManager().getRemainingTileCapacity().await();
                            Bitmap smallIconBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_4444);
                            BandIcon smallIcon = BandIcon.toBandIcon(smallIconBitmap);

                            Drawable myDrawable = getResources().getDrawable(edu.stanford.cs377m.stanfordmindfulnessapp.R.drawable.flower_bitmap); // flower_bitmap
                            Bitmap myLogo = ((BitmapDrawable) myDrawable).getBitmap();

                            Bitmap tileIconBitmap = Bitmap.createBitmap(46, 46, Bitmap.Config.ARGB_4444);
                            tileIcon = BandIcon.toBandIcon(myLogo);

                            UserStatus.tileUUID = UUID.randomUUID();
                            final BandTile tile = new BandTile.Builder(UserStatus.tileUUID, "Stanford Mindfulness", tileIcon).setTileSmallIcon(smallIcon).build();
                            if (bandClient.getTileManager().addTile(act, tile).await()) {
                                // do work if the tile was successfully created
                                Log.i(TAG, "TILE SUCCESSFULLY CREATED!" + UserStatus.bandStatus);
                                UserStatus.bandStatus = true;
                            } else {
                                Log.i(TAG, "TILE UNSUCCESSFULLY CREATED!");
                            }

                            try {
                                if(bandClient.getSensorManager().getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                                    Log.i(TAG, "HR CONSENT GRANTED");
                                    startHRListener();
                                } else {
                                    Log.i(TAG, "NO HR CONSENT GRANTED");
                                    bandClient.getSensorManager().requestHeartRateConsent(MainActivity.this, mHeartRateConsentListener);
                                }
                            } catch(Exception e) {
                                Log.i(TAG, "Band Exception registering heart rate sensor");
                                e.printStackTrace();
                            }

                        }

                    } catch (InterruptedException e) {
                        Log.i(TAG, "Interrupted Exception connecting");
                        e.printStackTrace();
                    } catch (BandException e) {
                        Log.i(TAG, "Band Exception connecting");
                        e.printStackTrace();
                    }
                }
            }).start();
        }



        // Start background service.
        Intent msgIntent = new Intent(this, BackgroundService.class);
        msgIntent.putExtra(BackgroundService.PARAM_IN_MSG, "strInputMsg");
        startService(msgIntent);

    }

    @Override
    public void userAccepted(boolean consentGiven) {
        if (bandClient.getSensorManager().getCurrentHeartRateConsent() != UserConsent.GRANTED) {
            bandClient.getSensorManager().requestHeartRateConsent(this, this);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.activity = this;

        final Button movementOne = (Button) findViewById(R.id.imageButton20);
        movementOne.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Start activity on HoloLens.
                taiChiGame();
                try {
                    URL url = new URL("http://ec2-54-165-135-172.compute-1.amazonaws.com/change_activity/1");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                } catch (Exception e) {}
                writeToFile("ACTIVITY1" + "\n", UserStatus.currUser + ".txt");
            }
        });

        final Button movementTwo = (Button) findViewById(R.id.imageButton20);
        movementTwo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Start activity on HoloLens.
                taiChiGame();
                try {
                    URL url = new URL("http://ec2-54-165-135-172.compute-1.amazonaws.com/change_activity/2");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                } catch (Exception e) {}
                writeToFile("ACTIVITY2" + "\n", UserStatus.currUser + ".txt");
            }
        });

        final Button movementThree = (Button) findViewById(R.id.imageButton20);
        movementThree.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Start activity on HoloLens.
                taiChiGame();
                try {
                    URL url = new URL("http://ec2-54-165-135-172.compute-1.amazonaws.com/change_activity/3");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                } catch (Exception e) {}
                writeToFile("ACTIVITY3" + "\n", UserStatus.currUser + ".txt");
            }
        });

        final Button movementFour = (Button) findViewById(R.id.imageButton20);
        movementFour.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Start activity on HoloLens.
                taiChiGame();
                try {
                    URL url = new URL("http://ec2-54-165-135-172.compute-1.amazonaws.com/change_activity/4");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                } catch (Exception e) {}
                writeToFile("ACTIVITY4" + "\n", UserStatus.currUser + ".txt");
            }
        });

        final Button movementFive = (Button) findViewById(R.id.imageButton4);
        movementFive.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Start activity on HoloLens.
                taiChiGame();
                try {
                    URL url = new URL("http://ec2-54-165-135-172.compute-1.amazonaws.com/change_activity/5");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                } catch (Exception e) {}
                writeToFile("ACTIVITY5" + "\n", UserStatus.currUser + ".txt");
            }
        });

        final Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Log start of user test for a particular user.
                writeToFile("START" + "\n", UserStatus.currUser + ".txt");
            }
        });

        final Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Log end of user test for a particular user.
                writeToFile("END" + "\n", UserStatus.currUser + ".txt");
            }
        });

        final EditText userNumber = (EditText) findViewById(R.id.editText2);

        final Button participantNumberButton = (Button) findViewById(R.id.imageButton5);
        participantNumberButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // Start activity on HoloLens.
                UserStatus.currUser = Integer.parseInt(userNumber.getText().toString());
                Log.i(TAG, "UPDATED USER");
            }
        });


        setupBand();

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i("TAG", "INTENT RECEIVED!!!!");
                //taiChiGame(); //TODO: IS THIS WHERE CLICKING BAND ICON TRIGGERS?
            }
        };

        IntentFilter filter = new IntentFilter("RESPONSE");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        registerReceiver(broadcastReceiver, filter);

    }


    public void taiChiGame() {

        final Handler h = new Handler();

        final FinalCounter breath = new FinalCounter(1);

        try {
            bandClient.getNotificationManager().showDialog(UserStatus.tileUUID, "Get ready for:", "Tai Chi Game!").await();
            Thread.sleep(4 * 1000);
        } catch (Exception e) {
        }

        try {
            bandClient.getNotificationManager().showDialog(UserStatus.tileUUID, "Game Has", "Started").await();
            bandClient.getNotificationManager().vibrate(VibrationType.ONE_TONE_HIGH);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(edu.stanford.cs377m.stanfordmindfulnessapp.R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == edu.stanford.cs377m.stanfordmindfulnessapp.R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void writeToFile(String data, String fileName) {
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            File dir = new File(sdcard.getAbsolutePath() + "/" + "ProjectLive" + "/");
            dir.mkdir();
            File file = new File(dir, fileName);
            FileOutputStream os = new FileOutputStream(file, true);
            os.write(data.getBytes());
            os.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
}
