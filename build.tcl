set top_module   [lindex $argv 0]
set src_path     [lindex $argv 1]
set part         [lindex $argv 2]
set clk_period   [lindex $argv 3]
set run_name     [lindex $argv 5]

set project_dir  "./vivado_proj_${run_name}"
set report_dir   "./reports_${run_name}"
set project_name "bench_${run_name}"

file mkdir $report_dir

create_project $project_name $project_dir -part $part -force

add_files "$src_file/FPMul.sv"

set_property top $top_module [current_fileset]

set xdc_path "$project_dir/auto_clock.xdc"
set xdc [open $xdc_path w]
puts $xdc "create_clock -name sys_clk -period $clk_period \[get_ports clock\]"
close $xdc
add_files -fileset constrs_1 -norecurse $xdc_path

synth_design -top $top_module -part $part -mode out_of_context
opt_design
place_design
route_design

report_utilization -file "$report_dir/utilization.rpt"
report_timing_summary -max_paths 10 -file "$report_dir/timing_summary.rpt"
report_power -file "$report_dir/power.rpt"
report_clock_utilization -file "$report_dir/clock_utilization.rpt"

close_project
