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

    public static AnalyzerSurface analyzerSurface = null;
    private static AnalyzerFragment instance = null;

    public static AnalyzerFragment getInstance() {
        if (instance == null) {
            instance = new AnalyzerFragment();
        }
        return instance;
    }

    private SharedPreferences preferences = null;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.analyzer_fragment, container, false);
        FrameLayout fl = (FrameLayout) rootView.findViewById(R.id.fl_analyzerFrame);
        if (fl != null) {
            fl.addView(analyzerSurface);
        }

        return rootView;
    }

}
