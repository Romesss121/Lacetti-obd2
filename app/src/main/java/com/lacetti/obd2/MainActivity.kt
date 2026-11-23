package com.lacetti.obd2

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val elmClient = Elm327WifiClient()

    private lateinit var cardStatus: MaterialCardView
    private lateinit var tvStatusDot: TextView
    private lateinit var tvStatusText: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cardStatus = findViewById(R.id.cardStatus)
        tvStatusDot = findViewById(R.id.tvStatusDot)
        tvStatusText = findViewById(R.id.tvStatusText)
        tvLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)

        findViewById<MaterialButton>(R.id.btnConnect).setOnClickListener {
            appendLog("Подключение к 192.168.0.10:35000...")
            lifecycleScope.launch {
                val ok = elmClient.connect()
                updateConnectionStatus(ok)
                if (ok) {
                    appendLog("Инициализация KWP2000 (ATSP5) -> OK")
                } else {
                    appendLog("Ошибка! Проверьте Wi-Fi сеть ELM327 и отключите мобильный интернет.")
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnReadEngine).setOnClickListener {
            appendLog("ATPC -> Запрос ошибок ДВС (Sirius D42)...")
            lifecycleScope.launch {
                appendLog(elmClient.readEngineErrors())
            }
        }

        findViewById<MaterialButton>(R.id.btnClearEngine).setOnClickListener {
            appendLog("ATPC -> Сброс ошибок ДВС...")
            lifecycleScope.launch {
                appendLog(elmClient.clearEngineErrors())
            }
        }

        findViewById<MaterialButton>(R.id.btnReadAbs).setOnClickListener {
            appendLog("ATPC -> Запрос ошибок ABS (Pin 12)...")
            lifecycleScope.launch {
                appendLog(elmClient.readAbsErrors())
            }
        }

        findViewById<MaterialButton>(R.id.btnClearAbs).setOnClickListener {
            appendLog("ATPC -> Сброс ошибок ABS...")
            lifecycleScope.launch {
                appendLog(elmClient.clearAbsErrors())
            }
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        if (connected) {
            cardStatus.setCardBackgroundColor(Color.parseColor("#132A1C"))
            cardStatus.strokeColor = Color.parseColor("#2E7D32")
            tvStatusDot.setTextColor(Color.parseColor("#4CAF50"))
            tvStatusText.text = "ELM327 Wi-Fi: Подключено (192.168.0.10)"
        } else {
            cardStatus.setCardBackgroundColor(Color.parseColor("#2A1B1B"))
            cardStatus.strokeColor = Color.parseColor("#C62828")
            tvStatusDot.setTextColor(Color.parseColor("#EF5350"))
            tvStatusText.text = "ELM327 Wi-Fi: Ошибка подключения"
        }
    }

    private fun appendLog(message: String) {
        tvLog.append("\n> $message")
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        elmClient.disconnect()
    }
}
