package com.android.gevart.taxiapp.driver;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;

import com.android.gevart.taxiapp.BuildConfig;
import com.android.gevart.taxiapp.ChooseModeActivity;
import com.android.gevart.taxiapp.R;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
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
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DriverMapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "DriverMapsActivity";

    private SharedPreferences sharedPreferences;

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
    private FirebaseDatabase database;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference ordererLocation;
    private GeoFire geoDriver;
    private GeoFire geoOrder;
    private MediaPlayer mediaPlayer;
    private Marker passengerMarker;
    private Marker driverMarker;
    private String driverUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_maps);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        drawerLayout = findViewById(R.id.driver_drawer_layout);
        NavigationView navView = findViewById(R.id.driver_nav_view);

        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        driverUserId = currentUser.getUid();
        DatabaseReference drivers = database.getReference().child("drivers");
        DatabaseReference orders = database.getReference().child("orders");
        geoDriver = new GeoFire(drivers);
        geoOrder = new GeoFire(orders);

        ImageButton hamburgerButton = findViewById(R.id.driverHamburgerButton);

        hamburgerButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            switch (id) {
                case R.id.settings:
                    startActivity(new Intent(DriverMapsActivity.this, DriverSettingsActivity.class));
                    break;
                case R.id.about:
                    startActivity(new Intent(DriverMapsActivity.this, DriverAboutActivity.class));
                    break;
                case R.id.sign_out:
                    auth.signOut();
                    String driverUserId = currentUser.getUid();
                    geoDriver.removeLocation(driverUserId);
                    stopLocationUpdates();
                    Intent intent = new Intent(DriverMapsActivity.this, ChooseModeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    break;
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        orders.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                if (dataSnapshot.getKey().equals(driverUserId)) {
                    if (sharedPreferences.getBoolean("enable_sound", true)) {
                        mediaPlayer = MediaPlayer.create(DriverMapsActivity.this, R.raw.new_order_sound);
                        mediaPlayer.start();
                        mediaPlayer.setLooping(true);
                    }

                    ordererLocation = database.getReference().child("orders").child(driverUserId).child("l");
                    ordererLocation.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                List<Object> passengerLocationParams = (List<Object>)dataSnapshot.getValue();

                                double latitude = 0.0;
                                double longitude = 0.0;

                                if (passengerLocationParams.get(0) != null) {
                                    latitude = Double.parseDouble(passengerLocationParams.get(0).toString());
                                }
                                if (passengerLocationParams.get(1) != null) {
                                    longitude = Double.parseDouble(passengerLocationParams.get(1).toString());
                                }

                                LatLng passengerLatLng = new LatLng(latitude, longitude);
                                if (passengerMarker != null) {
                                    passengerMarker.remove();
                                }

                                Location passengerLocation = new Location("");
                                passengerLocation.setLatitude(latitude);
                                passengerLocation.setLongitude(longitude);

                                int distanceToPassenger = (int)passengerLocation.distanceTo(currentLocation);
                                String message = "Distance to passenger: " + distanceToPassenger + "m";
                                showAlert(message, passengerLatLng, this);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {}
                    });
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {}

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
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
            LatLng driverLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            mMap.addMarker(new MarkerOptions().position(driverLocation).title("Driver location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(driverLocation));
        }
    }

    private void stopLocationUpdates() {
        if (!isLocationUpdatesActive) {
            return;
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(this, task -> isLocationUpdatesActive = false);
    }


    private void showAlert(String message, LatLng passengerLatLng, ValueEventListener listener) {
        AlertDialog alertDialog = new AlertDialog.Builder(DriverMapsActivity.this)
                .setTitle("New Order")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Accept", (dialog, which) -> {
                    passengerMarker = mMap.addMarker(new MarkerOptions().position(passengerLatLng).title("Your passenger is here"));
                    geoOrder.removeLocation(driverUserId);
                    ordererLocation.removeEventListener(listener);
                    mediaPlayer.stop();
                    //TODO
                    // 1.Send a signal to passenger that order is accepted
                    // 2.Send a signal to passenger when arriving to the destination point
                    // 3.Driver should not recieve orders during another order execution
                    // 4.Set a mechanism to calculate the transportation cost
                    // 5.Driver must recieve a message if order is cancelled by passenger
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    dialog.cancel();
                    geoOrder.removeLocation(driverUserId);
                    ordererLocation.removeEventListener(listener);
                    mediaPlayer.stop();
                }).create();

        alertDialog.show();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                alertDialog.dismiss();
                geoOrder.removeLocation(driverUserId);
                ordererLocation.removeEventListener(listener);
                mediaPlayer.stop();
                timer.cancel();
            }
        }, 20000);
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
                                resolvableApiException.startResolutionForResult(DriverMapsActivity.this, CHECK_SETTINGS_CODE);
                            } catch (IntentSender.SendIntentException sie) {
                                sie.printStackTrace();
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String message = "Adjust location settings on your device";
                            Toast.makeText(DriverMapsActivity.this, message, Toast.LENGTH_LONG).show();
                            isLocationUpdatesActive = false;
                            break;
                    }
                    updateLocationUI();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHECK_SETTINGS_CODE) {
            switch (resultCode) {
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
            LatLng driverLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(driverLocation));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(12));
            if (driverMarker != null) driverMarker.remove();
            driverMarker = mMap.addMarker(new MarkerOptions().position(driverLocation).title("Your location"));

            String driverUserId = currentUser.getUid();
            geoDriver.setLocation(driverUserId, new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude()));
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
                    v -> ActivityCompat.requestPermissions(DriverMapsActivity.this,
                            new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION));
        } else {
            ActivityCompat.requestPermissions(DriverMapsActivity.this,
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

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
