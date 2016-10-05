package us.potatosaur.p0t4t0labs.potatoradio;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mantz_it.rfanalyzer.AnalyzerProcessingLoop;
import com.mantz_it.rfanalyzer.AnalyzerSurface;
import com.mantz_it.rfanalyzer.Demodulator;
import com.mantz_it.rfanalyzer.FileIQSource;
import com.mantz_it.rfanalyzer.HackrfSource;
import com.mantz_it.rfanalyzer.IQSourceInterface;
import com.mantz_it.rfanalyzer.RtlsdrSource;
import com.mantz_it.rfanalyzer.Scheduler;
import com.mantz_it.rfanalyzer.SettingsActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bg.cytec.android.fskmodem.FSKConfig;
import bg.cytec.android.fskmodem.FSKDecoder;
import bg.cytec.android.fskmodem.FSKEncoder;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        IQSourceInterface.Callback, AnalyzerSurface.CallbackInterface {

    private static final String LOGTAG = "MainActivity";
    public static final int RTL2832U_RESULT_CODE = 1234; // arbitrary value, used when sending intent to RTL2832U
    private static final int FILE_SOURCE = 0;
    private static final int HACKRF_SOURCE = 1;
    private static final int RTLSDR_SOURCE = 2;
    private static final String[] SOURCE_NAMES = new String[] {"filesource", "hackrf", "rtlsdr"};

    private Bundle savedInstanceState = null;
    private SharedPreferences preferences = null;
    private Process logcat = null;
    private FragmentManager fragmentManager = null;

    private boolean running = false;
    private Scheduler scheduler = null;
    private Demodulator demodulator = null;
    private IQSourceInterface source = null;
    private int demodulationMode = Demodulator.DEMODULATION_WFM;
    private AnalyzerSurface analyzerSurface = null;
    private AnalyzerProcessingLoop analyzerProcessingLoop = null;

    public FSKConfig fskConfig = null;
    public FSKDecoder fskDecoder = null;
    private FSKEncoder fskEncoder = null;
    private AudioTrack audioTrack;
    public Transceiver transceiver = null;

    private MenuItem mi_startStop = null;
    private MenuItem mi_demodulationMode = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request permissions to support Android Marshmallow and above devices
        if (Build.VERSION.SDK_INT >= 23) {
            checkPermissions();
        }

        this.savedInstanceState = savedInstanceState;
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Hide application title in action bar (takes too much space)
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                drawer.bringChildToFront(drawerView);
                drawer.requestLayout();
            }
        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        final NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        /* END UI SETUP *************************************************************************/
        /* BEGIN PREFERENCES ********************************************************************/

        // Set default Settings on first run:
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Get reference to the shared preferences:
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Overwrite defaults for file paths in the preferences:
        String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();	// get the path to the ext. storage
        // File Source file:
        String defaultFile = getString(R.string.pref_filesource_file_default);
        if(preferences.getString(getString(R.string.pref_filesource_file), "").equals(defaultFile))
            preferences.edit().putString(getString(R.string.pref_filesource_file), extStorage + "/" + defaultFile).apply();
        // Log file:
        defaultFile = getString(R.string.pref_logfile_default);
        if(preferences.getString(getString(R.string.pref_logfile), "").equals(defaultFile))
            preferences.edit().putString(getString(R.string.pref_logfile), extStorage + "/" + defaultFile).apply();

        // Start logging if enabled:
        if(preferences.getBoolean(getString(R.string.pref_logging), false)) {
            try{
                File logfile = new File(preferences.getString(getString(R.string.pref_logfile), ""));
                logfile.getParentFile().mkdir();	// Create folder
                logcat = Runtime.getRuntime().exec("logcat -f " + logfile);
                Log.i("MainActivity", "onCreate: started logcat ("+logcat.toString()+") to " + logfile);
            } catch (Exception e) {
                Log.e("MainActivity", "onCreate: Failed to start logging!");
            }
        }

        /* END PREFERENCES **********************************************************************/
        /* BEGIN Service Setup ******************************************************************/

        try {
            // minimodem --rx -R 29400 -M 7350 -S 4900 1225
            fskConfig = new FSKConfig(FSKConfig.SAMPLE_RATE_29400, FSKConfig.PCM_16BIT,
                    FSKConfig.CHANNELS_MONO, FSKConfig.SOFT_MODEM_MODE_4, FSKConfig.THRESHOLD_20P);
        } catch (IOException e) {
            Log.e(LOGTAG, "FSK Config Failed");
            e.printStackTrace();
            return;
        }

        /**
         * TODO feed the decoder data, the following is the sample code:
         *
         * while (mRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
         *
         *    short[] data = new short[mBufferSize/2]; //the buffer size is in bytes
         *
         *    // gets the audio output from microphone to short array samples
         *    mRecorder.read(data, 0, mBufferSize/2);
         *
         *    mDecoder.appendSignal(data);
         * }
          */

        fskDecoder = new FSKDecoder(fskConfig, new FSKDecoder.FSKDecoderCallback() {

            @Override
            public void decoded(byte[] newData) {

                final String text = new String(newData);

                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(LOGTAG, "FSK Decoded: " + text);
                    }
                });
            }
        });

        /// INIT FSK ENCODER
        fskEncoder = new FSKEncoder(fskConfig, new FSKEncoder.FSKEncoderCallback() {
            @Override
            public void encoded(byte[] pcm8, short[] pcm16) {
                if (fskConfig.pcmFormat == fskConfig.PCM_16BIT) {
                    //16bit buffer is populated, 8bit buffer is null

                    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                            fskConfig.sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, pcm16.length*2,
                            AudioTrack.MODE_STATIC);

                    audioTrack.write(pcm16, 0, pcm16.length);

                    audioTrack.play();
                }
            }
        });

        // Initialize the Transceiver
        try{
            transceiver = new Transceiver(fskEncoder, fskDecoder);
        } catch (Exception e) {
            Log.e(LOGTAG, "Transmitter creation failed! " + e.getMessage());
        }


        // Restore / Initialize the running state and the demodulator mode:
        if(savedInstanceState != null) {
            running = savedInstanceState.getBoolean(getString(R.string.save_state_running));
            demodulationMode = savedInstanceState.getInt(getString(R.string.save_state_demodulatorMode));

			/* BUGFIX / WORKAROUND:
			 * The RTL2832U driver will not allow to close the socket and immediately start the driver
			 * again to reconnect after an orientation change / app kill + restart.
			 * It will report back in onActivityResult() with a -1 (not specified).
			 *
			 * Work-around:
			 * 1) We won't restart the Analyzer if the current source is set to a local RTL-SDR instance:
			 * 2) Delay the restart of the Analyzer after the driver was shut down correctly...
			 */
            if(running && Integer.valueOf(preferences.getString(getString(R.string.pref_sourceType), "1")) == RTLSDR_SOURCE
                    && !preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer),false)) {
                // 1) don't start Analyzer immediately
                running = false;

                // Just inform the user about what is going on (why does this take so long? ...)
                Toast.makeText(MainActivity.this,"Stopping and restarting RTL2832U driver...",Toast.LENGTH_SHORT).show();

                // 2) Delayed start of the Analyzer:
                Thread timer = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1500);
                            startAnalyzer();
                        } catch (InterruptedException e) {
                            Log.e(LOGTAG, "onCreate: (timer thread): Interrupted while sleeping.");
                        }
                    }
                };
                timer.start();
            }

        } else {
            // Set running to true if autostart is enabled (this will start the analyzer in onStart() )
            running = preferences.getBoolean((getString(R.string.pref_autostart)), false);
        }

        // Set the hardware volume keys to work on the music audio stream:
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        /* END Service Setup ******************************************************************/
        /* Show initial view ******************************************************************/

        // Create a analyzer surface:
        analyzerSurface = new AnalyzerSurface(this, this);
        analyzerSurface.setVerticalScrollEnabled(preferences.getBoolean(getString(R.string.pref_scrollDB), true));
        analyzerSurface.setVerticalZoomEnabled(preferences.getBoolean(getString(R.string.pref_zoomDB), true));
        analyzerSurface.setDecoupledAxis(preferences.getBoolean(getString(R.string.pref_decoupledAxis), false));
        analyzerSurface.setDisplayRelativeFrequencies(preferences.getBoolean(getString(R.string.pref_relativeFrequencies), false));
        analyzerSurface.setWaterfallColorMapType(Integer.valueOf(preferences.getString(getString(R.string.pref_colorMapType),"4")));
        analyzerSurface.setFftDrawingType(Integer.valueOf(preferences.getString(getString(R.string.pref_fftDrawingType),"2")));
        analyzerSurface.setFftRatio(Float.valueOf(preferences.getString(getString(R.string.pref_spectrumWaterfallRatio), "0.5")));
        analyzerSurface.setFontSize(Integer.valueOf(preferences.getString(getString(R.string.pref_fontSize),"2")));
        analyzerSurface.setShowDebugInformation(preferences.getBoolean(getString(R.string.pref_showDebugInformation), false));

        // Link
        AnalyzerFragment analyzerFragment = new AnalyzerFragment();
        analyzerFragment.setAnalyzerSurface(analyzerSurface);

        // Navigate to our initial view
        fragmentManager = getFragmentManager();
        fragmentManager.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                // Set checked whatever we just landed back on.
                String itemId = fragmentManager.getBackStackEntryAt(fragmentManager.getBackStackEntryCount() - 1).getName();
                navigationView.setCheckedItem(Integer.parseInt(itemId));
            }
        });

        FragmentTransaction trans = fragmentManager.beginTransaction();
        trans.replace(R.id.fragment_container, analyzerFragment);
        trans.addToBackStack("0");
        trans.commit();
        navigationView.setCheckedItem(R.id.nav_analyzer);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // close source
        if(source != null && source.isOpen())
            source.close();

        // stop logging:
        if(logcat != null) {
            try {
                logcat.destroy();
                logcat.waitFor();
                Log.i(LOGTAG, "onDestroy: logcat exit value: " + logcat.exitValue());
            } catch (Exception e) {
                Log.e(LOGTAG, "onDestroy: couldn't stop logcat: " + e.getMessage());
            }
        }

        // shut down RTL2832U driver if running:
        if(running && Integer.valueOf(preferences.getString(getString(R.string.pref_sourceType), "1")) == RTLSDR_SOURCE
                && !preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer),false)) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("iqsrc://-x"));	// -x is invalid. will cause the driver to shut down (if running)
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(LOGTAG, "onDestroy: RTL2832U is not installed");
            }
        }

        // Shut down FSK components
        fskEncoder.stop();
        fskDecoder.stop();

        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.STATE_INITIALIZED)
        {
            audioTrack.stop();
            audioTrack.release();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(getString(R.string.save_state_running), running);
        outState.putInt(getString(R.string.save_state_demodulatorMode), demodulationMode);
        if(analyzerSurface != null) {
            outState.putLong(getString(R.string.save_state_channelFrequency), analyzerSurface.getChannelFrequency());
            outState.putInt(getString(R.string.save_state_channelWidth), analyzerSurface.getChannelWidth());
            outState.putFloat(getString(R.string.save_state_squelch), analyzerSurface.getSquelch());
            outState.putLong(getString(R.string.save_state_virtualFrequency), analyzerSurface.getVirtualFrequency());
            outState.putInt(getString(R.string.save_state_virtualSampleRate), analyzerSurface.getVirtualSampleRate());
            outState.putFloat(getString(R.string.save_state_minDB), analyzerSurface.getMinDB());
            outState.putFloat(getString(R.string.save_state_maxDB), analyzerSurface.getMaxDB());
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        // Get a reference to the start-stop button:
        mi_startStop = menu.findItem(R.id.action_startStop);
        mi_demodulationMode = menu.findItem(R.id.action_setDemodulation);

        updateActionBar();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_startStop:
                if(running)
                    stopAnalyzer();
                else
                    startAnalyzer();
                break;
            case R.id.action_setDemodulation:
                showDemodulationDialog();
                break;
            case R.id.action_setFrequency:
                tuneToFrequency();
                break;
            case R.id.action_setGain:
                adjustGain();
                break;
            case R.id.action_autoscale:
                analyzerSurface.autoscale();
                break;
            case R.id.action_settings:
                Intent intentShowSettings = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intentShowSettings);
                break;
            default:
        }
        return true;
        //return super.onOptionsItemSelected(item);
    }

    /**
     * Will update the action bar icons and titles according to the current app state
     */
    private void updateActionBar() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
            // Set title and icon of the start/stop button according to the state:
            if(mi_startStop != null) {
                if (running) {
                    mi_startStop.setTitle(R.string.action_stop);
                    mi_startStop.setIcon(R.drawable.ic_action_pause);
                } else {
                    mi_startStop.setTitle(R.string.action_start);
                    mi_startStop.setIcon(R.drawable.ic_action_play);
                }
            }

            // Set title and icon for the demodulator mode button
            if(mi_demodulationMode != null) {
                int iconRes;
                int titleRes;
                switch (demodulationMode) {
                    case Demodulator.DEMODULATION_OFF:
                        iconRes = R.drawable.ic_action_demod_off;
                        titleRes = R.string.action_demodulation_off;
                        break;
                    case Demodulator.DEMODULATION_AM:
                        iconRes = R.drawable.ic_action_demod_am;
                        titleRes = R.string.action_demodulation_am;
                        break;
                    case Demodulator.DEMODULATION_NFM:
                        iconRes = R.drawable.ic_action_demod_nfm;
                        titleRes = R.string.action_demodulation_nfm;
                        break;
                    case Demodulator.DEMODULATION_WFM:
                        iconRes = R.drawable.ic_action_demod_wfm;
                        titleRes = R.string.action_demodulation_wfm;
                        break;
                    case Demodulator.DEMODULATION_LSB:
                        iconRes = R.drawable.ic_action_demod_lsb;
                        titleRes = R.string.action_demodulation_lsb;
                        break;
                    case Demodulator.DEMODULATION_USB:
                        iconRes = R.drawable.ic_action_demod_usb;
                        titleRes = R.string.action_demodulation_usb;
                        break;
                    default:
                        Log.e(LOGTAG,"updateActionBar: invalid mode: " + demodulationMode);
                        iconRes = -1;
                        titleRes = -1;
                        break;
                }
                if(titleRes > 0 && iconRes > 0) {
                    mi_demodulationMode.setTitle(titleRes);
                    mi_demodulationMode.setIcon(iconRes);
                }
            }
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if the user changed the preferences:
        checkForChangedPreferences();

        // Start the analyzer if running is true:
        if (running)
            startAnalyzer();

        // on the first time after the app was killed by the system, savedInstanceState will be
        // non-null and we restore the settings:
        if(savedInstanceState != null) {
            analyzerSurface.setVirtualFrequency(savedInstanceState.getLong(getString(R.string.save_state_virtualFrequency)));
            analyzerSurface.setVirtualSampleRate(savedInstanceState.getInt(getString(R.string.save_state_virtualSampleRate)));
            analyzerSurface.setDBScale(savedInstanceState.getFloat(getString(R.string.save_state_minDB)),
                    savedInstanceState.getFloat(getString(R.string.save_state_maxDB)));
            analyzerSurface.setChannelFrequency(savedInstanceState.getLong(getString(R.string.save_state_channelFrequency)));
            analyzerSurface.setChannelWidth(savedInstanceState.getInt(getString(R.string.save_state_channelWidth)));
            analyzerSurface.setSquelch(savedInstanceState.getFloat(getString(R.string.save_state_squelch)));
            if(demodulator != null && scheduler != null) {
                demodulator.setChannelWidth(savedInstanceState.getInt(getString(R.string.save_state_channelWidth)));
                scheduler.setChannelFrequency(savedInstanceState.getLong(getString(R.string.save_state_channelFrequency)));
            }
            savedInstanceState = null; // not needed any more...
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        boolean runningSaved = running;	// save the running state, to restore it after the app re-starts...
        stopAnalyzer();					// will stop the processing loop, scheduler and source
        running = runningSaved;			// running will be saved in onSaveInstanceState()

        // safe preferences:
        if(source != null) {
            SharedPreferences.Editor edit = preferences.edit();
            edit.putLong(getString(R.string.pref_frequency), source.getFrequency());
            edit.putInt(getString(R.string.pref_sampleRate), source.getSampleRate());
            edit.commit();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        Fragment nextFrag = null;
        Fragment current = fragmentManager.findFragmentById(R.id.fragment_container);

        // Where we switching to?
        if (id == R.id.nav_analyzer && !(current instanceof AnalyzerFragment)) {
            AnalyzerFragment af = new AnalyzerFragment();
            af.setAnalyzerSurface(analyzerSurface);
            nextFrag = af;
        } else if (id == R.id.nav_map && !(current instanceof MapFragment)) {
            nextFrag = new MapFragment();
        }

        if (nextFrag != null) {
            item.setChecked(true);
            FragmentTransaction trans = fragmentManager.beginTransaction();
            trans.replace(R.id.fragment_container, nextFrag);
            // Stick the item Id here so we can get it in addOnBackStackChangedListener
            trans.addToBackStack(Integer.toString(item.getItemId()));
            trans.commit();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Will check if any preference conflicts with the current state of the app and fix it
     */
    public void checkForChangedPreferences() {
        // Source Type (this is pretty complex as we have to check each type individually):
        int sourceType = Integer.valueOf(preferences.getString(getString(R.string.pref_sourceType), "1"));
        if(source != null) {
            switch (sourceType) {
                case FILE_SOURCE:
                    if(!(source instanceof FileIQSource)) {
                        source.close();
                        createSource();
                    }
                    else {
                        long freq = Integer.valueOf(preferences.getString(getString(R.string.pref_filesource_frequency), "97000000"));
                        int sampRate = Integer.valueOf(preferences.getString(getString(R.string.pref_filesource_sampleRate), "2000000"));
                        String fileName = preferences.getString(getString(R.string.pref_filesource_file), "");
                        int fileFormat = Integer.valueOf(preferences.getString(getString(R.string.pref_filesource_format), "0"));
                        boolean repeat = preferences.getBoolean(getString(R.string.pref_filesource_repeat), false);
                        if (freq != source.getFrequency() || sampRate != source.getSampleRate()
                                || !fileName.equals(((FileIQSource) source).getFilename())
                                || repeat != ((FileIQSource) source).isRepeat()
                                || fileFormat != ((FileIQSource) source).getFileFormat()) {
                            source.close();
                            createSource();
                        }
                    }
                    break;
                case HACKRF_SOURCE:
                    if(!(source instanceof HackrfSource)) {
                        source.close();
                        createSource();
                    }
                    else {
                        // overwrite hackrf source settings if changed:
                        boolean amp = preferences.getBoolean(getString(R.string.pref_hackrf_amplifier), false);
                        boolean antennaPower = preferences.getBoolean(getString(R.string.pref_hackrf_antennaPower), false);
                        int frequencyShift = Integer.valueOf(preferences.getString(getString(R.string.pref_hackrf_frequencyShift), "0"));
                        if(((HackrfSource)source).isAmplifierOn() != amp)
                            ((HackrfSource)source).setAmplifier(amp);
                        if(((HackrfSource)source).isAntennaPowerOn() != antennaPower)
                            ((HackrfSource)source).setAntennaPower(antennaPower);
                        if(((HackrfSource)source).getFrequencyShift() != frequencyShift)
                            ((HackrfSource)source).setFrequencyShift(frequencyShift);
                    }
                    break;
                case RTLSDR_SOURCE:
                    if(!(source instanceof RtlsdrSource)) {
                        source.close();
                        createSource();
                    }
                    else {
                        // Check if ip or port has changed and recreate source if necessary:
                        String ip = preferences.getString(getString(R.string.pref_rtlsdr_ip), "");
                        int port = Integer.valueOf(preferences.getString(getString(R.string.pref_rtlsdr_port), "1234"));
                        boolean externalServer = preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer), false);
                        if(externalServer) {
                            if(!ip.equals(((RtlsdrSource) source).getIpAddress()) || port != ((RtlsdrSource) source).getPort()) {
                                source.close();
                                createSource();
                                return;
                            }
                        } else {
                            if(!((RtlsdrSource) source).getIpAddress().equals("127.0.0.1") || 1234 != ((RtlsdrSource) source).getPort()) {
                                source.close();
                                createSource();
                                return;
                            }
                        }

                        // otherwise just overwrite rtl-sdr source settings if changed:
                        int frequencyCorrection = Integer.valueOf(preferences.getString(getString(R.string.pref_rtlsdr_frequencyCorrection), "0"));
                        int frequencyShift = Integer.valueOf(preferences.getString(getString(R.string.pref_rtlsdr_frequencyShift), "0"));
                        if(frequencyCorrection != ((RtlsdrSource) source).getFrequencyCorrection())
                            ((RtlsdrSource) source).setFrequencyCorrection(frequencyCorrection);
                        if(((RtlsdrSource)source).getFrequencyShift() != frequencyShift)
                            ((RtlsdrSource)source).setFrequencyShift(frequencyShift);
                    }
                    break;
                default:
            }
        }

        if(analyzerSurface != null) {
            // All GUI settings will just be overwritten:
            analyzerSurface.setVerticalScrollEnabled(preferences.getBoolean(getString(R.string.pref_scrollDB), true));
            analyzerSurface.setVerticalZoomEnabled(preferences.getBoolean(getString(R.string.pref_zoomDB), true));
            analyzerSurface.setDecoupledAxis(preferences.getBoolean(getString(R.string.pref_decoupledAxis), false));
            analyzerSurface.setDisplayRelativeFrequencies(preferences.getBoolean(getString(R.string.pref_relativeFrequencies), false));
            analyzerSurface.setWaterfallColorMapType(Integer.valueOf(preferences.getString(getString(R.string.pref_colorMapType),"4")));
            analyzerSurface.setFftDrawingType(Integer.valueOf(preferences.getString(getString(R.string.pref_fftDrawingType),"2")));
            analyzerSurface.setAverageLength(Integer.valueOf(preferences.getString(getString(R.string.pref_averaging),"0")));
            analyzerSurface.setPeakHoldEnabled(preferences.getBoolean(getString(R.string.pref_peakHold), false));
            analyzerSurface.setFftRatio(Float.valueOf(preferences.getString(getString(R.string.pref_spectrumWaterfallRatio), "0.5")));
            analyzerSurface.setFontSize(Integer.valueOf(preferences.getString(getString(R.string.pref_fontSize),"2")));
            analyzerSurface.setShowDebugInformation(preferences.getBoolean(getString(R.string.pref_showDebugInformation), false));
        }

        // Screen Orientation:
        String screenOrientation = preferences.getString(getString(R.string.pref_screenOrientation), "auto");
        if(screenOrientation.equals("auto"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        else if(screenOrientation.equals("landscape"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        else if(screenOrientation.equals("portrait"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else if(screenOrientation.equals("reverse_landscape"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        else if(screenOrientation.equals("reverse_portrait"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
    }

    /**
     * Will create a IQ Source instance according to the user settings.
     *
     * @return true on success; false on error
     */
    public boolean createSource() {
        long frequency;
        int sampleRate;
        int sourceType = Integer.valueOf(preferences.getString(getString(R.string.pref_sourceType), "1"));

        switch (sourceType) {
            case FILE_SOURCE:
                // Create IQ Source (filesource)
                try {
                    frequency = Integer.valueOf(preferences.getString(getString(R.string.pref_filesource_frequency), "97000000"));
                    sampleRate = Integer.valueOf(preferences.getString(getString(R.string.pref_filesource_sampleRate), "2000000"));
                } catch (NumberFormatException e) {
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "File Source: Wrong format of frequency or sample rate", Toast.LENGTH_LONG).show();
                        }
                    });
                    return false;
                }
                String filename = preferences.getString(getString(R.string.pref_filesource_file), "");
                int fileFormat = Integer.valueOf(preferences.getString(getString(R.string.pref_filesource_format), "0"));
                boolean repeat = preferences.getBoolean(getString(R.string.pref_filesource_repeat), false);
                source = new FileIQSource(filename, sampleRate, frequency, 16384, repeat, fileFormat);
                break;
            case HACKRF_SOURCE:
                // Create HackrfSource
                source = new HackrfSource();
                source.setFrequency(preferences.getLong(getString(R.string.pref_frequency),97000000));
                source.setSampleRate(preferences.getInt(getString(R.string.pref_sampleRate), HackrfSource.MAX_SAMPLERATE));
                ((HackrfSource) source).setVgaRxGain(preferences.getInt(getString(R.string.pref_hackrf_vgaRxGain), HackrfSource.MAX_VGA_RX_GAIN/2));
                ((HackrfSource) source).setLnaGain(preferences.getInt(getString(R.string.pref_hackrf_lnaGain), HackrfSource.MAX_LNA_GAIN/2));
                ((HackrfSource) source).setAmplifier(preferences.getBoolean(getString(R.string.pref_hackrf_amplifier), false));
                ((HackrfSource) source).setAntennaPower(preferences.getBoolean(getString(R.string.pref_hackrf_antennaPower), false));
                ((HackrfSource)source).setFrequencyShift(Integer.valueOf(
                        preferences.getString(getString(R.string.pref_hackrf_frequencyShift), "0")));
                break;
            case RTLSDR_SOURCE:
                // Create RtlsdrSource
                if(preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer), false))
                    source = new RtlsdrSource(preferences.getString(getString(R.string.pref_rtlsdr_ip), ""),
                            Integer.valueOf(preferences.getString(getString(R.string.pref_rtlsdr_port), "1234")));
                else {
                    source = new RtlsdrSource("127.0.0.1", 1234);
                }

                frequency = preferences.getLong(getString(R.string.pref_frequency),97000000);
                sampleRate = preferences.getInt(getString(R.string.pref_sampleRate), source.getMaxSampleRate());
                if(sampleRate > 2000000)	// might be the case after switching over from HackRF
                    sampleRate = 2000000;
                source.setFrequency(frequency);
                source.setSampleRate(sampleRate);

                ((RtlsdrSource) source).setFrequencyCorrection(Integer.valueOf(preferences.getString(getString(R.string.pref_rtlsdr_frequencyCorrection), "0")));
                ((RtlsdrSource)source).setFrequencyShift(Integer.valueOf(
                        preferences.getString(getString(R.string.pref_rtlsdr_frequencyShift), "0")));
                ((RtlsdrSource)source).setManualGain(preferences.getBoolean(getString(R.string.pref_rtlsdr_manual_gain), false));
                ((RtlsdrSource)source).setAutomaticGainControl(preferences.getBoolean(getString(R.string.pref_rtlsdr_agc), false));
                if(((RtlsdrSource)source).isManualGain()) {
                    ((RtlsdrSource) source).setGain(preferences.getInt(getString(R.string.pref_rtlsdr_gain), 0));
                    ((RtlsdrSource) source).setIFGain(preferences.getInt(getString(R.string.pref_rtlsdr_ifGain), 0));
                }
                break;
            default:	Log.e(LOGTAG, "createSource: Invalid source type: " + sourceType);
                return false;
        }

        // inform the analyzer surface about the new source
        analyzerSurface.setSource(source);

        return true;
    }

    /**
     * Will open the IQ Source instance.
     * Note: some sources need special treatment on opening, like the rtl-sdr source.
     *
     * @return true on success; false on error
     */
    public boolean openSource() {
        int sourceType = Integer.valueOf(preferences.getString(getString(R.string.pref_sourceType), "1"));

        switch (sourceType) {
            case FILE_SOURCE:
                if (source != null && source instanceof FileIQSource)
                    return source.open(this, this);
                else {
                    Log.e(LOGTAG, "openSource: sourceType is FILE_SOURCE, but source is null or of other type.");
                    return false;
                }
            case HACKRF_SOURCE:
                if (source != null && source instanceof HackrfSource)
                    return source.open(this, this);
                else {
                    Log.e(LOGTAG, "openSource: sourceType is HACKRF_SOURCE, but source is null or of other type.");
                    return false;
                }
            case RTLSDR_SOURCE:
                if (source != null && source instanceof RtlsdrSource) {
                    // We might need to start the driver:
                    if (!preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer), false)) {
                        // start local rtl_tcp instance:
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("iqsrc://-a 127.0.0.1 -p 1234 -n 1"));
                            startActivityForResult(intent, RTL2832U_RESULT_CODE);
                        } catch (ActivityNotFoundException e) {
                            Log.e(LOGTAG, "createSource: RTL2832U is not installed");

                            // Show a dialog that links to the play market:
                            new AlertDialog.Builder(this)
                                    .setTitle("RTL2832U driver not installed!")
                                    .setMessage("You need to install the (free) RTL2832U driver to use RTL-SDR dongles.")
                                    .setPositiveButton("Install from Google Play", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=marto.rtl_tcp_andro"));
                                            startActivity(marketIntent);
                                        }
                                    })
                                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            // do nothing
                                        }
                                    })
                                    .show();
                            return false;
                        }
                    }

                    return source.open(this, this);
                } else {
                    Log.e(LOGTAG, "openSource: sourceType is RTLSDR_SOURCE, but source is null or of other type.");
                    return false;
                }
            default:
                Log.e(LOGTAG, "openSource: Invalid source type: " + sourceType);
                return false;
        }
    }

    /**
     * Will stop the RF Analyzer. This includes shutting down the scheduler (which turns of the
     * source), the processing loop and the demodulator if running.
     */
    public void stopAnalyzer() {
        // Stop the Scheduler if running:
        if(scheduler != null) {
            // Stop recording in case it is running:
            //stopRecording();
            scheduler.stopScheduler();
        }

        // Stop the Processing Loop if running:
        if(analyzerProcessingLoop != null)
            analyzerProcessingLoop.stopLoop();

        // Stop the Demodulator if running:
        if(demodulator != null)
            demodulator.stopDemodulator();

        // Wait for the scheduler to stop:
        if(scheduler != null && !scheduler.getName().equals(Thread.currentThread().getName())) {
            try {
                scheduler.join();
            } catch (InterruptedException e) {
                Log.e(LOGTAG, "startAnalyzer: Error while stopping Scheduler.");
            }
        }

        // Wait for the processing loop to stop
        if(analyzerProcessingLoop != null) {
            try {
                analyzerProcessingLoop.join();
            } catch (InterruptedException e) {
                Log.e(LOGTAG, "startAnalyzer: Error while stopping Processing Loop.");
            }
        }

        // Wait for the demodulator to stop
        if(demodulator != null) {
            try {
                demodulator.join();
            } catch (InterruptedException e) {
                Log.e(LOGTAG, "startAnalyzer: Error while stopping Demodulator.");
            }
        }

        running = false;

        // update action bar icons and titles:
        updateActionBar();

        // allow screen to turn off again:
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    /**
     * Will start the RF Analyzer. This includes creating a source (if null), open a source
     * (if not open), starting the scheduler (which starts the source) and starting the
     * processing loop.
     */
    public void startAnalyzer() {
        this.stopAnalyzer();	// Stop if running; This assures that we don't end up with multiple instances of the thread loops

        // Retrieve fft size and frame rate from the preferences
        int fftSize = Integer.valueOf(preferences.getString(getString(R.string.pref_fftSize), "1024"));
        int frameRate = Integer.valueOf(preferences.getString(getString(R.string.pref_frameRate), "1"));
        boolean dynamicFrameRate = preferences.getBoolean(getString(R.string.pref_dynamicFrameRate), true);

        running = true;

        if(source == null) {
            if(!this.createSource())
                return;
        }

        // check if the source is open. if not, open it!
        if(!source.isOpen()) {
            if (!openSource()) {
                Toast.makeText(MainActivity.this, "Source not available (" + source.getName() + ")", Toast.LENGTH_LONG).show();
                running = false;
                return;
            }
            return;	// we have to wait for the source to become ready... onIQSourceReady() will call startAnalyzer() again...
        }

        // Create a new instance of Scheduler and Processing Loop:
        scheduler = new Scheduler(fftSize, source);
        analyzerProcessingLoop = new AnalyzerProcessingLoop(
                analyzerSurface, 			// Reference to the Analyzer Surface
                fftSize,					// FFT size
                scheduler.getFftOutputQueue(), // Reference to the input queue for the processing loop
                scheduler.getFftInputQueue()); // Reference to the buffer-pool-return queue
        if(dynamicFrameRate)
            analyzerProcessingLoop.setDynamicFrameRate(true);
        else {
            analyzerProcessingLoop.setDynamicFrameRate(false);
            analyzerProcessingLoop.setFrameRate(frameRate);
        }

        // Start both threads:
        scheduler.start();
        analyzerProcessingLoop.start();

        scheduler.setChannelFrequency(analyzerSurface.getChannelFrequency());

        // Start the demodulator thread:
        demodulator = new Demodulator(scheduler.getDemodOutputQueue(), scheduler.getDemodInputQueue(), source.getPacketSize());
        demodulator.start();

        // Set the demodulation mode (will configure the demodulator correctly)
        this.setDemodulationMode(demodulationMode);

        // update the action bar icons and titles:
        updateActionBar();

        // Prevent the screen from turning off:
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    /**
     * Will pop up a dialog to let the user choose a demodulation mode.
     */
    private void showDemodulationDialog() {
        if(scheduler == null || demodulator == null || source == null) {
            Toast.makeText(MainActivity.this, "Analyzer must be running to change modulation mode", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select a demodulation mode:")
                .setSingleChoiceItems(R.array.demodulation_modes, demodulator.getDemodulationMode(), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        setDemodulationMode(which);
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /**
     * Will set the modulation mode to the given value. Takes care of adjusting the
     * scheduler and the demodulator respectively and updates the action bar menu item.
     *
     * @param mode	Demodulator.DEMODULATION_OFF, *_AM, *_NFM, *_WFM
     */
    public void setDemodulationMode(int mode) {
        if(scheduler == null || demodulator == null || source == null) {
            Log.e(LOGTAG,"setDemodulationMode: scheduler/demodulator/source is null");
            return;
        }

        // (de-)activate demodulation in the scheduler and set the sample rate accordingly:
        if(mode == Demodulator.DEMODULATION_OFF) {
            scheduler.setDemodulationActivated(false);
        }
        else {
            /*if(recordingFile != null && source.getSampleRate() != Demodulator.INPUT_RATE) {
                // We are recording at an incompatible sample rate right now.
                Log.i(LOGTAG, "setDemodulationMode: Recording is running at " + source.getSampleRate() + " Sps. Can't start demodulation.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Recording is running at incompatible sample rate for demodulation!", Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }*/

            // adjust sample rate of the source:
            source.setSampleRate(Demodulator.INPUT_RATE);

            // Verify that the source supports the sample rate:
            if(source.getSampleRate() != Demodulator.INPUT_RATE) {
                Log.e(LOGTAG,"setDemodulationMode: cannot adjust source sample rate!");
                Toast.makeText(MainActivity.this, "Source does not support the sample rate necessary for demodulation (" +
                        Demodulator.INPUT_RATE/1000000 + " Msps)", Toast.LENGTH_LONG).show();
                scheduler.setDemodulationActivated(false);
                mode = Demodulator.DEMODULATION_OFF;	// deactivate demodulation...
            } else {
                scheduler.setDemodulationActivated(true);
            }
        }

        // set demodulation mode in demodulator:
        demodulator.setDemodulationMode(mode);
        this.demodulationMode = mode;	// save the setting

        // disable/enable demodulation view in surface:
        if(mode == Demodulator.DEMODULATION_OFF) {
            analyzerSurface.setDemodulationEnabled(false);
        } else {
            analyzerSurface.setDemodulationEnabled(true);	// will re-adjust channel freq, width and squelch,
            // if they are outside the current viewport and update the
            // demodulator via callbacks.
            analyzerSurface.setShowLowerBand(mode != Demodulator.DEMODULATION_USB);		// show lower side band if not USB
            analyzerSurface.setShowUpperBand(mode != Demodulator.DEMODULATION_LSB);		// show upper side band if not LSB
        }

        // update action bar:
        updateActionBar();
    }

    /**
     * Will pop up a dialog to let the user input a new frequency.
     * Note: A frequency can be entered either in Hz or in MHz. If the input value
     * is a number smaller than the maximum frequency of the source in MHz, then it
     * is interpreted as a frequency in MHz. Otherwise it will be handled as frequency
     * in Hz.
     */
    private void tuneToFrequency() {
        if(source == null)
            return;

        // calculate max frequency of the source in MHz:
        final double maxFreqMHz = source.getMaxFrequency() / 1000000f;

        final LinearLayout ll_view = (LinearLayout) this.getLayoutInflater().inflate(R.layout.tune_to_frequency, null);
        final EditText et_frequency = (EditText) ll_view.findViewById(R.id.et_tune_to_frequency);
        final CheckBox cb_bandwidth = (CheckBox) ll_view.findViewById(R.id.cb_tune_to_frequency_bandwidth);
        final EditText et_bandwidth = (EditText) ll_view.findViewById(R.id.et_tune_to_frequency_bandwidth);
        final Spinner sp_bandwidthUnit = (Spinner) ll_view.findViewById(R.id.sp_tune_to_frequency_bandwidth_unit);

        // Show warning if we are currently recording to file:
        /*final TextView tv_warning = (TextView) ll_view.findViewById(R.id.tv_tune_to_frequency_warning);
        if(recordingFile != null)
            tv_warning.setVisibility(View.VISIBLE);*/

        cb_bandwidth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                et_bandwidth.setEnabled(isChecked);
                sp_bandwidthUnit.setEnabled(isChecked);
            }
        });
        cb_bandwidth.toggle();	// to trigger the onCheckedChangeListener at least once to set inital state
        cb_bandwidth.setChecked(preferences.getBoolean(getString(R.string.pref_tune_to_frequency_setBandwidth), false));
        et_bandwidth.setText(preferences.getString(getString(R.string.pref_tune_to_frequency_bandwidth), "1"));
        sp_bandwidthUnit.setSelection(preferences.getInt(getString(R.string.pref_tune_to_frequency_bandwidthUnit), 0));

        new AlertDialog.Builder(this)
                .setTitle("Tune to Frequency")
                .setMessage("Frequency is " + source.getFrequency()/1000000f + "MHz. Type a new Frequency (Values below "
                        + maxFreqMHz + " will be interpreted as MHz, higher values as Hz): ")
                .setView(ll_view)
                .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        try {
                            float newFreq = source.getFrequency()/1000000f;
                            if(et_frequency.getText().length() != 0)
                                newFreq = Float.valueOf(et_frequency.getText().toString());
                            if (newFreq < maxFreqMHz)
                                newFreq = newFreq * 1000000;
                            if (newFreq <= source.getMaxFrequency() && newFreq >= source.getMinFrequency()) {
                                source.setFrequency((long)newFreq);
                                analyzerSurface.setVirtualFrequency((long)newFreq);
                                if(demodulationMode != Demodulator.DEMODULATION_OFF)
                                    analyzerSurface.setDemodulationEnabled(true);	// This will re-adjust the channel freq correctly

                                // Set bandwidth (virtual sample rate):
                                if(cb_bandwidth.isChecked() && et_bandwidth.getText().length() != 0) {
                                    float bandwidth = Float.valueOf(et_bandwidth.getText().toString());
                                    if(sp_bandwidthUnit.getSelectedItemPosition() == 0)			//MHz
                                        bandwidth *= 1000000;
                                    else if(sp_bandwidthUnit.getSelectedItemPosition() == 1)	//KHz
                                        bandwidth *= 1000;
                                    if(bandwidth > source.getMaxSampleRate())
                                        bandwidth = source.getMaxFrequency();
                                    source.setSampleRate(source.getNextHigherOptimalSampleRate((int)bandwidth));
                                    analyzerSurface.setVirtualSampleRate((int)bandwidth);
                                }
                                // safe preferences:
                                SharedPreferences.Editor edit = preferences.edit();
                                edit.putBoolean(getString(R.string.pref_tune_to_frequency_setBandwidth), cb_bandwidth.isChecked());
                                edit.putString(getString(R.string.pref_tune_to_frequency_bandwidth), et_bandwidth.getText().toString());
                                edit.putInt(getString(R.string.pref_tune_to_frequency_bandwidthUnit), sp_bandwidthUnit.getSelectedItemPosition());
                                edit.apply();

                            } else {
                                Toast.makeText(MainActivity.this, "Frequency is out of the valid range: " + (long)newFreq + " Hz", Toast.LENGTH_LONG).show();
                            }
                        } catch (NumberFormatException e) {
                            Log.e(LOGTAG, "tuneToFrequency: Error while setting frequency: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // do nothing
                    }
                })
                .show();
    }

    /**
     * Will pop up a dialog to let the user adjust gain settings
     */
    private void adjustGain() {
        if(source == null)
            return;

        int sourceType = Integer.valueOf(preferences.getString(getString(R.string.pref_sourceType), "1"));
        switch (sourceType) {
            case FILE_SOURCE:
                Toast.makeText(this, getString(R.string.filesource_doesnt_support_gain), Toast.LENGTH_LONG).show();
                break;
            case HACKRF_SOURCE:
                // Prepare layout:
                final LinearLayout view_hackrf = (LinearLayout) this.getLayoutInflater().inflate(R.layout.hackrf_gain, null);
                final SeekBar sb_hackrf_vga = (SeekBar) view_hackrf.findViewById(R.id.sb_hackrf_vga_gain);
                final SeekBar sb_hackrf_lna = (SeekBar) view_hackrf.findViewById(R.id.sb_hackrf_lna_gain);
                final TextView tv_hackrf_vga = (TextView) view_hackrf.findViewById(R.id.tv_hackrf_vga_gain);
                final TextView tv_hackrf_lna = (TextView) view_hackrf.findViewById(R.id.tv_hackrf_lna_gain);
                sb_hackrf_vga.setMax(HackrfSource.MAX_VGA_RX_GAIN / HackrfSource.VGA_RX_GAIN_STEP_SIZE);
                sb_hackrf_lna.setMax(HackrfSource.MAX_LNA_GAIN / HackrfSource.LNA_GAIN_STEP_SIZE);
                sb_hackrf_vga.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        tv_hackrf_vga.setText("" + progress * HackrfSource.VGA_RX_GAIN_STEP_SIZE);
                        ((HackrfSource)source).setVgaRxGain(progress*HackrfSource.VGA_RX_GAIN_STEP_SIZE);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                sb_hackrf_lna.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        tv_hackrf_lna.setText("" + progress * HackrfSource.LNA_GAIN_STEP_SIZE);
                        ((HackrfSource)source).setLnaGain(progress*HackrfSource.LNA_GAIN_STEP_SIZE);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                sb_hackrf_vga.setProgress(((HackrfSource) source).getVgaRxGain() / HackrfSource.VGA_RX_GAIN_STEP_SIZE);
                sb_hackrf_lna.setProgress(((HackrfSource) source).getLnaGain() / HackrfSource.LNA_GAIN_STEP_SIZE);

                // Show dialog:
                AlertDialog hackrfDialog = new AlertDialog.Builder(this)
                        .setTitle("Adjust Gain Settings")
                        .setView(view_hackrf)
                        .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // safe preferences:
                                SharedPreferences.Editor edit = preferences.edit();
                                edit.putInt(getString(R.string.pref_hackrf_vgaRxGain), sb_hackrf_vga.getProgress()*HackrfSource.VGA_RX_GAIN_STEP_SIZE);
                                edit.putInt(getString(R.string.pref_hackrf_lnaGain), sb_hackrf_lna.getProgress()*HackrfSource.LNA_GAIN_STEP_SIZE);
                                edit.apply();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // do nothing
                            }
                        })
                        .create();
                hackrfDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // sync source with (new/old) settings
                        int vgaRxGain = preferences.getInt(getString(R.string.pref_hackrf_vgaRxGain),HackrfSource.MAX_VGA_RX_GAIN/2);
                        int lnaGain = preferences.getInt(getString(R.string.pref_hackrf_lnaGain),HackrfSource.MAX_LNA_GAIN/2);
                        if(((HackrfSource)source).getVgaRxGain() != vgaRxGain)
                            ((HackrfSource)source).setVgaRxGain(vgaRxGain);
                        if(((HackrfSource)source).getLnaGain() != lnaGain)
                            ((HackrfSource)source).setLnaGain(lnaGain);
                    }
                });
                hackrfDialog.show();
                hackrfDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                break;
            case RTLSDR_SOURCE:
                final int[] possibleGainValues = ((RtlsdrSource)source).getPossibleGainValues();
                final int[] possibleIFGainValues = ((RtlsdrSource)source).getPossibleIFGainValues();
                if(possibleGainValues.length <= 1 && possibleIFGainValues.length <= 1) {
                    Toast.makeText(MainActivity.this, source.getName() + " does not support gain adjustment!", Toast.LENGTH_LONG).show();
                }
                // Prepare layout:
                final LinearLayout view_rtlsdr = (LinearLayout) this.getLayoutInflater().inflate(R.layout.rtlsdr_gain, null);
                final LinearLayout ll_rtlsdr_gain = (LinearLayout) view_rtlsdr.findViewById(R.id.ll_rtlsdr_gain);
                final LinearLayout ll_rtlsdr_ifgain = (LinearLayout) view_rtlsdr.findViewById(R.id.ll_rtlsdr_ifgain);
                final Switch sw_rtlsdr_manual_gain = (Switch) view_rtlsdr.findViewById(R.id.sw_rtlsdr_manual_gain);
                final CheckBox cb_rtlsdr_agc = (CheckBox) view_rtlsdr.findViewById(R.id.cb_rtlsdr_agc);
                final SeekBar sb_rtlsdr_gain = (SeekBar) view_rtlsdr.findViewById(R.id.sb_rtlsdr_gain);
                final SeekBar sb_rtlsdr_ifGain = (SeekBar) view_rtlsdr.findViewById(R.id.sb_rtlsdr_ifgain);
                final TextView tv_rtlsdr_gain = (TextView) view_rtlsdr.findViewById(R.id.tv_rtlsdr_gain);
                final TextView tv_rtlsdr_ifGain = (TextView) view_rtlsdr.findViewById(R.id.tv_rtlsdr_ifgain);

                // Assign current gain:
                int gainIndex = 0;
                int ifGainIndex = 0;
                for (int i = 0; i < possibleGainValues.length; i++) {
                    if(((RtlsdrSource)source).getGain() == possibleGainValues[i]) {
                        gainIndex = i;
                        break;
                    }
                }
                for (int i = 0; i < possibleIFGainValues.length; i++) {
                    if(((RtlsdrSource)source).getIFGain() == possibleIFGainValues[i]) {
                        ifGainIndex = i;
                        break;
                    }
                }
                sb_rtlsdr_gain.setMax(possibleGainValues.length - 1);
                sb_rtlsdr_ifGain.setMax(possibleIFGainValues.length - 1);
                sb_rtlsdr_gain.setProgress(gainIndex);
                sb_rtlsdr_ifGain.setProgress(ifGainIndex);
                tv_rtlsdr_gain.setText("" + possibleGainValues[gainIndex]);
                tv_rtlsdr_ifGain.setText("" + possibleIFGainValues[ifGainIndex]);

                // Assign current manual gain and agc setting
                sw_rtlsdr_manual_gain.setChecked(((RtlsdrSource)source).isManualGain());
                cb_rtlsdr_agc.setChecked(((RtlsdrSource)source).isAutomaticGainControl());

                // Add listener to gui elements:
                sw_rtlsdr_manual_gain.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        sb_rtlsdr_gain.setEnabled(isChecked);
                        tv_rtlsdr_gain.setEnabled(isChecked);
                        sb_rtlsdr_ifGain.setEnabled(isChecked);
                        tv_rtlsdr_ifGain.setEnabled(isChecked);
                        ((RtlsdrSource)source).setManualGain(isChecked);
                        if(isChecked) {
                            ((RtlsdrSource) source).setGain(possibleGainValues[sb_rtlsdr_gain.getProgress()]);
                            ((RtlsdrSource) source).setIFGain(possibleIFGainValues[sb_rtlsdr_ifGain.getProgress()]);
                        }
                    }
                });
                cb_rtlsdr_agc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        ((RtlsdrSource)source).setAutomaticGainControl(isChecked);
                    }
                });
                sb_rtlsdr_gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        tv_rtlsdr_gain.setText("" + possibleGainValues[progress]);
                        ((RtlsdrSource) source).setGain(possibleGainValues[progress]);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                sb_rtlsdr_ifGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        tv_rtlsdr_ifGain.setText("" + possibleIFGainValues[progress]);
                        ((RtlsdrSource) source).setIFGain(possibleIFGainValues[progress]);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                // Disable gui elements if gain cannot be adjusted:
                if(possibleGainValues.length <= 1)
                    ll_rtlsdr_gain.setVisibility(View.GONE);
                if(possibleIFGainValues.length <= 1)
                    ll_rtlsdr_ifgain.setVisibility(View.GONE);

                if(!sw_rtlsdr_manual_gain.isChecked()) {
                    sb_rtlsdr_gain.setEnabled(false);
                    tv_rtlsdr_gain.setEnabled(false);
                    sb_rtlsdr_ifGain.setEnabled(false);
                    tv_rtlsdr_ifGain.setEnabled(false);
                }

                // Show dialog:
                AlertDialog rtlsdrDialog = new AlertDialog.Builder(this)
                        .setTitle("Adjust Gain Settings")
                        .setView(view_rtlsdr)
                        .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // safe preferences:
                                SharedPreferences.Editor edit = preferences.edit();
                                edit.putBoolean(getString(R.string.pref_rtlsdr_manual_gain), sw_rtlsdr_manual_gain.isChecked());
                                edit.putBoolean(getString(R.string.pref_rtlsdr_agc), cb_rtlsdr_agc.isChecked());
                                edit.putInt(getString(R.string.pref_rtlsdr_gain), possibleGainValues[sb_rtlsdr_gain.getProgress()]);
                                edit.putInt(getString(R.string.pref_rtlsdr_ifGain), possibleIFGainValues[sb_rtlsdr_ifGain.getProgress()]);
                                edit.apply();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // do nothing
                            }
                        })
                        .create();
                rtlsdrDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        boolean manualGain = preferences.getBoolean(getString(R.string.pref_rtlsdr_manual_gain), false);
                        boolean agc = preferences.getBoolean(getString(R.string.pref_rtlsdr_agc), false);
                        int gain = preferences.getInt(getString(R.string.pref_rtlsdr_gain), 0);
                        int ifGain = preferences.getInt(getString(R.string.pref_rtlsdr_ifGain), 0);
                        ((RtlsdrSource)source).setGain(gain);
                        ((RtlsdrSource)source).setIFGain(ifGain);
                        ((RtlsdrSource)source).setManualGain(manualGain);
                        ((RtlsdrSource)source).setAutomaticGainControl(agc);
                        if(manualGain) {
                            // Note: This is a workaround. After setting manual gain to true we must
                            // rewrite the manual gain values:
                            ((RtlsdrSource) source).setGain(gain);
                            ((RtlsdrSource) source).setIFGain(ifGain);
                        }
                    }
                });
                rtlsdrDialog.show();
                rtlsdrDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                break;
            default:
                Log.e(LOGTAG, "adjustGain: Invalid source type: " + sourceType);
                break;
        }
    }

    /*****
     * IQ Source Interface
     */
    @Override
    public void onIQSourceReady(IQSourceInterface source) {	// is called after source.open()
        if (running)
            return;
        startAnalyzer();    // will start the processing loop, scheduler and source
    }

    @Override
    public void onIQSourceError(final IQSourceInterface source, final String message) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Error with Source [" + source.getName() + "]: " + message, Toast.LENGTH_LONG).show();
            }
        });
        stopAnalyzer();

        if(this.source != null && this.source.isOpen())
            this.source.close();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // err_info from RTL2832U:
        String[] rtlsdrErrInfo = {
                "permission_denied",
                "root_required",
                "no_devices_found",
                "unknown_error",
                "replug",
                "already_running"};

        switch (requestCode) {
            case RTL2832U_RESULT_CODE:
                // This happens if the RTL2832U driver was started.
                // We check for errors and print them:
                if (resultCode == RESULT_OK)
                    Log.i(LOGTAG, "onActivityResult: RTL2832U driver was successfully started.");
                else {
                    int errorId = -1;
                    int exceptionCode = 0;
                    String detailedDescription = null;
                    if(data != null) {
                        errorId = data.getIntExtra("marto.rtl_tcp_andro.RtlTcpExceptionId", -1);
                        exceptionCode = data.getIntExtra("detailed_exception_code", 0);
                        detailedDescription = data.getStringExtra("detailed_exception_message");
                    }
                    String errorMsg = "ERROR NOT SPECIFIED";
                    if(errorId >= 0 && errorId < rtlsdrErrInfo.length)
                        errorMsg = rtlsdrErrInfo[errorId];

                    Log.e(LOGTAG, "onActivityResult: RTL2832U driver returned with error: " + errorMsg + " ("+errorId+")"
                            + (detailedDescription != null ? ": " + detailedDescription + " (" + exceptionCode + ")" : ""));

                    if (source != null && source instanceof RtlsdrSource) {
                        Toast.makeText(MainActivity.this, "Error with Source [" + source.getName() + "]: " + errorMsg + " (" + errorId + ")"
                                + (detailedDescription != null ? ": " + detailedDescription + " (" + exceptionCode + ")" : ""), Toast.LENGTH_LONG).show();
                        source.close();
                    }
                }
                break;
        }
    }

    /*****
     * AnalyzerSurface CallbackInterface
     */
    /**
     * Called by the analyzer surface after the user changed the channel width
     * @param newChannelWidth    new channel width (single sided) in Hz
     * @return true if channel width is valid; false if out of range
     */
    @Override
    public boolean onUpdateChannelWidth(int newChannelWidth) {
        if(demodulator != null)
            return demodulator.setChannelWidth(newChannelWidth);
        else
            return false;
    }

    @Override
    public void onUpdateChannelFrequency(long newChannelFrequency) {
        if(scheduler != null)
            scheduler.setChannelFrequency(newChannelFrequency);
    }

    @Override
    public void onUpdateSquelchSatisfied(boolean squelchSatisfied) {
        if(scheduler != null)
            scheduler.setSquelchSatisfied(squelchSatisfied);
    }

    @Override
    public int onCurrentChannelWidthRequested() {
        if(demodulator != null)
            return demodulator.getChannelWidth();
        else
            return -1;
    }

    // START PERMISSION CHECK
    final private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        String message = "osmdroid permissions:";
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            message += "\nLocation to show user location.";
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            message += "\nStorage access to store map tiles.";
        }
        if (!permissions.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            String[] params = permissions.toArray(new String[permissions.size()]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(params, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            }
        } // else: We already have permissions, so handle as normal
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);
                // Check for ACCESS_FINE_LOCATION and WRITE_EXTERNAL_STORAGE
                Boolean location = perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                Boolean storage = perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if (location && storage) {
                    // All Permissions Granted
                    Toast.makeText(MainActivity.this, "All permissions granted", Toast.LENGTH_SHORT).show();
                } else if (location) {
                    Toast.makeText(this, "Storage permission is required to store map tiles to reduce data usage and for offline usage.", Toast.LENGTH_LONG).show();
                } else if (storage) {
                    Toast.makeText(this, "Location permission is required to show the user's location on map.", Toast.LENGTH_LONG).show();
                } else { // !location && !storage case
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Storage permission is required to store map tiles to reduce data usage and for offline usage." +
                            "\nLocation permission is required to show the user's location on map.", Toast.LENGTH_SHORT).show();
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // END PERMISSION CHECK
}
