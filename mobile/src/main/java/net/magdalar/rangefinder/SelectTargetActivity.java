package net.magdalar.rangefinder;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.DecimalFormat;

public class SelectTargetActivity
  extends FragmentActivity
  implements GoogleMap.OnMarkerDragListener,
  GoogleMap.OnMapClickListener,
  GooglePlayServicesClient.ConnectionCallbacks,
  GooglePlayServicesClient.OnConnectionFailedListener,
  LocationListener {
  private static final String TAG = SelectTargetActivity.class.getSimpleName();

  private static final DecimalFormat formatLatLng = new DecimalFormat("#.#####");
  private static final DecimalFormat formatDistance = new DecimalFormat("0");
  private static final DecimalFormat formatBearing = new DecimalFormat("0.#");


  public static final int DEFAULT_ZOOM = 15;

  /**
   * The start location if location services are not available.
   */
  public static final LatLng DEFAULT_START_LOCATION = new LatLng(0, 0);

  private final static int
    CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
  private static final int MAX_LOCATION_INTERVAL_MS = 10 * 1000;
  private static final int MIN_LOCATION_INTERVAL_MS = 1000;


  // May be null if Google Play services APK is not available.
  private GoogleMap mMap = null;

  private Marker targetMarker = null;

  private LatLng targetLatLng = DEFAULT_START_LOCATION;
  private Location userLocation = null;

  private LocationRequest locationRequest = null;
  private LocationClient locationClient = null;

  private TextView userLocationTextView = null;
  private TextView targetLocationTextView = null;
  private TextView distanceTextView = null;
  private TextView bearingTextView = null;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate: bundle? " + (savedInstanceState != null));
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_select_target);

    userLocationTextView = (TextView) findViewById(R.id.user_location_text);
    targetLocationTextView = (TextView) findViewById(R.id.target_location_text);
    distanceTextView = (TextView) findViewById(R.id.distance_text);
    bearingTextView = (TextView) findViewById(R.id.bearing_text);

    userLocation = getInitialUserLocation();
    if (userLocation != null) {
      // TODO: restore targetLatLng from bundle or sharedPreferences?
      targetLatLng =
        new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
    }

    setUpMapIfNeeded();
    setUpLocationListener();
  }

  @Override
  protected void onPause() {
    Log.d(TAG, "onPause");
    super.onPause();
  }

  @Override
  protected void onResume() {
    Log.d(TAG, "onResume");
    super.onResume();
    setUpMapIfNeeded();
    setUpLocationListener();
  }

  @Override
  protected void onStop() {
    Log.d(TAG, "onStop");
    if (locationClient != null &&
        (locationClient.isConnected() || locationClient.isConnecting())) {
      Log.d(TAG, "Disconnecting locationClient.");
      locationClient.removeLocationUpdates(this);
      locationClient.disconnect();
      locationClient = null;
    }
    super.onStop();
  }

  @Override
  /**
   * When we've connected to the play services.
   */
  public void onConnected(Bundle bundle) {
    Log.d(TAG, "Play services connected.");
    locationClient.requestLocationUpdates(locationRequest, this);
  }

  @Override
  /**
   * When we've disconnected from the play services.
   */
  public void onDisconnected() {
    Log.d(TAG, "Play services disconnected.");
    locationClient = null;
  }

  @Override
  /**
   * When we've had a connection failure with play services.
   */
  public void onConnectionFailed(ConnectionResult connectionResult) {
    Log.d(TAG, "onConnectionFailed: " + connectionResult.toString());
    // Google Play services can resolve some errors it detects.
    if (connectionResult.hasResolution()) {
      try {
        connectionResult.startResolutionForResult(
          this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
      } catch (IntentSender.SendIntentException e) {
        Log.e(TAG, "Failure connecting to Play Services.", e);
      }
    } else {
      GooglePlayServicesUtil.getErrorDialog(
        connectionResult.getErrorCode(), this, CONNECTION_FAILURE_RESOLUTION_REQUEST).show();
    }
  }

  @Override
  protected void onActivityResult(
    int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case CONNECTION_FAILURE_RESOLUTION_REQUEST:
        switch (resultCode) {
          case Activity.RESULT_OK:
            setUpLocationListener();
            break;
        }
    }
  }

  /**
   * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
   * installed) and the map has not already been instantiated.. This will ensure that we only ever
   * call {@link #setUpMap()} once when {@link #mMap} is not null.
   */
  private void setUpMapIfNeeded() {
    if (mMap == null) {
      // Try to obtain the map from the SupportMapFragment. This may prompt the user to
      // install/update Google Play Services, and thus may lose application focus.
      mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
        .getMap();
      if (mMap != null) {
        setUpMap();
      }
    }
  }

  @Override
  public void onMarkerDragStart(Marker m) {
  }

  @Override
  public void onMarkerDrag(Marker m) {
    // TODO: maybe show realtime distance updates while dragging?
  }

  @Override
  public void onMarkerDragEnd(Marker m) {
    updateMarkerLocation(m.getPosition());
  }


  @Override
  public void onMapClick(LatLng pos) {
    updateMarkerLocation(pos);
  }

  @Override
  public void onLocationChanged(Location l) {
    Log.d(TAG, "Updated Location: " + formatLatLng(l));
    userLocation = l;
    updateTextViews();
  }

  private void updateTextViews() {
    assert userLocation != null;
    assert targetLatLng != null;

    String userLocStr = "Current: " + formatLatLng(userLocation);
    userLocationTextView.setText(userLocStr);

    String targetLocStr = "Target: " + formatLatLng(targetLatLng);
    targetLocationTextView.setText(targetLocStr);

    float[] results = new float[2];
    Location.distanceBetween(
      userLocation.getLatitude(), userLocation.getLongitude(),
      targetLatLng.latitude, targetLatLng.longitude,
      results);

    String distStr = "Distance: " + formatDistance(results[0]);
    distanceTextView.setText(distStr);

    String bearingStr = "Bearing: " + formatBearing(results[1]);
    bearingTextView.setText(bearingStr);
  }

  private void updateMarkerLocation(LatLng pos) {
    Log.d(TAG, "Marked moved to: " + formatLatLng(pos));
    targetLatLng = pos;
    if (!targetMarker.getPosition().equals(pos)) {
      targetMarker.setPosition(pos);
    }
    mMap.animateCamera(CameraUpdateFactory.newLatLng(pos));

    updateTextViews();
  }

  private String formatDistance(double distance) {
    return formatDistance.format(distance) + "m";
  }

  private String formatBearing(double bearing) {
    if (bearing < 0 && bearing > -180) {
      // Normalize to [0,360]
      bearing = 360.0 + bearing;
    }
    if (bearing > 360 || bearing < -180) {
      Log.e(TAG, "Invalid bearing: " + bearing);
      return "Unknown";
    }

    String directions[] = {
      "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
      "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
      "N"};
    String cardinal = directions[(int) Math.floor(((bearing + 11.25) % 360) / 22.5)];
    return cardinal + " (" + formatBearing.format(bearing) + " deg)";
  }

  private String formatLatLng(Location pos) {
    return formatLatLng(pos.getLatitude(), pos.getLongitude());
  }

  private String formatLatLng(LatLng pos) {
    return formatLatLng(pos.latitude, pos.longitude);
  }

  private String formatLatLng(double lat, double lng) {
    return formatLatLng.format(lat)
      + ", "
      + formatLatLng.format(lng);
  }

  protected void setUpLocationListener() {
    if (locationClient != null &&
      (locationClient.isConnected() || locationClient.isConnecting())) {
      Log.d(TAG, "locationClient already running.");
      return;
    }

    Log.d(TAG, "Connecting locationClient.");
    locationClient = new LocationClient(this, this, this);
    locationClient.connect();

    locationRequest = LocationRequest.create();
    locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    locationRequest.setFastestInterval(MIN_LOCATION_INTERVAL_MS);
    locationRequest.setInterval(MAX_LOCATION_INTERVAL_MS);
  }

  protected Location getInitialUserLocation() {
    LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
    String provider = lm.getBestProvider(new Criteria(), true);
    if (provider == null) {
      return null;
    }
    return lm.getLastKnownLocation(provider);
  }

  /**
   * Set up the map interactions.
   *
   * This should only be called once and when we are sure that {@link #mMap} is not null.
   */
  private void setUpMap() {
    mMap.setMyLocationEnabled(true);
    mMap.setOnMapClickListener(this);
    mMap.setOnMarkerDragListener(this);
    //TODO
    //mMap.setOnMarkerClickListener(this);

    targetMarker = mMap.addMarker(
      new MarkerOptions()
        .position(targetLatLng)
        .draggable(true));

    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, DEFAULT_ZOOM));
  }

}
