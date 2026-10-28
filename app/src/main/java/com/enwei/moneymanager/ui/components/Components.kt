package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = { actions() },
    )
}

@Composable
fun AmountText(
    minor: Long,
    currency: String,
    type: TxnType? = null,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    colorBySign: Boolean = false,
) {
    val color = when {
        type == TxnType.INCOME -> IncomeColor
        type == TxnType.EXPENSE -> ExpenseColor
        colorBySign && minor < 0 -> ExpenseColor
        colorBySign && minor > 0 -> IncomeColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (type) { TxnType.INCOME -> "+"; TxnType.EXPENSE -> "-"; else -> "" }
    Text(prefix + Money.format(minor, currency), modifier = modifier, color = color, style = style, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun EmojiBadge(emoji: String, tint: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(shape = CircleShape, color = tint, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
    onNone: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(display) ?: (noneLabel ?: ""),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null && onNone != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onNone(); expanded = false })
            }
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' || it == ',' || (it == '-' && !decimal) }) },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit, label: String = "日期", modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = "${date}（${com.enwei.moneymanager.core.Recurrence.weekdayName(date.dayOfWeek)}）",
            onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        )
        // 覆蓋一層可點擊區域，讓唯讀欄位也能開啟日期選擇器
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.Filled.ChevronLeft, "上個月") }
        Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable { onChange(YearMonth.now()) })
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.Filled.ChevronRight, "下個月") }
    }
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HSpace(w: Int = 8) = Spacer(Modifier.width(w.dp))

fun formatPct(v: Double) = (if (v >= 0) "+" else "") + String.format("%.2f%%", v)

fun formatNum(v: Double, digits: Int = 2): String =
    if (v == Math.floor(v) && digits <= 2) String.format("%,.0f", v) else String.format("%,.${digits}f", v)
