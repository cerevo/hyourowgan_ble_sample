package com.cerevo.blueninja.hyourowgan_ble_sample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.echo.holographlibrary.Line;
import com.echo.holographlibrary.LineGraph;
import com.echo.holographlibrary.LinePoint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class AirpressureActivity extends AppCompatActivity {
    //BLEスキャンタイムアウト
    private static final int SCAN_TIMEOUT = 20000;
    //接続対象のデバイス名
    private static final String DEVICE_NAME = "HyouRowGan00";
    /* UUIDs */
    //BlueNinja Motion sensor Service
    private static final String UUID_SERVICE_APSS = "00060000-6727-11e5-988e-f07959ddcdfb";
    //Motion sensor values.
    private static final String UUID_CHARACTERISTIC_VALUE = "00060001-6727-11e5-988e-f07959ddcdfb";
    //キャラクタリスティック設定UUID
    private static final String UUID_CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    //ログのTAG
    private static final String LOG_TAG = "HRG_APSS";

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
        BLE_UPDATE_VALUE,
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

    private Handler mHandler;

    private Button mButtonConnect;
    private Button mButtonDisconnect;
    private CheckBox mCheckBoxActive;
    private LineGraph mGraphAirp;
    private LineGraph mGraphTemp;
    private TextView mTextStatus;

    private TextView mTextLatestAirp;
    private TextView mTextLatestTemp;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_airpressure);

        /* Bluetooth関連の初期化 */
        mBtManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();
        if ((mBtAdapter == null) || !mBtAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Warning: Bluetooth Disabled.", Toast.LENGTH_SHORT).show();
            finish();
        }

        mButtonConnect = (Button)findViewById(R.id.buttonConnect);
        mButtonDisconnect = (Button)findViewById(R.id.buttonDisconnect);
        mCheckBoxActive = (CheckBox)findViewById(R.id.checkBoxActive);
        mGraphAirp = (LineGraph)findViewById(R.id.graphAirp);
        mGraphTemp = (LineGraph)findViewById(R.id.graphTemp);
        mTextStatus = (TextView)findViewById(R.id.textStatus);
        mTextLatestAirp = (TextView)findViewById(R.id.textViewLatestAirp);
        mTextLatestTemp = (TextView)findViewById(R.id.textViewLatestTemp);

        mButtonConnect.setOnClickListener(buttonClickLinstener);
        mButtonDisconnect.setOnClickListener(buttonClickLinstener);

        mCheckBoxActive.setOnClickListener(checkboxClickListener);

        /* TODO */
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
                        mCheckBoxActive.setEnabled(false);
                        mCheckBoxActive.setChecked(false);
                        break;
                    case BLE_SRV_NOT_FOUND:
                    case BLE_NOTIF_REGISTER_FAILED:
                    case BLE_SCANNING:
                        mButtonConnect.setEnabled(false);
                        mButtonDisconnect.setEnabled(true);
                        mCheckBoxActive.setEnabled(false);
                        mCheckBoxActive.setChecked(false);
                        break;
                    case BLE_CONNECTED:
                    case BLE_WRITE:
                        mButtonConnect.setEnabled(false);
                        mButtonDisconnect.setEnabled(true);
                        mCheckBoxActive.setEnabled(true);
                        break;
                    case BLE_UPDATE_VALUE:
                        ArrayList<LinePoint> ap_points = mGraphAirp.getLine(0).getPoints();
                        ArrayList<LinePoint> tp_points = mGraphTemp.getLine(0).getPoints();

                        //Temperature
                        ByteBuffer buff;
                        buff = ByteBuffer.wrap(mRecvValue, 0, 2);
                        buff.order(ByteOrder.LITTLE_ENDIAN);
                        short rt = buff.getShort();
                        LinePoint rt_point = new LinePoint();
                        rt_point.setY((double) rt / 100);
                        tp_points.add(rt_point);
                        mTextLatestTemp.setText(String.format("Latest: %5.2f digC", (float)rt / 100));

                        //Airpressure
                        buff = ByteBuffer.wrap(mRecvValue, 2, 4);
                        buff.order(ByteOrder.LITTLE_ENDIAN);
                        int ra = buff.getInt();
                        LinePoint ra_point = new LinePoint();
                        ra_point.setY((double) ra / 256);
                        ap_points.add(ra_point);
                        mTextLatestAirp.setText(String.format("Latest: %7.2f hPa", (float)ra / 25600));

                        int cnt_points = 20;
                        chopLinePoints(mGraphTemp, tp_points, cnt_points);
                        chopLinePoints(mGraphAirp, ap_points, cnt_points);

                        mGraphTemp.invalidate();
                        mGraphAirp.invalidate();
                        break;
                }
            }
        };
    }
    
    private void chopLinePoints(LineGraph graph, ArrayList<LinePoint> points, int cnt) {
        int size;
        size = points.size();
        if (size > cnt) {
            for (int i = 0; i < (size - cnt); i++) {
                points.remove(0);
            }
        }
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setX(i);
            if (min > points.get(i).getY()) {
                min = points.get(i).getY();
            }
            if (max < points.get(i).getY()) {
                max = points.get(i).getY();
            }
        }
        float padding = (float)((max - min) * 2);
        graph.setRangeY(min - padding, max + padding);
    }

    @Override
    protected void onStart() {
        super.onStart();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setStatus(AppState.INIT);

        mCheckBoxActive.setChecked(false);
        mCheckBoxActive.setEnabled(false);

        Line l;
        //Airpressure
        mGraphAirp.setRangeX(0, 19);
        mGraphAirp.setRangeY(970, 1050);
        l = new Line();
        l.setColor(Color.BLUE);
        mGraphAirp.addLine(l);

        //Temperature
        mGraphTemp.setRangeX(0, 19);
        mGraphTemp.setRangeY(-10, 50);
        l = new Line();
        l.setColor(Color.RED);
        mGraphTemp.addLine(l);
    }

    @Override
    protected void onStop() {
        super.onStop();
        disconnectBLE();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /* Event handler */
    private View.OnClickListener buttonClickLinstener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.buttonConnect:
                    connectBLE();
                    break;
                case R.id.buttonDisconnect:
                    switch (getStats()) {
                        case BLE_SCANNING:
                            mBtAdapter.stopLeScan(mLeScanCallback);
                            setStatus(AppState.BLE_CLOSED);
                            break;
                        default:
                            disconnectBLE();
                            break;
                    }
                    break;
            }
        }
    };

    private View.OnClickListener checkboxClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            CheckBox chk = (CheckBox)v;
            if (chk.isChecked()) {
                enableBLENotification();
            } else {
                disableBLENotification();
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
            BluetoothGattService service = gatt.getService(UUID.fromString(UUID_SERVICE_APSS));
            if (service == null) {
                //サービスが見つからない
                setStatus(AppState.BLE_SRV_NOT_FOUND);
                return;
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
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //super.onCharacteristicChanged(gatt, characteristic);
            if (UUID_CHARACTERISTIC_VALUE.equals(characteristic.getUuid().toString())) {
                byte read_data[] = characteristic.getValue();
                mRecvValue = Arrays.copyOf(read_data, 6);
                setStatus(AppState.BLE_UPDATE_VALUE);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(LOG_TAG, String.format("mtu=%d", mtu));
            //super.onMtuChanged(gatt, mtu, status);
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
            disableBLENotification();

            mBtGatt.close();
            mBtGatt = null;
            mCharacteristic = null;

            setStatus(AppState.BLE_CLOSED);
        }
    }

    private void enableBLENotification()
    {
        if (mGatt.setCharacteristicNotification(mCharacteristic, true)) {
            BluetoothGattDescriptor desc = mCharacteristic.getDescriptor(UUID.fromString(UUID_CLIENT_CHARACTERISTIC_CONFIG));
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (mGatt.writeDescriptor(desc)) {
                setStatus(AppState.BLE_NOTIF_REGISTERD);
                return;
            }
        }
        setStatus(AppState.BLE_NOTIF_REGISTER_FAILED);
    }

    private void disableBLENotification()
    {
        BluetoothGattDescriptor desc = mCharacteristic.getDescriptor(UUID.fromString(UUID_CLIENT_CHARACTERISTIC_CONFIG));
        desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        if (mGatt.writeDescriptor(desc)) {
            if (mGatt.setCharacteristicNotification(mCharacteristic, false)) {
                setStatus(AppState.BLE_NOTIF_REGISTERD);
                return;
            }
        }
        setStatus(AppState.BLE_NOTIF_REGISTER_FAILED);
    }
}
