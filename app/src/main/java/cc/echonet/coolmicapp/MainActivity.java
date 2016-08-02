/*
 *      Copyright (C) Jordan Erickson                     - 2014-2016,
 *      Copyright (C) Löwenfelsen UG (haftungsbeschränkt) - 2015-2016
 *       on behalf of Jordan Erickson.
 */

/*
 * This file is part of Cool Mic.
 * 
 * Cool Mic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Cool Mic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Cool Mic.  If not, see <http://www.gnu.org/licenses/>.
 */
package cc.echonet.coolmicapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import cc.echonet.coolmicdspjava.Wrapper;

/**
 * This activity demonstrates how to use JNI to encode and decode ogg/vorbis audio
 */
public class MainActivity extends Activity {


    final Context context = this;
    Thread streamThread;
    boolean isThreadOn = false;
    CoolMic coolmic = null;
    Button start_button;
    Button stop_button;
    Animation animation = new AlphaAnimation(1, 0);
    ColorDrawable gray_color = new ColorDrawable(Color.parseColor("#66999999"));
    ColorDrawable[] color = {gray_color, new ColorDrawable(Color.RED)};
    TransitionDrawable trans = new TransitionDrawable(color);
    Drawable buttonColor;
    ImageView imageView1;
    Menu myMenu;
    boolean backyes = false;
    ClipboardManager myClipboard;
    long timeInMilliseconds = 0L;
    long timeSwapBuff = 0L;
    long updatedTime = 0L;

    TextView txtListeners;

    StreamStatsReceiver mStreamStatsReceiver = new StreamStatsReceiver();
    String strStreamFetchStatsURL;


    /**
     * Text view to show logged messages
     */
    private TextView logArea;
    //variable declaration for timer starts here
    private long startTime = 0L;
    private long lastStatsFetch = 0L;
    //code for displaying timer starts here
    Runnable updateTimerThread = new Runnable() {

        @Override
        public void run() {
            runOnUiThread(new Thread(new Runnable() {
                @Override
                public void run() {
                    timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
                    updatedTime = timeSwapBuff + timeInMilliseconds;
                    int secs = (int) (updatedTime / 1000);
                    int mins = secs / 60;
                    int hours = mins / 60;
                    secs = secs % 60;
                    mins = mins % 60;

                    timerValue.setText(MainActivity.this.getString(R.string.timer_format, hours, mins, secs));

                    if(lastStatsFetch+15*1000 < timeInMilliseconds) {
                        StreamStatsService.startActionStatsFetch(MainActivity.this, strStreamFetchStatsURL);
                        lastStatsFetch = timeInMilliseconds;
                    }
                }
            }));
            customHandler.postDelayed(this, 0);
        }
    };
    private TextView timerValue;
    private Handler customHandler = new Handler();

    //variable declaration for timer ends here
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (isThreadOn) {
            menu.findItem(R.id.menu_action_settings).setVisible(false);
        } else {
            menu.findItem(R.id.menu_action_settings).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu, menu);
        myMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_settings:
                goSettings();
                return true;
            case R.id.menu_action_about:
                goAbout();
                return true;
            case R.id.menu_action_help:
                Intent helpIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://coolmic.net/help"));
                startActivity(helpIntent);
                return true;
            case R.id.menu_action_quit:
                exitApp();
                return true;
            default:
                Toast.makeText(getApplicationContext(), R.string.menu_action_default, Toast.LENGTH_LONG).show();
                break;
        }
        return true;
    }

    private void exitApp() {
        ClearLED();
        Wrapper.stop();
        Wrapper.unref();
        finish();
        System.exit(0);
    }

    private void goSettings() {
        Intent i = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(i);
        finish();
    }

    private void goAbout() {

        Log.d("MainActivity", "goAbout() ");

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popUpView = inflater.inflate(R.layout.popup_about, null, false);

        final PopupWindow popUp = new PopupWindow(this);

        Button close = (Button) popUpView.findViewById(R.id.cmdPopUpDismiss);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View popupView) {
                popUp.dismiss();
            }
        });

        ((TextView) popUpView.findViewById(R.id.txtVersion)).setText(BuildConfig.VERSION_NAME);
        ((TextView) popUpView.findViewById(R.id.txtBuildType)).setText(BuildConfig.BUILD_TYPE);
        ((TextView) popUpView.findViewById(R.id.txtGITBranch)).setText(BuildConfig.GIT_BRANCH);
        ((TextView) popUpView.findViewById(R.id.txtGITRevision)).setText(BuildConfig.GIT_REVISION);
        ((TextView) popUpView.findViewById(R.id.txtGITAuthor)).setText(BuildConfig.GIT_AUTHOR);
        ((TextView) popUpView.findViewById(R.id.txtGITDirty)).setText(BuildConfig.GIT_DIRTY);

        popUpView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        popUp.setContentView(popUpView);

        Log.d("MainActivity", String.format("h: %s w: %s h: %s w: %s", popUp.getHeight(), popUp.getWidth(), popUpView.getMeasuredHeight(), popUpView.getMeasuredWidth() ));

        popUp.setHeight(popUpView.getMeasuredHeight());
        popUp.setWidth(popUpView.getMeasuredWidth());

        popUp.showAtLocation(popUpView, Gravity.CENTER, 0, 0);

        Log.d("MainActivity", "goAbout() end ");
    }

    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null &&
                cm.getActiveNetworkInfo().isConnectedOrConnecting();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            imageView1.getLayoutParams().height = 180;
        } else {
            imageView1.getLayoutParams().height = 400;
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void RedFlashLight() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notif = new Notification.Builder(context).setLights(0xFFff0000, 100, 100).setSmallIcon(R.drawable.icon).setContentTitle("Streaming").setContentText("Streaming...").build();
        nm.notify(Constants.NOTIFICATION_ID_LED, notif);
    }

    private void ClearLED() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(Constants.NOTIFICATION_ID_LED);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isThreadOn) {
            RedFlashLight();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v("$$$$$$", "In Method: onDestroy()");
        ClearLED();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.home);
        timerValue = (TextView) findViewById(R.id.timerValue);
        BroadcastReceiver mPowerKeyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String strAction = intent.getAction();
                if (strAction.equals(Intent.ACTION_SCREEN_OFF) || strAction.equals(Intent.ACTION_SCREEN_ON) || strAction.equals(Intent.ACTION_USER_PRESENT)) {
                    if (isThreadOn) {
                        RedFlashLight();
                    }
                }
            }
        };
        final IntentFilter theFilter = new IntentFilter();
        /** System Defined Broadcast */
        theFilter.addAction(Intent.ACTION_SCREEN_ON);
        theFilter.addAction(Intent.ACTION_SCREEN_OFF);
        theFilter.addAction(Intent.ACTION_USER_PRESENT);

        getApplicationContext().registerReceiver(mPowerKeyReceiver, theFilter);

        imageView1 = (ImageView) findViewById(R.id.imageView1);

        Log.v("onCreate", (imageView1 == null ? "iv null" : "iv ok"));

        android.view.ViewGroup.LayoutParams layoutParams = imageView1.getLayoutParams();

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutParams.height = 400;
        } else {
            layoutParams.height = 180;
        }

        imageView1.setLayoutParams(layoutParams);

        myClipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        animation.setDuration(500); // duration - half a second
        animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
        animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
        animation.setRepeatMode(Animation.REVERSE);
        start_button = (Button) findViewById(R.id.start_recording_button);
        stop_button = (Button) findViewById(R.id.stop_recording_button);
        buttonColor = start_button.getBackground();
        logArea = (TextView) findViewById(R.id.log_area);
        logArea.setMovementMethod(new ScrollingMovementMethod());

        coolmic = new CoolMic(this, "default");

        if(Wrapper.getState() == Wrapper.WrapperInitializationStatus.WRAPPER_UNINITIALIZED)
        {
            if(Wrapper.init() == Wrapper.WrapperInitializationStatus.WRAPPER_INITIALIZATION_ERROR)
            {
                Log.d("WrapperInit", Wrapper.getInitException().toString());
                Toast.makeText(getApplicationContext(), "Could not initialize native components :( Blocking controls!", Toast.LENGTH_SHORT).show();
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Native components initialized!", Toast.LENGTH_SHORT).show();
            }
        }
        else if(Wrapper.init() == Wrapper.WrapperInitializationStatus.WRAPPER_INITIALIZATION_ERROR)
        {
            Toast.makeText(getApplicationContext(), "Previous problem detected with native components :( Blocking controls!", Toast.LENGTH_SHORT).show();
        }
        else if(Wrapper.init() != Wrapper.WrapperInitializationStatus.WRAPPER_INTITIALIZED)
        {
            Toast.makeText(getApplicationContext(), "Native components in unknown state!", Toast.LENGTH_SHORT).show();
        }

        txtListeners = (TextView) findViewById(R.id.txtListeners);
        IntentFilter mStatusIntentFilter = new IntentFilter( Constants.BROADCAST_STREAM_STATS_SERVICE );
        LocalBroadcastManager.getInstance(this).registerReceiver(mStreamStatsReceiver, mStatusIntentFilter);
    }

    public void onImageClick(View view) {

        try {
            String portnum = "";
            String server = coolmic.getServerName();
            Integer port_num = 8000;
            int counter = 0;
            for (int i = 0; i < server.length(); i++) {
                if (server.charAt(i) == ':') {
                    counter++;
                }
            }
            if (counter == 1) {
                if (server.indexOf("/") > 0) {
                    String[] split = server.split(":");
                    server = split[0].concat(":").concat(split[1]);
                    portnum = "8000";
                    port_num = Integer.parseInt(portnum);
                } else {
                    String[] split = server.split(":");
                    server = split[0];
                    portnum = split[1];
                    port_num = Integer.parseInt(portnum);
                }
            } else if (counter == 2) {
                String[] split = server.split(":");
                server = split[0].concat(":").concat(split[1]);
                portnum = split[2];
                port_num = Integer.parseInt(portnum);
            }
            Log.d("VS", server);
            Log.d("VS", portnum);
            if (server != null && !server.isEmpty()) {
                String text = server + ":" + port_num.toString() + "/" + coolmic.getMountpoint();
                ClipData myClip = ClipData.newPlainText("text", text);
                myClipboard.setPrimaryClip(myClip);
                Toast.makeText(getApplicationContext(), "Broadcast URL copied to clipboard!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Set the connection details", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("VS", "Exception", e);
        }

    }

    @Override
    public void onBackPressed() {
        // Write your code here
        if (isThreadOn) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
            alertDialog.setTitle("Stop Broadcasting?");
            alertDialog.setMessage("Tap [ Ok ] to stop broadcasting.");
            alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    backyes = false;
                    dialog.cancel();
                }
            });
            alertDialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    backyes = true;
                    dialog.cancel();
                    invalidateOptionsMenu();
                    start_button.clearAnimation();
                    start_button.setBackground(buttonColor);
                    start_button.setText(R.string.start_broadcast);

                    ClearLED();

                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            });
            alertDialog.show();
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    public void startRecording(View view) {
        if(isThreadOn) {
            stopRecording(view);

            return;
        }

        if(!checkPermission())
        {
            Toast.makeText(getApplicationContext(), "Missing Permissions. Please request them in the Settings.", Toast.LENGTH_LONG).show();
            return;
        }

        if(Wrapper.getState() != Wrapper.WrapperInitializationStatus.WRAPPER_INTITIALIZED) {
            Toast.makeText(getApplicationContext(), "Native components not ready.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!isOnline()) {
            Toast.makeText(getApplicationContext(), "Check Internet Connection !", Toast.LENGTH_LONG).show();
            return;
        }

        if (!coolmic.isConnectionSet()) {
            Toast.makeText(getApplicationContext(), "Set the connection details !", Toast.LENGTH_LONG).show();
            return;
        }

        invalidateOptionsMenu();
        isThreadOn = true;
        //screenreceiver.setThreadStatus(true);
        startService(new Intent(getBaseContext(), MyService.class));
        RedFlashLight();
        timeInMilliseconds = 0L;
        timeSwapBuff = 0L;
        start_button.startAnimation(animation);
        start_button.setBackground(trans);
        trans.startTransition(5000);
        start_button.setText(R.string.broadcasting);
        streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (isThreadOn) {
                    try {
                        String portnum;
                        String server = coolmic.getServerName();
                        Integer port_num = 8000;

                        if (server.indexOf(":") > 0) {
                            String[] split = server.split(":");
                            server = split[0];
                            portnum = split[1];
                            port_num = Integer.parseInt(portnum);
                        }

                        Log.d("VS", server);
                        Log.d("VS", port_num.toString());
                        String username = coolmic.getUsername();
                        String password = coolmic.getPassword();
                        String mountpoint = coolmic.getMountpoint();
                        String sampleRate_string = coolmic.getSampleRate();
                        String channel_string = coolmic.getChannels();
                        String quality_string = coolmic.getQuality();
                        String title = coolmic.getTitle();
                        String artist = coolmic.getArtist();

                        Log.d("VS", String.format("Server: %s Port: %d Username: %s Password: %s Mountpoint: %s Samplerate: %s Channels: %s Quality: %s Title: %s Artist: %s", server, port_num, username, password, mountpoint, sampleRate_string, channel_string, quality_string, title, artist));

                        Integer buffersize = AudioRecord.getMinBufferSize(Integer.parseInt(sampleRate_string), Integer.parseInt(channel_string) == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                        Log.d("VS", "Minimum Buffer Size: " + String.valueOf(buffersize));
                        Wrapper.init(MainActivity.this, server, port_num, username, password, mountpoint, "audio/ogg; codec=vorbis", Integer.parseInt(sampleRate_string), Integer.parseInt(channel_string), buffersize);

                        int status = Wrapper.start();

                        Log.d("VS", "Status:" + status);

                        if(status != 0)
                        {
                            throw new Exception("Failed to start Recording: "+String.valueOf(status));
                        }

                        strStreamFetchStatsURL = String.format("http://%s:%s@%s:%s/admin/stats.xml?mount=/%s", username, password, server, port_num, mountpoint);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("VS", "Recording Start: Exception: ", e);

                        MainActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                stopRecording(null);

                                Toast.makeText(MainActivity.this, "Failed to start Recording. ", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }

            }

        });
        streamThread.start();
    }

    public void stopRecording(@SuppressWarnings("unused") View view) {
        if(Wrapper.getState() != Wrapper.WrapperInitializationStatus.WRAPPER_INTITIALIZED) {
            Toast.makeText(getApplicationContext(), "Native components not ready.", Toast.LENGTH_LONG).show();
        }

        timeSwapBuff += timeInMilliseconds;
        customHandler.removeCallbacks(updateTimerThread);
        //code to stop timer starts here
        ClearLED();
        invalidateOptionsMenu();
        start_button.clearAnimation();
        start_button.setBackground(buttonColor);
        start_button.setText(R.string.start_broadcast);
        stopService(new Intent(getBaseContext(), MyService.class));


        Wrapper.stop();
        Wrapper.unref();

        isThreadOn = false;
    }

    @SuppressWarnings("unused")
    private void logMessage(String msg) {
        logArea.append(msg + "\n");
        final int scrollAmount = logArea.getLayout().getLineTop(logArea.getLineCount())
                - logArea.getHeight();
        if (scrollAmount > 0)
            logArea.scrollTo(0, scrollAmount);
        else
            logArea.scrollTo(0, 0);
    }

    @SuppressWarnings("unused")
    private void callbackHandler(int what)
    {
        Log.d("Handler", String.valueOf(what));

        final int what_final = what;
        MainActivity.this.runOnUiThread(new Runnable(){
            public void run(){
                switch(what_final) {
                    case 1:
                        timeInMilliseconds = 0L;
                        timeSwapBuff = 0L;
                        updatedTime = 0L;
                        timeSwapBuff += timeInMilliseconds;
                        customHandler.removeCallbacks(updateTimerThread);
                        startTime = SystemClock.uptimeMillis();
                        customHandler.postDelayed(updateTimerThread, 0);
                        break;
                    case 2:
                        //code to stop timer starts here
                        timeSwapBuff += timeInMilliseconds;
                        customHandler.removeCallbacks(updateTimerThread);
                        //code to stop timer starts here
                        start_button.clearAnimation();
                        start_button.setBackground(buttonColor);
                        start_button.setText(R.string.start_broadcast);

                        ClearLED();
                        //logMessage("Stopping the broadcasting");
                        break;
                    case 3:
                        //code to stop timer starts here
                        timeSwapBuff += timeInMilliseconds;
                        customHandler.removeCallbacks(updateTimerThread);
                        //code to stop timer starts here
                        start_button.clearAnimation();
                        start_button.setBackground(buttonColor);
                        start_button.setText(R.string.start_broadcast);

                        ClearLED();

                        isThreadOn = false;

                        Toast.makeText(MainActivity.this, "there was an error!", Toast.LENGTH_LONG).show();

                        break;
                }
            }
        });
    }


    // Broadcast receiver for receiving status updates from the IntentService
    private class StreamStatsReceiver extends BroadcastReceiver
    {
        // Prevents instantiation
        private StreamStatsReceiver() {
        }
        // Called when the BroadcastReceiver gets an Intent it's registered to receive

        public void onReceive(Context context, Intent intent) {
            StreamStats obj = intent.getParcelableExtra(Constants.EXTRA_DATA_STATS_OBJ);

            txtListeners.setText(String.format("%s(%s)", obj.getListenersCurrent(), obj.getListenersPeak()));
        }
    }
}
