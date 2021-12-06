package kth.jjve.moveable;
/*
This activity is the home screen of the app
The homescreen allows the user to connect to a bluetooth device
The homescreen will also show data when its there

Names: Jitse van Esch & Elisa Perini
Date: 12.12.21
 */

import static kth.jjve.moveable.utilities.VisibilityChanger.setViewVisibility;
import kth.jjve.moveable.utilities.TypeConverter;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;
import java.util.UUID;

import kth.jjve.moveable.dialogs.BluetoothActivity;
import kth.jjve.moveable.dialogs.SaveDialog;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, SaveDialog.SaveDialogListener {

    /*--------------------------- VIEW ----------------------*/
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView bluetoothSettings;
    private ImageView bluetoothDisabled;
    private ImageView bluetoothEnabled;

    private TextView tempTimeView, tempAccView;

    /*--------------------------- LOG -----------------------*/
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    /*------------------------- PREFS ---------------------*/
    private String IMU_COMMAND = "Meas/Acc/13"; //Todo: get the IMU command from the preferences

    /*------------------------- BLUETOOTH ---------------------*/
    private BluetoothDevice mSelectedDevice;
    private boolean mBluetoothConnected;
    private final byte MOVESENSE_REQ = 1, MOVESENSE_RES = 2, REQUEST_ID = 99;
    private BluetoothGatt mBluetoothGatt;

//    public final UUID MOVESENSE_20_SERVICE = UUID.fromString(getResources().getString(R.string.uuidMS2_0Service));
//    public final UUID MOVESENSE_20_COMMAND_CHAR = UUID.fromString(getResources().getString(R.string.uuidMS2_0CommandChar));
//    public final UUID MOVESENSE_20_DATA_CHAR = UUID.fromString(getResources().getString(R.string.uuidMS2_0DataChar));
//    public final UUID CLIENT_CHAR_CONFIG = UUID.fromString(getResources().getString(R.string.uuidClientCharConfig));

    public static final UUID MOVESENSE_20_SERVICE =
            UUID.fromString("34802252-7185-4d5d-b431-630e7050e8f0");
    public static final UUID MOVESENSE_20_COMMAND_CHAR =
            UUID.fromString("34800001-7185-4d5d-b431-630e7050e8f0");
    public static final UUID MOVESENSE_20_DATA_CHAR =
            UUID.fromString("34800002-7185-4d5d-b431-630e7050e8f0");
    // UUID for the client characteristic, which is necessary for notifications
    public static final UUID CLIENT_CHAR_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /*----------------------- HANDLER -----------------------*/
    private Handler mHandler;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*---------------------- Hooks ----------------------*/
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        bluetoothSettings = findViewById(R.id.iv_main_bluetoothSearch);
        bluetoothDisabled = findViewById(R.id.iv_main_bluetoothDisabled);
        bluetoothEnabled = findViewById(R.id.iv_main_bluetoothEnabled);
        Button buttonRecord = findViewById(R.id.button_main_record);
        Button buttonSave = findViewById(R.id.button_main_stop);
        ImageView graph = findViewById(R.id.iv_main_datagraph);
        tempTimeView = findViewById(R.id.temp_tv_main_Time); //Todo: when graph is done, these can disappear
        tempAccView = findViewById(R.id.temp_tv_main_Acc);

        /*---------------------- Settings ----------------------*/

        /*--------------------- Tool bar --------------------*/
        setSupportActionBar(toolbar);

        /*---------------Navigation drawer menu -------------*/
        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this,drawerLayout, toolbar,
                R.string.nav_open, R.string.nav_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_home);

        /*-------------- On Click Listener ------------------*/
        bluetoothSettings.setOnClickListener(this::onClick);
        bluetoothDisabled.setOnClickListener(this::onClick);
        bluetoothEnabled.setOnClickListener(this::onClick);
        buttonRecord.setOnClickListener(v -> {
            Toast.makeText(getApplicationContext(), "Recording has started", Toast.LENGTH_SHORT).show();
            acquireData();
            Log.i(LOG_TAG, "Recording has started");
        });
        buttonSave.setOnClickListener(v -> {
            //Todo: add stuff to stop recording here
            stopData();
            graph.setVisibility(View.INVISIBLE);
            Toast.makeText(getApplicationContext(), "Recording has stopped", Toast.LENGTH_SHORT).show();
            openSaveDialog();
            Log.i(LOG_TAG, "Recording has stopped and is being saved");
        });
    }

    @Override
    protected void onResume(){
        super.onResume();
        navigationView.setCheckedItem(R.id.nav_home);
        Log.i(LOG_TAG, "onResume happens");
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopData();
    }

    @Override
    public void onBackPressed() {
        if(drawerLayout.isDrawerOpen(GravityCompat.START)){
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.nav_settings){
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void onClick(View v) {
        Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);
        activityLauncher.launch(intent);
    }

    ActivityResultLauncher<Intent> activityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == 69){
                        Intent intent = result.getData();
                        if (intent != null){
                            //Extract data
                            mSelectedDevice = intent.getParcelableExtra(BluetoothActivity.SELECTED_DEVICE);
                            mBluetoothConnected = mSelectedDevice != null;
                            setViewVisibility(bluetoothEnabled, bluetoothDisabled, bluetoothSettings);
                        }
                    } else {
                        mBluetoothConnected = false;
                    }
                }
            });

    private void acquireData(){
        if (mBluetoothConnected){
            mBluetoothGatt = mSelectedDevice.connectGatt(this, false, mBtGattCallback);
        } else{
            //Todo: method for internal sensor
        }
    }

    private void stopData(){
        if(mBluetoothGatt != null){
            mBluetoothGatt.disconnect();
            try{
                mBluetoothGatt.close();
            }catch (Exception e){
                Log.i(LOG_TAG, "Exception is " + e);
                e.printStackTrace();
            }
        }
    }

    /*||||||||||| BLUETOOTH STUFF |||||||||||*/
    private final BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED){
                mBluetoothGatt = gatt;
                mHandler.post(() -> setViewVisibility(bluetoothEnabled, bluetoothDisabled, bluetoothSettings));
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                mBluetoothGatt = null;
                mHandler.post(() -> setViewVisibility(bluetoothDisabled, bluetoothEnabled, bluetoothSettings));
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS){
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service: services){
                    Log.i(LOG_TAG, service.getUuid().toString());
                }

                BluetoothGattService movesenseService = gatt.getService(MOVESENSE_20_SERVICE);
                if (movesenseService != null){
                    List<BluetoothGattCharacteristic> characteristics =
                            movesenseService.getCharacteristics();
                    for (BluetoothGattCharacteristic chara : characteristics){
                        Log.i(LOG_TAG, chara.getUuid().toString());
                    }

                    BluetoothGattCharacteristic commandChar =
                            movesenseService.getCharacteristic(MOVESENSE_20_COMMAND_CHAR);
                    byte[] command =
                            TypeConverter.stringToAsciiArray(REQUEST_ID, IMU_COMMAND);
                    commandChar.setValue(command);
                    boolean wasSuccessfull = mBluetoothGatt.writeCharacteristic(commandChar);
                    Log.i("writeCharacteristic", "was succesfull = " + wasSuccessfull);
                } else {
                    Log.i(LOG_TAG, "service not found");
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicWrite " + characteristic.getUuid().toString());
            BluetoothGattService movesenseService = gatt.getService(MOVESENSE_20_SERVICE);
            BluetoothGattCharacteristic dataCharacteristic =
                    movesenseService.getCharacteristic(MOVESENSE_20_DATA_CHAR);
            boolean success = gatt.setCharacteristicNotification(dataCharacteristic, true);
            if (success) {
                Log.i(LOG_TAG, "setCharactNotification success");
                BluetoothGattDescriptor descriptor =
                        dataCharacteristic.getDescriptor(CLIENT_CHAR_CONFIG);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            } else {
                Log.i(LOG_TAG, "setCharacteristicNotification failed");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicRead " + characteristic.getUuid().toString());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (MOVESENSE_20_DATA_CHAR.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data[0] == MOVESENSE_RES && data[1] == REQUEST_ID) {
                    // NB! use length of the array to determine the number of values in this
                    // "packet", the number of values in the packet depends on the frequency set(!)
                    int len = data.length;

                    // parse and interpret the data, ...
                    int time = TypeConverter.fourBytesToInt(data, 2);
                    float accX = TypeConverter.fourBytesToFloat(data, 6);
                    float accY = TypeConverter.fourBytesToFloat(data, 10);
                    float accZ = TypeConverter.fourBytesToFloat(data, 14);

                    // Todo: filter the data (one filter function)
                    // Todo: add filtered data to a list
                    // Todo: save the list (expanding)
                    // Todo: save the data to a list with a fixed length to display in graph

                    String accStr = "" + accX + " " + accY + " " + accZ;
                    Log.i("acc data", "" + time + " " + accStr);

                    @SuppressLint("DefaultLocale") final String viewDataStr = String.format("%.2f, %.2f, %.2f", accX, accY, accZ);
                    mHandler.post(() -> {
                        String timeString = time + " ms";
                        tempTimeView.setText(timeString);
                        tempTimeView.setVisibility(View.VISIBLE);
                        tempAccView.setText(viewDataStr);
                        tempAccView.setVisibility(View.VISIBLE);
                    });
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i(LOG_TAG, "onDescriptorWrite, status " + status);

            if (CLIENT_CHAR_CONFIG.equals(descriptor.getUuid()))
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // if success, we should receive data in onCharacteristicChanged
                    mHandler.post(() -> {
                        //Todo: here we could update something to the ui, but we could also not
                    });
                }
        }

    };

    /*||||||||||| SAVE DIALOG |||||||||||*/

    public void openSaveDialog() {
        // Method that will open the dialog for saving
        SaveDialog saveDialog = new SaveDialog();
        saveDialog.show(getSupportFragmentManager(), "save dialog");
    }

    @Override
    public void applyName(String name) {
        // Method to get the wanted filename from the dialog into the activity
        // Todo: save the data here using the name somehow
    }

    @Override
    public void savingCancelled() {
        Toast.makeText(getApplicationContext(), "saving cancelled", Toast.LENGTH_SHORT).show();
    }
}