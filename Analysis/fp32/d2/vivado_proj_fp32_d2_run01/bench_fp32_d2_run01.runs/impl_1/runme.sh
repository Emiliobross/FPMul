#!/bin/bash

# 
# Vivado(TM)
# runme.sh: a Vivado-generated Runs Script for UNIX
# Copyright 1986-2022 Xilinx, Inc. All Rights Reserved.
# Copyright 2022-2026 Advanced Micro Devices, Inc. All Rights Reserved.
# 

if [ -z "$PATH" ]; then
  PATH=/home/leon/University/Bachelorarbeit/Xilinx/2026.1/Vitis/bin:/home/leon/University/Bachelorarbeit/Xilinx/2026.1/Vivado/ids_lite/ISE/bin/lin64:/home/leon/University/Bachelorarbeit/Xilinx/2026.1/Vivado/bin
else
  PATH=/home/leon/University/Bachelorarbeit/Xilinx/2026.1/Vitis/bin:/home/leon/University/Bachelorarbeit/Xilinx/2026.1/Vivado/ids_lite/ISE/bin/lin64:/home/leon/University/Bachelorarbeit/Xilinx/2026.1/Vivado/bin:$PATH
fi
export PATH

if [ -z "$LD_LIBRARY_PATH" ]; then
  LD_LIBRARY_PATH=
else
  LD_LIBRARY_PATH=:$LD_LIBRARY_PATH
fi
export LD_LIBRARY_PATH

HD_PWD='/home/leon/University/Bachelorarbeit/Analysis/fp32/d2/vivado_proj_fp32_d2_run01/bench_fp32_d2_run01.runs/impl_1'
cd "$HD_PWD"

HD_LOG=runme.log
/bin/touch $HD_LOG

ISEStep="./ISEWrap.sh"
EAStep()
{
     $ISEStep $HD_LOG "$@" >> $HD_LOG 2>&1
     if [ $? -ne 0 ]
     then
         exit
     fi
}

# pre-commands:
/bin/touch .init_design.begin.rst
EAStep vivado -log FPMul.vdi -applog -m64 -product Vivado -messageDb vivado.pb -mode batch -source FPMul.tcl -notrace


