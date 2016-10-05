package us.potatosaur.p0t4t0labs.potatoradio;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.MinimapOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment implements LocationListener {

    private final String TAG = "MapFragment";
    protected MapView mMapView;
    private MyLocationNewOverlay mLocationOverlay;
    private CompassOverlay mCompassOverlay;
    private ScaleBarOverlay mScaleBarOverlay;
    private RotationGestureOverlay mRotationGestureOverlay;
    private ItemizedIconOverlay<OverlayItem> mPeopleOverlay;
    protected ImageButton btCenterMap;
    protected ImageButton btFollowMe;
    private LocationManager lm;
    private Location currentLocation = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants.setUserAgentValue(BuildConfig.APPLICATION_ID);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.map_fragment, container, false);

        // Request permissions to support Android Marshmallow and above devices
        if (Build.VERSION.SDK_INT >= 23) {
            checkPermissions();
        }

        mMapView = (MapView) rootView.findViewById(R.id.mapview);
        lm = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0l, 0f, this);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        final Context context = this.getActivity();
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        this.mCompassOverlay = new CompassOverlay(context, new InternalCompassOrientationProvider(context),
                mMapView);
        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context),
                mMapView);

        mScaleBarOverlay = new ScaleBarOverlay(mMapView);
        mScaleBarOverlay.setCentred(true);
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);

        mRotationGestureOverlay = new RotationGestureOverlay(mMapView);
        mRotationGestureOverlay.setEnabled(true);

        mMapView.getController().setZoom(15);
        mMapView.setTilesScaledToDpi(true);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        mMapView.setFlingEnabled(true);
        mMapView.getOverlays().add(this.mLocationOverlay);
        mMapView.getOverlays().add(this.mCompassOverlay);
        mMapView.getOverlays().add(this.mScaleBarOverlay);
        mMapView.getOverlays().add(this.mRotationGestureOverlay);

        btCenterMap = (ImageButton) view.findViewById(R.id.ic_center_map);

        btCenterMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "centerMap clicked ");
                if (currentLocation!=null) {
                    GeoPoint myPosition = new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude());
                    mMapView.getController().animateTo(myPosition);
                    mMapView.setMapOrientation(360.0f);
                }
            }
        });

        btFollowMe = (ImageButton) view.findViewById(R.id.ic_follow_me);

        btFollowMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btFollowMe clicked ");
                if (!mLocationOverlay.isFollowLocationEnabled()) {
                    mLocationOverlay.enableFollowLocation();
                    btFollowMe.setBackgroundColor(Color.GREEN);
                    //btFollowMe.setImageResource(R.drawable.ic_follow_me_on);
                } else {
                    mLocationOverlay.disableFollowLocation();
                    btFollowMe.setBackgroundColor(Color.TRANSPARENT);
                    //btFollowMe.setImageResource(R.drawable.ic_follow_me);
                }
            }
        });

        // This is my poor attempt to turn off the follow highlight, but it doesn't work ever.
        mMapView.setMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent scrollEvent) {
                if (!mLocationOverlay.isFollowLocationEnabled()) {
                    btFollowMe.setBackgroundColor(Color.TRANSPARENT);
                }
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent zoomEvent) {
                if (!mLocationOverlay.isFollowLocationEnabled()) {
                    btFollowMe.setBackgroundColor(Color.TRANSPARENT);
                }
                return false;
            }
        });

        // Double tile storage
        Iterator<Overlay> iterator = mMapView.getOverlays().iterator();
        while(iterator.hasNext()){
            Overlay next = iterator.next();
            if (next instanceof TilesOverlay){
                TilesOverlay x = (TilesOverlay)next;
                x.setOvershootTileCache(x.getOvershootTileCache() * 2);
                break;
            }
        }

        // Icon overlay!
        mPeopleOverlay = new ItemizedIconOverlay<OverlayItem>(new ArrayList<OverlayItem>(),
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

                    @Override
                    public boolean onItemSingleTapUp(int i, OverlayItem item) {
                        if (item == null)
                            return false;

                        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                        dialog.setTitle(item.getTitle());
                        dialog.setMessage(item.getSnippet());
                        dialog.show();
                        return true;
                    }

                    @Override
                    public boolean onItemLongPress(int i, OverlayItem item) {
                        return false;
                    }
                }, getActivity());
        mMapView.getOverlays().add(mPeopleOverlay);

        // Tampa convention center
        addMarker("Test", "HErro herro", new GeoPoint(27.9429057,-82.4577568));
    }

    @Override
    public void onResume() {
        mLocationOverlay.enableMyLocation();
        //mLocationOverlay.enableFollowLocation();
        mLocationOverlay.setOptionsMenuEnabled(true);
        mCompassOverlay.enableCompass();
        super.onResume();
    }

    @Override
    public void onPause() {
        mLocationOverlay.disableMyLocation();
        mLocationOverlay.disableFollowLocation();
        mCompassOverlay.disableCompass();
        super.onPause();
    }

    @Override
    public void onDetach(){
        super.onDetach();
        mMapView.onDetach();
        mMapView=null;
    }

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /**
     * Use to add markers to map.
     * @param title
     * @param description
     * @param point
     */
    public void addMarker(String title, String description, GeoPoint point) {
        if (mPeopleOverlay == null)
            return;

        final Drawable icon = ContextCompat.getDrawable(getActivity(), R.drawable.ic_action_record_on);

        OverlayItem marker = new OverlayItem(title, description, point);
        marker.setMarker(icon);
        mPeopleOverlay.addItem(marker);
    }

    // START PERMISSION CHECK
    final private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        String message = "map permissions:";
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            message += "\nLocation to show detailed user location.";
        }
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            message += "\nLocation to show user location.";
        }
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            message += "\nStorage access to store map tiles.";
        }
        if (!permissions.isEmpty()) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
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
                perms.put(Manifest.permission.ACCESS_COARSE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);
                // Check for ACCESS_FINE_LOCATION and WRITE_EXTERNAL_STORAGE
                Boolean location = perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                Boolean coarse = perms.get(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                Boolean storage = perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

                if (location && coarse && storage) {
                    // All Permissions Granted
                    Toast.makeText(getActivity(), "All permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    String damage = "";
                    if (!location) {
                        damage += "Location permission is required to show the user's detailed location on map.";
                    }
                    if (!coarse) {
                        damage += "Location permission is required to show the user's location on map.";
                    }
                    if (!storage) {
                        damage += "Storage permission is required to store map tiles to reduce data usage and for offline usage.";
                    }

                    if (damage.length() > 0) {
                        Toast.makeText(getActivity(), damage, Toast.LENGTH_LONG).show();
                    }
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // END PERMISSION CHECK
}
