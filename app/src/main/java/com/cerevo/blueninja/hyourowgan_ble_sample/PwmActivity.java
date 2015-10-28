package com.cerevo.blueninja.hyourowgan_ble_sample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class PwmActivity extends AppCompatActivity {
    //BLEスキャンタイムアウト
    private static final int SCAN_TIMEOUT = 20000;
    //接続対象のデバイス名
    private static final String DEVICE_NAME = "HyouRowGan00";
    /* UUIDs */
    //BlueNinja Motion sensor Service
    private static final String UUID_SERVICE_PWMS = "00020000-6727-11e5-988e-f07959ddcdfb";
    //Motion sensor values.
    private static final String UUID_CHARACTERISTIC_PWM0EN = "00020001-6727-11e5-988e-f07959ddcdfb";
    private static final String UUID_CHARACTERISTIC_PWM0CLK = "00020002-6727-11e5-988e-f07959ddcdfb";
    private static final String UUID_CHARACTERISTIC_PWM0DUT = "00020003-6727-11e5-988e-f07959ddcdfb";
    private static final String UUID_CHARACTERISTIC_PWM1EN = "00020101-6727-11e5-988e-f07959ddcdfb";
    private static final String UUID_CHARACTERISTIC_PWM1CLK = "00020102-6727-11e5-988e-f07959ddcdfb";
    private static final String UUID_CHARACTERISTIC_PWM1DUT = "00020103-6727-11e5-988e-f07959ddcdfb";
    //ログのTAG
    private static final String LOG_TAG = "HRG_PWMS";

    /* State */
    private enum AppState {
        INIT,
        BLE_SCANNING,
        BLE_SCAN_FAILED,
        BLE_DEV_FOUND,
        BLE_SRV_FOUND,
        BLE_CHARACTERISTIC_NOT_FOUND,
        BLE_CONNECTED,
        BLE_DISCONNECTED,
        BLE_SRV_NOT_FOUND,
        BLE_READ_SUCCESS,
        BLE_NOTIF_REGISTERD,
        BLE_NOTIF_REGISTER_FAILED,
        BLE_WRITE_FALIED,
        BLE_WRITE,
        BLE_WRITEING,
        BLE_READ,
        BLE_CLOSED
    }
    private AppState mAppState = AppState.INIT;
    //状態変更
    private void setStatus(AppState state)
    {
        Message msg = new Message();
        msg.what = state.ordinal();
        msg.obj = state.name();

        mAppState = state;
        mHandler.sendMessage(msg);
    }
    //状態取得
    private AppState getStats()
    {
        return mAppState;
    }

    private byte[] mRecvValue;

    /* メンバ変数 */
    private BluetoothManager mBtManager;
    private BluetoothAdapter mBtAdapter;
    private BluetoothGatt mGatt;
    private BluetoothGatt mBtGatt;
    private BluetoothGattCharacteristic mCharstPwm0En;
    private BluetoothGattCharacteristic mCharstPwm0Clk;
    private BluetoothGattCharacteristic mCharstPwm0Dut;
    private BluetoothGattCharacteristic mCharstPwm1En;
    private BluetoothGattCharacteristic mCharstPwm1Clk;
    private BluetoothGattCharacteristic mCharstPwm1Dut;

    private Handler mHandler;

    Button mButtonConnect;
    Button mButtonDisconnect;
    Button mButtonPwm0ClockUpdate;
    Button mButtonPwm0DutyUpdate;
    Button mButtonPwm1ClockUpdate;
    Button mButtonPwm1DutyUpdate;
    CheckBox mCheckBoxPwm0Enable;
    CheckBox mCheckBoxPwm1Enable;
    EditText mEditPwm0Clock;
    EditText mEditPwm1Clock;
    SeekBar mSeekBarPwm0Duty;
    SeekBar mSeekBarPwm1Duty;

    TextView mTextPwm0Duty;
    TextView mTextPwm1Duty;
    TextView mTextStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pwm);

        /* Bluetooth関連の初期化 */
        mBtManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();
        if ((mBtAdapter == null) || !mBtAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Warning: Bluetooth Disabled.", Toast.LENGTH_SHORT).show();
            finish();
        }

        mButtonConnect = (Button)findViewById(R.id.buttonConnect);
        mButtonDisconnect = (Button)findViewById(R.id.buttonDisconnect);
        mButtonPwm0ClockUpdate = (Button)findViewById(R.id.buttonPwm0ClockUpdate);
        mButtonPwm0DutyUpdate = (Button)findViewById(R.id.buttonPwm0DutyUpdate);
        mButtonPwm1ClockUpdate = (Button)findViewById(R.id.buttonPwm1ClockUpdate);
        mButtonPwm1DutyUpdate = (Button)findViewById(R.id.buttonPwm1DutyUpdate);
        mCheckBoxPwm0Enable = (CheckBox)findViewById(R.id.checkBoxPwm0Enable);
        mCheckBoxPwm1Enable = (CheckBox)findViewById(R.id.checkBoxPwm1Enable);
        mEditPwm0Clock = (EditText)findViewById(R.id.editPwm0Clock);
        mEditPwm1Clock = (EditText)findViewById(R.id.editPwm1Clock);
        mSeekBarPwm0Duty = (SeekBar)findViewById(R.id.seekBarPwm0Duty);
        mSeekBarPwm1Duty = (SeekBar)findViewById(R.id.seekBarPwm1Duty);
        mTextPwm0Duty = (TextView)findViewById(R.id.textPwm0Duty);
        mTextPwm1Duty = (TextView)findViewById(R.id.textPwm1Duty);
        mTextStatus = (TextView)findViewById(R.id.textStatus);

        mButtonConnect.setOnClickListener(buttonOnClickListener);
        mButtonDisconnect.setOnClickListener(buttonOnClickListener);
        mButtonPwm0ClockUpdate.setOnClickListener(buttonOnClickListener);
        mButtonPwm0DutyUpdate.setOnClickListener(buttonOnClickListener);
        mButtonPwm1ClockUpdate.setOnClickListener(buttonOnClickListener);
        mButtonPwm1DutyUpdate.setOnClickListener(buttonOnClickListener);

        mCheckBoxPwm0Enable.setOnClickListener(checkBoxOnClickListener);
        mCheckBoxPwm1Enable.setOnClickListener(checkBoxOnClickListener);

        mEditPwm0Clock.setOnFocusChangeListener(clockOnFocusChangeListener);
        mEditPwm1Clock.setOnFocusChangeListener(clockOnFocusChangeListener);

        mSeekBarPwm0Duty.setOnSeekBarChangeListener(dutyOnSeekBarChangeListener);
        mSeekBarPwm1Duty.setOnSeekBarChangeListener(dutyOnSeekBarChangeListener);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mTextStatus.setText((String)msg.obj);
                AppState sts = AppState.values()[msg.what];
                switch (sts) {
                    case INIT:
                    case BLE_SCAN_FAILED:
                    case BLE_CLOSED:
                    case BLE_DISCONNECTED:
                    case BLE_SRV_NOT_FOUND:
                    case BLE_NOTIF_REGISTER_FAILED:
                        mButtonConnect.setEnabled(true);
                        mButtonDisconnect.setEnabled(false);
                        mCheckBoxPwm0Enable.setEnabled(false);
                        mCheckBoxPwm1Enable.setEnabled(false);
                        mButtonPwm0ClockUpdate.setEnabled(false);
                        mButtonPwm0DutyUpdate.setEnabled(false);
                        mButtonPwm1ClockUpdate.setEnabled(false);
                        mButtonPwm1DutyUpdate.setEnabled(false);

                        mCheckBoxPwm0Enable.setChecked(false);
                        mCheckBoxPwm1Enable.setChecked(false);

                        mEditPwm0Clock.setText("1000");
                        mEditPwm1Clock.setText("1000");
                        mTextPwm0Duty.setText("50%");
                        mTextPwm1Duty.setText("50%");

                        mSeekBarPwm0Duty.setProgress(50);
                        mSeekBarPwm1Duty.setProgress(50);
                        break;
                    case BLE_SCANNING:
                    case BLE_WRITEING:
                        mButtonConnect.setEnabled(false);
                        mButtonDisconnect.setEnabled(true);
                        mCheckBoxPwm0Enable.setEnabled(false);
                        mCheckBoxPwm1Enable.setEnabled(false);
                        mButtonPwm0ClockUpdate.setEnabled(false);
                        mButtonPwm0DutyUpdate.setEnabled(false);
                        mButtonPwm1ClockUpdate.setEnabled(false);
                        mButtonPwm1DutyUpdate.setEnabled(false);
                        break;
                    case BLE_WRITE:
                    case BLE_WRITE_FALIED:
                    case BLE_CONNECTED:
                        mButtonConnect.setEnabled(false);
                        mButtonDisconnect.setEnabled(true);
                        mCheckBoxPwm0Enable.setEnabled(true);
                        mCheckBoxPwm1Enable.setEnabled(true);
                        mButtonPwm0ClockUpdate.setEnabled(true);
                        mButtonPwm0DutyUpdate.setEnabled(true);
                        mButtonPwm1ClockUpdate.setEnabled(true);
                        mButtonPwm1DutyUpdate.setEnabled(true);
                        break;
                    case BLE_READ:
                        break;
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        mCheckBoxPwm0Enable.setChecked(false);
        mCheckBoxPwm1Enable.setChecked(false);
        mSeekBarPwm0Duty.setProgress(50);
        mSeekBarPwm1Duty.setProgress(50);

        setStatus(AppState.INIT);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
        disconnectBLE();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    View.OnClickListener buttonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ByteBuffer buff;
            try {
                switch (v.getId()) {
                    case R.id.buttonConnect:
                        connectBLE();
                        break;
                    case R.id.buttonDisconnect:
                        disconnectBLE();
                        break;
                    case R.id.buttonPwm0ClockUpdate:
                        int pwm0clk = Integer.parseInt(mEditPwm0Clock.getText().toString());
                        buff = ByteBuffer.allocate(4);
                        buff.order(ByteOrder.LITTLE_ENDIAN);
                        buff.putInt(pwm0clk);
                        mCharstPwm0Clk.setValue(buff.array());
                        mBtGatt.writeCharacteristic(mCharstPwm0Clk);
                        setStatus(AppState.BLE_WRITEING);
                        break;
                    case R.id.buttonPwm0DutyUpdate:
                        float pwm0duty = (float)(mSeekBarPwm0Duty.getProgress() * 0.01);
                        buff = ByteBuffer.allocate(4);
                        buff.order(ByteOrder.LITTLE_ENDIAN);
                        buff.putFloat(pwm0duty);
                        mCharstPwm0Dut.setValue(buff.array());
                        mBtGatt.writeCharacteristic(mCharstPwm0Dut);
                        setStatus(AppState.BLE_WRITEING);
                        break;
                    case R.id.buttonPwm1ClockUpdate:
                        int pwm1clk = Integer.parseInt(mEditPwm1Clock.getText().toString());
                        buff = ByteBuffer.allocate(4);
                        buff.order(ByteOrder.LITTLE_ENDIAN);
                        buff.putInt(pwm1clk);
                        mCharstPwm1Clk.setValue(buff.array());
                        mBtGatt.writeCharacteristic(mCharstPwm1Clk);
                        setStatus(AppState.BLE_WRITEING);
                        break;
                    case R.id.buttonPwm1DutyUpdate:
                        float pwm1duty = (float)(mSeekBarPwm1Duty.getProgress() * 0.01);
                        buff = ByteBuffer.allocate(4);
                        buff.order(ByteOrder.LITTLE_ENDIAN);
                        buff.putFloat(pwm1duty);
                        mCharstPwm1Dut.setValue(buff.array());
                        mBtGatt.writeCharacteristic(mCharstPwm1Dut);
                        setStatus(AppState.BLE_WRITEING);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    View.OnClickListener checkBoxOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            CheckBox c = (CheckBox)v;
            BluetoothGattCharacteristic charstUpdate = null;
            byte[] val_en = new byte[1];

            switch (v.getId()) {
                case R.id.checkBoxPwm0Enable:
                    charstUpdate = mCharstPwm0En;
                    break;
                case R.id.checkBoxPwm1Enable:
                    charstUpdate = mCharstPwm1En;
                    break;
                default:
                    return;
            }
            val_en[0] = (byte)(c.isChecked() ? 1 : 0);
            charstUpdate.setValue(val_en);
            mBtGatt.writeCharacteristic(charstUpdate);
        }
    };

    SeekBar.OnSeekBarChangeListener dutyOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            TextView tv = null;
            switch (seekBar.getId()) {
                case R.id.seekBarPwm0Duty:
                    tv = (TextView)findViewById(R.id.textPwm0Duty);
                    break;
                case R.id.seekBarPwm1Duty:
                    tv = (TextView)findViewById(R.id.textPwm1Duty);
                    break;
                default:
                    return;
            }
            tv.setText(String.format("%d%%", seekBar.getProgress()));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    View.OnFocusChangeListener clockOnFocusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus == false) {
                EditText et = (EditText)v;
                try {
                    int val = Integer.parseInt(et.getText().toString());
                    if((val < 50) || (val > 30000)) {
                        et.setText("1000");
                    }
                } catch (Exception e) {
                    et.setText("1000");
                }
            }
        }
    };

    /* BLEスキャンコールバック */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (DEVICE_NAME.equals(device.getName())) {
                //HyouRowGanを発見
                setStatus(AppState.BLE_DEV_FOUND);
                mBtAdapter.stopLeScan(this);
                mBtGatt = device.connectGatt(getApplicationContext(), false, mBluetoothGattCallback);
            }
        }
    };

    /* GATTコールバック */
    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    /* 接続 */
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    /* 切断 */
                    setStatus(AppState.BLE_DISCONNECTED);
                    mBtGatt = null;
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(UUID.fromString(UUID_SERVICE_PWMS));
            if (service == null) {
                //サービスが見つからない
                setStatus(AppState.BLE_SRV_NOT_FOUND);
            } else {
                //サービスが見つかった
                setStatus(AppState.BLE_SRV_FOUND);
                mCharstPwm0En = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_PWM0EN));
                mCharstPwm0Clk = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_PWM0CLK));
                mCharstPwm0Dut = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_PWM0DUT));
                mCharstPwm1En = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_PWM1EN));
                mCharstPwm1Clk = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_PWM1CLK));
                mCharstPwm1Dut = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_PWM1DUT));

                if ((mCharstPwm0En == null) || (mCharstPwm0Clk == null) || (mCharstPwm0Dut == null) ||
                        (mCharstPwm1En == null) || (mCharstPwm1Clk == null) || (mCharstPwm1Dut == null)) {
                    //Characteristicが見つからない
                    setStatus(AppState.BLE_CHARACTERISTIC_NOT_FOUND);
                    return;
                }
            }
            mGatt = gatt;
            setStatus(AppState.BLE_CONNECTED);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String charstUUID = characteristic.getUuid().toString();


                if ((UUID_CHARACTERISTIC_PWM0EN.equals(charstUUID)) || (UUID_CHARACTERISTIC_PWM0CLK.equals(charstUUID)) || (UUID_CHARACTERISTIC_PWM0DUT.equals(charstUUID))
                        || (UUID_CHARACTERISTIC_PWM1EN.equals(charstUUID)) ||(UUID_CHARACTERISTIC_PWM1CLK.equals(charstUUID)) || (UUID_CHARACTERISTIC_PWM1DUT.equals(charstUUID))) {
                    setStatus(AppState.BLE_WRITE);
                }
            } else {
                setStatus(AppState.BLE_WRITE_FALIED);
            }
        }
    };

    private void connectBLE()
    {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBtAdapter.stopLeScan(mLeScanCallback);
                if (AppState.BLE_SCANNING.equals(getStats())) {
                    setStatus(AppState.BLE_SCAN_FAILED);
                }
            }
        }, SCAN_TIMEOUT);

        mBtAdapter.stopLeScan(mLeScanCallback);
        mBtAdapter.startLeScan(mLeScanCallback);
        setStatus(AppState.BLE_SCANNING);
    }

    private void disconnectBLE()
    {
        if (mBtGatt != null) {
            mBtGatt.close();
            mBtGatt = null;
            mCharstPwm0En = null;
            mCharstPwm0Clk = null;
            mCharstPwm0Dut = null;
            mCharstPwm1En = null;
            mCharstPwm1Clk = null;
            mCharstPwm1Dut = null;

            setStatus(AppState.BLE_CLOSED);
        }
    }

}
