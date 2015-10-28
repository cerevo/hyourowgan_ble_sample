package com.cerevo.blueninja.hyourowgan_ble_sample;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    Button buttonShowGPIO;
    Button buttonShowPWM;
    Button buttonShowMotionSensor;
    Button buttonShowAirpressureSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonShowGPIO = (Button)findViewById(R.id.buttonShowGPIO);
        buttonShowGPIO.setOnClickListener(buttonClickListener);
        buttonShowPWM = (Button)findViewById(R.id.buttonShowPWM);
        buttonShowPWM.setOnClickListener(buttonClickListener);
        buttonShowMotionSensor = (Button)findViewById(R.id.buttonShowMotionSensor);
        buttonShowMotionSensor.setOnClickListener(buttonClickListener);
        buttonShowAirpressureSensor = (Button)findViewById(R.id.buttonShowAirpressureSensor);
        buttonShowAirpressureSensor.setOnClickListener(buttonClickListener);

    }

    public View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent;
            switch (v.getId()) {
                case R.id.buttonShowGPIO:
                    intent = new Intent(getApplicationContext(), GpioActivity.class);
                    startActivity(intent);
                    break;
                case R.id.buttonShowPWM:
                    intent = new Intent(getApplicationContext(), PwmActivity.class);
                    startActivity(intent);
                    break;
                case R.id.buttonShowMotionSensor:
                    intent = new Intent(getApplicationContext(), MotionSensorActivity.class);
                    startActivity(intent);
                    break;
                case R.id.buttonShowAirpressureSensor:
                    intent = new Intent(getApplicationContext(), AirpressureActivity.class);
                    startActivity(intent);
                    break;
            }
        }
    };
}
