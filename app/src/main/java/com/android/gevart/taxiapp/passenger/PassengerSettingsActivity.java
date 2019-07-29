package com.android.gevart.taxiapp.passenger;

import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import com.android.gevart.taxiapp.R;

public class PassengerSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_settings);

        ImageButton passengerSettingsBackButton = findViewById(R.id.passenger_settings_back_button);
        passengerSettingsBackButton.setOnClickListener(v ->
                NavUtils.navigateUpFromSameTask(PassengerSettingsActivity.this));
    }
}
