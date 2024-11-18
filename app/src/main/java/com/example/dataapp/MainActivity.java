package com.example.dataapp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import android.graphics.Color;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1;
    private static final String PREFS_NAME = "BluetoothData";
    private static final String KEY_LAST_DATA = "last_data";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_DATA_HISTORY = "data_history";
    private static final int MAX_HISTORY_SIZE = 100; // Número máximo de registros a guardar

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private InputStream inputStream;
    private OutputStream outputStream;
    private SharedPreferences sharedPreferences;

    private TextView tvData;
    private TextView tvStoredData;
    private Button btnConnect;
    private Button btnViewGrap;
    private TextView tvAverage;

    private final String DEVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB";


    @SuppressLint("MissingInflatedId")
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

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        tvData = findViewById(R.id.tvData);
        tvStoredData = findViewById(R.id.tvStoredData);
        tvAverage = findViewById(R.id.tvAverage);
        btnConnect = findViewById(R.id.btnConnect);
        btnViewGrap = findViewById(R.id.btnViewGrap);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Cargar y mostrar los últimos datos guardados
        loadLastStoredData();

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
        btnViewGrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Aquí puedes implementar la lógica para mostrar la gráfica
                // usando los datos almacenados en SharedPreferences
                showGraph();
            }
        });
        updateAverage();
    }

    private void loadLastStoredData() {
        String lastData = sharedPreferences.getString(KEY_LAST_DATA, "No hay datos guardados");
        String timestamp = sharedPreferences.getString(KEY_TIMESTAMP, "");
        if (!timestamp.isEmpty()) {
            tvStoredData.setText(String.format("Último dato: %s\nRecibido: %s", lastData, timestamp));
        } else {
            tvStoredData.setText("No hay datos guardados");
        }
    }

    private void updateAverage() {
        try {
            JSONArray history = new JSONArray(sharedPreferences.getString(KEY_DATA_HISTORY, "[]"));
            if (history.length() > 0) {
                double sum = 0;
                int count = 0;

                for (int i = 0; i < history.length(); i++) {
                    JSONArray record = history.getJSONArray(i);
                    try {
                        // Asumimos que el dato está en la posición 1 del array
                        double value = Double.parseDouble(record.getString(1));
                        sum += value;
                        count++;
                    } catch (NumberFormatException e) {
                        // Ignorar valores que no se puedan convertir a número
                        continue;
                    }
                }

                if (count > 0) {
                    double average = sum / count;
                    int finalCount = count;
                    runOnUiThread(() -> {
                        tvAverage.setText(String.format(Locale.getDefault(),
                                "Promedio: %.2f (%d muestras)", average, finalCount));
                    });
                } else {
                    runOnUiThread(() -> {
                        tvAverage.setText("No hay datos numéricos válidos");
                    });
                }
            } else {
                runOnUiThread(() -> {
                    tvAverage.setText("No hay datos para promediar");
                });
            }
        } catch (JSONException e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                tvAverage.setText("Error al calcular el promedio");
            });
        }
    }

    private void saveDataToPreferences(String data) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Obtener timestamp actual
        String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date());

        // Guardar último dato
        editor.putString(KEY_LAST_DATA, data);
        editor.putString(KEY_TIMESTAMP, timestamp);

        // Guardar en historial
        try {
            JSONArray history = new JSONArray(sharedPreferences.getString(KEY_DATA_HISTORY, "[]"));

            // Crear nuevo registro
            JSONArray newRecord = new JSONArray();
            newRecord.put(timestamp);
            newRecord.put(data);

            // Mantener solo los últimos MAX_HISTORY_SIZE registros
            if (history.length() >= MAX_HISTORY_SIZE) {
                JSONArray tempArray = new JSONArray();
                for (int i = 1; i < history.length(); i++) {
                    tempArray.put(history.get(i));
                }
                history = tempArray;
            }

            history.put(newRecord);
            editor.putString(KEY_DATA_HISTORY, history.toString());
            editor.apply();

            // Actualizar la vista con los datos guardados
            tvStoredData.setText(String.format("Último dato: %s\nRecibido: %s", data, timestamp));

            // Actualizar el promedio
            updateAverage();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showGraph() {
        try {
            JSONArray history = new JSONArray(sharedPreferences.getString(KEY_DATA_HISTORY, "[]"));
            LineChart chart = findViewById(R.id.chart);

            List<Entry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (int i = 0; i < history.length(); i++) {
                JSONArray record = history.getJSONArray(i);
                try {
                    double value = Double.parseDouble(record.getString(1));
                    entries.add(new Entry(i, (float) value));
                    labels.add(record.getString(0)); // Timestamp
                } catch (NumberFormatException e) {
                    continue;
                }
            }

            LineDataSet dataSet = new LineDataSet(entries, "Lecturas de pH");
            dataSet.setColor(Color.BLUE);
            dataSet.setValueTextColor(Color.RED);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            // Configurar ejes
            XAxis xAxis = chart.getXAxis();
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

            chart.invalidate(); // Refrescar gráfica
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void startReadingData() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            try {
                while ((bytes = inputStream.read(buffer)) > 0) {
                    String receivedData = new String(buffer, 0, bytes).trim();

                    // Validar formato de datos para pH
                    try {
                        // Extraer valor numérico usando expresión regular
                        String phString = receivedData.replaceAll("[^0-9.]", "");
                        double phValue = Double.parseDouble(phString);

                        // Verificar rango válido de pH (0-14)
                        if (phValue >= 0 && phValue <= 14) {
                            runOnUiThread(() -> {
                                tvData.setText("pH: " + phValue);
                                saveDataToPreferences(String.valueOf(phValue));

                                // Añadir color según el pH
                                if (phValue < 6.5) {
                                    tvData.setTextColor(Color.RED); // Ácido
                                } else if (phValue > 8.5) {
                                    tvData.setTextColor(Color.BLUE); // Básico
                                } else {
                                    tvData.setTextColor(Color.GREEN); // Neutro
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        // Manejar datos no numéricos
                        runOnUiThread(() -> tvData.setText("Dato inválido: " + receivedData));
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> tvData.setText("Error leyendo datos: " + e.getMessage()));
            }
        }).start();
    }


    private void checkWaterQuality(double phValue) {
        String qualityMessage;

        if (phValue < 6.5) {
            qualityMessage = "Agua ácida - Puede ser corrosiva";
        } else if (phValue > 8.5) {
            qualityMessage = "Agua muy básica - Posible contaminación";
        } else if (phValue >= 6.5 && phValue <= 8.5) {
            qualityMessage = "Agua dentro del rango óptimo";
        } else {
            qualityMessage = "Dato de pH no válido";
        }

        // Mostrar mensaje de calidad
        runOnUiThread(() -> {
            TextView tvQuality = findViewById(R.id.tvQualityMessage);
            tvQuality.setText(qualityMessage);
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
            String deviceName = "COCO77"; // Cambia este nombre según tu módulo
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

