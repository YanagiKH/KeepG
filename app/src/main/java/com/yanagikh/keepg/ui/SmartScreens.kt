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
import com.yanagikh.keepg.smart.SmartRuleEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SmartScreen(photos: List<PhotoEntity>, faces: List<FaceObservationEntity>, people: List<PersonProfileEntity>, rules: List<SmartRuleEntity>, busy: Boolean, viewModel: MainViewModel) {
    var rename by remember { mutableStateOf<Long?>(null) }; var personName by remember { mutableStateOf("") }; var addRule by remember { mutableStateOf(false) }
    val clusters = remember(faces) { faces.mapNotNull { it.clusterId }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value } }
    val matchingCounts by produceState<Map<Long, Int>>(emptyMap(), photos, faces, rules) {
        value = withContext(Dispatchers.Default) {
            val facesByMedia = faces.groupBy { it.mediaId }
            rules.associate { rule ->
                rule.id to photos.count { photo -> SmartRuleEvaluator.matches(photo, facesByMedia[photo.mediaId].orEmpty(), rule) }
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("On-device smart organization"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tr("Detect faces, extract local features, group likely matches, and organize by people, time, location, or expression."))
                Button(viewModel::analyzeLibrary, enabled = !busy && photos.isNotEmpty()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text(tr(if (busy) "Analyzing…" else "Analyze library")) }
            } }
        }
        item { Text(tr("People"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (clusters.isEmpty()) item { Text(tr("Run analysis to create local person groups."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(clusters, key = { it.key }) { cluster ->
            val profile = people.firstOrNull { it.clusterId == cluster.key }
            val personLabel = profile?.displayName ?: trf("Person %s", cluster.key)
            ListItem({ Text(personLabel) }, supportingContent = { Text(trf("%s detected faces · heuristic group", cluster.value)) }, leadingContent = { Icon(Icons.Default.Person, null) }, trailingContent = {
                Row { IconButton({ viewModel.addPersonRule(personLabel, cluster.key) }) { Icon(Icons.Default.PlaylistAdd, tr("Create person rule")) }; IconButton({ rename = cluster.key; personName = profile?.displayName.orEmpty() }) { Icon(Icons.Default.Edit, tr("Name person")) } }
            })
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Smart albums"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); FilledTonalButton({ addRule = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(tr("Rule")) } }
        }
        if (rules.isEmpty()) item { Text(tr("Create rules for time, location, expression, or a named person group."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rules, key = { it.id }) { rule ->
            ListItem({ Text(rule.name) }, supportingContent = { Text(trf("%s · %s matches", tr(rule.kind.lowercase().replaceFirstChar { it.uppercase() }), matchingCounts[rule.id] ?: 0)) }, leadingContent = { Icon(ruleIcon(rule.kind), null) }, trailingContent = { IconButton({ viewModel.deleteRule(rule.id) }) { Icon(Icons.Default.Delete, tr("Delete")) } })
        }
    }
    rename?.let { id -> AlertDialog(onDismissRequest = { rename = null }, title = { Text(tr("Name person group")) }, text = { OutlinedTextField(personName, { personName = it }, label = { Text(tr("Name")) }) }, confirmButton = { TextButton({ viewModel.namePerson(id, personName); rename = null }) { Text(tr("Save")) } }, dismissButton = { TextButton({ rename = null }) { Text(tr("Cancel")) } }) }
    if (addRule) SmartRuleDialog({ addRule = false }, viewModel)
}

@Composable
internal fun SmartRuleDialog(onDismiss: () -> Unit, viewModel: MainViewModel) {
    var kind by remember { mutableStateOf("EXPRESSION") }; var name by remember { mutableStateOf("") }; var expression by remember { mutableStateOf("smiling") }; var threshold by remember { mutableStateOf("0.72") }
    var start by remember { mutableStateOf("2026-01-01") }; var end by remember { mutableStateOf("2026-12-31") }; var lat by remember { mutableStateOf("") }; var lon by remember { mutableStateOf("") }; var radius by remember { mutableStateOf("1000") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Create smart album rule")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("EXPRESSION", "TIME", "LOCATION").forEach { FilterChip(kind == it, { kind = it }, { Text(tr(it.lowercase().replaceFirstChar { c -> c.uppercase() })) }) } }
        OutlinedTextField(name, { name = it }, label = { Text(tr("Rule name")) })
        when (kind) {
            "EXPRESSION" -> { Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("smiling", "neutral", "eyes_closed").forEach { FilterChip(expression == it, { expression = it }, { Text(tr(it.replace('_', ' '))) }) } }; OutlinedTextField(threshold, { threshold = it }, label = { Text(tr("Threshold 0–1")) }) }
            "TIME" -> { OutlinedTextField(start, { start = it }, label = { Text(tr("Start YYYY-MM-DD")) }); OutlinedTextField(end, { end = it }, label = { Text(tr("End YYYY-MM-DD")) }) }
            else -> { OutlinedTextField(lat, { lat = it }, label = { Text(tr("Latitude")) }); OutlinedTextField(lon, { lon = it }, label = { Text(tr("Longitude")) }); OutlinedTextField(radius, { radius = it }, label = { Text(tr("Radius meters")) }) }
        }
    } }, confirmButton = { TextButton({
        when (kind) {
            "EXPRESSION" -> viewModel.addExpressionRule(name, expression, threshold.toFloatOrNull()?.coerceIn(0f, 1f) ?: .72f)
            "TIME" -> viewModel.addTimeRule(name, start, end)
            else -> viewModel.addLocationRule(name, lat.toDoubleOrNull() ?: 0.0, lon.toDoubleOrNull() ?: 0.0, radius.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 1000.0)
        }; onDismiss()
    }) { Text(tr("Create")) } }, dismissButton = { TextButton(onDismiss) { Text(tr("Cancel")) } })
}
