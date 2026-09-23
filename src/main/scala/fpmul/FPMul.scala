package fpmul 

import chisel3._
import chisel3.util._
import chisel3.util.ShiftRegisterIntf

class CustomFloat(val expWidth: Int, val sigWidth: Int) extends Bundle {
  val isNaN = Bool()
  val isInf = Bool()
  val isZero = Bool()
  val sign = Bool()
  val sExp = SInt((expWidth + 2).W)
  val sig = UInt((sigWidth + 1).W)
}

object CustomFromRaw {
  def apply(expWidth: Int, sigWidth: Int, input: UInt): CustomFloat = {
    require(expWidth >= 2)
    require(sigWidth >= 1)

    val sign = input(expWidth + sigWidth)
    val exp = input(expWidth + sigWidth - 1, sigWidth)
    val sig = input(sigWidth - 1, 0)

    val sigZero = !sig.orR
    val isZero = !exp.orR

    val biasValue = (BigInt(1) << (expWidth - 1)) - 1
    val bias = biasValue.S((expWidth + 2).W)

    val out = Wire(new CustomFloat(expWidth, sigWidth))

    out.isNaN := exp.andR && !sigZero
    out.isInf := exp.andR && sigZero
    out.isZero := isZero
    out.sign := sign
    out.sExp := Cat(0.U(1.W), exp).asSInt - bias
    out.sig := Cat(1.U(1.W), sig)

    out
  }
}

class StageAReg(val expWidth: Int, val sigWidth: Int) extends Bundle {
  val tempSig = UInt((sigWidth * 2 + 2).W)
  val tempExp = SInt((expWidth + 2).W)
  val infInput = Bool()
  val zeroInput = Bool()
  val isNaN = Bool()
  val sign = Bool()
}

class StageBReg(val expWidth: Int, val sigWidth: Int) extends Bundle {
  val sig = UInt((sigWidth + 1).W)
  val tempExp2 = SInt((expWidth + 2).W)
  val isNaN = Bool()
  val isInf = Bool()
  val isZero = Bool()

  val guard = Bool()
  val sticky = Bool()

  val sign = Bool()
}

object FPMul {
  def inferMulStages(sigWidth: Int): Int = {
    if (sigWidth == 7) {
      3
    } else if (sigWidth == 23 || sigWidth == 10) {
      5
    } else {
      2
    }
  }
}

class FPMul(val expWidth: Int, val sigWidth: Int, requestedMulStages: Int = 0)
    extends Module {
  require(expWidth >= 2)
  require(sigWidth >= 1)

  val mulStages: Int =
    if (requestedMulStages > 0) {
      requestedMulStages
    } else {
      FPMul.inferMulStages(sigWidth)
    }

  val io = IO(new Bundle {
    val a = Input(UInt((expWidth + sigWidth + 1).W))
    val b = Input(UInt((expWidth + sigWidth + 1).W))
    val out = Output(UInt((expWidth + sigWidth + 1).W))
  })

  val biasValue: BigInt = (BigInt(1) << (expWidth - 1)) - 1
  val bias = biasValue.S((expWidth + 2).W)

  val aFloat = CustomFromRaw(expWidth, sigWidth, io.a)
  val bFloat = CustomFromRaw(expWidth, sigWidth, io.b)

  val sA =
    Reg(new StageAReg(expWidth, sigWidth))

  sA.tempSig := ShiftRegister(aFloat.sig * bFloat.sig, mulStages)

  sA.tempExp := ShiftRegister(aFloat.sExp + bFloat.sExp, mulStages)

  sA.infInput := ShiftRegister(aFloat.isInf || bFloat.isInf, mulStages)

  sA.zeroInput := ShiftRegister(aFloat.isZero || bFloat.isZero, mulStages)

  sA.isNaN := ShiftRegister(aFloat.isNaN || bFloat.isNaN, mulStages)

  sA.sign := ShiftRegister(aFloat.sign ^ bFloat.sign, mulStages)

  val renorm = sA.tempSig(sigWidth * 2 + 1)

  val tempExp2 = Wire(SInt((expWidth + 2).W))

  tempExp2 := Mux(
    renorm,
    sA.tempExp + 1.S,
    sA.tempExp
  )

  val selSig = Mux(
    renorm,
    sA.tempSig(
      sigWidth * 2 + 1,
      sigWidth + 1
    ),
    sA.tempSig(
      sigWidth * 2,
      sigWidth
    )
  )

  val guardBit = Mux(
    renorm,
    sA.tempSig(sigWidth),
    sA.tempSig(sigWidth - 1)
  )

  val stickyBit = Mux(
    renorm,
    sA.tempSig(sigWidth - 1, 0).orR,
    sA.tempSig(sigWidth - 2, 0).orR
  )

  val underflowNoRenorm = sA.tempExp < ((-bias) + 1.S)

  val underflowRenorm = sA.tempExp < (-bias)

  val isUnderflow = Mux(
    renorm,
    underflowRenorm,
    underflowNoRenorm
  )

  val overflowNoRenorm = sA.tempExp > bias

  val overflowRenorm = sA.tempExp >= bias

  val isOverflow = Mux(
    renorm,
    overflowRenorm,
    overflowNoRenorm
  )

  val sB = Reg(new StageBReg(expWidth, sigWidth))

  sB.sig := selSig

  sB.guard := guardBit

  sB.sticky := stickyBit

  sB.tempExp2 := tempExp2

  sB.isNaN := sA.isNaN || (sA.infInput && sA.zeroInput)

  sB.isInf := (sA.infInput && !sA.zeroInput) || isOverflow

  // Only an actual zero/FTZ input is committed to zero here.
  sB.isZero := sA.zeroInput || isUnderflow

  sB.sign := sA.sign

  val roundUp = sB.guard && (sB.sticky || sB.sig(0))

  val roundCarry = sB.guard && sB.sig.andR

  val roundedSig = sB.sig + roundUp.asUInt

  val expAfterRound = sB.tempExp2 + roundCarry.asUInt.zext

  val roundOverflow =
    !sB.isNaN && !sB.isInf && !sB.isZero && roundCarry && (sB.tempExp2 === bias)

  val finalIsInf = sB.isInf || roundOverflow

  val tempExpBiased =
    (expAfterRound + bias).asUInt

  val finalSign = Mux(sB.isNaN, 0.U(1.W), sB.sign)

  val finalExp = MuxCase(
    tempExpBiased(expWidth - 1, 0),
    Seq(
      (sB.isNaN || finalIsInf) -> ~0.U(expWidth.W),
      sB.isZero -> 0.U(expWidth.W)
    )
  )

  val normalSig =
    roundedSig(sigWidth - 1, 0)

  val finalSig = MuxCase(
    normalSig,
    Seq(
      sB.isNaN ->
        Cat(
          1.U(1.W),
          0.U((sigWidth - 1).W)
        ),
      (finalIsInf || sB.isZero) ->
        0.U(sigWidth.W)
    )
  )

  val outReg = RegNext(
    Cat(
      finalSign,
      finalExp,
      finalSig
    )
  )

  io.out :=
    outReg
}
