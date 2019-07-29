package com.android.gevart.taxiapp.passenger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.gevart.taxiapp.BuildConfig;
import com.android.gevart.taxiapp.ChooseModeActivity;
import com.android.gevart.taxiapp.R;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

import static java.lang.Thread.sleep;

public class PassengerMapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "PassengerMapsActivity";

    private DrawerLayout drawerLayout;

    private GoogleMap mMap;

    private static final int CHECK_SETTINGS_CODE = 111;
    private static final int REQUEST_LOCATION_PERMISSION = 222;

    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private LocationSettingsRequest locationSettingsRequest;
    private Location currentLocation;

    private boolean isLocationUpdatesActive;
    private Button searchTaxiButton, orderButton;
    private FirebaseDatabase database;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private GeoFire geoPassenger;
    private GeoFire geoDriver;
    private GeoFire geoOrder;
    private int searchRadius = 1;
    private boolean isDriverFound = false;
    private String nearestDriverId;
    private Marker driverMarker;
    private Marker passengerMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_maps);

        drawerLayout = findViewById(R.id.passenger_drawer_layout);
        NavigationView navView = findViewById(R.id.passenger_nav_view);

        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        DatabaseReference passengers = database.getReference().child("passengers");
        DatabaseReference drivers = database.getReference().child("drivers");
        DatabaseReference orders = database.getReference().child("orders");
        geoPassenger = new GeoFire(passengers);
        geoDriver = new GeoFire(drivers);
        geoOrder = new GeoFire(orders);

        searchTaxiButton = findViewById(R.id.searchTaxiButton);
        orderButton = findViewById(R.id.orderButton);

        ImageButton hamburgerButton = findViewById(R.id.passengerHamburgerButton);

        hamburgerButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            switch (id) {
                case R.id.settings:
                    startActivity(new Intent(PassengerMapsActivity.this, PassengerSettingsActivity.class));
                    break;
                case R.id.about:
                    startActivity(new Intent(PassengerMapsActivity.this, PassengerAboutActivity.class));
                    break;
                case R.id.sign_out:
                    auth.signOut();
                    String passengerUserId = currentUser.getUid();
                    geoPassenger.removeLocation(passengerUserId);
                    stopLocationUpdates();
                    Intent intent = new Intent(PassengerMapsActivity.this, ChooseModeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        searchTaxiButton.setOnClickListener(v -> {
            searchTaxiButton.setEnabled(false);
            searchTaxiButton.setText("Looking for the nearest taxi...");
            getNearestTaxi();
            searchTaxiButton.setEnabled(true);
        });

        orderButton.setOnClickListener(v -> {
            orderButton.setEnabled(false);
            geoOrder.setLocation(nearestDriverId, new GeoLocation(currentLocation.getLatitude(),
                    currentLocation.getLongitude()));
            //TODO
            // 1.Add a possibility to cancel the order
            // 2.If a driver has accepted the order, the passenger must not see the searchTaxiButton and orderButton
            //        instead he/she must see the cancelOrderButton
            // 3.Passenger must recieve a signal when order is denied by the driver
            try {
                sleep(20000);
                orderButton.setEnabled(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        buildLocationRequest();
        buildLocationCallback();
        buildLocationSettingsRequest();

        startLocationUpdates();
    }

    private void getNearestTaxi() {
        GeoQuery query = geoDriver.queryAtLocation(new GeoLocation(currentLocation.getLatitude(),
                currentLocation.getLongitude()), searchRadius);

        query.removeAllListeners();

        query.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!isDriverFound) {
                    isDriverFound = true;
                    nearestDriverId = key;
                    getNearestDriverLocation();
                }
            }

            @Override
            public void onKeyExited(String key) {}

            @Override
            public void onKeyMoved(String key, GeoLocation location) {}

            @Override
            public void onGeoQueryReady() {
                if (!isDriverFound) {
                    if (searchRadius < 6) {
                        searchRadius++;
                        getNearestTaxi();
                    }
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {}
        });
    }

    private void getNearestDriverLocation() {
        searchTaxiButton.setText("Getting your driver location...");
        DatabaseReference nearestDriverLocation = database.getReference().child("drivers").child(nearestDriverId).child("l");
        nearestDriverLocation.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    List<Object> driverLocationParams = (List<Object>)dataSnapshot.getValue();

                    double latitude = 0.0;
                    double longitude = 0.0;

                    if (driverLocationParams.get(0) != null) {
                        latitude = Double.parseDouble(driverLocationParams.get(0).toString());
                    }
                    if (driverLocationParams.get(1) != null) {
                        longitude = Double.parseDouble(driverLocationParams.get(1).toString());
                    }

                    LatLng driverLatLng = new LatLng(latitude, longitude);
                    if (driverMarker != null) {
                        driverMarker.remove();
                    }

                    Location driverLocation = new Location("");
                    driverLocation.setLatitude(latitude);
                    driverLocation.setLongitude(longitude);

                    int distanceToDriver = (int)driverLocation.distanceTo(currentLocation);
                    searchTaxiButton.setText("Distance to taxi: " + distanceToDriver + "m");

                    driverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Your driver is here"));
                    orderButton.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (currentLocation != null) {
            LatLng passengerLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            mMap.addMarker(new MarkerOptions().position(passengerLocation).title("Passenger location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(passengerLocation));
        }
    }

    private void stopLocationUpdates() {
        if (!isLocationUpdatesActive) {
            return;
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(this, task -> isLocationUpdatesActive = false);
    }

    private void startLocationUpdates() {
        isLocationUpdatesActive = true;

        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this, locationSettingsResponse -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    Activity#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for Activity#requestPermissions for more details.
                            return;
                        }
                    }
                    fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.myLooper()
                    );

                    updateLocationUI();
                })
                .addOnFailureListener(this, e -> {
                    int statusCode = ((ApiException) e).getStatusCode();

                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                                resolvableApiException.startResolutionForResult(PassengerMapsActivity.this, CHECK_SETTINGS_CODE);
                            } catch (IntentSender.SendIntentException sie) {
                                sie.printStackTrace();
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String message = "Adjust location settings on your device";
                            Toast.makeText(PassengerMapsActivity.this, message, Toast.LENGTH_LONG).show();
                            isLocationUpdatesActive = false;
                            break;
                    }
                    updateLocationUI();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case CHECK_SETTINGS_CODE:
                switch (requestCode) {
                    case Activity.RESULT_OK:
                        Log.d(TAG, "User has agreed to change location settings");
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.d(TAG, "User has not agreed to change location settings");
                        isLocationUpdatesActive = false;
                        updateLocationUI();
                        break;
                }
                break;
        }
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    private void buildLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                currentLocation = locationResult.getLastLocation();

                updateLocationUI();
            }
        };
    }

    private void updateLocationUI() {
        if (currentLocation != null) {
            LatLng passengerLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(passengerLocation));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(12));
            if (passengerMarker != null) passengerMarker.remove();
            passengerMarker =  mMap.addMarker(new MarkerOptions().position(passengerLocation).title("Your location"));

            String passengerUserId = currentUser.getUid();
            geoPassenger.setLocation(passengerUserId, new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude()));
        }
    }

    private void buildLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isLocationUpdatesActive && checkLocationPermission()) {
            startLocationUpdates();
        } else if (!checkLocationPermission()) {
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (shouldProvideRationale) {
            showSnackBar("Location permission is needed for app functionality", "OK",
                    v -> ActivityCompat.requestPermissions(PassengerMapsActivity.this,
                            new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION));
        } else {
            ActivityCompat.requestPermissions(PassengerMapsActivity.this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    private void showSnackBar(final String mainText, final String action, View.OnClickListener listener) {
        Snackbar.make(findViewById(android.R.id.content), mainText, Snackbar.LENGTH_INDEFINITE)
                .setAction(action, listener).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length <= 0) {
                Log.d("onRequestPermissions", "Request was cancelled");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isLocationUpdatesActive) {
                    startLocationUpdates();
                }
            } else {
                showSnackBar("Turn on location on settings", "Settings",
                        v -> {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                            intent.setData(uri);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        });
            }
        }
    }

    private boolean checkLocationPermission() {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }
}
