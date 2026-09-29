package com.whatsup.automation.presentation.screens.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.domain.model.CountryCode
import com.whatsup.automation.domain.model.CountryCodeRepository
import com.whatsup.automation.service.ServicePermissionManager
import com.whatsup.automation.service.WhatsAppForegroundService
import com.whatsup.automation.data.security.SessionKeystore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PairingMethod {
    PAIRING_CODE,
    QR_CODE,
    DIRECT_SYSTEM
}

data class PairingUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedMethod: PairingMethod = PairingMethod.PAIRING_CODE,
    val qrCodePayload: String? = null,
    val pairingCode: String? = null,
    val codeCountdownSeconds: Int = 0,
    val isNotificationListenerActive: Boolean = false,
    val isAccessibilityActive: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val selectedCountry: CountryCode = CountryCodeRepository.defaultCountry,
    val nationalPhoneNumber: String = "",
    val phoneNumberInput: String = "",
    val isLoading: Boolean = false,
    val isPairedSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val whatsAppEngine: WhatsAppEngine,
    private val sessionKeystore: SessionKeystore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    init {
        // فحص الصلاحيات عند بدء الشاشة
        checkAllPermissions(appContext)

        // مراقبة حالة الاتصال من المحرك
        viewModelScope.launch {
            whatsAppEngine.connectionState.collect { state ->
                _uiState.update { it.copy(
                    connectionState = state,
                    isPairedSuccess = state is ConnectionState.Connected,
                    isLoading = if (state is ConnectionState.Error || state is ConnectionState.Connected || state is ConnectionState.Disconnected) false else it.isLoading,
                    errorMessage = when (state) {
                        is ConnectionState.Error -> state.message
                        is ConnectionState.Connected -> null
                        else -> it.errorMessage
                    }
                ) }
            }
        }

        // مراقبة رمز QR
        viewModelScope.launch {
            whatsAppEngine.qrCode.collect { qr ->
                _uiState.update { it.copy(qrCodePayload = qr) }
            }
        }

        // مراقبة كود الاقتران وبدء مؤقت الصلاحية لمدة 60 ثانية
        viewModelScope.launch {
            whatsAppEngine.pairingCode.collect { code ->
                if (!code.isNullOrBlank()) {
                    countdownJob?.cancel()
                    _uiState.update { it.copy(
                        pairingCode = code,
                        isLoading = false,
                        errorMessage = null,
                        codeCountdownSeconds = 60
                    ) }
                    countdownJob = viewModelScope.launch {
                        for (sec in 59 downTo 0) {
                            delay(1000L)
                            if (_uiState.value.pairingCode == null) break
                            if (sec == 0) {
                                _uiState.update { it.copy(
                                    pairingCode = null,
                                    codeCountdownSeconds = 0,
                                    errorMessage = "انتهت صلاحية كود الاقتران (60 ثانية). يرجى طلب كود جديد."
                                ) }
                            } else {
                                _uiState.update { it.copy(codeCountdownSeconds = sec) }
                            }
                        }
                    }
                } else {
                    countdownJob?.cancel()
                    _uiState.update { it.copy(pairingCode = null, codeCountdownSeconds = 0) }
                }
            }
        }
    }

    fun selectMethod(method: PairingMethod) {
        _uiState.update { it.copy(selectedMethod = method, errorMessage = null) }
        if (method == PairingMethod.QR_CODE && _uiState.value.qrCodePayload == null) {
            whatsAppEngine.generateNewQr()
        }
    }

    fun requestPairingCode() {
        val cleanNational = _uiState.value.nationalPhoneNumber.filter { it.isDigit() }.trimStart('0')
        val dialDigits = _uiState.value.selectedCountry.dialCode.filter { it.isDigit() }
        val sanitizedPhone = "$dialDigits$cleanNational"

        if (cleanNational.length < 5 || sanitizedPhone.length < 8) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال رقم هاتف صحيح مع مفتاح الدولة.") }
            return
        }

        countdownJob?.cancel()
        _uiState.update { it.copy(
            isLoading = true,
            errorMessage = null,
            pairingCode = null,
            codeCountdownSeconds = 0,
            phoneNumberInput = "+$sanitizedPhone"
        ) }
        sessionKeystore.setSessionPhone("+$sanitizedPhone")
        whatsAppEngine.requestPairingCode(sanitizedPhone)
        viewModelScope.launch {
            delay(25_000L)
            if (_uiState.value.isLoading && _uiState.value.pairingCode == null) {
                _uiState.update { current ->
                    if (current.isLoading && current.pairingCode == null) {
                        current.copy(
                            isLoading = false,
                            errorMessage = "انتهت مهلة طلب كود الاقتران. تأكد من اتصال الإنترنت وصحة الرقم ثم أعد المحاولة."
                        )
                    } else current
                }
            }
        }
    }

    fun openWhatsApp(context: Context) {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        try {
            val genericIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://wa.me")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericIntent)
        } catch (_: Exception) {}
    }

    fun shareQrCode(context: Context) {
        val payload = _uiState.value.qrCodePayload
        if (payload.isNullOrBlank()) return
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }

            val qrDir = java.io.File(context.cacheDir, "qr").apply { mkdirs() }
            val qrFile = java.io.File(qrDir, "whatsapp_qr_pairing.png")
            java.io.FileOutputStream(qrFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                qrFile
            )

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "WhatsApp Pairing QR Code")
                putExtra(android.content.Intent.EXTRA_TEXT, "امسح رمز QR التالي من هاتف آخر أو كمبيوتر لربط واتساب بالبرنامج.")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(shareIntent, "مشاركة رمز QR").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("PairingViewModel", "Failed to share QR image", e)
        }
    }

    fun refreshQr() {
        _uiState.update { it.copy(errorMessage = null) }
        whatsAppEngine.generateNewQr()
    }

    fun confirmSimulatedSuccess() {
        countdownJob?.cancel()
        whatsAppEngine.confirmPairingSuccess(_uiState.value.phoneNumberInput)
        _uiState.update { it.copy(isPairedSuccess = true, connectionState = ConnectionState.Connected, errorMessage = null, pairingCode = null, codeCountdownSeconds = 0) }
    }

    /**
     * فحص كافة الصلاحيات الحقيقية في النظام وتحديث حالة الشاشة والمحرك.
     * تم فصل فحص صلاحية الإشعار عن حالة اتصال Baileys.
     */
    fun checkAllPermissions(context: Context) {
        val isNotif = ServicePermissionManager.isNotificationListenerEnabled(context)
        val isAccess = ServicePermissionManager.isAccessibilityServiceEnabled(context)
        val isBattery = ServicePermissionManager.isBatteryOptimizationIgnored(context)
        val hasContacts = ServicePermissionManager.hasContactsPermissions(context)

        val isBaileysConnected = whatsAppEngine.connectionState.value is ConnectionState.Connected

        if (isNotif) {
            whatsAppEngine.activateDirectAutomation()
            WhatsAppForegroundService.start(context)
        }

        _uiState.update { it.copy(
            isNotificationListenerActive = isNotif,
            isAccessibilityActive = isAccess,
            isBatteryOptimizationIgnored = isBattery,
            hasContactsPermission = hasContacts,
            isPairedSuccess = isBaileysConnected,
            connectionState = whatsAppEngine.connectionState.value
        ) }
    }

    fun openNotificationSettings(context: Context) {
        ServicePermissionManager.openNotificationListenerSettings(context)
    }

    fun openAccessibilitySettings(context: Context) {
        ServicePermissionManager.openAccessibilitySettings(context)
    }

    fun openBatterySettings(context: Context) {
        ServicePermissionManager.openBatteryOptimizationSettings(context)
    }

    fun openAppSettings(context: Context) {
        ServicePermissionManager.openAppSettings(context)
    }

    fun onCountrySelected(country: CountryCode) {
        _uiState.update { current ->
            val cleanNational = current.nationalPhoneNumber.trimStart('0')
            val fullPhone = if (cleanNational.isNotBlank()) "${country.dialCode}$cleanNational" else ""
            sessionKeystore.setSessionPhone(fullPhone)
            current.copy(
                selectedCountry = country,
                phoneNumberInput = fullPhone
            )
        }
    }

    fun onNationalPhoneNumberChange(number: String) {
        _uiState.update { current ->
            val cleanNational = number.trimStart('0')
            val fullPhone = if (number.isNotBlank()) "${current.selectedCountry.dialCode}$cleanNational" else ""
            sessionKeystore.setSessionPhone(fullPhone)
            current.copy(
                nationalPhoneNumber = number,
                phoneNumberInput = fullPhone
            )
        }
    }

    fun onPhoneNumberChange(phone: String) {
        sessionKeystore.setSessionPhone(phone)
        _uiState.update { it.copy(phoneNumberInput = phone) }
    }

    fun logout() {
        whatsAppEngine.logout()
        _uiState.update { it.copy(
            isPairedSuccess = false,
            connectionState = ConnectionState.Disconnected,
            qrCodePayload = null,
            pairingCode = null,
            errorMessage = null,
            isLoading = false
        ) }
    }
}
