package com.monitorwhatsapp

import android.util.Log
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.monitorwhatsapp.api.ApiClient
import com.monitorwhatsapp.databinding.ActivityMainBinding
import com.monitorwhatsapp.models.DeviceRegistration
import com.monitorwhatsapp.models.LoginRequest
import com.monitorwhatsapp.services.MonitoringService
import com.monitorwhatsapp.utils.TokenManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenManager: TokenManager

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startMonitoring()
        } else {
            Toast.makeText(this, "Permissões necessárias negadas", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        setupListeners()

        // Verificar se já está logado
        if (tokenManager.isLoggedIn()) {
            Log.d("MainActivity", "Usuário já logado, Device ID: ${tokenManager.getDeviceId()}")
            if (tokenManager.getDeviceId() != null) {
                checkPermissionsAndStart()
            } else {
                // Se tem token mas não tem deviceId, registra novamente
                lifecycleScope.launch {  // ← ADICIONE ESTA LINHA
                    registerDevice()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        try {
            // Parar o serviço
            stopService(Intent(this, MonitoringService::class.java))

            // Limpar dados
            tokenManager.logout()

            // Limpar campos com try-catch individual
            try { binding.etEmail.text = null } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao limpar email: ${e.message}")
            }

            try { binding.etPassword.text = null } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao limpar senha: ${e.message}")
            }

            try {
                binding.statusText.text = "Deslogado"
                binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao atualizar status: ${e.message}")
            }

            Toast.makeText(this, "Logout realizado!", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "✅ Logout realizado")

        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Erro geral no logout: ${e.message}")
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                login(email, password)
            } else {
                Toast.makeText(this, "Preencha email e senha", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun login(email: String, password: String) {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "📤 Tentando login com: $email")

                val apiService = ApiClient.create(tokenManager)
                val response = apiService.login(LoginRequest(email, password))

                Log.d("MainActivity", "📥 Resposta código: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()

                    // Verificação: corpo da resposta não pode ser null
                    if (body == null) {
                        Log.e("MainActivity", "❌ Corpo da resposta é null")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Erro: resposta vazia do servidor",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    // Verificação: access token não pode ser null ou vazio
                    if (body.access.isNullOrEmpty()) {
                        Log.e("MainActivity", "❌ Access token é null ou vazio")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Erro: token não recebido",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    // Verificação: refresh token não pode ser null ou vazio
                    if (body.refreshToken.isNullOrEmpty()) {
                        Log.e("MainActivity", "❌ Refresh token é null ou vazio")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Erro: refresh token não recebido",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    // Salvar tokens
                    tokenManager.saveTokens(body.access, body.refreshToken)

                    Log.d("MainActivity", "✅ Login sucesso! Token: ${body.access.substring(0, 20)}...")

                    // Registrar dispositivo
                    registerDevice()

                } else {
                    // Tratamento de erro HTTP
                    val errorBody = response.errorBody()?.string()
                    Log.e("MainActivity", "❌ Erro ${response.code()}: $errorBody")

                    val errorMessage = when (response.code()) {
                        400 -> "Dados inválidos"
                        401 -> "Email ou senha incorretos"
                        403 -> "Acesso negado"
                        404 -> "Serviço não encontrado"
                        500 -> "Erro interno do servidor"
                        else -> "Erro ${response.code()}"
                    }

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            errorMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                // Tratamento de exceções de rede
                Log.e("MainActivity", "❌ Exceção: ${e.message}")
                e.printStackTrace()

                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Sem conexão com a internet"
                    is java.net.SocketTimeoutException -> "Tempo limite excedido"
                    else -> "Erro: ${e.message}"
                }

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun registerDevice() {
        try {
            Log.d("MainActivity", "📱 Registrando dispositivo...")

            // Verificar se tem token antes de tentar registrar
            if (!tokenManager.isLoggedIn()) {
                Log.e("MainActivity", "❌ Usuário não está logado")
                return
            }

            val apiService = ApiClient.create(tokenManager)
            val deviceInfo = DeviceRegistration.fromContext(this)

            Log.d("MainActivity", "📱 Device info: $deviceInfo")

            val response = apiService.registerDevice(deviceInfo)

            if (response.isSuccessful) {
                val device = response.body()

                // Verificação: resposta não pode ser null
                if (device == null) {
                    Log.e("MainActivity", "❌ Resposta de registro é null")
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Erro: resposta vazia do servidor",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                }

                // Verificação: deviceId não pode ser null ou vazio
                if (device.deviceId.isNullOrEmpty()) {
                    Log.e("MainActivity", "❌ Device ID é null ou vazio")
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Erro: ID do dispositivo não recebido",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                }

                // Salvar device ID
                tokenManager.saveDeviceId(device.deviceId)
                Log.d("MainActivity", "✅ Dispositivo registrado! Device ID: ${device.deviceId}")

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Dispositivo registrado!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Verificar permissões e iniciar monitoramento
                    checkPermissionsAndStart()
                }

            } else {
                // Tratamento de erro HTTP
                val errorBody = response.errorBody()?.string()
                Log.e("MainActivity", "❌ Erro ao registrar dispositivo: Código ${response.code()}")
                Log.e("MainActivity", "❌ Detalhes: $errorBody")

                when (response.code()) {
                    400 -> {
                        // Verificar se o erro é porque o dispositivo já existe
                        if (errorBody?.contains("já existe") == true ||
                            errorBody?.contains("already exists") == true) {

                            Log.d("MainActivity", "ℹ️ Dispositivo já existe - usando ID existente")

                            // Tentar extrair o device_id da mensagem de erro?
                            // Infelizmente não vem no erro, então precisamos gerar o mesmo ID

                            // O device_id é gerado a partir do Android ID
                            // Vamos gerar novamente e salvar
                            val existingDeviceId = deviceInfo.deviceId
                            tokenManager.saveDeviceId(existingDeviceId)

                            Log.d("MainActivity", "✅ Device ID recuperado: $existingDeviceId")

                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Dispositivo já registrado!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                checkPermissionsAndStart()
                            }

                        } else {
                            // Outro erro 400
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Dados do dispositivo inválidos",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    401 -> {
                        Log.e("MainActivity", "❌ Token expirado - fazendo logout")
                        tokenManager.logout()
                        runOnUiThread {
                            binding.etEmail.text?.clear()
                            binding.etPassword.text?.clear()
                            binding.statusText.text = "Sessão expirada - faça login novamente"
                        }
                    }

                    403 -> {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Sem permissão para registrar dispositivo",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    else -> {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Erro ao registrar dispositivo (${response.code()})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

        } catch (e: Exception) {
            // Tratamento de exceções de rede
            Log.e("MainActivity", "❌ Exceção ao registrar dispositivo: ${e.message}")
            e.printStackTrace()

            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Sem conexão com a internet"
                is java.net.SocketTimeoutException -> "Tempo limite excedido"
                else -> "Erro: ${e.message}"
            }

            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    errorMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions.launch(missingPermissions.toTypedArray())
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        val intent = Intent(this, MonitoringService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        binding.statusText.text = "Monitorando..."
        binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }
}