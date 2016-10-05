package us.potatosaur.p0t4t0labs.potatoradio;


import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;

public class MapFragment extends Fragment{

    private MapView mMapView;
    private TilesOverlay mTilesOverlay;
    private MapTileProviderBasic mProvider;
    private MyLocationNewOverlay mLocationOverlay;
    private RotationGestureOverlay mRotationGestureOverlay;
    private CompassOverlay mCompassOverlay;
    private ArrayList<OverlayItem> mOverlayItems;
    private Context baseContext;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Set our unique user agent
        org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants.setUserAgentValue(BuildConfig.APPLICATION_ID);

        baseContext = getActivity().getApplicationContext();
        LinearLayout ll = (LinearLayout) inflater.inflate(R.layout.map_fragment, container, false);

        mMapView = (MapView) ll.findViewById(R.id.map);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);

        mMapView.setTilesScaledToDpi(true);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        mMapView.setUseDataConnection(false); //keeps the mapView from loading online tiles using network connection.

        mOverlayItems = new ArrayList<OverlayItem>();
        mMapView.getController().setZoom(16);

        // Add tiles layer
        mProvider = new MapTileProviderBasic(baseContext);
        mProvider.setTileSource(TileSourceFactory.MAPNIK);
        mTilesOverlay = new TilesOverlay(mProvider, baseContext);
        mMapView.getOverlays().add(mTilesOverlay);

        mLocationOverlay = new MyLocationNewOverlay(baseContext, new GpsMyLocationProvider(baseContext),mMapView);
        mLocationOverlay.enableMyLocation();
        mMapView.getOverlays().add(mLocationOverlay);

        mCompassOverlay = new CompassOverlay(baseContext, new InternalCompassOrientationProvider(baseContext), mMapView);
        mMapView.getOverlays().add(mCompassOverlay);

        mRotationGestureOverlay = new RotationGestureOverlay(baseContext, mMapView);
        mRotationGestureOverlay.setEnabled(true);
        mMapView.setMultiTouchControls(true);
        mMapView.getOverlays().add(mRotationGestureOverlay);

        addLocationMarker("Hello","I am a marker", new GeoPoint(28.064283d, -82.566480d));
        mLocationOverlay.enableFollowLocation();

        return ll;
    }




    public void addLocationMarker(String title, String description, GeoPoint location) {
        OverlayItem tempItem = new OverlayItem(title, description, location); // Lat/Lon decimal degrees
//        tempItem.setMarker(ContextCompat.getDrawable(getApplicationContext(), R.drawable.sfgpuci));
        mOverlayItems.add(tempItem); // Lat/Lon decimal degrees

//        ItemizedIconOverlay<OverlayItem> mItemizedIconOverlay = new ItemizedIconOverlay<OverlayItem>(this, mOverlayItems, null);
//        mMapView.getOverlays().add(mItemizedIconOverlay);
        ResourceProxy resourceProxy = (ResourceProxy) new DefaultResourceProxyImpl(baseContext);

        ItemizedIconOverlay<OverlayItem> mItemizedIconOverlay = new ItemizedIconOverlay<OverlayItem>(
                mOverlayItems, ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.sfgpuci),
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    @Override public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                        return onSingleTapUpHelper(item);
                    }

                    @Override public boolean onItemLongPress(final int index, final OverlayItem item) {
                        return true;
                    }
                }, resourceProxy);
        mMapView.getOverlays().add(mItemizedIconOverlay);
    }

    public boolean onSingleTapUpHelper(OverlayItem item) {
        //Toast.makeText(mContext, "Item " + i + " has been tapped!", Toast.LENGTH_SHORT).show();
        AlertDialog.Builder dialog = new AlertDialog.Builder(baseContext);
        dialog.setTitle(item.getTitle());
        dialog.setMessage(item.getSnippet());
        dialog.show();
        return true;
    }
}
