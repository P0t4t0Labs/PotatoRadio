package us.potatosaur.p0t4t0labs.potatoradio;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.mantz_it.rfanalyzer.AnalyzerSurface;
import com.mantz_it.rfanalyzer.Demodulator;
import com.mantz_it.rfanalyzer.IQSourceInterface;
import com.mantz_it.rfanalyzer.Scheduler;

import java.io.File;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        IQSourceInterface.Callback, AnalyzerSurface.CallbackInterface {

    private Bundle savedInstanceState = null;
    private SharedPreferences preferences = null;
    private Process logcat = null;
    private FragmentManager fragmentManager = null;

    private boolean running = false;
    private Scheduler scheduler = null;
    private Demodulator demodulator = null;
    private IQSourceInterface source = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.savedInstanceState = savedInstanceState;
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        fragmentManager = getFragmentManager();
        FragmentTransaction trans = fragmentManager.beginTransaction();
        AnalyzerFragment anal = new AnalyzerFragment();
        trans.add(R.id.fragment_container, anal);
        trans.commit();

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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        /*if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }*/

        //addToBackStack();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /*****
     * IQ Source Interface
     */
    @Override
    public void onIQSourceReady(IQSourceInterface source) {	// is called after source.open()
        if (running)
            return;
        //TODO: REPLACE
            //startAnalyzer();    // will start the processing loop, scheduler and source
    }

    @Override
    public void onIQSourceError(final IQSourceInterface source, final String message) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Error with Source [" + source.getName() + "]: " + message, Toast.LENGTH_LONG).show();
            }
        });
        //TODO: REPLACE
        //stopAnalyzer();

        if(this.source != null && this.source.isOpen())
            this.source.close();
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
}
