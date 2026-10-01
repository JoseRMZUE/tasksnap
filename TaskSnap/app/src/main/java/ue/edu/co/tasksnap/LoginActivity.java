package ue.edu.co.tasksnap;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import ue.edu.co.tasksnap.data.local.db.AppDatabase;
import ue.edu.co.tasksnap.data.local.db.SeedInitializer;
import ue.edu.co.tasksnap.data.local.entity.Tecnico;
import ue.edu.co.tasksnap.repository.AuthRepository;

/**
 * Pantalla de inicio de sesión de TaskSnap (RF-01, RF-02, RF-03).
 *
 * FLUJO CORREGIDO:
 * 1. Chequeo rápido de sesión previa.
 * 2. Inflado de layout con controles bloqueados.
 * 3. Ejecución de Seed en background.
 * 4. Al terminar Seed, desbloquear controles y conectar listeners.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etUser;
    private EditText etPassword;
    private Button btnLogin;
    private ProgressBar pbLogin;
    private TextView tvLoginError;

    private AuthRepository authRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inicializar Repo
        authRepo = new AuthRepository(this);

        // 2. CHEQUEO RÁPIDO: Si hay sesión activa, saltar directo a Main.
        if (authRepo.haySesionActiva()) {
            irAMain();
            finish();
            return;
        }

        // 3. INFLAR LAYOUT PRIMERO (para tener referencias válidas)
        setContentView(R.layout.activity_login);

        // 4. ENLAZAR VISTAS AHORA QUE EL LAYOUT EXISTE
        enlazarVistas();

        // 5. ESTADO INICIAL: Bloquear interacción mientras preparamos datos
        setEstadoBloqueado(true);

        // 6. LANZAR SEED EN BACKGROUND
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                SeedInitializer.sembrarSiVacio(db);

                // Volver a UI thread para desbloquear
                runOnUiThread(() -> {
                    setEstadoBloqueado(false);

                    // Conectar lógica solo cuando está listo
                    conectarBotones();
                    configurarValidacionDinamica();

                    Toast.makeText(this, "Sistema listo. Ingresa credenciales.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    mostrarError("Error preparando datos: " + e.getMessage());
                    setEstadoBloqueado(false); // Desbloquear aunque falle para poder ver el error
                });
            }
        }).start();
    }

    /** Enlaza vistas del contrato UI v1.1. */
    private void enlazarVistas() {
        etUser = findViewById(R.id.etUser);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        pbLogin = findViewById(R.id.pbLogin);
        tvLoginError = findViewById(R.id.tvLoginError);
    }

    /** Controla el estado visual de "bloqueo" vs "listo". */
    private void setEstadoBloqueado(boolean bloqueado) {
        if (pbLogin != null) pbLogin.setVisibility(bloqueado ? View.VISIBLE : View.GONE);
        if (etUser != null) etUser.setEnabled(!bloqueado);
        if (etPassword != null) etPassword.setEnabled(!bloqueado);
        if (btnLogin != null) btnLogin.setEnabled(!bloqueado); // Siempre disabled aquí, lo gestiona TextWatcher

        // Limpiar errores previos al cambiar de estado
        if (!bloqueado && tvLoginError != null) {
            tvLoginError.setText("");
            tvLoginError.setVisibility(View.GONE);
        }
    }

    /** Asigna comportamiento al botón de ingreso. */
    private void conectarBotones() {
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> intentarLogin());
        }
    }

    /** Validación dinámica: Botón activo solo si ambos campos tienen texto. */
    private void configurarValidacionDinamica() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                actualizarEstadoBoton();
            }
        };

        if (etUser != null) etUser.addTextChangedListener(watcher);
        if (etPassword != null) etPassword.addTextChangedListener(watcher);

        // Estado inicial tras re-inflar views
        actualizarEstadoBoton();
    }

    private void actualizarEstadoBoton() {
        if (etUser == null || etPassword == null || btnLogin == null) return;

        boolean usuarioValido = etUser.getText().toString().trim().length() > 0;
        boolean passValida = etPassword.getText().toString().length() > 0;
        btnLogin.setEnabled(usuarioValido && passValida);
    }

    /** Orquesta el flujo de login delegando en AuthRepository. */
    private void intentarLogin() {
        limpiarError();
        setEstadoCargando(true);

        String usuario = etUser.getText().toString();
        String password = etPassword.getText().toString();

        authRepo.iniciarSesion(usuario, password, new AuthRepository.CallbackLogin() {
            @Override
            public void onExito(Tecnico tecnicoAutenticado) {
                setEstadoCargando(false);
                Toast.makeText(LoginActivity.this,
                        "Bienvenido, " + tecnicoAutenticado.getNombre(),
                        Toast.LENGTH_SHORT).show();

                irAMain();
                finish();
            }

            @Override
            public void onError(String mensaje) {
                setEstadoCargando(false);
                mostrarError(mensaje);
            }
        });
    }

    private void setEstadoCargando(boolean cargando) {
        if (pbLogin != null) pbLogin.setVisibility(cargando ? View.VISIBLE : View.GONE);
        if (btnLogin != null) btnLogin.setEnabled(!cargando);
        if (etUser != null) etUser.setEnabled(!cargando);
        if (etPassword != null) etPassword.setEnabled(!cargando);

        if (!cargando) {
            actualizarEstadoBoton();
        }
    }

    private void mostrarError(String mensaje) {
        if (tvLoginError != null) {
            tvLoginError.setText(mensaje);
            tvLoginError.setVisibility(View.VISIBLE);
        }
    }

    private void limpiarError() {
        if (tvLoginError != null) {
            tvLoginError.setText("");
            tvLoginError.setVisibility(View.GONE);
        }
    }

    private void irAMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}