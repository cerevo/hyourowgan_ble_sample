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
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

public class GpioActivity extends AppCompatActivity {
    //BLEスキャンタイムアウト
    private static final int SCAN_TIMEOUT = 20000;
    //接続対象のデバイス名
    private static final String DEVICE_NAME = "HyouRowGan00";
    /* UUIDs */
    //BlueNinja Motion sensor Service
    private static final String UUID_SERVICE_GPIOS = "00010000-6727-11e5-988e-f07959ddcdfb";
    //Motion sensor values.
    private static final String UUID_CHARACTERISTIC_VALUE = "00010001-6727-11e5-988e-f07959ddcdfb";
    //ログのTAG
    private static final String LOG_TAG = "HRG_GPIOS";

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
    private BluetoothGattCharacteristic mCharacteristic;

    private byte mValue;

    private Handler mHandler;

    private TextView mTextStatus;
    private CheckBox mCheckBoxGPIO16;
    private CheckBox mCheckBoxGPIO17;
    private CheckBox mCheckBoxGPIO18;
    private CheckBox mCheckBoxGPIO19;
    private CheckBox mCheckBoxGPIO20;
    private CheckBox mCheckBoxGPIO21;
    private CheckBox mCheckBoxGPIO22;
    private CheckBox mCheckBoxGPIO23;
    private Button mButtonRead;
    private Button mButtonWrite;
    private Button mButtonConnect;
    private Button mButtonDisconnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpio);

        /* Bluetooth関連の初期化 */
        mBtManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();
        if ((mBtAdapter == null) || !mBtAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Warning: Bluetooth Disabled.", Toast.LENGTH_SHORT).show();
            finish();
        }

        mCheckBoxGPIO16 = (CheckBox)findViewById(R.id.checkBoxGPIO16);
        mCheckBoxGPIO17 = (CheckBox)findViewById(R.id.checkBoxGPIO17);
        mCheckBoxGPIO18 = (CheckBox)findViewById(R.id.checkBoxGPIO18);
        mCheckBoxGPIO19 = (CheckBox)findViewById(R.id.checkBoxGPIO19);
        mCheckBoxGPIO20 = (CheckBox)findViewById(R.id.checkBoxGPIO20);
        mCheckBoxGPIO21 = (CheckBox)findViewById(R.id.checkBoxGPIO21);
        mCheckBoxGPIO22 = (CheckBox)findViewById(R.id.checkBoxGPIO22);
        mCheckBoxGPIO23 = (CheckBox)findViewById(R.id.checkBoxGPIO23);
        mButtonRead = (Button)findViewById(R.id.buttonRead);
        mButtonWrite = (Button)findViewById(R.id.buttonWrite);
        mButtonConnect = (Button)findViewById(R.id.buttonConnect);
        mButtonDisconnect = (Button)findViewById(R.id.buttonDisconnect);
        mTextStatus = (TextView)findViewById(R.id.textStatus);

        mButtonConnect.setOnClickListener(buttonClickListener);
        mButtonDisconnect.setOnClickListener(buttonClickListener);
        mButtonRead.setOnClickListener(buttonClickListener);
        mButtonWrite.setOnClickListener(buttonClickListener);

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
                        mButtonConnect.setEnabled(true);
                        mButtonDisconnect.setEnabled(false);
                        mButtonRead.setEnabled(false);
                        mButtonWrite.setEnabled(false);
                        break;
                    case BLE_SRV_NOT_FOUND:
                    case BLE_NOTIF_REGISTER_FAILED:
                    case BLE_SCANNING:
                        mButtonConnect.setEnabled(false);
                        mButtonDisconnect.setEnabled(true);
                        mButtonRead.setEnabled(false);
                        mButtonWrite.setEnabled(false);
                        break;
                    case BLE_CONNECTED:
                        mButtonConnect.setEnabled(false);
                        mButtonDisconnect.setEnabled(true);
                        mButtonRead.setEnabled(true);
                        mButtonWrite.setEnabled(true);
                        break;
                    case BLE_READ:
                        mCheckBoxGPIO16.setChecked((mValue & 0x01) != 0);
                        mCheckBoxGPIO17.setChecked((mValue & 0x02) != 0);
                        mCheckBoxGPIO18.setChecked((mValue & 0x04) != 0);
                        mCheckBoxGPIO19.setChecked((mValue & 0x08) != 0);
                        mCheckBoxGPIO20.setChecked((mValue & 0x10) != 0);
                        mCheckBoxGPIO21.setChecked((mValue & 0x20) != 0);
                        mCheckBoxGPIO22.setChecked((mValue & 0x40) != 0);
                        mCheckBoxGPIO23.setChecked((mValue & 0x80) != 0);
                        break;
                    case BLE_WRITE:
                        break;
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        setStatus(AppState.INIT);

        mCheckBoxGPIO16.setChecked(false);
        mCheckBoxGPIO17.setChecked(false);
        mCheckBoxGPIO18.setChecked(false);
        mCheckBoxGPIO19.setChecked(false);
        mCheckBoxGPIO20.setChecked(false);
        mCheckBoxGPIO21.setChecked(false);
        mCheckBoxGPIO22.setChecked(false);
        mCheckBoxGPIO23.setChecked(false);

        mButtonRead.setEnabled(false);
        mButtonWrite.setEnabled(false);
        mButtonDisconnect.setEnabled(false);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
        disconnectBLE();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /* Event handler */
    View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.buttonRead:
                    mGatt.readCharacteristic(mCharacteristic);
                    break;
                case R.id.buttonWrite:
                    byte val[] = new byte[1];
                    mValue &= ~0xf0;
                    mValue |= mCheckBoxGPIO20.isChecked() ? 0x10 : 0x00;
                    mValue |= mCheckBoxGPIO21.isChecked() ? 0x20 : 0x00;
                    mValue |= mCheckBoxGPIO22.isChecked() ? 0x40 : 0x00;
                    mValue |= mCheckBoxGPIO23.isChecked() ? 0x80 : 0x00;
                    val[0] = mValue;
                    mCharacteristic.setValue(val);
                    mGatt.writeCharacteristic(mCharacteristic);
                    break;
                case R.id.buttonConnect:
                    connectBLE();
                    break;
                case R.id.buttonDisconnect:
                    disconnectBLE();
                    break;
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
            BluetoothGattService service = gatt.getService(UUID.fromString(UUID_SERVICE_GPIOS));
            if (service == null) {
                //サービスが見つからない
                setStatus(AppState.BLE_SRV_NOT_FOUND);
            } else {
                //サービスが見つかった
                setStatus(AppState.BLE_SRV_FOUND);
                mCharacteristic = service.getCharacteristic(UUID.fromString(UUID_CHARACTERISTIC_VALUE));
                if (mCharacteristic == null) {
                    //Characteristicが見つからない
                    setStatus(AppState.BLE_CHARACTERISTIC_NOT_FOUND);
                    return;
                }
            }
            mGatt = gatt;
            setStatus(AppState.BLE_CONNECTED);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (UUID_CHARACTERISTIC_VALUE.equals(characteristic.getUuid().toString())) {
                    mValue = characteristic.getValue()[0];
                    setStatus(AppState.BLE_READ);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (UUID_CHARACTERISTIC_VALUE.equals(characteristic.getUuid().toString())) {
                        setStatus(AppState.BLE_WRITE);
                    }
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
            mCharacteristic = null;

            setStatus(AppState.BLE_CLOSED);
        }
    }
}



