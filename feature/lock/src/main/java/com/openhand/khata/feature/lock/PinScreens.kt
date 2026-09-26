package com.openhand.khata.feature.lock

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.security.lock.CheckResult
import com.openhand.khata.core.security.lock.PinManager
import com.openhand.khata.core.security.lock.RecoveryCode
import com.openhand.khata.core.ui.focusOnAppear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PinLockStep { PIN, RECOVERY, NEW_PIN }

/** Unlock with the app PIN, or recover with the recovery code and choose a new PIN. */
@Composable
fun PinLockScreen(viewModel: LockViewModel) {
    var step by rememberSaveable { mutableStateOf(PinLockStep.PIN) }
    when (step) {
        PinLockStep.PIN -> EnterPin(viewModel, onForgot = {
            viewModel.clearLastCheck()
            step = PinLockStep.RECOVERY
        })
        PinLockStep.RECOVERY -> EnterRecoveryCode(
            viewModel,
            onCorrect = { step = PinLockStep.NEW_PIN },
            onBack = {
                viewModel.clearLastCheck()
                step = PinLockStep.PIN
            }
        )
        PinLockStep.NEW_PIN -> PinSetupFlow(
            viewModel,
            onFinished = viewModel::unlock,
            onCancel = null
        )
    }
}

@Composable
private fun EnterPin(viewModel: LockViewModel, onForgot: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pin by rememberSaveable { mutableStateOf("") }
    val submit = {
        viewModel.checkPin(pin)
        pin = ""
    }
    LockLayout(
        title = stringResource(R.string.lock_pin_title),
        body = null,
        primaryAction = stringResource(R.string.lock_unlock) to submit,
        primaryEnabled =
        PinManager.isValidPin(pin) && !state.checking && state.lastCheck !is CheckResult.Wait,
        secondaryActions = listOf(stringResource(R.string.lock_forgot_pin) to onForgot)
    ) {
        PinField(pin, stringResource(R.string.lock_pin_label), onChange = {
            pin = it
        }, onDone = submit)
        CheckMessage(state.lastCheck, onWaitOver = viewModel::clearLastCheck)
    }
}

@Composable
private fun EnterRecoveryCode(viewModel: LockViewModel, onCorrect: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    val submit = { viewModel.checkRecoveryCode(code, onCorrect) }
    LockLayout(
        title = stringResource(R.string.lock_recovery_title),
        body = stringResource(R.string.lock_recovery_body),
        primaryAction = stringResource(R.string.lock_continue) to submit,
        primaryEnabled = RecoveryCode.normalize(code).length == RecoveryCode.LENGTH &&
            !state.checking &&
            state.lastCheck !is CheckResult.Wait,
        secondaryActions = listOf(stringResource(R.string.lock_back_to_pin) to onBack)
    ) {
        OutlinedTextField(
            value = code,
            // Only the code's own characters are kept; the dashes are drawn, not typed.
            onValueChange = { code = RecoveryCode.normalize(it).take(RecoveryCode.LENGTH) },
            label = { Text(stringResource(R.string.lock_recovery_label)) },
            placeholder = { Text(RecoveryCode.PLACEHOLDER, fontFamily = FontFamily.Monospace) },
            visualTransformation = RecoveryCodeTransformation,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().focusOnAppear("recovery")
        )
        CheckMessage(state.lastCheck, onWaitOver = viewModel::clearLastCheck)
    }
}

private enum class SetupStep { ENTER, CONFIRM, SHOW_CODE }

/**
 * Choose a PIN, confirm it, then show the new one-time recovery code. Used when turning the app PIN
 * on, changing it, and after recovering with the old code.
 */
@Composable
fun PinSetupFlow(viewModel: LockViewModel, onFinished: () -> Unit, onCancel: (() -> Unit)?) {
    var step by rememberSaveable { mutableStateOf(SetupStep.ENTER) }
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var mismatch by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cancel = onCancel?.let { listOf(stringResource(R.string.lock_cancel) to it) }.orEmpty()

    when (step) {
        SetupStep.ENTER -> LockLayout(
            title = stringResource(R.string.pin_setup_title),
            body = stringResource(
                R.string.pin_setup_body,
                PinManager.MIN_LENGTH,
                PinManager.MAX_LENGTH
            ),
            primaryAction = stringResource(R.string.lock_continue) to { step = SetupStep.CONFIRM },
            primaryEnabled = PinManager.isValidPin(first),
            secondaryActions = cancel
        ) {
            PinField(first, stringResource(R.string.lock_pin_label), onChange = {
                first = it
                mismatch = false
            }, onDone = { if (PinManager.isValidPin(first)) step = SetupStep.CONFIRM })
            if (mismatch) ErrorText(stringResource(R.string.pin_setup_mismatch))
        }
        SetupStep.CONFIRM -> {
            val confirm = {
                if (second == first) {
                    scope.launch {
                        code = viewModel.setPin(first)
                        first = ""
                        second = ""
                        step = SetupStep.SHOW_CODE
                    }
                } else {
                    first = ""
                    second = ""
                    mismatch = true
                    step = SetupStep.ENTER
                }
            }
            LockLayout(
                title = stringResource(R.string.pin_setup_confirm_title),
                body = null,
                primaryAction = stringResource(R.string.lock_continue) to { confirm() },
                primaryEnabled = PinManager.isValidPin(second),
                secondaryActions = cancel
            ) {
                PinField(second, stringResource(R.string.lock_pin_label), onChange = {
                    second = it
                }, onDone = { confirm() })
            }
        }
        SetupStep.SHOW_CODE -> RecoveryCodeScreen(code.orEmpty(), onDone = {
            code = null
            viewModel.finishPinSetup()
            onFinished()
        })
    }
}

@Composable
private fun RecoveryCodeScreen(code: String, onDone: () -> Unit) {
    var saved by rememberSaveable { mutableStateOf(false) }
    LockLayout(
        title = stringResource(R.string.recovery_code_title),
        body = stringResource(R.string.recovery_code_body),
        primaryAction = stringResource(R.string.recovery_code_done) to onDone,
        primaryEnabled = saved
    ) {
        Text(
            RecoveryCode.format(code),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(value = saved, role = Role.Checkbox, onValueChange = {
                saved =
                    it
            })
        ) {
            Checkbox(checked = saved, onCheckedChange = null)
            Text(stringResource(R.string.recovery_code_saved))
        }
    }
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (new.length <= PinManager.MAX_LENGTH &&
                new.all(Char::isDigit)
            ) {
                onChange(new)
            }
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusOnAppear(label)
    )
}

/** "Wrong, N more tries" or a live "try again in m:ss" countdown. */
@Composable
private fun CheckMessage(result: CheckResult?, onWaitOver: () -> Unit) {
    when (result) {
        is CheckResult.Wrong -> ErrorText(
            pluralStringResource(
                R.plurals.lock_wrong_attempts_left,
                result.attemptsBeforeWait,
                result.attemptsBeforeWait
            )
        )
        is CheckResult.Wait -> {
            var now by androidx.compose.runtime.remember {
                mutableLongStateOf(System.currentTimeMillis())
            }
            LaunchedEffect(result.untilMillis) {
                while (now < result.untilMillis) {
                    delay(1_000)
                    now = System.currentTimeMillis()
                }
                onWaitOver()
            }
            val seconds = ((result.untilMillis - now).coerceAtLeast(0) + 999) / 1_000
            ErrorText(
                stringResource(R.string.lock_wait, "%d:%02d".format(seconds / 60, seconds % 60))
            )
        }
        else -> Unit
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}
