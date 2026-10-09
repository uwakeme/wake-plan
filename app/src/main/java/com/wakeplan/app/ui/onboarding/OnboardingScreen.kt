package com.wakeplan.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeplan.app.R
import com.wakeplan.app.notification.NotificationPermissionChecker
import com.wakeplan.app.ui.theme.WpBgCardDark
import com.wakeplan.app.ui.theme.WpBorderDark
import com.wakeplan.app.ui.theme.WpPrimaryDark
import com.wakeplan.app.ui.theme.WpPrimaryHoverDark
import com.wakeplan.app.ui.theme.WpTextTertiaryDark

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var notificationGranted by remember {
        mutableStateOf(NotificationPermissionChecker.hasPostNotificationsPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
    }

    // 引导页支持逐步返回——之前系统返回键直接退出应用
    androidx.activity.compose.BackHandler(enabled = step > 0) {
        step--
    }

    val infiniteTransition = rememberInfiniteTransition(label = "logo_float")
    val logoOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo_float_anim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090E1A),
                        Color(0xFF0F1829),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopStart)
                .offset(y = (-100).dp, x = (-100).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WpPrimaryDark.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.BottomEnd)
                .offset(y = 100.dp, x = 100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4A90D9).copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .offset(y = logoOffset.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = WpPrimaryDark.copy(alpha = 0.3f),
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(WpPrimaryDark, WpPrimaryHoverDark),
                        ),
                        shape = RoundedCornerShape(24.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "W",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF090E1A),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = when (step) {
                    0 -> stringResource(R.string.onboarding_step1_title)
                    1 -> stringResource(R.string.onboarding_step2_title)
                    else -> stringResource(R.string.onboarding_step3_title)
                },
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.04).sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = when (step) {
                    0 -> stringResource(R.string.onboarding_step1_body)
                    1 -> stringResource(R.string.onboarding_step2_body)
                    else -> if (notificationGranted) {
                        stringResource(R.string.onboarding_step3_body_granted)
                    } else {
                        stringResource(R.string.onboarding_step3_body_denied)
                    }
                },
                fontSize = 15.sp,
                color = WpTextTertiaryDark,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.width(280.dp),
            )

            Spacer(Modifier.height(40.dp))

            when (step) {
                0 -> WelcomeFeatures()
                1 -> PermissionFeatures(
                    granted = notificationGranted,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationGranted = true
                        }
                    },
                )
                2 -> FinishFeatures()
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (i == step) WpPrimaryDark else WpBorderDark,
                                shape = RoundedCornerShape(4.dp),
                            ),
                    )
                    if (i < 2) Spacer(Modifier.width(8.dp))
                }
            }

            Button(
                onClick = {
                    if (step < 2) {
                        step++
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WpPrimaryDark,
                    contentColor = Color(0xFF090E1A),
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                ),
            ) {
                Text(
                    text = if (step < 2) stringResource(R.string.onboarding_next)
                    else stringResource(R.string.onboarding_get_started),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (step < 2) {
                TextButton(
                    onClick = { onFinished() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        fontSize = 14.sp,
                        color = WpTextTertiaryDark,
                    )
                }
            } else {
                Spacer(Modifier.height(40.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WelcomeFeatures() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureItem(
            icon = "📋",
            title = stringResource(R.string.onboarding_feature_plans_title),
            description = stringResource(R.string.onboarding_feature_plans_desc),
        )
        FeatureItem(
            icon = "⏰",
            title = stringResource(R.string.onboarding_feature_reminder_title),
            description = stringResource(R.string.onboarding_feature_reminder_desc),
        )
    }
}

@Composable
private fun PermissionFeatures(
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureItem(
            icon = "🔔",
            title = stringResource(R.string.onboarding_feature_notify_title),
            description = stringResource(R.string.onboarding_feature_notify_desc),
        )
        FeatureItem(
            icon = "🔒",
            title = stringResource(R.string.onboarding_feature_privacy_title),
            description = stringResource(R.string.onboarding_feature_privacy_desc),
        )
        if (!granted) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WpBgCardDark,
                    contentColor = WpPrimaryDark,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, WpBorderDark),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_request_permission),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_permission_granted),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = WpPrimaryDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FinishFeatures() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureItem(
            icon = "✨",
            title = stringResource(R.string.onboarding_feature_ready_title),
            description = stringResource(R.string.onboarding_feature_ready_desc),
        )
        FeatureItem(
            icon = "📊",
            title = stringResource(R.string.onboarding_feature_local_title),
            description = stringResource(R.string.onboarding_feature_local_desc),
        )
    }
}

@Composable
private fun FeatureItem(
    icon: String,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = WpBgCardDark,
                    shape = RoundedCornerShape(16.dp),
                )
                .border(
                    width = 1.dp,
                    color = WpBorderDark,
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                fontSize = 20.sp,
            )
        }
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = WpTextTertiaryDark,
                lineHeight = 18.sp,
            )
        }
    }
}
