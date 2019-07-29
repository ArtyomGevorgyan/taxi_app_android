package com.android.gevart.taxiapp.driver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import android.os.Bundle;
import android.widget.ImageButton;

import com.android.gevart.taxiapp.R;

public class DriverAboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_about);

        ImageButton driverAboutBackButton = findViewById(R.id.driver_about_back_button);
        driverAboutBackButton.setOnClickListener(v -> NavUtils.navigateUpFromSameTask(DriverAboutActivity.this));
    }
}
