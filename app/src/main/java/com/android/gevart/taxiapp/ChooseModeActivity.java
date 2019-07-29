package com.android.gevart.taxiapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.android.gevart.taxiapp.driver.DriverSignInActivity;
import com.android.gevart.taxiapp.passenger.PassengerSignInActivity;

public class ChooseModeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_mode);
    }

    public void goToPassengerSignIn(View view) {
        startActivity(new Intent(this, PassengerSignInActivity.class));
    }

    public void goToDriverSignIn(View view) {
        startActivity(new Intent(this, DriverSignInActivity.class));
    }
}
