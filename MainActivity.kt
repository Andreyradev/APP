package com.example.zebracontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

private const val PRINTER_PORT = 9100
private const val DOTS_PER_MM = 12.0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ZebraControllerScreen() } }
    }
}

private data class SendResult(val ok: Boolean, val message: String)

private fun sendZpl(ip: String, command: String): SendResult {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, PRINTER_PORT), 3000)
            socket.soTimeout = 3000
            socket.getOutputStream().use { out ->
                out.write(command.toByteArray(Charsets.US_ASCII))
                out.flush()
            }
        }
        SendResult(true, "Командата е изпратена до $ip")
    } catch (e: Exception) {
        val reason = when (e) {
            is java.net.SocketTimeoutException -> "Изтече времето за връзка."
            is java.net.ConnectException -> "Връзката е отказана. Провери IP адреса и порт 9100."
            is java.net.UnknownHostException -> "Невалиден адрес."
            else -> e.localizedMessage ?: e.javaClass.simpleName
        }
        SendResult(false, "Грешка към $ip: $reason")
    }
}

@Composable
private fun ZebraControllerScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zebra_controller", android.content.Context.MODE_PRIVATE) }
    var prefix by remember { mutableStateOf(prefs.getString("prefix", "10.100.208.") ?: "10.100.208.") }
    var lastOctet by remember { mutableStateOf(prefs.getString("last", "1") ?: "1") }
    var stepMm by remember { mutableFloatStateOf(prefs.getFloat("step", 1f)) }
    var offsetX by remember { mutableIntStateOf(prefs.getInt("offsetX", 0)) }
    var offsetY by remember { mutableIntStateOf(prefs.getInt("offsetY", 0)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Въведи адреса на принтера.") }
    var statusOk by remember { mutableStateOf(true) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val counts = remember {
        mutableStateMapOf("UP" to 0, "DOWN" to 0, "LEFT" to 0, "RIGHT" to 0)
    }
    val scope = rememberCoroutineScope()
    val ip = prefix + lastOctet.trim()
    val validIp = lastOctet.trim().toIntOrNull()?.let { it in 1..254 } == true &&
        (prefix == "10.100.208." || prefix == "10.136.200.")

    fun runCommand(command: String, onSuccess: (() -> Unit)? = null) {
        if (!validIp) {
            statusOk = false
            status = "Невалиден IP адрес. Последният октет трябва да е между 1 и 254."
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { sendZpl(ip, command) }
            busy = false
            statusOk = result.ok
            status = result.message
            if (result.ok) onSuccess?.invoke()
        }
    }

    fun move(direction: String) {
        val dots = (stepMm * DOTS_PER_MM).toInt().coerceAtLeast(1)
        var newX = offsetX
        var newY = offsetY
        when (direction) {
            "LEFT" -> newX += dots
            "RIGHT" -> newX -= dots
            "UP" -> newY -= dots
            "DOWN" -> newY += dots
        }
        // Keep accidental offsets bounded to +/- 1000 dots.
        if (kotlin.math.abs(newX) > 1000 || kotlin.math.abs(newY) > 1000) {
            statusOk = false
            status = "Достигнат е лимитът за отместване (±1000 dots). Нулирай позицията."
            return
        }
        fun signed(n: Int) = if (n > 0) "+$n" else n.toString()
        runCommand("^XA^LS${signed(newX)}^LT${signed(newY)}^JUS^XZ") {
            offsetX = newX
            offsetY = newY
            counts[direction] = (counts[direction] ?: 0) + 1
            prefs.edit().putInt("offsetX", offsetX).putInt("offsetY", offsetY).apply()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Zebra GX430t Controller", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Управление през локална мрежа · TCP $PRINTER_PORT", style = MaterialTheme.typography.bodyMedium)

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Мрежови настройки", style = MaterialTheme.typography.titleMedium)
                Text("IP префикс")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = prefix == "10.100.208.", onClick = {
                        prefix = "10.100.208."
                        prefs.edit().putString("prefix", prefix).apply()
                    }, label = { Text("10.100.208.") })
                    FilterChip(selected = prefix == "10.136.200.", onClick = {
                        prefix = "10.136.200."
                        prefs.edit().putString("prefix", prefix).apply()
                    }, label = { Text("10.136.200.") })
                }
                OutlinedTextField(
                    value = lastOctet,
                    onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 3) lastOctet = v },
                    label = { Text("Последен октет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Адрес: $ip")
                Button(
                    onClick = { runCommand("^XA^JUS^XZ") },
                    enabled = !busy && validIp,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Провери връзката") }
                Text("Забележка: проверката изпраща безвредна команда за запазване на настройките; това не е само ping.")
            }
        }

        Card {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Позициониране", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                Text("Стъпка: ${String.format(Locale.US, "%.1f", stepMm)} мм")
                Slider(value = stepMm, onValueChange = { stepMm = it }, valueRange = 0.5f..10f,
                    steps = 18, onValueChangeFinished = {
                        prefs.edit().putFloat("step", stepMm).apply()
                    })
                Button(onClick = { move("UP") }, enabled = !busy, modifier = Modifier.width(150.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Нагоре (${counts["UP"] ?: 0})")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { move("LEFT") }, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null); Text(" ${counts["LEFT"] ?: 0}")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("X: $offsetX", fontWeight = FontWeight.SemiBold)
                        Text("Y: $offsetY", fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = { move("RIGHT") }, enabled = !busy) {
                        Text("${counts["RIGHT"] ?: 0} "); Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
                Button(onClick = { move("DOWN") }, enabled = !busy, modifier = Modifier.width(150.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Надолу (${counts["DOWN"] ?: 0})")
                }
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Режим на печат", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { runCommand("^XA^MMP^JUS^XZ") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Отлепящ") }
                Button(onClick = { runCommand("^XA^MMC^JUS^XZ") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Режещ") }
                Button(onClick = { runCommand("^XA^MMT^JUS^XZ") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Късащ") }
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Управление", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { runCommand("~JC") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Калибриране на принтера")
                }
                OutlinedButton(onClick = { showResetConfirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Възстанови позиция (0)")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor =
            if (statusOk) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text(if (busy) "Изпращане…" else if (statusOk) "Статус" else "Грешка",
                    fontWeight = FontWeight.Bold)
                Text(status)
            }
        }
        Text("Отместване: X=$offsetX dots, Y=$offsetY dots", style = MaterialTheme.typography.bodySmall)
        Text("Разработено за сервизно управление на Zebra GX430t", style = MaterialTheme.typography.labelSmall)
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Нулиране на позицията?") },
            text = { Text("Ще изпратим ^LS0 и ^LT0 към принтера. Продължаваме ли?") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    runCommand("^XA^LS0^LT0^JUS^XZ") {
                        offsetX = 0
                        offsetY = 0
                        counts.keys.toList().forEach { counts[it] = 0 }
                        prefs.edit().putInt("offsetX", 0).putInt("offsetY", 0).apply()
                    }
                }) { Text("Нулирай") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Отказ") } }
        )
    }
}
