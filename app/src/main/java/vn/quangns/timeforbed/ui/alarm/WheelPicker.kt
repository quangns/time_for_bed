package vn.quangns.timeforbed.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import vn.quangns.timeforbed.ui.theme.Night

private const val VISIBLE = 5
/** Lặp danh sách nhiều vòng để cuộn như bánh xe vô tận. */
private const val LOOPS = 400

/**
 * Bánh xe cuộn chọn số (giờ 0–23, phút 0–59): vuốt lên/xuống, tự dừng đúng ô, ô giữa là giá trị chọn.
 * [value] đổi từ bên ngoài (nút "+15 phút"…) thì bánh xe tự cuộn tới.
 */
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 56.dp,
) {
    val base = (LOOPS / 2) * count
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + value - VISIBLE / 2)
    val haptics = LocalHapticFeedback.current
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val onChange by rememberUpdatedState(onValueChange)

    /** Chỉ số ô đang nằm giữa khung. */
    val centerIndex by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + VISIBLE / 2 + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centerIndex % count }.distinctUntilChanged().collect {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(it)
        }
    }

    // Giá trị đổi từ bên ngoài: cuộn theo đường ngắn nhất tới số mới.
    LaunchedEffect(value) {
        val current = centerIndex % count
        if (current != value && !state.isScrollInProgress) {
            var delta = (value - current + count) % count
            if (delta > count / 2) delta -= count
            state.animateScrollToItem(centerIndex + delta - VISIBLE / 2)
        }
    }

    Box(
        modifier
            .width(96.dp)
            .height(itemHeight * VISIBLE)
            .semantics { this.contentDescription = "$contentDescription: $value" },
        contentAlignment = Alignment.Center,
    ) {
        // Dải sáng đánh dấu ô đang chọn.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Night.Raised, RoundedCornerShape(16.dp)),
        )
        LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state)) {
            items(count * LOOPS) { i ->
                val distance = kotlin.math.abs(i - centerIndex)
                Text(
                    "%02d".format(i % count),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Light),
                    color = if (distance == 0) Night.Moonlight else Night.Mist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .graphicsLayer {
                            val s = if (distance == 0) 1f else 0.78f
                            scaleX = s
                            scaleY = s
                            alpha = when (distance) {
                                0 -> 1f
                                1 -> 0.6f
                                else -> 0.25f
                            }
                        },
                )
            }
        }
    }
}
