// A7 matrix: ROW1..4 have 330-ohm pull-ups, COL1..4 have 4.7k pull-downs.
// Select one column low; the others stay high impedance. Never drive them high.
// With no selected contact, the row pull-up dominates the unselected column pull-down.
module LOONGSIM_KEYPAD_MODULE #(
    parameter integer COLUMN_TICKS = 100000, // 1 ms at the board's 100 MHz clock
    parameter integer DEBOUNCE_FRAMES = 5   // 5 complete, matching 4 ms scans
)(
    input clk,
    inout [3:0] columns,
    input [3:0] rows,
    output reg [15:0] pressed = 16'b0
);
  localparam integer TICK_BITS = COLUMN_TICKS > 1 ? $clog2(COLUMN_TICKS) : 1;
  localparam integer DEBOUNCE_BITS = DEBOUNCE_FRAMES > 1 ? $clog2(DEBOUNCE_FRAMES + 1) : 1;
  reg [TICK_BITS-1:0] ticks = 0;
  reg [1:0] column_index = 0;
  (* ASYNC_REG = "TRUE" *) reg [3:0] rows_meta = 4'b1111;
  (* ASYNC_REG = "TRUE" *) reg [3:0] rows_sync = 4'b1111;
  reg [15:0] scan_frame = 0;
  reg [15:0] completed_frame = 0;
  reg frame_check_pending = 1'b0;
  reg [15:0] candidate_frame = 0;
  reg candidate_valid = 1'b0;
  reg [15:0] previous_frame = 0;
  reg [DEBOUNCE_BITS-1:0] stable_frames = 0;
  reg [15:0] sampled_frame;
  reg ambiguous;
  reg [3:0] common_columns;
  integer row, first_row, second_row;

  assign columns[0] = column_index == 0 ? 1'b0 : 1'bz;
  assign columns[1] = column_index == 1 ? 1'b0 : 1'bz;
  assign columns[2] = column_index == 2 ? 1'b0 : 1'bz;
  assign columns[3] = column_index == 3 ? 1'b0 : 1'bz;

  // Key index = zero-based row * 4 + zero-based column.
  always @* begin
    sampled_frame = scan_frame;
    for (row = 0; row < 4; row = row + 1)
      sampled_frame[row*4 + column_index] = ~rows_sync[row];
  end

  always @* begin
    // A rectangle cannot distinguish three physical contacts from four without diodes.
    // Suppress ambiguous scans instead of presenting a phantom key to the circuit.
    ambiguous = 1'b0;
    common_columns = 0;
    for (first_row = 0; first_row < 4; first_row = first_row + 1) begin
      for (second_row = first_row + 1; second_row < 4; second_row = second_row + 1) begin
        common_columns = (completed_frame >> (first_row*4)) & (completed_frame >> (second_row*4));
        if ((common_columns & (common_columns - 1'b1)) != 0) ambiguous = 1'b1;
      end
    end
  end

  always @(posedge clk) begin
    frame_check_pending <= 1'b0;
    candidate_valid <= 1'b0;
    rows_meta <= rows;
    rows_sync <= rows_meta;
    if (ticks == COLUMN_TICKS - 1) begin
      ticks <= 0;
      column_index <= column_index + 1'b1;
      scan_frame <= sampled_frame;
      if (column_index == 3) begin
        completed_frame <= sampled_frame;
        frame_check_pending <= 1'b1;
      end
    end else ticks <= ticks + 1'b1;

    if (frame_check_pending) begin
      candidate_frame <= ambiguous ? 16'b0 : completed_frame;
      candidate_valid <= 1'b1;
    end
    if (candidate_valid) begin
      if (candidate_frame != previous_frame) begin
        previous_frame <= candidate_frame;
        stable_frames <= 1;
        if (DEBOUNCE_FRAMES <= 1) pressed <= candidate_frame;
      end else if (stable_frames < DEBOUNCE_FRAMES) begin
        stable_frames <= stable_frames + 1'b1;
        if (stable_frames == DEBOUNCE_FRAMES - 1) pressed <= candidate_frame;
      end
    end
  end
endmodule
