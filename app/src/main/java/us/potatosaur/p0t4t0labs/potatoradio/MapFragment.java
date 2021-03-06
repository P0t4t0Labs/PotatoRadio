package us.potatosaur.p0t4t0labs.potatoradio;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment implements LocationListener {

    private final String TAG = "MapFragment";
    protected MapView mMapView;
    private MyLocationNewOverlay mLocationOverlay;
    private ScaleBarOverlay mScaleBarOverlay;
    private RotationGestureOverlay mRotationGestureOverlay;
    private SimpleLocationOverlay mMyLocationOverlay;
    private ItemizedIconOverlay<OverlayItem> mPeopleOverlay;
    protected ImageButton btCenterMap;
    protected ImageButton btFollowMe;
    private LocationManager lm;
    private Location currentLocation = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants.setUserAgentValue(BuildConfig.APPLICATION_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Request permissions to support Android Marshmallow and above devices
        if (Build.VERSION.SDK_INT >= 23) {
            checkPermissions();
        }

        View rootView = inflater.inflate(R.layout.map_fragment, container, false);
        mMapView = new MapView(inflater.getContext());
        ((FrameLayout)rootView.findViewById(R.id.map_container)).addView(mMapView);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        final Context context = this.getActivity();
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context),
                mMapView);

        mScaleBarOverlay = new ScaleBarOverlay(mMapView);
        mScaleBarOverlay.setCentred(true);
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);

        mRotationGestureOverlay = new RotationGestureOverlay(mMapView);
        mRotationGestureOverlay.setEnabled(true);

        mMyLocationOverlay = new SimpleLocationOverlay(BitmapFactory.decodeResource(getResources(), R.drawable.person));

        mMapView.getController().setZoom(15);
        mMapView.setTilesScaledToDpi(true);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        mMapView.setFlingEnabled(true);
        mMapView.getOverlays().add(this.mLocationOverlay);
        mMapView.getOverlays().add(this.mScaleBarOverlay);
        mMapView.getOverlays().add(this.mMyLocationOverlay);

        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
        mLocationOverlay.setOptionsMenuEnabled(true);

        // Center to center of USA (will update to your location shortly after)
        mMapView.getController().setCenter(new GeoPoint(39.8333333, -98.585522));

        btCenterMap = (ImageButton) view.findViewById(R.id.ic_center_map);
        btCenterMap.setVisibility(View.GONE); // Will get enabled when we receive a location

        btCenterMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "centerMap clicked ");
                if (currentLocation!=null) {
                    GeoPoint center = (GeoPoint) mMapView.getMapCenter();
                    if (center != null)
                        Log.d(TAG, "Current: " + center.getLatitude() + ", " + center.getLongitude());
                    GeoPoint myPosition = new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude());
                    Log.d(TAG, "Moving: " + myPosition.getLatitude() + ", " + myPosition.getLongitude());
                    mMapView.getController().animateTo(myPosition);
                    mMapView.setMapOrientation(360.0f);
                }
            }
        });

        btFollowMe = (ImageButton) view.findViewById(R.id.ic_follow_me);
        btFollowMe.setVisibility(View.GONE); // Will get enabled when we receive a location

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
        addMarker("Test", "Test message", new GeoPoint(27.9429057,-82.4577568));

        // Need to manually say we've resumed so location and such works.
        this.myResume();
        Log.d(TAG, "OnViewCreated");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (lm != null && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lm.removeUpdates(this);
        }

        mLocationOverlay.disableFollowLocation();
        mLocationOverlay.disableMyLocation();
        mScaleBarOverlay.disableScaleBar();
    }

    @Override
    public void onResume(){
        super.onResume();
        myResume();
    }

    private void myResume() {
        lm = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }

        mLocationOverlay.enableFollowLocation();
        mLocationOverlay.enableMyLocation();
        mScaleBarOverlay.enableScaleBar();

        btCenterMap.callOnClick();
    }

    @Override
    public void onDetach(){
        super.onDetach();
        mMapView.onDetach();
        mMapView=null;
    }

    @Override
    public void onLocationChanged(Location location) {
        GeoPoint geoLoc = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (currentLocation == null) {
            btCenterMap.setVisibility(View.VISIBLE);
            btFollowMe.setVisibility(View.VISIBLE);
            mMapView.getController().animateTo(geoLoc);
            mMapView.setMapOrientation(360.0f);
        }
        currentLocation = location;
        mMyLocationOverlay.setLocation(geoLoc);
        Log.d(TAG, "location updated " + location.toString());
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

        final Drawable icon = ContextCompat.getDrawable(getActivity(), R.drawable.ic_map_place);

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
