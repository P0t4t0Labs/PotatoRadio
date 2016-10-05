package us.potatosaur.p0t4t0labs.potatoradio;

import android.app.Fragment;
import android.os.Bundle;
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

    private AnalyzerSurface analyzerSurface = null;
    private FrameLayout rootView = null;

    public void setAnalyzerSurface(AnalyzerSurface as) {
        this.analyzerSurface = as;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (analyzerSurface == null) {
            throw new NullPointerException("No AnalyzerSurface to AnalyzerFragment.");
        }

        // Inflate the layout for this fragment
        rootView = (FrameLayout) inflater.inflate(R.layout.analyzer_fragment, container, false);
        // analyzerSurface may already be on a child view. Let's steal it from them.
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) analyzerSurface.getParent();
            if (parent != null) {
                parent.removeView(analyzerSurface);
            }
            rootView.addView(analyzerSurface);
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {

        if (rootView != null) {
            rootView.removeAllViews();
        }

        super.onDestroyView();
    }
}
