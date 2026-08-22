package com.yanagikh.keepg.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yanagikh.keepg.MainViewModel
import com.yanagikh.keepg.data.*

@Composable
internal fun SmartScreen(photos: List<PhotoEntity>, faces: List<FaceObservationEntity>, people: List<PersonProfileEntity>, rules: List<SmartRuleEntity>, busy: Boolean, viewModel: MainViewModel) {
    var rename by remember { mutableStateOf<Long?>(null) }; var personName by remember { mutableStateOf("") }; var addRule by remember { mutableStateOf(false) }
    val clusters = faces.mapNotNull { it.clusterId }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On-device smart organization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Detect faces, extract local features, group likely matches, and organize by people, time, location, or expression.")
                Button(viewModel::analyzeLibrary, enabled = !busy && photos.isNotEmpty()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "Analyzing…" else "Analyze library") }
            } }
        }
        item { Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (clusters.isEmpty()) item { Text("Run analysis to create local person groups.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(clusters, key = { it.key }) { cluster ->
            val profile = people.firstOrNull { it.clusterId == cluster.key }
            ListItem({ Text(profile?.displayName ?: "Person ${cluster.key}") }, supportingContent = { Text("${cluster.value} detected faces · heuristic group") }, leadingContent = { Icon(Icons.Default.Person, null) }, trailingContent = {
                Row { IconButton({ viewModel.addPersonRule(profile?.displayName ?: "Person ${cluster.key}", cluster.key) }) { Icon(Icons.Default.PlaylistAdd, "Create person rule") }; IconButton({ rename = cluster.key; personName = profile?.displayName.orEmpty() }) { Icon(Icons.Default.Edit, "Name person") } }
            })
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Smart albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); FilledTonalButton({ addRule = true }) { Icon(Icons.Default.Add, null); Text(" Rule") } }
        }
        if (rules.isEmpty()) item { Text("Create rules for time, location, expression, or a named person group.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rules, key = { it.id }) { rule ->
            ListItem({ Text(rule.name) }, supportingContent = { Text("${rule.kind.lowercase().replaceFirstChar { it.uppercase() }} · ${viewModel.matchingCount(rule)} matches") }, leadingContent = { Icon(ruleIcon(rule.kind), null) }, trailingContent = { IconButton({ viewModel.deleteRule(rule.id) }) { Icon(Icons.Default.Delete, "Delete") } })
        }
    }
    rename?.let { id -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("Name person group") }, text = { OutlinedTextField(personName, { personName = it }, label = { Text("Name") }) }, confirmButton = { TextButton({ viewModel.namePerson(id, personName); rename = null }) { Text("Save") } }, dismissButton = { TextButton({ rename = null }) { Text("Cancel") } }) }
    if (addRule) SmartRuleDialog({ addRule = false }, viewModel)
}

@Composable
internal fun SmartRuleDialog(onDismiss: () -> Unit, viewModel: MainViewModel) {
    var kind by remember { mutableStateOf("EXPRESSION") }; var name by remember { mutableStateOf("") }; var expression by remember { mutableStateOf("smiling") }; var threshold by remember { mutableStateOf("0.72") }
    var start by remember { mutableStateOf("2026-01-01") }; var end by remember { mutableStateOf("2026-12-31") }; var lat by remember { mutableStateOf("") }; var lon by remember { mutableStateOf("") }; var radius by remember { mutableStateOf("1000") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create smart album rule") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("EXPRESSION", "TIME", "LOCATION").forEach { FilterChip(kind == it, { kind = it }, { Text(it.lowercase().replaceFirstChar { c -> c.uppercase() }) }) } }
        OutlinedTextField(name, { name = it }, label = { Text("Rule name") })
        when (kind) {
            "EXPRESSION" -> { Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("smiling", "neutral", "eyes_closed").forEach { FilterChip(expression == it, { expression = it }, { Text(it.replace('_', ' ')) }) } }; OutlinedTextField(threshold, { threshold = it }, label = { Text("Threshold 0–1") }) }
            "TIME" -> { OutlinedTextField(start, { start = it }, label = { Text("Start YYYY-MM-DD") }); OutlinedTextField(end, { end = it }, label = { Text("End YYYY-MM-DD") }) }
            else -> { OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }); OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }); OutlinedTextField(radius, { radius = it }, label = { Text("Radius meters") }) }
        }
    } }, confirmButton = { TextButton({
        when (kind) {
            "EXPRESSION" -> viewModel.addExpressionRule(name, expression, threshold.toFloatOrNull()?.coerceIn(0f, 1f) ?: .72f)
            "TIME" -> viewModel.addTimeRule(name, start, end)
            else -> viewModel.addLocationRule(name, lat.toDoubleOrNull() ?: 0.0, lon.toDoubleOrNull() ?: 0.0, radius.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 1000.0)
        }; onDismiss()
    }) { Text("Create") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
