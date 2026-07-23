package com.nathan.bingoauto

import kotlin.math.roundToInt

/** A number recognised on screen, with the pixel centre of its bounding box. */
data class NumberToken(
    val value: Int,
    val cx: Float,
    val cy: Float,
)

/** A tap the engine wants performed, in absolute screen pixels. */
data class TapTarget(val x: Float, val y: Float, val value: Int)

/**
 * Continuous auto-daub logic.
 *
 * Every captured frame is OCR'd into [NumberToken]s. Tokens in the top band of
 * the screen (where the called balls live) feed the running set of called
 * numbers. Tokens in the card area whose value has been called — and that we
 * have not already tapped — become [TapTarget]s.
 *
 * The engine is deliberately stateful and forgiving: a called ball only shows
 * for a moment, so we accumulate calls over time; a daubed cell is remembered by
 * its quantised position so the same physical cell is never tapped twice, while
 * an identical number on the other card (a different position) is still tapped.
 */
class BingoEngine(
    private val screenWidth: Int,
    private val screenHeight: Int,
) {
    private val calledNumbers = HashSet<Int>()
    private val tappedCells = HashSet<Long>()

    @Volatile var lastCalledCount = 0
        private set
    @Volatile var lastTapCount = 0
        private set

    /** Forget everything — call this when a new game starts. */
    @Synchronized
    fun reset() {
        calledNumbers.clear()
        tappedCells.clear()
        lastCalledCount = 0
        lastTapCount = 0
    }

    /**
     * Feed one frame of recognised tokens and get back the cells to daub.
     * The tokens' coordinates must be in the same absolute screen space used
     * for tapping (i.e. full-resolution capture, no scaling).
     */
    @Synchronized
    fun onFrame(tokens: List<NumberToken>): List<TapTarget> {
        val calledBandBottom = screenHeight * CALLED_BAND_RATIO
        val counterZoneLeft = screenWidth * COUNTER_ZONE_RATIO

        // 1. Update the set of called numbers from the top-centre band.
        for (t in tokens) {
            if (t.value !in VALID_RANGE) continue
            val inCalledBand = t.cy <= calledBandBottom
            val inCounterZone = t.cx >= counterZoneLeft // score / "balls left" live top-right
            if (inCalledBand && !inCounterZone) {
                calledNumbers.add(t.value)
            }
        }

        // 2. Any card-area token whose number was called and whose cell was not
        //    yet daubed becomes a tap.
        val targets = ArrayList<TapTarget>()
        for (t in tokens) {
            if (t.value !in VALID_RANGE) continue
            if (t.cy <= calledBandBottom) continue // skip the called band itself
            if (t.value !in calledNumbers) continue
            val key = cellKey(t.value, t.cx, t.cy)
            if (tappedCells.add(key)) {
                targets.add(TapTarget(t.cx, t.cy, t.value))
            }
        }

        lastCalledCount = calledNumbers.size
        if (targets.isNotEmpty()) lastTapCount += targets.size
        return targets
    }

    /** Quantise a cell to a stable key so OCR jitter maps to the same cell. */
    private fun cellKey(value: Int, cx: Float, cy: Float): Long {
        val bx = (cx / CELL_BUCKET_PX).roundToInt()
        val by = (cy / CELL_BUCKET_PX).roundToInt()
        // pack value(0..127) + bx(0..4095) + by(0..4095) into a Long
        return (value.toLong() shl 40) or (bx.toLong() shl 20) or by.toLong()
    }

    companion object {
        /** US 75-ball bingo. Widened slightly to tolerate OCR noise. */
        private val VALID_RANGE = 1..75

        /** Top fraction of the screen treated as the called-ball band. */
        private const val CALLED_BAND_RATIO = 0.28f

        /** Right fraction ignored for called balls (score / remaining counter). */
        private const val COUNTER_ZONE_RATIO = 0.80f

        /** A daubed cell is remembered to this pixel granularity. */
        private const val CELL_BUCKET_PX = 45f
    }
}
