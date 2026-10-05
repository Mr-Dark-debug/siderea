package io.github.mrdarkdebug.siderea.core.camera.control

/** Minimal row-major 3x3 matrix and 3-vector helpers, enough for colour maths. */
internal object Matrix3 {
    val IDENTITY: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    fun multiply(
        a: DoubleArray,
        b: DoubleArray,
    ): DoubleArray {
        require(a.size == SIZE && b.size == SIZE)
        val out = DoubleArray(SIZE)
        for (row in 0 until DIM) {
            for (col in 0 until DIM) {
                var sum = 0.0
                for (k in 0 until DIM) sum += a[row * DIM + k] * b[k * DIM + col]
                out[row * DIM + col] = sum
            }
        }
        return out
    }

    fun apply(
        m: DoubleArray,
        v: DoubleArray,
    ): DoubleArray {
        require(m.size == SIZE && v.size == DIM)
        return DoubleArray(DIM) { row -> (0 until DIM).sumOf { col -> m[row * DIM + col] * v[col] } }
    }

    fun diagonal(
        a: Double,
        b: Double,
        c: Double,
    ): DoubleArray = doubleArrayOf(a, 0.0, 0.0, 0.0, b, 0.0, 0.0, 0.0, c)

    /** Linear blend: `(1 - t) * a + t * b`. */
    fun blend(
        a: DoubleArray,
        b: DoubleArray,
        t: Double,
    ): DoubleArray = DoubleArray(SIZE) { (1 - t) * a[it] + t * b[it] }

    /** @throws IllegalArgumentException when the matrix is singular. */
    fun inverse(m: DoubleArray): DoubleArray {
        require(m.size == SIZE)

        fun at(
            row: Int,
            col: Int,
        ) = m[row * DIM + col]

        // Cofactor expansion with wrapped indices keeps the formulas uniform.
        fun cofactor(
            row: Int,
            col: Int,
        ): Double {
            val r1 = (row + 1) % DIM
            val r2 = (row + 2) % DIM
            val c1 = (col + 1) % DIM
            val c2 = (col + 2) % DIM
            return at(r1, c1) * at(r2, c2) - at(r1, c2) * at(r2, c1)
        }

        val det = at(0, 0) * cofactor(0, 0) + at(0, 1) * cofactor(0, 1) + at(0, 2) * cofactor(0, 2)
        require(kotlin.math.abs(det) > SINGULAR_EPSILON) { "matrix is singular" }
        // inverse[row][col] = cofactor(col, row) / det
        return DoubleArray(SIZE) { i -> cofactor(i % DIM, i / DIM) / det }
    }

    private const val DIM = 3
    private const val SIZE = 9
    private const val SINGULAR_EPSILON = 1e-12
}
