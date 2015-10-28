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
import android.os.Build;
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

public class MotionSensorActivity extends AppCompatActivity {
    //BLEスキャンタイムアウト
    private static final int SCAN_TIMEOUT = 20000;
    //接続対象のデバイス名
    private static final String DEVICE_NAME = "HyouRowGan00";
    /* UUIDs */
    //BlueNinja Motion sensor Service
    private static final String UUID_SERVICE_MSS = "00050000-6727-11e5-988e-f07959ddcdfb";
    //Motion sensor values.
    private static final String UUID_CHARACTERISTIC_VALUE = "00050001-6727-11e5-988e-f07959ddcdfb";
    //キャラクタリスティック設定UUID
    private static final String UUID_CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    //ログのTAG
    private static final String LOG_TAG = "HRG_MSS";

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

    private LineGraph mGraphGyro;
    private LineGraph mGraphAccel;
    private LineGraph mGraphMagm;
    private Button mButtonConnect;
    private Button mButtonDisconnect;
    private CheckBox mCheckBoxActive;
    private TextView mTextStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion_sensor);

        /* Bluetooth関連の初期化 */
        mBtManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();
        if ((mBtAdapter == null) || !mBtAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Warning: Bluetooth Disabled.", Toast.LENGTH_SHORT).show();
            finish();
        }

        mGraphGyro = (LineGraph)findViewById(R.id.graphGyro);
        mGraphAccel = (LineGraph)findViewById(R.id.graphAccel);
        mGraphMagm = (LineGraph)findViewById(R.id.graphMagm);
        mButtonConnect = (Button)findViewById(R.id.buttonConnect);
        mButtonDisconnect = (Button)findViewById(R.id.buttonDisconnect);
        mCheckBoxActive = (CheckBox)findViewById(R.id.checkBoxActive);
        mTextStatus = (TextView)findViewById(R.id.textStatus);

        mButtonConnect.setOnClickListener(buttonClickLinstener);
        mButtonDisconnect.setOnClickListener(buttonClickLinstener);

        mCheckBoxActive.setOnClickListener(checkboxClickListener);

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
                        updateGraph();
                        break;
                }
            }
        };
    }

    private void updateGraph() {
        short grx, gry, grz, arx, ary, arz, mrx, mry, mrz;
        ArrayList<LinePoint> gx_points = mGraphGyro.getLine(0).getPoints();
        ArrayList<LinePoint> gy_points = mGraphGyro.getLine(1).getPoints();
        ArrayList<LinePoint> gz_points = mGraphGyro.getLine(2).getPoints();
        ArrayList<LinePoint> ax_points = mGraphAccel.getLine(0).getPoints();
        ArrayList<LinePoint> ay_points = mGraphAccel.getLine(1).getPoints();
        ArrayList<LinePoint> az_points = mGraphAccel.getLine(2).getPoints();
        ArrayList<LinePoint> mx_points = mGraphMagm.getLine(0).getPoints();
        ArrayList<LinePoint> my_points = mGraphMagm.getLine(1).getPoints();
        ArrayList<LinePoint> mz_points = mGraphMagm.getLine(2).getPoints();

        int recv_len;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            recv_len = mRecvValue.length;
        } else {
            recv_len = 18;
        }
        for (int offset = 0; offset < recv_len; offset += 18) {
            /* Convert byte array to values. */
            ByteBuffer buff;
            //Gyro X
            buff = ByteBuffer.wrap(mRecvValue, offset + 0, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            grx = buff.getShort();
            //Gyro Y
            buff = ByteBuffer.wrap(mRecvValue, offset + 2, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            gry = buff.getShort();
            //Gyro Z
            buff = ByteBuffer.wrap(mRecvValue, offset + 4, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            grz = buff.getShort();
            //Accel X
            buff = ByteBuffer.wrap(mRecvValue, offset + 6, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            arx = buff.getShort();
            //Accel Y
            buff = ByteBuffer.wrap(mRecvValue, offset + 8, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            ary = buff.getShort();
            //Accel Z
            buff = ByteBuffer.wrap(mRecvValue, offset + 10, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            arz = buff.getShort();
            //Magneto X
            buff = ByteBuffer.wrap(mRecvValue, offset + 12, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            mrx = buff.getShort();
            //Magneto Y
            buff = ByteBuffer.wrap(mRecvValue, offset + 14, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            mry = buff.getShort();
            //Magneto Z
            buff = ByteBuffer.wrap(mRecvValue, offset + 16, 2);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            mrz = buff.getShort();

            /* Add points */
            //Gyro
            LinePoint gx_point = new LinePoint();
            gx_point.setY((double) grx / 16.4);
            gx_points.add(gx_point);

            LinePoint gy_point = new LinePoint();
            gy_point.setY((double) gry / 16.4);
            gy_points.add(gy_point);

            LinePoint gz_point = new LinePoint();
            gz_point.setY((double) grz / 16.4);
            gz_points.add(gz_point);

            //Accel
            LinePoint ax_point = new LinePoint();
            ax_point.setY((double) arx / 2048);
            ax_points.add(ax_point);

            LinePoint ay_point = new LinePoint();
            ay_point.setY((double) ary / 2048);
            ay_points.add(ay_point);

            LinePoint az_point = new LinePoint();
            az_point.setY((double) arz / 2048);
            az_points.add(az_point);

            //Magm
            LinePoint mx_point = new LinePoint();
            mx_point.setY(mrx);
            mx_points.add(mx_point);

            LinePoint my_point = new LinePoint();
            my_point.setY(mry);
            my_points.add(my_point);

            LinePoint mz_point = new LinePoint();
            mz_point.setY(mrz);
            mz_points.add(mz_point);
        }

        int cnt_points = 20;
        chopLinePoints(gx_points, cnt_points);
        chopLinePoints(gy_points, cnt_points);
        chopLinePoints(gz_points, cnt_points);
        chopLinePoints(ax_points, cnt_points);
        chopLinePoints(ay_points, cnt_points);
        chopLinePoints(az_points, cnt_points);
        chopLinePoints(mx_points, cnt_points);
        chopLinePoints(my_points, cnt_points);
        chopLinePoints(mz_points, cnt_points);

        mGraphGyro.invalidate();
        mGraphAccel.invalidate();
        mGraphMagm.invalidate();
    }

    private void chopLinePoints(ArrayList<LinePoint> points, int cnt) {
        int size;
        size = points.size();
        if (size > cnt) {
            for (int i = 0; i < (size - cnt); i++) {
                points.remove(0);
            }
        }
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setX(i);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setStatus(AppState.INIT);

        mCheckBoxActive.setChecked(false);
        mCheckBoxActive.setEnabled(false);

        Line l;
        //Gyro
        mGraphGyro.setRangeX(0, 19);
        mGraphGyro.setRangeY(-360, 360);
        l = new Line();
        l.setColor(Color.RED);
        mGraphGyro.addLine(l);
        l = new Line();
        l.setColor(Color.GREEN);
        mGraphGyro.addLine(l);
        l = new Line();
        l.setColor(Color.BLUE);
        mGraphGyro.addLine(l);

        //Accel
        mGraphAccel.setRangeX(0, 19);
        mGraphAccel.setRangeY(-4, 4);
        l = new Line();
        l.setColor(Color.RED);
        mGraphAccel.addLine(l);
        l = new Line();
        l.setColor(Color.GREEN);
        mGraphAccel.addLine(l);
        l = new Line();
        l.setColor(Color.BLUE);
        mGraphAccel.addLine(l);

        //Magnetometer
        mGraphMagm.setRangeX(0, 19);
        mGraphMagm.setRangeY(-4095, 4095);
        l = new Line();
        l.setColor(Color.RED);
        mGraphMagm.addLine(l);
        l = new Line();
        l.setColor(Color.GREEN);
        mGraphMagm.addLine(l);
        l = new Line();
        l.setColor(Color.BLUE);
        mGraphMagm.addLine(l);

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
            BluetoothGattService service = gatt.getService(UUID.fromString(UUID_SERVICE_MSS));
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                gatt.requestMtu(40);
            }
            setStatus(AppState.BLE_CONNECTED);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //super.onCharacteristicChanged(gatt, characteristic);
            if (UUID_CHARACTERISTIC_VALUE.equals(characteristic.getUuid().toString())) {
                byte read_data[] = characteristic.getValue();
                mRecvValue = Arrays.copyOf(read_data, 36);
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
