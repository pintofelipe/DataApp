package com.example.dataapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


//permisos

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private InputStream inputStream;
    private OutputStream outputStream;

    private TextView tvData;
    private Button btnConnect;



    // UUID estándar para Bluetooth serial


    private final String DEVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvData = findViewById(R.id.tvData);
        btnConnect = findViewById(R.id.btnConnect);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        // Verificar permisos
        if (!checkPermissions()) {
            requestPermissions();
        }




        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (checkPermissions()) {
                    connectBluetooth();
                } else {
                    tvData.setText("Permisos no concedidos");
                }

            }
        });
    }


    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }


    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                REQUEST_PERMISSIONS);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvData.setText("Permisos concedidos");
            } else {
                tvData.setText("Permisos denegados");
            }
        }
    }




    private void connectBluetooth() {
        try {
            // Verificar permisos antes de continuar
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                tvData.setText("Permisos Bluetooth no concedidos");
                return;
            }

            // Activar Bluetooth si está desactivado
            if (!bluetoothAdapter.isEnabled()) {
                tvData.setText("Activando Bluetooth...");
                bluetoothAdapter.enable();
                Thread.sleep(2000); // Esperar un momento para que Bluetooth se active
            }

            // Buscar el dispositivo Bluetooth emparejado (modificar según el nombre real)
            String deviceName = "HC-05"; // Cambia este nombre según tu módulo
            bluetoothDevice = null;
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (device.getName().equals(deviceName)) {
                    bluetoothDevice = device;
                    break;
                }
            }

            // Validar si el dispositivo fue encontrado
            if (bluetoothDevice == null) {
                tvData.setText("Dispositivo " + deviceName + " no encontrado");
                return;
            }

            // Crear el socket Bluetooth y conectar
            tvData.setText("Conectando a " + bluetoothDevice.getName() + "...");
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString(DEVICE_UUID));
            bluetoothSocket.connect();

            // Inicializar los flujos de entrada y salida
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();

            // Confirmar conexión
            tvData.setText("Conectado a " + bluetoothDevice.getName());
            startReadingData();

        } catch (SecurityException e) {
            tvData.setText("Permiso requerido para Bluetooth no concedido");
            e.printStackTrace();
        } catch (IOException e) {
            tvData.setText("Error al conectar al dispositivo: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            tvData.setText("Operación interrumpida: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            tvData.setText("Error desconocido: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void startReadingData() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            try {
                while ((bytes = inputStream.read(buffer)) > 0) {
                    String receivedData = new String(buffer, 0, bytes);
                    runOnUiThread(() -> tvData.setText("Datos: " + receivedData));
                }
            } catch (Exception e) {
                runOnUiThread(() -> tvData.setText("Error leyendo datos"));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}











































