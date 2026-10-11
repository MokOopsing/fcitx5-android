/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.rerank

import java.nio.ByteBuffer

/**
 * A weight matrix of [rows] × [columns], row-major: int8 with a scale per row as the model file
 * stores it (a quarter of the memory of floats), multiplied by a [MatrixKernel], or floats.
 */
internal sealed class Matrix(val rows: Int, val columns: Int) {

    /** `x · row r`. */
    abstract fun dot(x: FloatArray, r: Int): Float

    /** `y[r] = x · row r` for every row. */
    abstract fun times(x: FloatArray, y: FloatArray)

    /** Row [r] as floats, into [out]. */
    abstract fun row(r: Int, out: FloatArray)

    /** `y[o] = x · row o + bias[o]` for every row. */
    fun apply(x: FloatArray, bias: FloatArray, y: FloatArray) {
        times(x, y)
        for (o in 0 until rows) y[o] += bias[o]
    }

    /** The same weights as floats: four times the memory, no conversion per use. */
    abstract fun unpacked(): Matrix

    private class Quantized(
        rows: Int,
        columns: Int,
        private val q: ByteBuffer,
        private val scales: FloatArray,
        private val kernel: MatrixKernel,
    ) : Matrix(rows, columns) {

        override fun dot(x: FloatArray, r: Int): Float = dotQ(q, scales, columns, x, r)

        override fun times(x: FloatArray, y: FloatArray) {
            kernel.times(q, scales, rows, columns, x, y)
        }

        override fun row(r: Int, out: FloatArray) {
            val base = r * columns
            val scale = scales[r]
            for (i in 0 until columns) out[i] = q[base + i] * scale
        }

        override fun unpacked(): Matrix {
            val out = FloatArray(rows * columns)
            val row = FloatArray(columns)
            for (r in 0 until rows) {
                row(r, row)
                System.arraycopy(row, 0, out, r * columns, columns)
            }
            return Floats(rows, columns, out)
        }
    }

    private class Floats(rows: Int, columns: Int, private val f: FloatArray) : Matrix(rows, columns) {

        override fun dot(x: FloatArray, r: Int): Float = dotF(f, columns, x, r)

        override fun times(x: FloatArray, y: FloatArray) {
            for (r in 0 until rows) y[r] = dotF(f, columns, x, r)
        }

        override fun row(r: Int, out: FloatArray) {
            System.arraycopy(f, r * columns, out, 0, columns)
        }

        override fun unpacked(): Matrix = this
    }

    companion object {
        fun of(rows: Int, columns: Int, f: FloatArray): Matrix = Floats(rows, columns, f)

        fun quantized(rows: Int, columns: Int, q: ByteBuffer, scales: FloatArray, kernel: MatrixKernel = MatrixKernel.JVM): Matrix =
            Quantized(rows, columns, q, scales, kernel)
    }
}
