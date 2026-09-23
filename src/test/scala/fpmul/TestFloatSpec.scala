package fpmul

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import scala.io.Source

class TestFloatSpec extends AnyFreeSpec with ChiselSim {

  protected case class FloatFormat(name: String, expWidth: Int, sigWidth: Int, requestedMulStages: Int, resource: String) {
    val width: Int = 1 + expWidth + sigWidth
    val latency: Int = requestedMulStages + 3
    val expMask: BigInt = (BigInt(1) << expWidth) - 1
    val sigMask: BigInt = (BigInt(1) << sigWidth) - 1
    val signPosition: Int = expWidth + sigWidth
  }

  protected case class MulVector(lineNumber: Int, a: BigInt, b: BigInt, expected: BigInt, flags: Int)

  protected val fp16 = FloatFormat("FP16", 5, 10, 2, "/testfloat/f16_mul.txt")
  protected val fp32 = FloatFormat("FP32", 8, 23, 2, "/testfloat/f32_mul.txt")
  protected val fp64 = FloatFormat("FP64", 11, 52, 2, "/testfloat/f64_mul.txt")

  protected val formats: Seq[FloatFormat] = Seq(fp16, fp32, fp64)

  protected def loadVectors(resource: String): Seq[MulVector] = {
    val stream = Option(getClass.getResourceAsStream(resource)).getOrElse {
      throw new RuntimeException(s"Could not find TestFloat file: $resource")
    }

    val source = Source.fromInputStream(stream)

    try {
      source.getLines().zipWithIndex.filter { case (line, _) => line.trim.nonEmpty }.map { case (line, index) =>
        val fields = line.trim.split("\\s+")
        MulVector(
          lineNumber = index + 1,
          a = BigInt(fields(0), 16),
          b = BigInt(fields(1), 16),
          expected = BigInt(fields(2), 16),
          flags = Integer.parseInt(fields(3), 16)
        )
      }.toVector
    } finally {
      source.close()
    }
  }

  protected def sign(value: BigInt, format: FloatFormat): BigInt =
    (value >> format.signPosition) & 1

  protected def exponent(value: BigInt, format: FloatFormat): BigInt =
    (value >> format.sigWidth) & format.expMask

  protected def significand(value: BigInt, format: FloatFormat): BigInt =
    value & format.sigMask

  protected def isNaN(value: BigInt, format: FloatFormat): Boolean =
    (exponent(value, format) == format.expMask) && (significand(value, format) != 0)

  protected def isInf(value: BigInt, format: FloatFormat): Boolean =
    (exponent(value, format) == format.expMask) && (significand(value, format) == 0)

  protected def isSubnormal(value: BigInt, format: FloatFormat): Boolean =
    (exponent(value, format) == 0) && (significand(value, format) != 0)

  protected def hasZeroExponent(value: BigInt, format: FloatFormat): Boolean =
    exponent(value, format) == 0

  protected def resultSign(vector: MulVector, format: FloatFormat): BigInt =
    sign(vector.a, format) ^ sign(vector.b, format)

  protected def signedZero(sign: BigInt, format: FloatFormat): BigInt =
    sign << format.signPosition

  protected def canonicalNaN(format: FloatFormat): BigInt = {
    val quietBit = BigInt(1) << (format.sigWidth - 1)
    (format.expMask << format.sigWidth) | quietBit
  }

  protected def subnormalResult(vector: MulVector, format: FloatFormat): Boolean = {
    val bias = (BigInt(1) << (format.expWidth - 1)) - 1
    val expA = exponent(vector.a, format) - bias
    val expB = exponent(vector.b, format) - bias
    val implicitBit = BigInt(1) << format.sigWidth
    val sigA = implicitBit | significand(vector.a, format)
    val sigB = implicitBit | significand(vector.b, format)
    val sig = sigA * sigB
    val renorm = if (sig.bitLength > 2 * format.sigWidth + 1) 1 else 0
    val exp = expA + expB + renorm

    exp < 1 - bias
  }

  protected def adaptedExpected(vector: MulVector, format: FloatFormat): BigInt = {
    val signOut = resultSign(vector, format)
    val inputNaN = isNaN(vector.a, format) || isNaN(vector.b, format)
    val inputInfinity = isInf(vector.a, format) || isInf(vector.b, format)
    val inputZeroExponent = hasZeroExponent(vector.a, format) || hasZeroExponent(vector.b, format)

    if (inputNaN) {
      vector.expected
    } else if (inputInfinity && inputZeroExponent) {
      canonicalNaN(format)
    } else if (inputZeroExponent) {
      signedZero(signOut, format)
    } else if (!inputInfinity && subnormalResult(vector, format)) {
      signedZero(signOut, format)
    } else if (isSubnormal(vector.expected, format)) {
      signedZero(signOut, format)
    } else {
      vector.expected
    }
  }

  protected def hexString(value: BigInt, width: Int): String = {
    val digits = (width + 3) / 4
    val raw = value.toString(16).toUpperCase
    val padded = ("0" * math.max(0, digits - raw.length)) + raw
    "0x" + padded
  }

  protected def run(
      format: FloatFormat,
      vectors: Seq[MulVector],
      expected: MulVector => BigInt,
      description: String
  ) = {
    simulate(new FPMul(
      expWidth = format.expWidth,
      sigWidth = format.sigWidth,
      requestedMulStages = format.requestedMulStages
    )) { dut =>

      dut.io.a.poke(0.U(format.width.W))
      dut.io.b.poke(0.U(format.width.W))

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      val totalCycles = vectors.length + format.latency - 1

      var checked = 0
      var mismatches = 0

      for (cycle <- 0 until totalCycles) {
        if (cycle < vectors.length) {
          val vector = vectors(cycle)
          dut.io.a.poke(vector.a.U(format.width.W))
          dut.io.b.poke(vector.b.U(format.width.W))
        } else {
          dut.io.a.poke(0.U(format.width.W))
          dut.io.b.poke(0.U(format.width.W))
        }

        dut.clock.step()

        val outputIndex = cycle - (format.latency - 1)

        if (outputIndex >= 0 && outputIndex < vectors.length) {
          val vector = vectors(outputIndex)
          val observed = dut.io.out.peek().litValue

          checked += 1

          if (observed != expected(vector)) {
            mismatches += 1

            println(
              s"""
                 |Format:         ${format.name}
                 |A:              ${hexString(vector.a, format.width)}
                 |B:              ${hexString(vector.b, format.width)}
                 |TestFloat:      ${hexString(vector.expected, format.width)}
                 |Expected:       ${hexString(expected(vector), format.width)}
                 |Observed:       ${hexString(observed, format.width)}
                 |""".stripMargin
              )
          }
        }
      }

      println(
        s"[$description] ${format.name}: completed=$checked, " +
          s"passed=${checked - mismatches}, mismatches=$mismatches"
      )
    }
  }

  formats.foreach { format =>
    s"${format.name} at depth ${format.requestedMulStages}" in {
      run(
        format = format,
        vectors = loadVectors(format.resource),
        expected = vector => adaptedExpected(vector, format),
        description = "TestFloat FULL"
      )
    }
  }
}
