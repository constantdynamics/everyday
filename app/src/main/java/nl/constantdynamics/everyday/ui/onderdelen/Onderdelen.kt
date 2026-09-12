package nl.constantdynamics.everyday.ui.onderdelen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LegeStaat(
    titel: String,
    uitleg: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = titel, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = uitleg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun NaamDialoog(
    titel: String,
    uitleg: String,
    beginwaarde: String,
    bevestigLabel: String,
    sluit: () -> Unit,
    bevestig: (String) -> Unit,
) {
    var naam by remember { mutableStateOf(beginwaarde) }

    AlertDialog(
        onDismissRequest = sluit,
        title = { Text(titel) },
        text = {
            Column {
                Text(text = uitleg, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = naam,
                    onValueChange = { naam = it },
                    singleLine = true,
                    label = { Text("Naam") },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { bevestig(naam) }, enabled = naam.isNotBlank()) {
                Text(bevestigLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = sluit) { Text("Annuleren") }
        },
    )
}
