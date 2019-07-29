package com.android.gevart.taxiapp.passenger;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.gevart.taxiapp.R;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class PassengerSignInActivity extends AppCompatActivity {

    private static final String TAG = "PassengerSignInActivity";

    private TextInputLayout textInputEmail;
    private TextInputLayout textInputName;
    private TextInputLayout textInputPassword;
    private TextInputLayout textInputConfirmPassword;
    private Button logInSignUpButton;
    private TextView toggleLogInSignUpTextView;
    private boolean isLogInModeActive;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_sign_in);

        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(this, PassengerMapsActivity.class));
        }

        textInputEmail = findViewById(R.id.textInputEmail);
        textInputName = findViewById(R.id.textInputName);
        textInputPassword = findViewById(R.id.textInputPassword);
        textInputConfirmPassword = findViewById(R.id.textInputConfirmPassword);
        logInSignUpButton = findViewById(R.id.logInSignUpButton);
        toggleLogInSignUpTextView = findViewById(R.id.toggleLogInSignUpTextView);
    }

    private boolean validateName() {
        String nameInput = textInputName.getEditText().getText().toString().trim();
        if (nameInput.isEmpty()) {
            textInputName.setError("Please input your name");
            return false;
        } else if (nameInput.length() > 15) {
            textInputName.setError("Name length must be less than 15");
            return false;
        } else {
            textInputName.setError("");
            return true;
        }
    }

    private boolean validateEmail() {
        String emailInput = textInputEmail.getEditText().getText().toString().trim();
        if (emailInput.isEmpty()) {
            textInputEmail.setError("Please input your email");
            return false;
        } else {
            textInputEmail.setError("");
            return true;
        }
    }

    private boolean validatePassword() {
        String passwordInput = textInputPassword.getEditText().getText().toString().trim();
        if (passwordInput.isEmpty()) {
            textInputPassword.setError("Please input your password");
            return false;
        } else if (passwordInput.length() < 6) {
            textInputPassword.setError("Password must contain more than 5 characters");
            return false;
        } else {
            textInputPassword.setError("");
            return true;
        }
    }

    private boolean validateConfirmPassword() {
        String passwordInput = textInputPassword.getEditText().getText().toString().trim();
        String confirmPasswordInput = textInputConfirmPassword.getEditText().getText().toString().trim();
        if (!confirmPasswordInput.equals(passwordInput)) {
            textInputConfirmPassword.setError("Passwords don't match");
            return false;
        } else {
            textInputPassword.setError("");
            return true;
        }
    }

    public void logInSignUpUser(View view) {
        if(isLogInModeActive) {
            if(!validateEmail() || !validatePassword()) {
                return;
            }
            auth.signInWithEmailAndPassword(textInputEmail.getEditText().getText().toString().trim(),
                    textInputPassword.getEditText().getText().toString().trim())
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithEmail:success");
                            startActivity(new Intent(PassengerSignInActivity.this, PassengerMapsActivity.class));
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(PassengerSignInActivity.this, "Authentication failed.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            if(!validateName() || !validateEmail() || !validatePassword() || !validateConfirmPassword()) {
                return;
            }
            auth.createUserWithEmailAndPassword(textInputEmail.getEditText().getText().toString().trim(),
                    textInputPassword.getEditText().getText().toString().trim())
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "createUserWithEmail:success");
                            startActivity(new Intent(PassengerSignInActivity.this, PassengerMapsActivity.class));
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.e(TAG, "createUserWithEmail:failure", task.getException());
                            Toast.makeText(PassengerSignInActivity.this, "Authentication failed.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }

    }

    public void toggleLogInSignUp(View view) {
        if (isLogInModeActive) {
            isLogInModeActive = false;
            logInSignUpButton.setText("Sign Up");
            toggleLogInSignUpTextView.setText("Tap To Log In");
            textInputName.setVisibility(View.VISIBLE);
            textInputConfirmPassword.setVisibility(View.VISIBLE);
        } else {
            isLogInModeActive = true;
            logInSignUpButton.setText("Log In");
            toggleLogInSignUpTextView.setText("Create Account");
            textInputName.setVisibility(View.GONE);
            textInputConfirmPassword.setVisibility(View.GONE);
        }
    }
}
