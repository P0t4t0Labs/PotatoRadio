package us.potatosaur.p0t4t0labs.potatoradio;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mantz_it.rfanalyzer.AnalyzerSurface;

/**
 * This fragment will show the analyzer. By no means are we trying to take credit for this.
 * We're simply implementing this here to break as little as possible from mantz_it.rfanalyzer.
 */

public class AnalyzerFragment extends Fragment {

    private SharedPreferences preferences = null;
    private AnalyzerSurface analyzerSurface = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Context context = getActivity().getApplicationContext();

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.analyzer_fragment, container, false);

        // Create a analyzer surface:
        analyzerSurface = new AnalyzerSurface(context, (AnalyzerSurface.CallbackInterface) getActivity());
        analyzerSurface.setVerticalScrollEnabled(preferences.getBoolean(getString(R.string.pref_scrollDB), true));
        analyzerSurface.setVerticalZoomEnabled(preferences.getBoolean(getString(R.string.pref_zoomDB), true));
        analyzerSurface.setDecoupledAxis(preferences.getBoolean(getString(R.string.pref_decoupledAxis), false));
        analyzerSurface.setDisplayRelativeFrequencies(preferences.getBoolean(getString(R.string.pref_relativeFrequencies), false));
        analyzerSurface.setWaterfallColorMapType(Integer.valueOf(preferences.getString(getString(R.string.pref_colorMapType),"4")));
        analyzerSurface.setFftDrawingType(Integer.valueOf(preferences.getString(getString(R.string.pref_fftDrawingType),"2")));
        analyzerSurface.setFftRatio(Float.valueOf(preferences.getString(getString(R.string.pref_spectrumWaterfallRatio), "0.5")));
        analyzerSurface.setFontSize(Integer.valueOf(preferences.getString(getString(R.string.pref_fontSize),"2")));
        analyzerSurface.setShowDebugInformation(preferences.getBoolean(getString(R.string.pref_showDebugInformation), false));

        FrameLayout fl = (FrameLayout) rootView.findViewById(R.id.fl_analyzerFrame);
        if (fl != null) {
            fl.addView(analyzerSurface);
        }

        return rootView;
    }

}
