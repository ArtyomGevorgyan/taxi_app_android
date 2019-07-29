package com.android.gevart.taxiapp.driver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import android.os.Bundle;
import android.widget.ImageButton;

import com.android.gevart.taxiapp.R;

public class DriverSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings);

        ImageButton driverSettingsBackButton = findViewById(R.id.driver_settings_back_button);
        driverSettingsBackButton.setOnClickListener(v -> NavUtils.navigateUpFromSameTask(DriverSettingsActivity.this));
    }
}
