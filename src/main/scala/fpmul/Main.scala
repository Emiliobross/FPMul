package fpmul 

import circt.stage.ChiselStage

object Main extends App {
  ChiselStage.emitSystemVerilogFile(
    new FPMul(expWidth = 8, sigWidth = 7, 1),
    args = Array("--target-dir", "generated/"),
    firtoolOpts = Array("-disable-all-randomization")
  )
}
