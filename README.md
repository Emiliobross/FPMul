Command for compiling SoftFloat and TestFloat:

make -C berkeley-softfloat-3/build/Linux-x86_64-GCC SPECIALIZE_TYPE=RISCV
make -C berkeley-testfloat-3/build/Linux-x86_64-GCC SPECIALIZE_TYPE=RISCV

Command for generating test files for each format:

./berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen -rnear_even -seed 1 f16_mul > f16_mul.txt
./berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen -rnear_even -seed 1 f32_mul > f32_mul.txt
./berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen -rnear_even -seed 1 f64_mul > f64_mul.txt

Command for running all tests:
./mill FPMul.test

The file that specifies the format and depth for the module is:
/FPMul/src/main/scala/fpmul/Main.scala

Command for generating the SystemVerilog file from the Scala/Chisel source:
./mill FPMul.run

Command for implementing the design and generating the reports with the buil.tcl file:
vivado -mode batch -source <path/to/build.tcl> -tclargs <top_module> <src_path> <fpga_part> <clock_period_ns> <run_name>
