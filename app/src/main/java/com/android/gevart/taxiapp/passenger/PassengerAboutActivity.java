package com.android.gevart.taxiapp.passenger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import android.os.Bundle;
import android.widget.ImageButton;

import com.android.gevart.taxiapp.R;

public class PassengerAboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_about);

        ImageButton passengerAboutBackButton = findViewById(R.id.passenger_about_back_button);

        passengerAboutBackButton.setOnClickListener(v -> NavUtils.navigateUpFromSameTask(PassengerAboutActivity.this));
    }
}
