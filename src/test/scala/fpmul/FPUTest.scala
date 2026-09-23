// See README.md for license details.

package fpmul

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class BFloat16MulSpec extends AnyFreeSpec with Matchers with ChiselSim {

  // INFO: bfloat16 Tests:
  //
  // Special Values:
  //
  // NaNs 
  private val BFQNANPOS1 = "b0111111111000000".U(16.W)
  private val BFQNANNEG1 = "b1111111111000000".U(16.W)
  private val BFQNANPOS2 = "b0111111111010000".U(16.W)
  private val BFQNANNEG2 = "b1111111111000100".U(16.W)
  private val BFQNANNEG3 = "b1111111111000101".U(16.W)

  private val BFSNANPOS1 = "b0111111110100000".U(16.W)
  private val BFSNANPOS2 = "b0111111110010000".U(16.W)
  private val BFSNANNEG1 = "b1111111110001000".U(16.W)
  private val BFSNANNEG2 = "b1111111110000100".U(16.W)

  // INF
  private val BFINFPOS = "b0111111110000000".U(16.W)
  private val BFINFNEG = "b1111111110000000".U(16.W)

  // ZERO
  private val BFZEROPOS = "b0000000000000000".U(16.W)
  private val BFZERONEG = "b1000000000000000".U(16.W)

  // Subnormals
  private val BFSUBPOS1 = "b0000000001000000".U(16.W)
  private val BFSUBPOS2 = "b0000000001100000".U(16.W)
  private val BFSUBPOS3 = "b0000000001111100".U(16.W)
  private val BFSUBPOS4 = "b0000000001111110".U(16.W)

  private val BFSUBNEG1 = "b1000000001110000".U(16.W)
  private val BFSUBNEG2 = "b1000000001111000".U(16.W)
  private val BFSUBNEG3 = "b1000000001111111".U(16.W)

  def runMulTest(
      a: UInt,
      b: UInt,
      expected: UInt,
      expWidth: Int = 8,
      sigWidth: Int = 7,
      name: String
  ) = {
    s"Testing Multiplier Edge Cases for bfloat16: $name" in {
      simulate(new fpmul.FPMul(expWidth, sigWidth)) { dut =>
        dut.reset.poke(true.B)
        dut.clock.step()
        dut.reset.poke(false.B)
        dut.clock.step()

        dut.io.a.poke(a)
        dut.io.b.poke(b)

        dut.clock.step(6)

        dut.io.out.expect(expected)
      }
    }
  }

  // INFO: Run bfloat16 Tests:
  //
  // Edge Cases:

 runMulTest(BFQNANPOS1, BFQNANPOS1, BFQNANPOS1, 8, 7, "QNAN x QNAN 1")
  runMulTest(BFQNANPOS1, BFQNANNEG1, BFQNANPOS1, 8, 7, "QNAN x QNAN 2") 
  runMulTest(BFQNANPOS2, BFQNANNEG2, BFQNANPOS1, 8, 7, "QNAN x QNAN 3") 
  runMulTest(BFQNANNEG2, BFQNANNEG3, BFQNANPOS1, 8, 7, "QNAN x QNAN 4") 

  runMulTest(BFQNANPOS1, BFSNANPOS1, BFQNANPOS1, 8, 7, "QNAN x SNAN 1")
  runMulTest(BFQNANPOS1, BFSNANNEG2, BFQNANPOS1, 8, 7, "QNAN x SNAN 2") 
  runMulTest(BFQNANNEG1, BFSNANPOS1, BFQNANPOS1, 8, 7, "QNAN x SNAN 3") 
  runMulTest(BFQNANPOS2, BFSNANPOS2, BFQNANPOS1, 8, 7, "QNAN x SNAN 4")
  runMulTest(BFQNANNEG2, BFSNANNEG2, BFQNANPOS1, 8, 7, "QNAN x SNAN 5")
  runMulTest(BFQNANNEG3, BFSNANNEG2, BFQNANPOS1, 8, 7, "QNAN x SNAN 6")

  runMulTest(BFQNANPOS1, BFINFPOS, BFQNANPOS1, 8, 7, "QNAN x INF 1")
  runMulTest(BFQNANPOS1, BFINFNEG, BFQNANPOS1, 8, 7, "QNAN x INF 2") 
  runMulTest(BFQNANNEG1, BFINFPOS, BFQNANPOS1, 8, 7, "QNAN x INF 3") 
  runMulTest(BFQNANNEG1, BFINFNEG, BFQNANPOS1, 8, 7, "QNAN x INF 4") 

  runMulTest(BFQNANPOS1, BFZEROPOS, BFQNANPOS1, 8, 7, "QNAN x ZERO 1")
  runMulTest(BFQNANPOS2, BFZERONEG, BFQNANPOS1, 8, 7, "QNAN x ZERO 2") 
  runMulTest(BFQNANNEG1, BFZEROPOS, BFQNANPOS1, 8, 7, "QNAN x ZERO 3") 
  runMulTest(BFQNANNEG2, BFZERONEG, BFQNANPOS1, 8, 7, "QNAN x ZERO 4") 

  runMulTest(BFINFPOS, BFINFPOS, BFINFPOS, 8, 7, "INF x INF 1")
  runMulTest(BFINFPOS, BFINFNEG, BFINFNEG, 8, 7, "INF x INF 2")
  runMulTest(BFINFNEG, BFINFPOS, BFINFNEG, 8, 7, "INF x INF 3")
  runMulTest(BFINFNEG, BFINFNEG, BFINFPOS, 8, 7, "INF x INF 4")

  runMulTest(BFINFPOS, BFZEROPOS, BFQNANPOS1, 8, 7, "INF x ZERO 1")
  runMulTest(BFINFNEG, BFZEROPOS, BFQNANPOS1, 8, 7, "INF x ZERO 2")
  runMulTest(BFZEROPOS, BFINFPOS, BFQNANPOS1, 8, 7, "ZERO x INF 1")
  runMulTest(BFZERONEG, BFINFNEG, BFQNANPOS1, 8, 7, "ZERO x INF 2")

  runMulTest(BFSUBPOS1, 0x5e00.U(16.W), BFZEROPOS, 8, 7, "SUBNORMAL AS ZERO 1")
  runMulTest(0x5e00.U(16.W), BFSUBNEG1, BFZERONEG, 8, 7, "SUBNORMAL AS ZERO 2")

  runMulTest(BFSUBPOS1, BFSUBPOS1, BFZEROPOS, 8, 7, "SUBNORMAL x SUBNORMAL 1")
  runMulTest(BFSUBNEG1, BFSUBPOS2, BFZERONEG, 8, 7, "SUBNORMAL x SUBNORMAL 2")
  runMulTest(BFSUBPOS3, BFSUBNEG2, BFZERONEG, 8, 7, "SUBNORMAL x SUBNORMAL 3")
  runMulTest(BFSUBNEG3, BFSUBNEG1, BFZEROPOS, 8, 7, "SUBNORMAL x SUBNORMAL 4")

  runMulTest(0x7f7f.U(16.W), 0x7f7f.U(16.W), BFINFPOS, 8, 7, "Overflow Detection")

  runMulTest(0x008a.U(16.W), 0x008a.U(16.W), BFZEROPOS, 8, 7, "Underflow Detection")

  // Normal Cases:

  // 1.0 x 1.0
  runMulTest(0x3f80.U(16.W), 0x3f80.U(16.W), 0x3f80.U(16.W), 8, 7, "Normal x Normal 1")

  // 1.3046875 x 1.3046875 = 1.702209473
  runMulTest(0x3fa7.U(16.W), 0x3fa7.U(16.W), 0x3fda.U(16.W), 8, 7, "Normal x Normal 2")

  // 2.5 x 2.5 = 6.25
  runMulTest(0x4020.U(16.W), 0x4020.U(16.W), 0x40c8.U(16.W), 8, 7, "Normal x Normal 3")

  runMulTest(0xb4a6.U(16.W), 0xb4a6.U(16.W), 0x29d7.U(16.W), 8, 7, "Normal x Normal 4")
}
